package com.scaler.paymentservice.service;

import com.scaler.paymentservice.dto.InitiatePaymentRequestDto;
import com.scaler.paymentservice.dto.PaymentResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {

    /** Initiate a new payment for an order. Simulates gateway processing synchronously. */
    PaymentResponseDto initiatePayment(InitiatePaymentRequestDto request);

    /** Retrieve a single payment by its ID. */
    PaymentResponseDto getPaymentById(Long id);

    /** Retrieve all payment records for a given order (includes refunds). */
    List<PaymentResponseDto> getPaymentsByOrder(Long orderId);

    /** Paginated payment history for a user. */
    Page<PaymentResponseDto> getPaymentsByUser(Long userId, Pageable pageable);
}
