package com.hpethani.order_service.enums;

/**
 * Order lifecycle statuses.
 *
 * Flow:
 *   PENDING → CONFIRMED → SHIPPED → DELIVERED
 *      ↓           ↓
 *   CANCELLED   CANCELLED  (cannot cancel once SHIPPED)
 */
public enum OrderStatus {
    PENDING,      // order placed, awaiting confirmation
    CONFIRMED,    // confirmed by admin/warehouse
    SHIPPED,      // out for delivery
    DELIVERED,    // received by customer
    CANCELLED     // cancelled by customer or admin
}

