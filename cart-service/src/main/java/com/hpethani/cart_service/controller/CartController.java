package com.hpethani.cart_service.controller;

import com.hpethani.cart_service.dto.AddCartItemRequest;
import com.hpethani.cart_service.dto.CartResponse;
import com.hpethani.cart_service.dto.UpdateCartItemRequest;
import com.hpethani.cart_service.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    /**
     * GET /api/cart
     * Returns the current user's cart.
     * User identity comes from the "email" header forwarded by API Gateway from the JWT.
     */
    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            @RequestHeader("email") String userEmail, @RequestHeader("userid") String userId) {
        return ResponseEntity.ok(cartService.getCart(userEmail, userId));
    }

    /**
     * POST /api/cart/items
     * Adds a product to the cart.
     * Checks product existence (product-service) and stock availability (inventory-service).
     * If product already in cart, increases quantity.
     *
     * Body: { "productId": 1, "quantity": 2 }
     */
    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader("email") String userEmail,
            @RequestHeader("userid") String userId,
            @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.addItem(userEmail, userId, request));
    }

    /**
     * PUT /api/cart/items/{productId}
     * Updates quantity of a specific item in the cart.
     * Re-validates stock availability for the new quantity.
     *
     * Body: { "quantity": 3 }
     */
    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItemQuantity(
            @RequestHeader("email") String userEmail,
            @RequestHeader("userid") String userId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItemQuantity(userEmail, userId, productId, request));
    }

    /**
     * DELETE /api/cart/items/{productId}
     * Removes a specific product from the cart.
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader("email") String userEmail,
            @RequestHeader("userid") String userId,
            @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(userEmail, userId, productId));
    }

    /**
     * DELETE /api/cart
     * Clears all items but keeps the cart row — user manually emptied cart.
     */
    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @RequestHeader("email") String userEmail, @RequestHeader("userid") String userId) {
        cartService.clearCart(userEmail, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/cart/session
     * Deletes the entire cart row from DB.
     * Called by order-service internally after a successful order is placed.
     */
    @DeleteMapping("/session")
    public ResponseEntity<Void> deleteCart(
            @RequestHeader("email") String userEmail, @RequestHeader("userid") String userId) {
        cartService.deleteCart(userEmail, userId);
        return ResponseEntity.noContent().build();
    }
}