package com.hpethani.inventory_service.service;

import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.inventory_service.dto.InventoryRequest;
import com.hpethani.inventory_service.dto.InventoryResponse;
import com.hpethani.inventory_service.entity.Inventory;
import com.hpethani.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

//    @Transactional(readOnly = true)
//    public List<InventoryResponse> isInStock(List<String> skuCodes) {
//        return inventoryRepository.findBySkuCodeIn(skuCodes).stream()
//                .map(inventory ->
//                    InventoryResponse.builder()
//                            .skuCode(inventory.getSkuCode())
//                            .inStock(inventory.getQuantity() > 0)
//                            .build()
//                ).toList();
//    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BadRequestException("Inventory not found for productId: " + productId));
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .build();
    }

    @Transactional
    public void createInventory(InventoryRequest request) {

        Inventory inventory = Inventory.builder()
                        .productId(request.getProductId())
                        .availableQuantity(request.getAvailableQuantity())
                        .reservedQuantity(0)
                        .build();

        inventoryRepository.save(inventory);
    }


    @Transactional
    public void deleteInventory(Long productId) {

        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow(() -> new BadRequestException("Inventory item not found for productId: " + productId));

        inventoryRepository.delete(inventory);
    }

    @Transactional
    public void syncInventory(InventoryRequest inventoryRequest) {
        Long productId = inventoryRequest.getProductId();
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow(() -> new BadRequestException("Inventory item not found for productId: " + productId));

        inventory.setAvailableQuantity(inventoryRequest.getAvailableQuantity());
        inventoryRepository.save(inventory);
    }

    /**
     * Deducts stock when an order is placed.
     * Throws BadRequestException if requested quantity exceeds available stock.
     *
     * TODO: When payment gateway is added, consider a two-phase approach:
     *       1. reserve() — move quantity to reservedQuantity (stock held, not deducted)
     *       2. confirm() — deduct on payment success
     *       3. release() — release reserved on payment failure/timeout
     */
    @Transactional
    public void deductStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BadRequestException("Inventory not found for productId: " + productId));

        if (inventory.getAvailableQuantity() < quantity) {
            throw new BadRequestException("Insufficient stock for productId: " + productId +
                    ". Available: " + inventory.getAvailableQuantity() + ", Requested: " + quantity);
        }

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
        inventoryRepository.save(inventory);
        log.info("Deducted {} units from productId={}, remaining={}", quantity, productId, inventory.getAvailableQuantity());
    }

    /**
     * Restores stock when an order is cancelled.
     */
    @Transactional
    public void restoreStock(Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new BadRequestException("Inventory not found for productId: " + productId));

        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventoryRepository.save(inventory);
        log.info("Restored {} units to productId={}, new total={}", quantity, productId, inventory.getAvailableQuantity());
    }
}
