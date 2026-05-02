package com.scaler.productservice.controller;

import com.scaler.productservice.dto.CategoryRequestDto;
import com.scaler.productservice.dto.CategoryResponseDto;
import com.scaler.productservice.exception.CategoryNotFoundException;
import com.scaler.productservice.model.Category;
import com.scaler.productservice.security.RoleValidator;
import com.scaler.productservice.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final RoleValidator roleValidator;

    public CategoryController(CategoryService categoryService, RoleValidator roleValidator) {
        this.categoryService = categoryService;
        this.roleValidator   = roleValidator;
    }

    // ── Read endpoints — any authenticated user ────────────────────────────────

    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getAllCategories() {
        List<CategoryResponseDto> categories = categoryService.getAllCategories()
                .stream()
                .map(CategoryResponseDto::from)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDto> getCategoryById(@PathVariable Long id) {
        Category category = categoryService.getCategoryById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        return ResponseEntity.ok(CategoryResponseDto.from(category));
    }

    // ── Write endpoints — ADMIN only ───────────────────────────────────────────

    @PostMapping
    public ResponseEntity<CategoryResponseDto> createCategory(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CategoryRequestDto dto) {
        roleValidator.requireAdmin(role);
        Category category = Category.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
        Category created = categoryService.createCategory(category);
        if (created == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponseDto.from(created));
    }
}
