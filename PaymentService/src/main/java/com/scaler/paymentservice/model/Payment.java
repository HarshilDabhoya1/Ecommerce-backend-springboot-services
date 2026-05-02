package com.scaler.paymentservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the order being paid for (cross-service — no FK constraint). */
    @Column(nullable = false)
    private Long orderId;

    /** Reference to the paying user (cross-service — no FK constraint). */
    @Column(nullable = false)
    private Long userId;

    /** Denormalized for self-contained records and email notifications. */
    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private Double amount;

    /** ISO 4217 currency code, e.g. "INR", "USD". */
    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * Simulated gateway transaction reference.
     * Populated on SUCCESS; null on PENDING/FAILED.
     */
    private String transactionId;

    /** Human-readable reason populated when status == FAILED. */
    private String failureReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
