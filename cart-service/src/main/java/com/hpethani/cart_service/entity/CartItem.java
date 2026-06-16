package com.hpethani.cart_service.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * CartItem — one row per product in the cart.
 *
 * Stores a PRICE SNAPSHOT at time of addition because jewellery prices can change.
 * If product price changes later, the cart shows what the user originally saw.
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    // Snapshot of product title at time of addition — avoids a product-service call on every cart fetch
    @Column(nullable = false)
    private String productTitle;

    // Snapshot of price at time of addition
    @Column(nullable = false)
    private double priceAtAddition;

    @Column(nullable = false)
    private int quantity;

    // Owning side — controls the cart_id FK in cart_items table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
}

