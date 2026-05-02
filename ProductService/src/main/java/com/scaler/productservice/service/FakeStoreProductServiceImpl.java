package com.scaler.productservice.service;

import com.scaler.productservice.dto.FakeStoreProductDto;
import com.scaler.productservice.dto.ProductSearchCriteria;
import com.scaler.productservice.model.Product;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Qualifier("fakeStoreProductService")
public class FakeStoreProductServiceImpl implements ProductService {

    private static final String FAKE_STORE_BASE_URL = "https://fakestoreapi.com/products";

    private final RestTemplate restTemplate;

    public FakeStoreProductServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Product> getAllProducts() {
        FakeStoreProductDto[] response = restTemplate.getForObject(FAKE_STORE_BASE_URL, FakeStoreProductDto[].class);
        if (response == null) return List.of();
        return Arrays.stream(response).map(FakeStoreProductDto::toProduct).toList();
    }

    @Override
    public Page<Product> getAllProducts(Pageable pageable) {
        List<Product> all = getAllProducts();
        return toPage(all, pageable);
    }

    @Override
    public Optional<Product> getProductById(Long id) {
        try {
            FakeStoreProductDto response = restTemplate.getForObject(FAKE_STORE_BASE_URL + "/" + id, FakeStoreProductDto.class);
            return Optional.ofNullable(response).map(FakeStoreProductDto::toProduct);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Product createProduct(String title, double price, String description, Long categoryId, String imageUrl, int stockQuantity) {
        FakeStoreProductDto request = new FakeStoreProductDto();
        request.setTitle(title);
        request.setPrice(price);
        request.setDescription(description);
        request.setImageUrl(imageUrl);

        return request.toProduct();
    }

    @Override
    public Optional<Product> updateProduct(Long id, String title, double price, String description, Long categoryId, String imageUrl, int stockQuantity) {
        FakeStoreProductDto request = new FakeStoreProductDto();
        request.setId(id);
        request.setTitle(title);
        request.setPrice(price);
        request.setDescription(description);
        request.setImageUrl(imageUrl);

        return Optional.of(request.toProduct());
    }

    @Override
    public Optional<Product> updateStock(Long id, int quantity) {
        // FakeStore API has no stock concept — return product with quantity set
        return getProductById(id).map(p -> {
            p.setStockQuantity(quantity);
            return p;
        });
    }

    @Override
    public Page<Product> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        List<Product> filtered = getAllProducts().stream()
                .filter(p -> criteria.getTitle() == null
                        || p.getTitle().toLowerCase().contains(criteria.getTitle().toLowerCase()))
                .filter(p -> criteria.getCategory() == null || (p.getCategory() != null
                        && p.getCategory().getName().toLowerCase().contains(criteria.getCategory().toLowerCase())))
                .filter(p -> criteria.getMinPrice() == null || p.getPrice() >= criteria.getMinPrice())
                .filter(p -> criteria.getMaxPrice() == null || p.getPrice() <= criteria.getMaxPrice())
                .toList();
        return toPage(filtered, pageable);
    }

    @Override
    public boolean deleteProduct(Long id) {
        return true;
    }

    private Page<Product> toPage(List<Product> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        List<Product> slice = start >= list.size() ? List.of() : list.subList(start, end);
        return new PageImpl<>(slice, pageable, list.size());
    }
}