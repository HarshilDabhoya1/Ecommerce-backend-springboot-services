package com.scaler.paymentservice.service;

import com.scaler.paymentservice.dto.InitiatePaymentRequestDto;
import com.scaler.paymentservice.dto.PaymentResponseDto;
import com.scaler.paymentservice.exception.PaymentNotFoundException;
import com.scaler.paymentservice.gateway.StripePaymentGateway;
import com.scaler.paymentservice.model.Payment;
import com.scaler.paymentservice.model.PaymentStatus;
import com.scaler.paymentservice.producers.PaymentEventProducer;
import com.scaler.paymentservice.repository.PaymentRepository;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    /** Default Stripe test payment method — a Visa card that always succeeds in test mode. */
    private static final String DEFAULT_TEST_PAYMENT_METHOD = "pm_card_visa";

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final StripePaymentGateway stripePaymentGateway;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentEventProducer paymentEventProducer,
                              StripePaymentGateway stripePaymentGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.stripePaymentGateway = stripePaymentGateway;
    }

    // ── Initiate ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentResponseDto initiatePayment(InitiatePaymentRequestDto request) {
        // 1 ── Persist with PENDING status first so we always have a record,
        //      even if the gateway call fails mid-flight.
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setUserId(request.getUserId());
        payment.setUserEmail(request.getUserEmail());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        // 2 ── Call Stripe ───────────────────────────────────────────────────
        String paymentMethodId = (request.getStripePaymentMethodId() != null
                && !request.getStripePaymentMethodId().isBlank())
                ? request.getStripePaymentMethodId()
                : DEFAULT_TEST_PAYMENT_METHOD;

        try {
            long amountInSmallestUnit = stripePaymentGateway.toSmallestUnit(
                    request.getAmount(), request.getCurrency());

            PaymentIntent intent = stripePaymentGateway.charge(
                    amountInSmallestUnit, request.getCurrency(), paymentMethodId);

            if ("succeeded".equals(intent.getStatus())) {
                payment.setStatus(PaymentStatus.SUCCESS);
                // Store Stripe's PaymentIntent ID as the transaction reference.
                payment.setTransactionId(intent.getId());
                log.info("Stripe PaymentIntent {} succeeded", intent.getId());
            } else {
                // e.g. "requires_action" (3DS redirect needed in live mode)
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment not completed. Stripe status: " + intent.getStatus());
                log.warn("Stripe PaymentIntent {} has unexpected status: {}", intent.getId(), intent.getStatus());
            }

        } catch (CardException e) {
            // Card was declined — Stripe returns a human-readable message.
            payment.setStatus(PaymentStatus.FAILED);
            String reason = e.getUserMessage() != null ? e.getUserMessage() : e.getCode();
            payment.setFailureReason(reason);
            log.warn("Stripe card declined — code: {}, message: {}", e.getCode(), reason);

        } catch (StripeException e) {
            // Any other Stripe API error (network, auth, rate-limit, etc.)
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment gateway error: " + e.getMessage());
            log.error("Stripe API error (requestId: {}): {}", e.getRequestId(), e.getMessage());
        }

        // 3 ── Persist final status ──────────────────────────────────────────
        Payment saved = paymentRepository.save(payment);

        // 4 ── Publish email event ───────────────────────────────────────────
        if (saved.getStatus() == PaymentStatus.SUCCESS) {
            paymentEventProducer.sendPaymentSuccessEmail(saved);
        } else {
            paymentEventProducer.sendPaymentFailedEmail(saved);
        }

        return PaymentResponseDto.from(saved);
    }

    // ── Read ────────────────────────────────────────────────────────────────────

    @Override
    public PaymentResponseDto getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponseDto.from(payment);
    }

    @Override
    public List<PaymentResponseDto> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findAllByOrderId(orderId)
                .stream()
                .map(PaymentResponseDto::from)
                .toList();
    }

    @Override
    public Page<PaymentResponseDto> getPaymentsByUser(Long userId, Pageable pageable) {
        return paymentRepository.findAllByUserId(userId, pageable)
                .map(PaymentResponseDto::from);
    }
}
