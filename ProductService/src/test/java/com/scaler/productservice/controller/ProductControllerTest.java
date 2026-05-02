package com.scaler.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaler.productservice.dto.ProductRequestDto;
import com.scaler.productservice.dto.ProductResponseDto;
import com.scaler.productservice.dto.StockUpdateDto;
import com.scaler.productservice.exception.ForbiddenException;
import com.scaler.productservice.exception.ProductNotFoundException;
import com.scaler.productservice.model.Category;
import com.scaler.productservice.model.Product;
import com.scaler.productservice.security.RoleValidator;
import com.scaler.productservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── mocked beans ───────────────────────────────────────────────────────────

    // Must match the @Qualifier("productServiceImpl") in ProductController constructor
    @MockitoBean(name = "productServiceImpl") ProductService productService;
    @MockitoBean RoleValidator roleValidator;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private Product buildProduct(Long id) {
        Category cat = Category.builder().id(1L).name("Electronics").build();
        return Product.builder()
                .id(id)
                .title("iPhone 16 Pro")
                .description("Latest Apple flagship")
                .price(134900.0)
                .stockQuantity(50)
                .category(cat)
                .imageUrl("https://example.com/iphone.jpg")
                .build();
    }

    private ProductRequestDto buildRequest() {
        return new ProductRequestDto(
                "iPhone 16 Pro", 134900.0, "Latest Apple flagship",
                1L, "https://example.com/iphone.jpg", 50);
    }

    // ── GET /products ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /products")
    class GetAll {

        @Test
        @DisplayName("returns 200 OK with a list of all products")
        void returns200_withProductList() throws Exception {
            given(productService.getAllProducts())
                    .willReturn(List.of(buildProduct(1L), buildProduct(2L)));

            mockMvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].title").value("iPhone 16 Pro"));
        }

        @Test
        @DisplayName("returns 200 OK with empty list when no products exist")
        void returns200_withEmptyList() throws Exception {
            given(productService.getAllProducts()).willReturn(List.of());

            mockMvc.perform(get("/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ── GET /products/page ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /products/page")
    class GetPaged {

        @Test
        @DisplayName("returns 200 OK with paginated products")
        void returns200_withPage() throws Exception {
            PageRequest pageable = PageRequest.of(0, 10);
            given(productService.getAllProducts(any()))
                    .willReturn(new PageImpl<>(List.of(buildProduct(1L)), pageable, 1));

            mockMvc.perform(get("/products/page").param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ── GET /products/{id} ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /products/{id}")
    class GetById {

        @Test
        @DisplayName("returns 200 OK with product when found")
        void returns200_whenFound() throws Exception {
            given(productService.getProductById(1L)).willReturn(Optional.of(buildProduct(1L)));

            mockMvc.perform(get("/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("iPhone 16 Pro"))
                    .andExpect(jsonPath("$.price").value(134900.0))
                    .andExpect(jsonPath("$.category.name").value("Electronics"));
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when product does not exist")
        void returns404_whenNotFound() throws Exception {
            given(productService.getProductById(999L)).willReturn(Optional.empty());

            mockMvc.perform(get("/products/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Product not found with id: 999"));
        }
    }

    // ── GET /products/search ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /products/search")
    class Search {

        @Test
        @DisplayName("returns 200 OK with search results for given criteria")
        void returns200_withResults() throws Exception {
            PageRequest pageable = PageRequest.of(0, 10);
            given(productService.searchProducts(any(), any()))
                    .willReturn(new PageImpl<>(List.of(buildProduct(1L)), pageable, 1));

            mockMvc.perform(get("/products/search")
                            .param("title", "iPhone")
                            .param("minPrice", "1000")
                            .param("maxPrice", "200000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("iPhone 16 Pro"));
        }
    }

    // ── POST /products (ADMIN) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /products")
    class Create {

        @Test
        @DisplayName("returns 201 CREATED when ADMIN creates a product")
        void returns201_forAdmin() throws Exception {
            // RoleValidator mock does nothing by default → requireAdmin passes
            given(productService.createProduct(anyString(), anyDouble(), anyString(),
                    anyLong(), anyString(), anyInt())).willReturn(buildProduct(1L));

            mockMvc.perform(post("/products")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("iPhone 16 Pro"));
        }

        @Test
        @DisplayName("returns 403 FORBIDDEN when non-ADMIN tries to create a product")
        void returns403_forUser() throws Exception {
            willThrow(new ForbiddenException("Access denied: ADMIN role required"))
                    .given(roleValidator).requireAdmin("USER");

            mockMvc.perform(post("/products")
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when title is blank")
        void returns400_whenTitleBlank() throws Exception {
            ProductRequestDto invalid = new ProductRequestDto(
                    "", 100.0, "desc", null, null, 5);

            mockMvc.perform(post("/products")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when price is negative")
        void returns400_whenPriceNegative() throws Exception {
            ProductRequestDto invalid = new ProductRequestDto(
                    "Title", -1.0, "desc", null, null, 5);

            mockMvc.perform(post("/products")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /products/{id} (ADMIN) ─────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /products/{id}")
    class UpdateProduct {

        @Test
        @DisplayName("returns 200 OK with updated product when ADMIN updates")
        void returns200_whenUpdated() throws Exception {
            given(productService.updateProduct(eq(1L), anyString(), anyDouble(),
                    anyString(), any(), anyString(), anyInt()))
                    .willReturn(Optional.of(buildProduct(1L)));

            mockMvc.perform(put("/products/1")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when product does not exist")
        void returns404_whenNotFound() throws Exception {
            given(productService.updateProduct(eq(999L), anyString(), anyDouble(),
                    anyString(), any(), anyString(), anyInt()))
                    .willReturn(Optional.empty());

            mockMvc.perform(put("/products/999")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 403 FORBIDDEN for non-ADMIN")
        void returns403_forUser() throws Exception {
            willThrow(new ForbiddenException("Access denied: ADMIN role required"))
                    .given(roleValidator).requireAdmin("USER");

            mockMvc.perform(put("/products/1")
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ── PATCH /products/{id}/stock (ADMIN) ────────────────────────────────────

    @Nested
    @DisplayName("PATCH /products/{id}/stock")
    class UpdateStock {

        @Test
        @DisplayName("returns 200 OK with updated product when ADMIN updates stock")
        void returns200_whenUpdated() throws Exception {
            given(productService.updateStock(eq(1L), eq(100)))
                    .willReturn(Optional.of(buildProduct(1L)));

            StockUpdateDto dto = new StockUpdateDto(100);

            mockMvc.perform(patch("/products/1/stock")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when stock quantity is negative")
        void returns400_whenQuantityNegative() throws Exception {
            StockUpdateDto dto = new StockUpdateDto(-1);

            mockMvc.perform(patch("/products/1/stock")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when product does not exist")
        void returns404_whenNotFound() throws Exception {
            given(productService.updateStock(eq(999L), anyInt())).willReturn(Optional.empty());

            mockMvc.perform(patch("/products/999/stock")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StockUpdateDto(10))))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /products/{id} (ADMIN) ──────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("returns 204 NO_CONTENT when product is soft-deleted successfully")
        void returns204_whenDeleted() throws Exception {
            given(productService.deleteProduct(1L)).willReturn(true);

            mockMvc.perform(delete("/products/1").header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when product does not exist")
        void returns404_whenNotFound() throws Exception {
            given(productService.deleteProduct(999L)).willReturn(false);

            mockMvc.perform(delete("/products/999").header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 403 FORBIDDEN when non-ADMIN tries to delete")
        void returns403_forUser() throws Exception {
            willThrow(new ForbiddenException("Access denied: ADMIN role required"))
                    .given(roleValidator).requireAdmin("USER");

            mockMvc.perform(delete("/products/1").header("X-User-Role", "USER"))
                    .andExpect(status().isForbidden());
        }
    }
}
