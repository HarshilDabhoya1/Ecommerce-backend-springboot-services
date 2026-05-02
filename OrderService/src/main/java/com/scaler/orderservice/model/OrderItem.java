package com.scaler.orderservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Cross-service reference — no FK to ProductService
    @Column(nullable = false)
    private Long productId;

    // Denormalized: snapshot the product title at order time so the record
    // is resilient to product renames or deletions
    @Column(nullable = false)
    private String productTitle;

    @Column(nullable = false)
    private int quantity;

    // Snapshot of price at time of order
    @Column(nullable = false)
    private double unitPrice;
}
