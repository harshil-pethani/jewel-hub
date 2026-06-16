package com.hpethani.inventory_service.controller;

import com.hpethani.inventory_service.dto.InventoryRequest;
import com.hpethani.inventory_service.dto.InventoryResponse;
import com.hpethani.inventory_service.dto.InventorySyncRequest;
import com.hpethani.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<InventoryResponse> getInventory(@RequestParam Long productId) {
        return ResponseEntity.ok(inventoryService.getInventory(productId));
    }

    @PostMapping
    public ResponseEntity<Void> createInventory(@RequestBody InventoryRequest request) {
        inventoryService.createInventory(request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteInventory(@RequestParam Long productId) {
        inventoryService.deleteInventory(productId);
        return ResponseEntity.ok().build();
    }

    @PutMapping
    public ResponseEntity<Void> syncInventory(@RequestBody InventoryRequest inventoryRequest) {
        inventoryService.syncInventory(inventoryRequest);
        return ResponseEntity.ok().build();
    }

    // Called by order-service when order is placed — deducts available stock
    // TODO: When payment gateway added, consider reserving stock first (PENDING payment)
    //       and deducting only after payment confirmation
    @PutMapping("/deduct")
    public ResponseEntity<Void> deductStock(@RequestParam Long productId, @RequestParam int quantity) {
        inventoryService.deductStock(productId, quantity);
        return ResponseEntity.ok().build();
    }

    // Called by order-service when order is cancelled — restores available stock
    @PutMapping("/restore")
    public ResponseEntity<Void> restoreStock(@RequestParam Long productId, @RequestParam int quantity) {
        inventoryService.restoreStock(productId, quantity);
        return ResponseEntity.ok().build();
    }
}