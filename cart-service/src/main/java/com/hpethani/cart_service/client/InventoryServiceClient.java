package com.hpethani.cart_service.client;

import com.hpethani.cart_service.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for inventory-service.
 * Used to check product stock availability before adding to cart.
 * Uses Eureka service name — direct internal call, no JWT needed.
 */
@FeignClient(name = "INVENTORY-SERVICE")
public interface InventoryServiceClient {

    @GetMapping("/api/inventory")
    InventoryResponse getInventory(@RequestParam Long productId);
}
