package com.hpethani.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productTitle;
    private double priceAtAddition;  // price when item was added to cart
    private int quantity;
    private double subtotal;         // priceAtAddition * quantity
}

