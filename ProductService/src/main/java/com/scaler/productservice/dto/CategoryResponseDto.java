package com.scaler.productservice.dto;

import com.scaler.productservice.model.Category;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponseDto {

    private Long id;
    private String name;
    private String description;

    public static CategoryResponseDto from(Category category) {
        return CategoryResponseDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}