package com.scaler.orderservice.dto;

import com.scaler.orderservice.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderStatusDto {

    @NotNull(message = "status is required")
    private OrderStatus status;
}
