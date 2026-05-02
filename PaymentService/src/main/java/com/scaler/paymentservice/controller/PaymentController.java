package com.scaler.paymentservice.controller;

import com.scaler.paymentservice.dto.InitiatePaymentRequestDto;
import com.scaler.paymentservice.dto.PaymentResponseDto;
import com.scaler.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /payments
     * Initiate a payment for an order.
     * Any authenticated user can initiate their own payment.
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDto> initiatePayment(
            @RequestBody @Valid InitiatePaymentRequestDto request) {
        PaymentResponseDto response = paymentService.initiatePayment(request);
        HttpStatus status = switch (response.getStatus()) {
            case SUCCESS -> HttpStatus.CREATED;
            case FAILED  -> HttpStatus.PAYMENT_REQUIRED;
            default      -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(response);
    }

    /**
     * GET /payments/{id}
     * Get a single payment by ID.
     * Any authenticated user can view payment details.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> getPaymentById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    /**
     * GET /payments/order/{orderId}
     * Get all payments for a specific order (includes refund records).
     * Any authenticated user can query this.
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentResponseDto>> getPaymentsByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(orderId));
    }

    /**
     * GET /payments/user/{userId}
     * Paginated payment history for a user.
     * Any authenticated user can view their own history.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<PaymentResponseDto>> getPaymentsByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(paymentService.getPaymentsByUser(userId, pageable));
    }
}
