package com.hpethani.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartItemResponse {
    private Long productId;
    private String productTitle;
    private double priceAtAddition;
    private int quantity;
    private double subtotal;
}

