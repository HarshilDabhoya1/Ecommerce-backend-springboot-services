package com.scaler.productservice.service;

import com.scaler.productservice.dto.ProductSearchCriteria;
import com.scaler.productservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<Product> getAllProducts();

    Page<Product> getAllProducts(Pageable pageable);

    Page<Product> searchProducts(ProductSearchCriteria criteria, Pageable pageable);

    Optional<Product> getProductById(Long id);

    Product createProduct(String title, double price, String description, Long categoryId, String imageUrl, int stockQuantity);

    Optional<Product> updateProduct(Long id, String title, double price, String description, Long categoryId, String imageUrl, int stockQuantity);

    Optional<Product> updateStock(Long id, int quantity);

    boolean deleteProduct(Long id);
}