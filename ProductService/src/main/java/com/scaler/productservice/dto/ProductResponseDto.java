package com.scaler.productservice.dto;

import com.scaler.productservice.model.Product;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductResponseDto {

    private Long id;
    private String title;
    private String description;
    private double price;
    private CategoryResponseDto category;
    private String imageUrl;
    private int stockQuantity;

    public static ProductResponseDto from(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .category(product.getCategory() != null ? CategoryResponseDto.from(product.getCategory()) : null)
                .imageUrl(product.getImageUrl())
                .stockQuantity(product.getStockQuantity())
                .build();
    }
}