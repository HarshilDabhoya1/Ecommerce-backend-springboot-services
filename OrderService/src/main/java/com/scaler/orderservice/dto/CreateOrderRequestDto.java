package com.scaler.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequestDto {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "userEmail is required")
    @Email(message = "userEmail must be a valid email address")
    private String userEmail;

    @NotEmpty(message = "items must not be empty")
    @Valid
    private List<OrderItemRequestDto> items;
}
