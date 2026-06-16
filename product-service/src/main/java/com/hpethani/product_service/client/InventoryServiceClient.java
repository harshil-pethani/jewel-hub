package com.hpethani.product_service.client;

import com.hpethani.product_service.dto.InventoryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for inventory-service.
 *
 * Uses Eureka service name (INVENTORY-SERVICE) NOT a hardcoded url.
 * Jewellery has no variants — inventory is tracked per productId directly.
 */
@FeignClient(name = "INVENTORY-SERVICE")
public interface InventoryServiceClient {

    // Called after product is created — registers product stock in inventory
    @PostMapping("/api/inventory")
    void createInventory(@RequestBody InventoryRequest request);

    // Called after product is deleted — removes product stock from inventory
    @DeleteMapping("/api/inventory")
    void deleteInventory(@RequestParam("productId") Long productId);

    // Called after product is updated — syncs new quantity in inventory
    @PutMapping("/api/inventory")
    void syncInventory(@RequestBody InventoryRequest request);
}
