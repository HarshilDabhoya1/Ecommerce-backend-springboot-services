package com.scaler.productservice.service;

import com.scaler.productservice.dto.ProductSearchCriteria;
import com.scaler.productservice.exception.CategoryNotFoundException;
import com.scaler.productservice.model.Category;
import com.scaler.productservice.model.Product;
import com.scaler.productservice.repository.CategoryRepository;
import com.scaler.productservice.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductServiceImpl")
class ProductServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock CategoryRepository categoryRepository;

    @InjectMocks ProductServiceImpl productService;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private Category buildCategory(Long id, String name) {
        return Category.builder().id(id).name(name).build();
    }

    private Product buildProduct(Long id, String title, Category category) {
        return Product.builder()
                .id(id)
                .title(title)
                .description("A fine product")
                .price(999.00)
                .stockQuantity(50)
                .category(category)
                .imageUrl("https://example.com/img.jpg")
                .build();
    }

    // ── getAllProducts ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllProducts")
    class GetAll {

        @Test
        @DisplayName("returns all products from the repository (cache hit/miss handled by @Cacheable)")
        void returnsList() {
            Category cat = buildCategory(1L, "Electronics");
            List<Product> products = List.of(
                    buildProduct(1L, "iPhone 16", cat),
                    buildProduct(2L, "MacBook Pro", cat));
            given(productRepository.findAll()).willReturn(products);

            List<Product> result = productService.getAllProducts();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Product::getTitle)
                    .containsExactly("iPhone 16", "MacBook Pro");
        }

        @Test
        @DisplayName("returns paginated products")
        void returnsPage() {
            Category cat = buildCategory(1L, "Electronics");
            PageRequest pageable = PageRequest.of(0, 5);
            Page<Product> page = new PageImpl<>(List.of(buildProduct(1L, "iPhone 16", cat)), pageable, 1);
            given(productRepository.findAll(pageable)).willReturn(page);

            Page<Product> result = productService.getAllProducts(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ── getProductById ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductById")
    class GetById {

        @Test
        @DisplayName("returns Optional containing product when found")
        void found() {
            Product product = buildProduct(1L, "iPhone 16", buildCategory(1L, "Electronics"));
            given(productRepository.findById(1L)).willReturn(Optional.of(product));

            Optional<Product> result = productService.getProductById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getTitle()).isEqualTo("iPhone 16");
        }

        @Test
        @DisplayName("returns empty Optional when product does not exist")
        void notFound() {
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            Optional<Product> result = productService.getProductById(999L);

            assertThat(result).isEmpty();
        }
    }

    // ── searchProducts ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("searchProducts")
    class Search {

        @Test
        @DisplayName("delegates to repository with a Specification built from criteria")
        void delegatesToRepository() {
            Category cat = buildCategory(1L, "Electronics");
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(buildProduct(1L, "iPhone 16", cat)), pageable, 1);
            given(productRepository.findAll(any(Specification.class), eq(pageable))).willReturn(page);

            ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                    .title("iPhone").category("Electronics")
                    .minPrice(100.0).maxPrice(200000.0).build();

            Page<Product> result = productService.searchProducts(criteria, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            then(productRepository).should().findAll(any(Specification.class), eq(pageable));
        }
    }

    // ── createProduct ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProduct")
    class Create {

        @Test
        @DisplayName("saves and returns a new product with resolved category")
        void savesProduct_withResolvedCategory() {
            Category cat = buildCategory(1L, "Electronics");
            given(categoryRepository.findById(1L)).willReturn(Optional.of(cat));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Product result = productService.createProduct(
                    "iPhone 16", 99900.0, "Latest iPhone", 1L, "https://img.jpg", 10);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            then(productRepository).should().save(captor.capture());
            Product saved = captor.getValue();

            assertThat(saved.getTitle()).isEqualTo("iPhone 16");
            assertThat(saved.getPrice()).isEqualTo(99900.0);
            assertThat(saved.getStockQuantity()).isEqualTo(10);
            assertThat(saved.getCategory()).isEqualTo(cat);
        }

        @Test
        @DisplayName("creates product without category when categoryId is null")
        void savesProduct_withNullCategory() {
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            productService.createProduct("Generic Item", 500.0, "No category", null, null, 5);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            then(productRepository).should().save(captor.capture());
            assertThat(captor.getValue().getCategory()).isNull();
            then(categoryRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("throws CategoryNotFoundException when categoryId points to non-existent category")
        void throwsCategoryNotFound_whenCategoryMissing() {
            given(categoryRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    productService.createProduct("Item", 100.0, "desc", 99L, null, 1))
                    .isInstanceOf(CategoryNotFoundException.class);

            then(productRepository).should(never()).save(any());
        }
    }

    // ── updateProduct ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProduct")
    class Update {

        @Test
        @DisplayName("updates all fields and returns the updated product when found")
        void updatesProduct_whenFound() {
            Category oldCat = buildCategory(1L, "Electronics");
            Category newCat = buildCategory(2L, "Gadgets");
            Product existing = buildProduct(1L, "Old Title", oldCat);
            given(productRepository.findById(1L)).willReturn(Optional.of(existing));
            given(categoryRepository.findById(2L)).willReturn(Optional.of(newCat));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Optional<Product> result = productService.updateProduct(
                    1L, "New Title", 149900.0, "Updated desc", 2L, "https://new-img.jpg", 20);

            assertThat(result).isPresent();
            Product updated = result.get();
            assertThat(updated.getTitle()).isEqualTo("New Title");
            assertThat(updated.getPrice()).isEqualTo(149900.0);
            assertThat(updated.getStockQuantity()).isEqualTo(20);
            assertThat(updated.getCategory()).isEqualTo(newCat);
        }

        @Test
        @DisplayName("returns empty Optional when product does not exist")
        void returnsEmpty_whenNotFound() {
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            Optional<Product> result = productService.updateProduct(
                    999L, "Title", 0, "desc", null, null, 0);

            assertThat(result).isEmpty();
            then(productRepository).should(never()).save(any());
        }
    }

    // ── updateStock ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStock")
    class UpdateStock {

        @Test
        @DisplayName("updates stockQuantity and returns the product when found")
        void updatesStock_whenFound() {
            Product product = buildProduct(1L, "iPhone 16", buildCategory(1L, "Electronics"));
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Optional<Product> result = productService.updateStock(1L, 200);

            assertThat(result).isPresent();
            assertThat(result.get().getStockQuantity()).isEqualTo(200);
        }

        @Test
        @DisplayName("allows setting stock to zero (product out of stock)")
        void allowsZeroStock() {
            Product product = buildProduct(1L, "iPhone 16", buildCategory(1L, "Electronics"));
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Optional<Product> result = productService.updateStock(1L, 0);

            assertThat(result).isPresent();
            assertThat(result.get().getStockQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns empty Optional when product does not exist")
        void returnsEmpty_whenNotFound() {
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            Optional<Product> result = productService.updateStock(999L, 50);

            assertThat(result).isEmpty();
            then(productRepository).should(never()).save(any());
        }
    }

    // ── deleteProduct ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProduct")
    class Delete {

        @Test
        @DisplayName("stamps deletedAt (soft delete) and returns true when product exists")
        void softDeletes_andReturnsTrue() {
            Product product = buildProduct(1L, "iPhone 16", buildCategory(1L, "Electronics"));
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            boolean result = productService.deleteProduct(1L);

            assertThat(result).isTrue();

            // The product must have a non-null deletedAt timestamp (soft delete)
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            then(productRepository).should().save(captor.capture());
            assertThat(captor.getValue().getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("returns false without touching the DB when product does not exist")
        void returnsFalse_whenNotFound() {
            given(productRepository.findById(999L)).willReturn(Optional.empty());

            boolean result = productService.deleteProduct(999L);

            assertThat(result).isFalse();
            then(productRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("does NOT hard-delete — row still exists in DB after deleteProduct")
        void doesNotHardDelete() {
            Product product = buildProduct(1L, "iPhone 16", buildCategory(1L, "Electronics"));
            given(productRepository.findById(1L)).willReturn(Optional.of(product));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            productService.deleteProduct(1L);

            // save() is called (soft delete) — deleteById() must NEVER be called
            then(productRepository).should(never()).deleteById(any());
        }
    }
}
