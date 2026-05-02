package com.scaler.paymentservice.service;

import com.scaler.paymentservice.model.Payment;
import com.scaler.paymentservice.model.PaymentStatus;
import com.scaler.paymentservice.producers.PaymentEventProducer;
import com.scaler.paymentservice.repository.PaymentRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processes Stripe webhook events and updates the local Payment record.
 *
 * <h2>Why Webhooks are Critical</h2>
 * Consider this scenario: your server calls Stripe, Stripe processes the payment,
 * but your server crashes before it reads the response. Your DB still shows PENDING
 * while Stripe actually charged the card. The webhook fires regardless of your server state,
 * so it acts as the reliable source of truth.
 *
 * <h2>Event types handled</h2>
 * <ul>
 *   <li>{@code payment_intent.succeeded}       — mark payment SUCCESS</li>
 *   <li>{@code payment_intent.payment_failed}  — mark payment FAILED with reason</li>
 *   <li>{@code payment_intent.canceled}        — mark payment FAILED (canceled by Stripe)</li>
 * </ul>
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    public WebhookService(PaymentRepository paymentRepository,
                          PaymentEventProducer paymentEventProducer) {
        this.paymentRepository = paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
    }

    /**
     * Verifies the Stripe signature then routes the event to the correct handler.
     *
     * @param payload         raw JSON body from Stripe (must not be modified)
     * @param stripeSignature value of the Stripe-Signature HTTP header
     * @throws SignatureVerificationException if the signature is invalid
     */
    @Transactional
    public void processEvent(String payload, String stripeSignature)
            throws SignatureVerificationException {

        // ── Step 1: Verify signature ─────────────────────────────────────────
        // Webhook.constructEvent throws SignatureVerificationException if the
        // payload was tampered with or the secret is wrong.
        Event event = Webhook.constructEvent(payload, stripeSignature, webhookSecret);

        log.info("Received Stripe event: {} (id: {})", event.getType(), event.getId());

        // ── Step 2: Route to handler ─────────────────────────────────────────
        switch (event.getType()) {
            case "payment_intent.succeeded"      -> handleSucceeded(event);
            case "payment_intent.payment_failed" -> handleFailed(event);
            case "payment_intent.canceled"       -> handleCanceled(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleSucceeded(Event event) {
        PaymentIntent intent = extractPaymentIntent(event);
        if (intent == null) return;

        findByTransactionId(intent.getId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                // Already updated (probably by the original API response). Idempotent — skip.
                log.info("Webhook: payment {} already SUCCESS — skipping", payment.getId());
                return;
            }
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setFailureReason(null);
            paymentRepository.save(payment);
            paymentEventProducer.sendPaymentSuccessEmail(payment);
            log.info("Webhook: payment {} updated to SUCCESS via event", payment.getId());
        }, () -> log.warn("Webhook succeeded: no payment found for PaymentIntent {}", intent.getId()));
    }

    private void handleFailed(Event event) {
        PaymentIntent intent = extractPaymentIntent(event);
        if (intent == null) return;

        String reason = "Payment failed";
        if (intent.getLastPaymentError() != null) {
            reason = intent.getLastPaymentError().getMessage();
        }

        final String failureReason = reason;
        findByTransactionId(intent.getId()).ifPresentOrElse(payment -> {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                log.info("Webhook: payment {} already FAILED — skipping", payment.getId());
                return;
            }
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            paymentRepository.save(payment);
            paymentEventProducer.sendPaymentFailedEmail(payment);
            log.info("Webhook: payment {} updated to FAILED — reason: {}", payment.getId(), failureReason);
        }, () -> log.warn("Webhook failed: no payment found for PaymentIntent {}", intent.getId()));
    }

    private void handleCanceled(Event event) {
        PaymentIntent intent = extractPaymentIntent(event);
        if (intent == null) return;

        findByTransactionId(intent.getId()).ifPresentOrElse(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment was canceled by Stripe");
            paymentRepository.save(payment);
            paymentEventProducer.sendPaymentFailedEmail(payment);
            log.info("Webhook: payment {} set to FAILED (canceled)", payment.getId());
        }, () -> log.warn("Webhook canceled: no payment found for PaymentIntent {}", intent.getId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentIntent extractPaymentIntent(Event event) {
        return (PaymentIntent) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);
    }

    private Optional<Payment> findByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId);
    }
}
