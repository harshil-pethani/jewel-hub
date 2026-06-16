package com.hpethani.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CartResponse {
    private Long cartId;
    private String userEmail;
    private List<CartItemResponse> items;
    private int totalItems;       // total number of items in cart
    private double totalAmount;   // sum of all subtotals
}

