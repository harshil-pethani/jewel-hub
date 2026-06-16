package com.hpethani.order_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for placing an order.
 * Cart items are fetched automatically from cart-service — no need to send them manually.
 *
 * TODO: Add paymentMethod field when online payment is integrated.
 *       For now only CASH_ON_DELIVERY is supported.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlaceOrderRequest {

    @NotBlank(message = "FullName is required")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "AddressType is required")
    private String addressType;

    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    @NotBlank(message = "Pincode is required")
    private String pincode;

    // TODO: private PaymentMethod paymentMethod; — add when payment gateway integrated
}

