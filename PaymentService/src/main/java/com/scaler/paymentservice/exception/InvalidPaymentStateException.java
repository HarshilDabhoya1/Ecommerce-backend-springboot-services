package com.scaler.paymentservice.exception;

import com.scaler.paymentservice.model.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(PaymentStatus current, PaymentStatus requested) {
        super("Cannot transition payment from " + current + " to " + requested);
    }

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
