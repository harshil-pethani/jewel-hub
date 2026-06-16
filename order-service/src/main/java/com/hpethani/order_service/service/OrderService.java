package com.hpethani.order_service.service;

import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.commonconfig.exception.ResourceNotFoundException;
import com.hpethani.order_service.client.CartServiceClient;
import com.hpethani.order_service.client.InventoryServiceClient;
import com.hpethani.order_service.dto.*;
import com.hpethani.order_service.entity.Order;
import com.hpethani.order_service.entity.OrderItem;
import com.hpethani.order_service.enums.OrderStatus;
import com.hpethani.order_service.enums.PaymentMethod;
import com.hpethani.order_service.enums.PaymentStatus;
import com.hpethani.order_service.repository.OrderRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartServiceClient cartServiceClient;
    private final InventoryServiceClient inventoryServiceClient;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Places a COD order from the user's current cart.
     *
     * Flow:
     * 1. Fetch cart from cart-service
     * 2. Validate cart is not empty
     * 3. Build order + order items (with price snapshots from cart)
     * 4. Save order (PENDING status, COD payment)
     * 5. Deduct stock in inventory-service for each item
     * 6. Delete cart from cart-service
     *
     * TODO: When payment gateway is added:
     *   - Step 5 should RESERVE stock (not deduct) until payment confirmation
     *   - Add payment initiation step
     *   - Deduct stock only on payment success webhook
     *   - Release reserved stock on payment failure/timeout
     */
    @Transactional
    public OrderResponse placeOrder(String userEmail, String userId, PlaceOrderRequest request) {

        // Step 1: Fetch cart
        CartResponse cart = fetchCart(userEmail, userId);

        // Step 2: Validate cart not empty
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Cannot place order — cart is empty");
        }

        // Step 3: Build order with PENDING status + COD payment
        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userEmail(userEmail)
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .paymentStatus(PaymentStatus.PENDING)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .addressType(request.getAddressType())
                .shippingAddress(request.getShippingAddress())
                .pincode(request.getPincode())
                .totalAmount(cart.getTotalAmount())
                .orderItems(new ArrayList<>())
                .build();

        // Step 4: Save order first to get ID, then link items (same pattern as product-service)
        Order savedOrder = orderRepository.save(order);

        // Step 5: Build order items with owning side (order) set to saved order
        // IMPORTANT: must use mutable ArrayList — Hibernate calls .clear() on this
        // collection during cascade operations. .toList() returns an unmodifiable list
        // and throws UnsupportedOperationException when Hibernate tries to manage it.
        List<OrderItem> items = cart.getItems().stream()
                .map(cartItem -> OrderItem.builder()
                        .productId(cartItem.getProductId())
                        .productTitle(cartItem.getProductTitle())
                        .priceAtOrder(cartItem.getPriceAtAddition())
                        .quantity(cartItem.getQuantity())
                        .order(savedOrder)   // owning side — sets order_id FK correctly
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        savedOrder.setOrderItems(items);
        final Order finalOrder = orderRepository.save(savedOrder);

        log.info("Order {} created for user={} with {} items, total={}",
                finalOrder.getOrderNumber(), userEmail, items.size(), finalOrder.getTotalAmount());

        // Step 6: Deduct inventory stock for each item
        // TODO: When payment gateway added, reserve stock here instead of deducting
        List<OrderItem> deductedItems = new ArrayList<>();
        try {
            for (OrderItem item : finalOrder.getOrderItems()) {
                inventoryServiceClient.deductStock(item.getProductId(), item.getQuantity());
                deductedItems.add(item);
            }
        } catch (Exception e) {
            // Rollback already-deducted items before re-throwing
            log.error("Inventory deduction failed — rolling back {} deducted items", deductedItems.size());
            for (OrderItem deducted : deductedItems) {
                try {
                    inventoryServiceClient.restoreStock(deducted.getProductId(), deducted.getQuantity());
                } catch (Exception restoreEx) {
                    log.error("Failed to restore stock for productId={}: {}", deducted.getProductId(), restoreEx.getMessage());
                }
            }
            throw new BadRequestException("Order failed — inventory update error: " + e.getMessage());
        }

        // Step 7: Clear cart after successful order
        // TODO: If payment gateway added, clear cart only after payment confirmation
        try {
            cartServiceClient.deleteCart(userEmail, userId);
            log.info("Cart cleared for user={} after order {}", userEmail, finalOrder.getOrderNumber());
        } catch (Exception e) {
            // Non-critical — order is already placed, cart cleanup failure should not fail the order
            log.warn("Failed to clear cart for user={} after order {}: {}", userEmail, finalOrder.getOrderNumber(), e.getMessage());
        }

        return mapToOrderResponse(finalOrder);
    }

    /**
     * Returns paginated list of orders for the current user.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(String userEmail, String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return orderRepository.findByUserEmail(userEmail, pageable)
                .map(this::mapToOrderResponse);
    }

    /**
     * Returns details of a single order.
     * Ensures user can only see their own orders.
     */
    @Cacheable(
            value = "order",
            key = "#orderId"
    )
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String userEmail, Long orderId) {
        Order order = orderRepository.findByIdAndUserEmail(orderId, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    /**
     * Cancels an order.
     * Only PENDING or CONFIRMED orders can be cancelled.
     * Restores inventory stock on cancellation.
     *
     * TODO: When payment gateway added — trigger refund if payment was made.
     */
    @CachePut(
            value = "order",
            key = "#orderId"
    )
    @Transactional
    public OrderResponse cancelOrder(String userEmail, Long orderId) {
        Order order = orderRepository.findByIdAndUserEmail(orderId, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot cancel order " + order.getOrderNumber() +
                    " — already " + order.getStatus().name().toLowerCase());
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Order " + order.getOrderNumber() + " is already cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);

        // Restore stock for each cancelled item
        for (OrderItem item : order.getOrderItems()) {
            try {
                inventoryServiceClient.restoreStock(item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.error("Failed to restore stock for productId={} on cancel: {}", item.getProductId(), e.getMessage());
                // Continue cancelling even if restore fails — log for manual fix
            }
        }

        // TODO: If payment gateway added — trigger refund here if paymentStatus == PAID
        // TODO: Update paymentStatus to REFUNDED after refund is processed

        log.info("Order {} cancelled for user={}", order.getOrderNumber(), userEmail);
        return mapToOrderResponse(orderRepository.save(order));
    }

    /**
     * Updates order status — ADMIN only (enforced via API Gateway role check).
     * e.g. PENDING → CONFIRMED → SHIPPED → DELIVERED
     */
    @Transactional
    @CachePut(
            value = "order",
            key = "#orderId"
    )
    public OrderResponse updateOrderStatus(Long orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot update status of a cancelled order");
        }

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Order is already delivered");
        }

        // Mark as PAID when delivered (COD — payment collected on delivery)
        // TODO: For online payment — set PAID on payment gateway webhook, not on delivery
        if (request.getStatus() == OrderStatus.DELIVERED) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        order.setStatus(request.getStatus());
        log.info("Order {} status updated to {} by admin", order.getOrderNumber(), request.getStatus());
        return mapToOrderResponse(orderRepository.save(order));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private CartResponse fetchCart(String userEmail, String userId) {
        try {
            return cartServiceClient.getCart(userEmail, userId);
        } catch (FeignException.NotFound e) {
            throw new BadRequestException("Cart not found for user: " + userEmail);
        } catch (Exception e) {
            log.error("Failed to reach cart-service for user={}: {}", userEmail, e.getMessage());
            throw new BadRequestException("Unable to fetch cart — cart-service unavailable");
        }
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productTitle(item.getProductTitle())
                        .priceAtOrder(item.getPriceAtOrder())
                        .quantity(item.getQuantity())
                        .subtotal(item.getPriceAtOrder() * item.getQuantity())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userEmail(order.getUserEmail())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .shippingAddress(order.getShippingAddress())
                .totalAmount(order.getTotalAmount())
                .orderItems(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
