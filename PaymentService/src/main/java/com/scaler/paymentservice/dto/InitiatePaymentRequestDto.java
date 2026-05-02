package com.scaler.paymentservice.dto;

import com.scaler.paymentservice.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitiatePaymentRequestDto {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "userEmail is required")
    private String userEmail;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private Double amount;

    /** ISO 4217 currency code, e.g. "INR", "USD". */
    @NotNull(message = "currency is required")
    private String currency;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    /**
     * Stripe payment method ID to charge.
     * <p>
     * In <b>test mode</b> use one of Stripe's predefined test payment method IDs:
     * <ul>
     *   <li>{@code pm_card_visa}                            — always succeeds</li>
     *   <li>{@code pm_card_mastercard}                      — always succeeds</li>
     *   <li>{@code pm_card_chargeDeclined}                  — card_declined</li>
     *   <li>{@code pm_card_chargeDeclinedInsufficientFunds} — insufficient_funds</li>
     *   <li>{@code pm_card_chargeDeclinedExpiredCard}       — expired_card</li>
     * </ul>
     * If omitted, defaults to {@code pm_card_visa} (always succeeds in test mode).
     */
    private String stripePaymentMethodId;
}
