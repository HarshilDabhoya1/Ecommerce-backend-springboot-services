package com.scaler.paymentservice.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Thin wrapper around the Stripe Java SDK.
 *
 * <p><b>Test-mode payment method IDs</b> (pass in {@code stripePaymentMethodId}):
 * <ul>
 *   <li>{@code pm_card_visa}                              — always succeeds</li>
 *   <li>{@code pm_card_mastercard}                        — always succeeds</li>
 *   <li>{@code pm_card_chargeDeclined}                    — card_declined</li>
 *   <li>{@code pm_card_chargeDeclinedInsufficientFunds}   — insufficient_funds</li>
 *   <li>{@code pm_card_chargeDeclinedExpiredCard}         — expired_card</li>
 *   <li>{@code pm_card_chargeDeclinedProcessingError}     — processing_error</li>
 * </ul>
 *
 * <p>Stripe expects amounts in the <em>smallest currency unit</em>:
 * 100 paise = ₹1, 100 cents = $1. {@link #toSmallestUnit} handles this conversion.
 */
@Component
public class StripePaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    /**
     * ISO 4217 codes for zero-decimal currencies (Stripe does NOT multiply these by 100).
     * This list covers the most common ones; extend as needed.
     */
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW",
            "MGA", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    @Value("${stripe.return-url:https://example.com/payment/return}")
    private String returnUrl;

    // ── Charge ────────────────────────────────────────────────────────────────

    /**
     * Creates and immediately confirms a Stripe PaymentIntent.
     *
     * @param amountInSmallestUnit amount already converted via {@link #toSmallestUnit}
     * @param currency             ISO 4217 currency code (e.g. "INR", "USD")
     * @param paymentMethodId      Stripe payment method ID (test: "pm_card_visa")
     * @return the confirmed {@link PaymentIntent}
     * @throws StripeException on any Stripe API error (including card declines)
     */
    public PaymentIntent charge(long amountInSmallestUnit, String currency, String paymentMethodId)
            throws StripeException {

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestUnit)
                .setCurrency(currency.toLowerCase())
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                // returnUrl is required when the payment method might trigger 3DS redirect.
                // For most test cards it is unused, but Stripe requires it when confirm=true.
                .setReturnUrl(returnUrl)
                .build();

        log.info("Creating Stripe PaymentIntent — amount: {} {}, paymentMethod: {}",
                amountInSmallestUnit, currency.toUpperCase(), paymentMethodId);

        return PaymentIntent.create(params);
    }

    // ── Currency helper ───────────────────────────────────────────────────────

    /**
     * Converts a human-readable amount to the smallest currency unit expected by Stripe.
     * <p>
     * Example: ₹149.50 → 14950 paise | $5.00 → 500 cents | ¥500 → 500 (no conversion)
     */
    public long toSmallestUnit(double amount, String currency) {
        if (ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase())) {
            return Math.round(amount);
        }
        return Math.round(amount * 100);
    }
}
