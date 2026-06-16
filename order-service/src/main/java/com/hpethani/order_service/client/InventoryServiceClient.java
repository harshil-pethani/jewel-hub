package com.hpethani.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for inventory-service.
 * Used to deduct stock when an order is placed.
 * Uses Eureka service name — direct internal call, no JWT needed.
 */
@FeignClient(name = "INVENTORY-SERVICE")
public interface InventoryServiceClient {

    // Deduct available stock when order is placed
    // TODO: When payment gateway is added, consider reserving stock first (PENDING)
    //       and deducting only after payment confirmation
    @PutMapping("/api/inventory/deduct")
    void deductStock(@RequestParam Long productId, @RequestParam int quantity);

    // Restore stock when order is cancelled
    @PutMapping("/api/inventory/restore")
    void restoreStock(@RequestParam Long productId, @RequestParam int quantity);
}

