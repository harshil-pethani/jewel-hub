package com.hpethani.order_service.dto;

import com.hpethani.order_service.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Used by ADMIN to update order status (confirm, ship, deliver).
 * Customer can only cancel — handled by a separate endpoint.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateOrderStatusRequest {

    @NotNull(message = "Status is required")
    private OrderStatus status;
}

