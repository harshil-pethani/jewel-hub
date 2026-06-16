package com.hpethani.order_service.enums;

/**
 * Payment status for the order.
 *
 * COD flow:  PENDING → PAID (on delivery)
 *
 * TODO: Online payment flow: PENDING → PAID / FAILED → REFUNDED
 */
public enum PaymentStatus {
    PENDING,   // payment not yet received (COD = always starts here)
    PAID,      // payment received (COD = collected on delivery)
    FAILED,    // TODO: payment gateway failure
    REFUNDED   // TODO: refunded after cancellation
}

