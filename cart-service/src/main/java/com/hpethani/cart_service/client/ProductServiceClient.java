package com.hpethani.cart_service.client;

import com.hpethani.cart_service.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for product-service.
 * Used to fetch product details (title, price) when adding item to cart.
 * Uses Eureka service name — direct internal call, no JWT needed.
 */
@FeignClient(name = "PRODUCT-SERVICE")
public interface ProductServiceClient {

    @GetMapping("/api/product/{productId}")
    ProductResponse getProductById(@PathVariable Long productId);
}

