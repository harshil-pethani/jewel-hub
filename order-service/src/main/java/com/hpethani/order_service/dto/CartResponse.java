package com.hpethani.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Subset of CartResponse from cart-service.
 * Only fields needed by order-service.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartResponse {
    private Long cartId;
    private String userEmail;
    private List<CartItemResponse> items;
    private double totalAmount;
}

