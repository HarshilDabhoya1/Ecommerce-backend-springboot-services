package com.scaler.productservice.dto;

import com.scaler.productservice.model.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDto {

    @NotBlank(message = "Title must not be blank")
    private String title;

    @PositiveOrZero(message = "Price must be greater than or equal to 0")
    private double price;

    private String description;

    private Long categoryId;

    private String imageUrl;

    @Min(value = 0, message = "Stock quantity must be >= 0")
    private int stockQuantity;
}