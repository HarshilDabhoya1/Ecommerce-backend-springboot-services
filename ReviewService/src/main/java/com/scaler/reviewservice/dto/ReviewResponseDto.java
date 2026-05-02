package com.scaler.reviewservice.dto;

import com.scaler.reviewservice.model.Review;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ReviewResponseDto {

    private Long id;
    private Long productId;
    private int rating;
    private String comment;
    private String reviewerName;
    private Instant createdAt;

    public static ReviewResponseDto from(Review review) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .rating(review.getRating())
                .comment(review.getComment())
                .reviewerName(review.getReviewerName())
                .createdAt(review.getCreatedAt())
                .build();
    }
}