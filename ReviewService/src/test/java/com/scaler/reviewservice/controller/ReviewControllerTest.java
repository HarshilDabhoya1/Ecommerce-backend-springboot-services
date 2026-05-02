package com.scaler.reviewservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaler.reviewservice.dto.ReviewRequestDto;
import com.scaler.reviewservice.model.Review;
import com.scaler.reviewservice.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@DisplayName("ReviewController")
class ReviewControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ReviewService reviewService;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private Review buildReview(Long id, Long productId, int rating) {
        return Review.builder()
                .id(id)
                .productId(productId)
                .rating(rating)
                .comment("Really good product.")
                .reviewerName("Harshil Dabhoya")
                .createdAt(Instant.now())
                .build();
    }

    private ReviewRequestDto buildRequest(int rating, String reviewerName) {
        return new ReviewRequestDto(rating, "Really good product.", reviewerName);
    }

    // ── POST /products/{productId}/reviews ─────────────────────────────────────

    @Nested
    @DisplayName("POST /products/{productId}/reviews")
    class AddReview {

        @Test
        @DisplayName("returns 201 CREATED with review body on success")
        void returns201_onSuccess() throws Exception {
            given(reviewService.createReview(eq(1L), any(Review.class)))
                    .willReturn(buildReview(10L, 1L, 5));

            mockMvc.perform(post("/products/1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest(5, "Harshil Dabhoya"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.productId").value(1))
                    .andExpect(jsonPath("$.rating").value(5))
                    .andExpect(jsonPath("$.reviewerName").value("Harshil Dabhoya"));
        }

        @ParameterizedTest(name = "rating = {0}")
        @ValueSource(ints = {0, 6, -1, 100})
        @DisplayName("returns 400 BAD_REQUEST when rating is out of valid range [1-5]")
        void returns400_whenRatingOutOfRange(int invalidRating) throws Exception {
            mockMvc.perform(post("/products/1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest(invalidRating, "Tester"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when reviewerName is blank")
        void returns400_whenReviewerNameBlank() throws Exception {
            ReviewRequestDto invalid = new ReviewRequestDto(4, "Good product.", "");

            mockMvc.perform(post("/products/1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when reviewerName is missing (null)")
        void returns400_whenReviewerNameNull() throws Exception {
            ReviewRequestDto invalid = new ReviewRequestDto(4, "Good product.", null);

            mockMvc.perform(post("/products/1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("passes the correct productId from the path variable to the service")
        void passesCorrectProductId() throws Exception {
            given(reviewService.createReview(eq(42L), any(Review.class)))
                    .willReturn(buildReview(1L, 42L, 4));

            mockMvc.perform(post("/products/42/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest(4, "Reviewer"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.productId").value(42));
        }

        @ParameterizedTest(name = "rating = {0}")
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("accepts all valid ratings in range [1-5]")
        void accepts_allValidRatings(int validRating) throws Exception {
            given(reviewService.createReview(eq(1L), any()))
                    .willReturn(buildReview(1L, 1L, validRating));

            mockMvc.perform(post("/products/1/reviews")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest(validRating, "Tester"))))
                    .andExpect(status().isCreated());
        }
    }

    // ── GET /products/{productId}/reviews ──────────────────────────────────────

    @Nested
    @DisplayName("GET /products/{productId}/reviews")
    class GetReviews {

        @Test
        @DisplayName("returns 200 OK with paginated reviews for the product")
        void returns200_withReviews() throws Exception {
            PageRequest pageable = PageRequest.of(0, 10);
            given(reviewService.getReviewsByProduct(eq(1L), any()))
                    .willReturn(new PageImpl<>(
                            List.of(buildReview(1L, 1L, 5), buildReview(2L, 1L, 3)),
                            pageable, 2));

            mockMvc.perform(get("/products/1/reviews").param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].rating").value(5))
                    .andExpect(jsonPath("$.content[1].rating").value(3));
        }

        @Test
        @DisplayName("returns 200 OK with empty page when product has no reviews")
        void returns200_withEmptyPage() throws Exception {
            PageRequest pageable = PageRequest.of(0, 10);
            given(reviewService.getReviewsByProduct(eq(999L), any()))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));

            mockMvc.perform(get("/products/999/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("uses default pagination (page=0, size=10) when params are absent")
        void usesDefaultPagination_whenParamsAbsent() throws Exception {
            PageRequest defaultPageable = PageRequest.of(0, 10);
            given(reviewService.getReviewsByProduct(eq(1L), eq(defaultPageable)))
                    .willReturn(new PageImpl<>(List.of(), defaultPageable, 0));

            mockMvc.perform(get("/products/1/reviews"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("respects custom page and size parameters")
        void respectsCustomPagination() throws Exception {
            PageRequest customPageable = PageRequest.of(2, 5);
            given(reviewService.getReviewsByProduct(eq(1L), eq(customPageable)))
                    .willReturn(new PageImpl<>(List.of(), customPageable, 0));

            mockMvc.perform(get("/products/1/reviews")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk());
        }
    }
}
