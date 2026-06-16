package com.hpethani.order_service.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row per product in the order.
 * Price and title are snapshots at the time of ordering.
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    // Snapshot — product title at time of order
    @Column(nullable = false)
    private String productTitle;

    // Snapshot — price at time of order (not affected by future price changes)
    @Column(nullable = false)
    private double priceAtOrder;

    @Column(nullable = false)
    private int quantity;

    // Owning side — controls order_id FK in order_items table
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
}

