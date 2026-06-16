package com.hpethani.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sent to inventory-service when a jewellery product is created/updated.
 * Jewellery has no size/color variants — inventory is tracked per product directly.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryRequest {
    private Long productId;
    private Integer availableQuantity;
}