package com.hpethani.order_service.controller;

import com.hpethani.order_service.dto.OrderResponse;
import com.hpethani.order_service.dto.PlaceOrderRequest;
import com.hpethani.order_service.dto.UpdateOrderStatusRequest;
import com.hpethani.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/order
     * Places a COD order from the user's current cart.
     * Cart items are fetched automatically — client only provides shipping address.
     *
     * Body: { "shippingAddress": "123 Main St, Mumbai" }
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader("email") String userEmail,
            @RequestHeader("userId") String userId,
            @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(userEmail, userId, request));
    }

    /**
     * GET /api/order
     * Returns paginated list of the current user's orders (newest first).
     */
    @GetMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Page<OrderResponse>> getUserOrders(
            @RequestHeader("email") String userEmail,
            @RequestHeader("userId") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getUserOrders(userEmail, userId, page, size));
    }

    /**
     * GET /api/order/{orderId}
     * Returns details of a specific order.
     * User can only access their own orders.
     */
    @GetMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<OrderResponse> getOrderById(
            @RequestHeader("email") String userEmail,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(userEmail, orderId));
    }

    /**
     * PUT /api/order/{orderId}/cancel
     * Cancels an order (PENDING or CONFIRMED only).
     * Automatically restores inventory stock.
     * TODO: Trigger refund when payment gateway is integrated.
     */
    @PutMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<OrderResponse> cancelOrder(
            @RequestHeader("email") String userEmail,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(userEmail, orderId));
    }

    /**
     * PUT /api/order/{orderId}/status
     * Updates order status — ADMIN only (role enforced by API Gateway).
     * Flow: PENDING → CONFIRMED → SHIPPED → DELIVERED
     *
     * Body: { "status": "CONFIRMED" }
     */
    @PutMapping("/{orderId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")  // only ADMIN can update order status
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, request));
    }
}