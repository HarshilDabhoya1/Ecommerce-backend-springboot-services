package com.scaler.productservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scaler.productservice.model.Category;
import com.scaler.productservice.model.Product;
import lombok.Data;

@Data
public class FakeStoreProductDto {

    private Long id;
    private String title;
    private double price;
    private String description;
    private String category;

    @JsonProperty("image")
    private String imageUrl;

    public Product toProduct() {
        Category cat = new Category();
        cat.setName(category);

        Product product = new Product();
        product.setId(id);
        product.setTitle(title);
        product.setPrice(price);
        product.setDescription(description);
        product.setCategory(cat);
        product.setImageUrl(imageUrl);
        return product;
    }
}