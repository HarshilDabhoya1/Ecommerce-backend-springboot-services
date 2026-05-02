package com.scaler.productservice.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateDto {

    @Min(value = 0, message = "Stock quantity must be >= 0")
    private int quantity;
}