package com.scaler.reviewservice.controller;

import com.scaler.reviewservice.dto.ReviewRequestDto;
import com.scaler.reviewservice.dto.ReviewResponseDto;
import com.scaler.reviewservice.model.Review;
import com.scaler.reviewservice.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponseDto> addReview(
            @PathVariable Long productId, @Valid @RequestBody ReviewRequestDto dto) {
        Review review = Review.builder()
                .rating(dto.getRating())
                .comment(dto.getComment())
                .reviewerName(dto.getReviewerName())
                .build();
        ReviewResponseDto created = ReviewResponseDto.from(reviewService.createReview(productId, review));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<ReviewResponseDto>> getReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReviewResponseDto> reviews = reviewService
                .getReviewsByProduct(productId, PageRequest.of(page, size))
                .map(ReviewResponseDto::from);
        return ResponseEntity.ok(reviews);
    }
}