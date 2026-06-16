package com.hpethani.order_service.client;

import com.hpethani.order_service.dto.CartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for cart-service.
 * Used to fetch cart items when placing an order, and clear cart after order is placed.
 * Uses Eureka service name — direct internal call, no JWT needed.
 */
@FeignClient(name = "CART-SERVICE")
public interface CartServiceClient {

    // Fetch user's cart — "email" header identifies the user (same as gateway forwards)
    @GetMapping("/api/cart")
    CartResponse getCart(@RequestHeader("email") String userEmail, @RequestHeader("userid") String userId);

    // Delete entire cart row after order is successfully placed
    @DeleteMapping("/api/cart/session")
    void deleteCart(@RequestHeader("email") String userEmail, @RequestHeader("userid") String userId);
}

