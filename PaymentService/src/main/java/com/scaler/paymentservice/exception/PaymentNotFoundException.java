package com.scaler.paymentservice.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long id) {
        super("Payment not found with id: " + id);
    }

    public PaymentNotFoundException(String field, Object value) {
        super("Payment not found with " + field + ": " + value);
    }
}
