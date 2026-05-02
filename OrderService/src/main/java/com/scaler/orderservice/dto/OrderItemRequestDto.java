package com.scaler.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class OrderItemRequestDto {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotBlank(message = "productTitle is required")
    private String productTitle;

    @Min(value = 1, message = "quantity must be at least 1")
    private int quantity;

    @Positive(message = "unitPrice must be positive")
    private double unitPrice;
}
