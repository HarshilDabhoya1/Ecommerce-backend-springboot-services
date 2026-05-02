package com.scaler.reviewservice.service;

import com.scaler.reviewservice.model.Review;
import com.scaler.reviewservice.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewServiceImpl(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public Review createReview(Long productId, Review review) {
        review.setProductId(productId);
        return reviewRepository.save(review);
    }

    @Override
    public Page<Review> getReviewsByProduct(Long productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable);
    }
}