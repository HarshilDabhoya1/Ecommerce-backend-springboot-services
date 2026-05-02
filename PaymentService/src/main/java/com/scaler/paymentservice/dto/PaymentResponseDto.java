package com.scaler.paymentservice.dto;

import com.scaler.paymentservice.model.Payment;
import com.scaler.paymentservice.model.PaymentMethod;
import com.scaler.paymentservice.model.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PaymentResponseDto {

    private Long id;
    private Long orderId;
    private Long userId;
    private String userEmail;
    private Double amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String transactionId;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;

    public static PaymentResponseDto from(Payment payment) {
        PaymentResponseDto dto = new PaymentResponseDto();
        dto.setId(payment.getId());
        dto.setOrderId(payment.getOrderId());
        dto.setUserId(payment.getUserId());
        dto.setUserEmail(payment.getUserEmail());
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setStatus(payment.getStatus());
        dto.setTransactionId(payment.getTransactionId());
        dto.setFailureReason(payment.getFailureReason());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setUpdatedAt(payment.getUpdatedAt());
        return dto;
    }
}
