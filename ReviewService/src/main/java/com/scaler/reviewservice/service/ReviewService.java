package com.scaler.reviewservice.service;

import com.scaler.reviewservice.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    Review createReview(Long productId, Review review);

    Page<Review> getReviewsByProduct(Long productId, Pageable pageable);
}