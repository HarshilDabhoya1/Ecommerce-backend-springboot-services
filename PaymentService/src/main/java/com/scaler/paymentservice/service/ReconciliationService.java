package com.scaler.paymentservice.service;

import com.scaler.paymentservice.model.Payment;
import com.scaler.paymentservice.model.PaymentStatus;
import com.scaler.paymentservice.producers.PaymentEventProducer;
import com.scaler.paymentservice.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reconciliation Service — the safety net of the payment system.
 *
 * <h2>What is Reconciliation?</h2>
 * Reconciliation means comparing your internal database against Stripe's records
 * and fixing any mismatches. Mismatches happen when:
 * <ul>
 *   <li>Your server crashed after Stripe charged the card but before you saved SUCCESS.</li>
 *   <li>A network timeout left the payment in PENDING while Stripe has a final status.</li>
 *   <li>A webhook event was lost/missed for any reason.</li>
 * </ul>
 *
 * <h2>Strategy</h2>
 * <ol>
 *   <li>Find all payments stuck in PENDING for more than {@value #STALE_THRESHOLD_MINUTES} minutes.</li>
 *   <li>For each one that has a Stripe transaction ID, call Stripe to get the real status.</li>
 *   <li>Update the local record to match Stripe's status.</li>
 *   <li>For ones with no transaction ID (Stripe was never called), mark FAILED directly.</li>
 * </ol>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /**
     * How long (in minutes) a payment is allowed to stay in PENDING before
     * the reconciliation job investigates it.
     * Stripe confirms synchronous payments almost instantly, so 10 minutes is very generous.
     */
    private static final int STALE_THRESHOLD_MINUTES = 10;

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    public ReconciliationService(PaymentRepository paymentRepository,
                                 PaymentEventProducer paymentEventProducer) {
        this.paymentRepository = paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
    }

    /**
     * Main reconciliation entry point — called by the scheduler every hour.
     *
     * @return a summary string describing what was fixed (useful for logging/alerting)
     */
    @Transactional
    public ReconciliationResult reconcile() {
        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<Payment> stalePayments = paymentRepository.findStalePayments(PaymentStatus.PENDING, cutoff);

        if (stalePayments.isEmpty()) {
            log.info("Reconciliation: no stale payments found");
            return ReconciliationResult.empty();
        }

        log.warn("Reconciliation: found {} stale PENDING payment(s) older than {} minutes",
                stalePayments.size(), STALE_THRESHOLD_MINUTES);

        int fixed = 0, failed = 0, skipped = 0;

        for (Payment payment : stalePayments) {
            try {
                if (payment.getTransactionId() != null) {
                    // ── Has a Stripe PaymentIntent ID — ask Stripe what actually happened ──
                    reconcileWithStripe(payment);
                    fixed++;
                } else {
                    // ── No transaction ID — Stripe was never reached (server crashed before call)
                    //    There is nothing to retrieve. Mark FAILED to unblock the order.
                    log.warn("Reconciliation: payment {} has no transactionId — marking FAILED (Stripe never reached)",
                            payment.getId());
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setFailureReason("Payment timed out — gateway was never reached. Please retry.");
                    paymentRepository.save(payment);
                    paymentEventProducer.sendPaymentFailedEmail(payment);
                    failed++;
                }
            } catch (Exception e) {
                log.error("Reconciliation: error processing payment {} — skipping: {}",
                        payment.getId(), e.getMessage());
                skipped++;
            }
        }

        ReconciliationResult result = new ReconciliationResult(stalePayments.size(), fixed, failed, skipped);
        log.info("Reconciliation complete — {}", result);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calls Stripe to get the current status of a PaymentIntent and syncs the local record.
     */
    private void reconcileWithStripe(Payment payment) throws StripeException {
        PaymentIntent intent = PaymentIntent.retrieve(payment.getTransactionId());
        String stripeStatus = intent.getStatus();

        log.info("Reconciliation: payment {} — DB=PENDING, Stripe={}", payment.getId(), stripeStatus);

        switch (stripeStatus) {
            case "succeeded" -> {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setFailureReason(null);
                paymentRepository.save(payment);
                paymentEventProducer.sendPaymentSuccessEmail(payment);
                log.info("Reconciliation: payment {} corrected to SUCCESS", payment.getId());
            }
            case "canceled", "payment_failed" -> {
                String reason = "Stripe status at reconciliation: " + stripeStatus;
                if (intent.getLastPaymentError() != null) {
                    reason = intent.getLastPaymentError().getMessage();
                }
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(reason);
                paymentRepository.save(payment);
                paymentEventProducer.sendPaymentFailedEmail(payment);
                log.info("Reconciliation: payment {} corrected to FAILED — reason: {}", payment.getId(), reason);
            }
            default ->
                // "requires_payment_method", "requires_confirmation", "processing" — still in-flight.
                // Leave as PENDING for now; next reconciliation cycle will check again.
                log.info("Reconciliation: payment {} still transitioning in Stripe (status: {}) — leaving as PENDING",
                        payment.getId(), stripeStatus);
        }
    }

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Simple value object summarising what the reconciliation job did this run.
     */
    public record ReconciliationResult(int total, int fixedToSuccess, int fixedToFailed, int skipped) {

        static ReconciliationResult empty() {
            return new ReconciliationResult(0, 0, 0, 0);
        }

        @Override
        public String toString() {
            return String.format("total=%d, fixedToSuccess=%d, fixedToFailed=%d, skipped=%d",
                    total, fixedToSuccess, fixedToFailed, skipped);
        }
    }
}
