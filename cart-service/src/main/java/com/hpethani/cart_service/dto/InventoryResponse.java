package com.hpethani.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subset of InventoryResponse from inventory-service.
 * Only fields needed by cart-service — availability check before adding to cart.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponse {
    private Long productId;
    private Integer availableQuantity;
}

