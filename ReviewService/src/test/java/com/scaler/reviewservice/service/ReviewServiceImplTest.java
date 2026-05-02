package com.scaler.reviewservice.service;

import com.scaler.reviewservice.model.Review;
import com.scaler.reviewservice.repository.ReviewRepository;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewServiceImpl")
class ReviewServiceImplTest {

    @Mock ReviewRepository reviewRepository;

    @InjectMocks ReviewServiceImpl reviewService;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private Review buildReview(Long id, Long productId, int rating) {
        return Review.builder()
                .id(id)
                .productId(productId)
                .rating(rating)
                .comment("Great product!")
                .reviewerName("Harshil Dabhoya")
                .createdAt(Instant.now())
                .build();
    }

    // ── createReview ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createReview")
    class CreateReview {

        @Test
        @DisplayName("sets the productId on the review entity before saving")
        void setsProductId_beforeSaving() {
            // Review arrives from the controller without productId set (path variable is separate)
            Review incoming = Review.builder()
                    .rating(5).comment("Amazing!").reviewerName("Harshil").build();
            Review saved = buildReview(1L, 42L, 5);
            given(reviewRepository.save(any())).willReturn(saved);

            reviewService.createReview(42L, incoming);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should().save(captor.capture());
            // The productId from the path variable must be stamped onto the entity
            assertThat(captor.getValue().getProductId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("returns the saved review returned by the repository")
        void returnsSavedReview() {
            Review incoming = Review.builder()
                    .rating(4).comment("Good.").reviewerName("Test User").build();
            Review saved = buildReview(10L, 1L, 4);
            given(reviewRepository.save(any())).willReturn(saved);

            Review result = reviewService.createReview(1L, incoming);

            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getRating()).isEqualTo(4);
        }

        @Test
        @DisplayName("preserves reviewer name and comment from the input")
        void preservesReviewDetails() {
            Review incoming = Review.builder()
                    .rating(3).comment("Decent.").reviewerName("Jane Doe").build();
            given(reviewRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            reviewService.createReview(5L, incoming);

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should().save(captor.capture());
            Review captured = captor.getValue();
            assertThat(captured.getReviewerName()).isEqualTo("Jane Doe");
            assertThat(captured.getComment()).isEqualTo("Decent.");
            assertThat(captured.getRating()).isEqualTo(3);
        }
    }

    // ── getReviewsByProduct ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getReviewsByProduct")
    class GetReviews {

        @Test
        @DisplayName("returns paginated reviews for the given productId")
        void returnsPaginatedReviews() {
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Review> page = new PageImpl<>(
                    List.of(buildReview(1L, 42L, 5), buildReview(2L, 42L, 4)),
                    pageable, 2);
            given(reviewRepository.findByProductId(42L, pageable)).willReturn(page);

            Page<Review> result = reviewService.getReviewsByProduct(42L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).extracting(Review::getRating)
                    .containsExactly(5, 4);
        }

        @Test
        @DisplayName("returns empty page when no reviews exist for the product")
        void returnsEmptyPage_whenNoReviews() {
            PageRequest pageable = PageRequest.of(0, 10);
            given(reviewRepository.findByProductId(999L, pageable))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<Review> result = reviewService.getReviewsByProduct(999L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("passes pageable correctly to the repository")
        void passesPaginationToRepository() {
            PageRequest pageable = PageRequest.of(2, 5);
            given(reviewRepository.findByProductId(eq(1L), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));

            reviewService.getReviewsByProduct(1L, pageable);

            then(reviewRepository).should().findByProductId(1L, pageable);
        }
    }
}
