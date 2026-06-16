package com.hpethani.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productTitle;
    private double priceAtOrder;
    private int quantity;
    private double subtotal;   // priceAtOrder * quantity
}

