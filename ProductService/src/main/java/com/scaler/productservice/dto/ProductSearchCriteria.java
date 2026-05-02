package com.scaler.productservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductSearchCriteria {
    private String title;
    private String category;
    private Double minPrice;
    private Double maxPrice;
}
