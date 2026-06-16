package com.hpethani.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryRequest {
    private Long productId;
    private Integer availableQuantity;
    // true = newly created product, false = updating existing product's quantity
    private Boolean newProduct;
}