package com.hpethani.cart_service.service;

import com.hpethani.cart_service.client.InventoryServiceClient;
import com.hpethani.cart_service.client.ProductServiceClient;
import com.hpethani.cart_service.dto.*;
import com.hpethani.cart_service.entity.Cart;
import com.hpethani.cart_service.entity.CartItem;
import com.hpethani.cart_service.repository.CartRepository;
import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.commonconfig.exception.ResourceNotFoundException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns the cart for the logged-in user.
     * If no cart exists yet, returns an empty cart response (cart is created lazily on first add).
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "cart",
            key = "#userId"
    )
    public CartResponse getCart(String userEmail, String userId) {
        Optional<Cart> cart = cartRepository.findByUserEmail(userEmail);
        if (cart.isEmpty()) {
            // Return empty cart — don't persist until user adds something
            return CartResponse.builder()
                    .userEmail(userEmail)
                    .items(List.of())
                    .totalItems(0)
                    .totalAmount(0.0)
                    .build();
        }
        return mapToCartResponse(cart.get());
    }

    /**
     * Adds a product to the user's cart.
     * - Fetches product details (title, price) from product-service
     * - Checks inventory availability from inventory-service
     * - If product already in cart, increases quantity
     * - Stores price snapshot at time of addition
     */
    @Transactional
    @CachePut(
            value = "cart",
            key = "#userId"
    )
    public CartResponse addItem(String userEmail, String userId, AddCartItemRequest request) {
        // Step 1: Fetch product details — title and price snapshot
        ProductResponse product = fetchProduct(request.getProductId());

        // Step 2: Check inventory availability
        checkInventoryAvailability(request.getProductId(), request.getQuantity());

        // Step 3: Get or create cart for this user
        Cart cart = cartRepository.findByUserEmail(userEmail)
                .orElseGet(() -> {
                    log.info("Creating new cart for user: {}", userEmail);
                    return cartRepository.save(Cart.builder().userEmail(userEmail).build());
                });

        // Step 4: Check if product already exists in cart → increase quantity
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.getProductId()))
                .findFirst();

        if (existingItem.isPresent()) {
            int newQuantity = existingItem.get().getQuantity() + request.getQuantity();
            // Re-check inventory for the total quantity needed
            checkInventoryAvailability(request.getProductId(), newQuantity);
            existingItem.get().setQuantity(newQuantity);
            log.info("Updated quantity for productId={} in cart for user={}", request.getProductId(), userEmail);
        } else {
            // Step 5: Add new CartItem — set cart (owning side) to ensure cart_id FK is populated
            double price = product.getDiscountedPrice() > 0
                    ? product.getDiscountedPrice()
                    : product.getBasePrice();

            CartItem newItem = CartItem.builder()
                    .productId(request.getProductId())
                    .productTitle(product.getTitle())
                    .priceAtAddition(price)
                    .quantity(request.getQuantity())
                    .cart(cart)          // owning side — sets cart_id FK
                    .build();

            cart.getItems().add(newItem);
            log.info("Added productId={} to cart for user={}", request.getProductId(), userEmail);
        }

        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart);
    }

    /**
     * Updates the quantity of an existing cart item.
     * Re-checks inventory for the new quantity.
     */
    @Transactional
    @CachePut(
            value = "cart",
            key = "#userId"
    )
    public CartResponse updateItemQuantity(String userEmail, String userId, Long productId, UpdateCartItemRequest request) {
        Cart cart = getCartOrThrow(userEmail);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found in cart"));

        // Check inventory for updated quantity
        checkInventoryAvailability(productId, request.getQuantity());

        item.setQuantity(request.getQuantity());
        log.info("Updated productId={} quantity to {} for user={}", productId, request.getQuantity(), userEmail);

        return mapToCartResponse(cartRepository.save(cart));
    }

    /**
     * Removes a specific product from the cart.
     */
    @Transactional
    @CachePut(
            value = "cart",
            key = "#userId"
    )
    public CartResponse removeItem(String userEmail, String userId, Long productId) {
        Cart cart = getCartOrThrow(userEmail);

        boolean removed = cart.getItems().removeIf(item -> item.getProductId().equals(productId));

        if (!removed) {
            throw new ResourceNotFoundException("Product " + productId + " not found in cart");
        }

        log.info("Removed productId={} from cart for user={}", productId, userEmail);
        return mapToCartResponse(cartRepository.save(cart));
    }

    /**
     * Clears all items from the cart but keeps the cart row in DB.
     * Called when user manually empties their cart.
     * Next time user adds an item, the existing cart row is reused (no new row created).
     */
    @Transactional
    @CachePut(
            value = "cart",
            key = "#userId"
    )
    public void clearCart(String userEmail, String userId) {
        Cart cart = getCartOrThrow(userEmail);
        cart.getItems().clear();
        cartRepository.save(cart);
        log.info("Cleared cart items for user={}", userEmail);
    }

    /**
     * Deletes the entire cart row from DB.
     * Called internally by order-service after a successful order is placed.
     * Next time user adds an item, a fresh cart is created.
     */
    @Transactional
    @CacheEvict(
            value = "cart",
            key = "#userId"
    )
    public void deleteCart(String userEmail, String userId) {
        Cart cart = getCartOrThrow(userEmail);
        cartRepository.delete(cart);
        log.info("Deleted cart for user={} after order placed", userEmail);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Fetches product from product-service via Feign.
     * Throws BadRequestException if product is not found or service is down.
     */
    private ProductResponse fetchProduct(Long productId) {
        try {
            return productServiceClient.getProductById(productId);
        } catch (FeignException.NotFound e) {
            throw new BadRequestException("Product not found with id: " + productId);
        } catch (Exception e) {
            log.error("Failed to reach product-service for productId={}: {}", productId, e.getMessage());
            throw new BadRequestException("Unable to fetch product details — product-service unavailable");
        }
    }

    private Cart getCartOrThrow(String userEmail) {
        return cartRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userEmail));
    }

    /**
     * Checks inventory-service to ensure enough stock is available.
     * Throws BadRequestException if out of stock or service is down.
     */
    private void checkInventoryAvailability(Long productId, int requiredQuantity) {
        try {
            InventoryResponse inventory = inventoryServiceClient.getInventory(productId);
            if (inventory.getAvailableQuantity() < requiredQuantity) {
                throw new BadRequestException(
                        "Insufficient stock for product " + productId +
                        ". Available: " + inventory.getAvailableQuantity() +
                        ", Requested: " + requiredQuantity
                );
            }
        } catch (BadRequestException e) {
            throw e; // re-throw our own exception
        } catch (FeignException.NotFound e) {
            throw new BadRequestException("Inventory not found for product: " + productId);
        } catch (Exception e) {
            log.error("Failed to reach inventory-service for productId={}: {}", productId, e.getMessage());
            throw new BadRequestException("Unable to check stock — inventory-service unavailable");
        }
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private CartResponse mapToCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::mapToCartItemResponse)
                .toList();

        double totalAmount = itemResponses.stream()
                .mapToDouble(CartItemResponse::getSubtotal)
                .sum();

        return CartResponse.builder()
                .cartId(cart.getId())
                .userEmail(cart.getUserEmail())
                .items(itemResponses)
                .totalItems(itemResponses.size())
                .totalAmount(totalAmount)
                .build();
    }

    private CartItemResponse mapToCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productTitle(item.getProductTitle())
                .priceAtAddition(item.getPriceAtAddition())
                .quantity(item.getQuantity())
                .subtotal(item.getPriceAtAddition() * item.getQuantity())
                .build();
    }
}
