package com.hpethani.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subset of ProductResponse from product-service.
 * Only fields needed by cart-service — title and price for snapshot + validation.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponse {
    private Long id;
    private String title;
    private double basePrice;
    private double discountedPrice;
}

