package com.scaler.paymentservice.repository;

import com.scaler.paymentservice.model.Payment;
import com.scaler.paymentservice.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Fetch all payments for a specific order (typically one, but refunds create new records). */
    List<Payment> findAllByOrderId(Long orderId);

    /** Paginated list of all payments made by a user (admin dashboard / user history). */
    Page<Payment> findAllByUserId(Long userId, Pageable pageable);

    /** Convenience — find the most recent successful payment for an order. */
    Optional<Payment> findTopByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentStatus status);

    /**
     * Used by {@link com.scaler.paymentservice.service.WebhookService} to match an incoming
     * Stripe event back to the local payment record.
     */
    Optional<Payment> findByTransactionId(String transactionId);

    /**
     * Used by the reconciliation job to find payments that have been stuck in PENDING
     * longer than expected — i.e. the original API response was never received.
     *
     * <p>A payment is "stale" when:
     * <ul>
     *   <li>Its status is still {@code PENDING}, AND</li>
     *   <li>It was created before {@code cutoff} (e.g. 10 minutes ago)</li>
     * </ul>
     *
     * @param status  always {@code PENDING} in practice
     * @param cutoff  the oldest acceptable createdAt timestamp
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :cutoff")
    List<Payment> findStalePayments(@Param("status") PaymentStatus status,
                                    @Param("cutoff") Instant cutoff);
}
