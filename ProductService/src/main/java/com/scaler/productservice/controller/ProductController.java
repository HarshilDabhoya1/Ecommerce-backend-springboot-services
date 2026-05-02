package com.scaler.productservice.controller;

import com.scaler.productservice.dto.*;
import com.scaler.productservice.exception.ProductNotFoundException;
import com.scaler.productservice.model.Product;
import com.scaler.productservice.security.RoleValidator;
import com.scaler.productservice.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final RoleValidator roleValidator;

    public ProductController(@Qualifier("productServiceImpl") ProductService productService,
                             RoleValidator roleValidator) {
        this.productService = productService;
        this.roleValidator  = roleValidator;
    }

    // ── Read endpoints — any authenticated user ────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getAllProducts() {
        List<ProductResponseDto> products = productService.getAllProducts()
                .stream()
                .map(ProductResponseDto::from)
                .toList();
        return ResponseEntity.ok(products);
    }

    // Supports: ?page=0&size=10&sort=price,asc&sort=title,desc
    @GetMapping("/page")
    public ResponseEntity<Page<ProductResponseDto>> getAllProductsPaged(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ProductResponseDto> products = productService
                .getAllProducts(pageable)
                .map(ProductResponseDto::from);
        return ResponseEntity.ok(products);
    }

    // Supports: ?title=x&category=y&minPrice=1&maxPrice=100&page=0&size=10&sort=price,asc&sort=title,desc
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponseDto>> searchProducts(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                .title(title).category(category)
                .minPrice(minPrice).maxPrice(maxPrice).build();

        Page<ProductResponseDto> result = productService
                .searchProducts(criteria, pageable)
                .map(ProductResponseDto::from);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ResponseEntity.ok(ProductResponseDto.from(product));
    }

    // ── Write endpoints — ADMIN only ───────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody ProductRequestDto dto) {
        roleValidator.requireAdmin(role);
        Product created = productService.createProduct(dto.getTitle(),
                                                    dto.getPrice(),
                                                    dto.getDescription(),
                                                    dto.getCategoryId(),
                                                    dto.getImageUrl(),
                                                    dto.getStockQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponseDto.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody ProductRequestDto dto) {
        roleValidator.requireAdmin(role);
        Product updated = productService.updateProduct(id, dto.getTitle(),
                                                    dto.getPrice(),
                                                    dto.getDescription(),
                                                    dto.getCategoryId(),
                                                    dto.getImageUrl(),
                                                    dto.getStockQuantity())
                                        .orElseThrow(() -> new ProductNotFoundException(id));
        return ResponseEntity.ok(ProductResponseDto.from(updated));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductResponseDto> updateStock(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody StockUpdateDto dto) {
        roleValidator.requireAdmin(role);
        return productService.updateStock(id, dto.getQuantity())
                .map(ProductResponseDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role) {
        roleValidator.requireAdmin(role);
        if (!productService.deleteProduct(id)) {
            throw new ProductNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
