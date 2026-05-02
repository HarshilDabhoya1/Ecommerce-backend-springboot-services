package com.scaler.paymentservice.service;

import com.scaler.paymentservice.dto.InitiatePaymentRequestDto;
import com.scaler.paymentservice.dto.PaymentResponseDto;
import com.scaler.paymentservice.exception.PaymentNotFoundException;
import com.scaler.paymentservice.gateway.StripePaymentGateway;
import com.scaler.paymentservice.model.Payment;
import com.scaler.paymentservice.model.PaymentMethod;
import com.scaler.paymentservice.model.PaymentStatus;
import com.scaler.paymentservice.producers.PaymentEventProducer;
import com.scaler.paymentservice.repository.PaymentRepository;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentEventProducer paymentEventProducer;
    @Mock StripePaymentGateway stripePaymentGateway;

    @InjectMocks
    PaymentServiceImpl paymentService;

    // ── Shared fixtures ────────────────────────────────────────────────────────

    private InitiatePaymentRequestDto buildRequest(String stripeMethodId) {
        InitiatePaymentRequestDto req = new InitiatePaymentRequestDto();
        req.setOrderId(10L);
        req.setUserId(1L);
        req.setUserEmail("harshil@example.com");
        req.setAmount(1499.00);
        req.setCurrency("INR");
        req.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        req.setStripePaymentMethodId(stripeMethodId);
        return req;
    }

    private Payment savedPendingPayment() {
        Payment p = new Payment();
        p.setId(42L);
        p.setOrderId(10L);
        p.setUserId(1L);
        p.setUserEmail("harshil@example.com");
        p.setAmount(1499.00);
        p.setCurrency("INR");
        p.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        p.setStatus(PaymentStatus.PENDING);
        return p;
    }

    // ── initiatePayment ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiatePayment")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class InitiatePayment {

        @BeforeEach
        void setupMocks() {
            // First save (PENDING) always returns the pending payment
            given(paymentRepository.save(argThat(p -> p != null && p.getStatus() == PaymentStatus.PENDING)))
                    .willReturn(savedPendingPayment());
        }

        @Test
        @DisplayName("returns SUCCESS and stores Stripe PaymentIntent ID when Stripe succeeds")
        void success_whenStripeSucceeds() throws StripeException {
            // Arrange — Stripe gateway converts amount and returns a succeeded PaymentIntent
            given(stripePaymentGateway.toSmallestUnit(1499.00, "INR")).willReturn(149900L);

            PaymentIntent intent = mock(PaymentIntent.class);
            given(intent.getStatus()).willReturn("succeeded");
            given(intent.getId()).willReturn("pi_test_abc123");
            given(stripePaymentGateway.charge(149900L, "INR", "pm_card_visa")).willReturn(intent);

            Payment successPayment = savedPendingPayment();
            successPayment.setStatus(PaymentStatus.SUCCESS);
            successPayment.setTransactionId("pi_test_abc123");
            given(paymentRepository.save(argThat(p -> p != null && p.getStatus() == PaymentStatus.SUCCESS)))
                    .willReturn(successPayment);

            // Act
            PaymentResponseDto result = paymentService.initiatePayment(buildRequest("pm_card_visa"));

            // Assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(result.getTransactionId()).isEqualTo("pi_test_abc123");
            then(paymentEventProducer).should().sendPaymentSuccessEmail(any());
            then(paymentEventProducer).should(never()).sendPaymentFailedEmail(any());
        }

        @Test
        @DisplayName("defaults to pm_card_visa when stripePaymentMethodId is null")
        void usesDefaultPaymentMethod_whenNotProvided() throws StripeException {
            given(stripePaymentGateway.toSmallestUnit(1499.00, "INR")).willReturn(149900L);

            PaymentIntent intent = mock(PaymentIntent.class);
            given(intent.getStatus()).willReturn("succeeded");
            given(intent.getId()).willReturn("pi_default");
            // Verify it calls with the default method ID
            given(stripePaymentGateway.charge(149900L, "INR", "pm_card_visa")).willReturn(intent);

            Payment successPayment = savedPendingPayment();
            successPayment.setStatus(PaymentStatus.SUCCESS);
            given(paymentRepository.save(argThat(p -> p != null && p.getStatus() == PaymentStatus.SUCCESS)))
                    .willReturn(successPayment);

            paymentService.initiatePayment(buildRequest(null));  // no method ID

            // The gateway must have been called with the default "pm_card_visa"
            verify(stripePaymentGateway).charge(149900L, "INR", "pm_card_visa");
        }

        @Test
        @DisplayName("returns FAILED and stores failure reason when Stripe throws CardException")
        void failed_whenCardDeclined() throws StripeException {
            given(stripePaymentGateway.toSmallestUnit(1499.00, "INR")).willReturn(149900L);

            CardException cardEx = mock(CardException.class);
            given(cardEx.getUserMessage()).willReturn("Your card was declined.");
            given(stripePaymentGateway.charge(anyLong(), anyString(), anyString())).willThrow(cardEx);

            Payment failedPayment = savedPendingPayment();
            failedPayment.setStatus(PaymentStatus.FAILED);
            failedPayment.setFailureReason("Your card was declined.");
            given(paymentRepository.save(argThat(p -> p != null && p.getStatus() == PaymentStatus.FAILED)))
                    .willReturn(failedPayment);

            // Act
            PaymentResponseDto result = paymentService.initiatePayment(buildRequest("pm_card_chargeDeclined"));

            // Assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getFailureReason()).isEqualTo("Your card was declined.");
            then(paymentEventProducer).should().sendPaymentFailedEmail(any());
            then(paymentEventProducer).should(never()).sendPaymentSuccessEmail(any());
        }

        @Test
        @DisplayName("returns FAILED with gateway error message when Stripe throws generic StripeException")
        void failed_whenStripeApiError() throws StripeException {
            given(stripePaymentGateway.toSmallestUnit(1499.00, "INR")).willReturn(149900L);

            StripeException stripeEx = mock(StripeException.class);
            given(stripeEx.getMessage()).willReturn("No such payment_method: pm_invalid");
            given(stripeEx.getRequestId()).willReturn("req_test_xyz");
            given(stripePaymentGateway.charge(anyLong(), anyString(), anyString())).willThrow(stripeEx);

            Payment failedPayment = savedPendingPayment();
            failedPayment.setStatus(PaymentStatus.FAILED);
            failedPayment.setFailureReason("Payment gateway error: No such payment_method: pm_invalid");
            given(paymentRepository.save(argThat(p -> p != null && p.getStatus() == PaymentStatus.FAILED)))
                    .willReturn(failedPayment);

            PaymentResponseDto result = paymentService.initiatePayment(buildRequest("pm_invalid"));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getFailureReason()).contains("Payment gateway error");
        }

        @Test
        @DisplayName("marks payment FAILED when Stripe PaymentIntent status is not 'succeeded'")
        void failed_whenIntentStatusIsNotSucceeded() throws StripeException {
            given(stripePaymentGateway.toSmallestUnit(1499.00, "INR")).willReturn(149900L);

            PaymentIntent intent = mock(PaymentIntent.class);
            given(intent.getStatus()).willReturn("requires_action");
            given(intent.getId()).willReturn("pi_requires_action");
            given(stripePaymentGateway.charge(anyLong(), anyString(), anyString())).willReturn(intent);

            Payment failedPayment = savedPendingPayment();
            failedPayment.setStatus(PaymentStatus.FAILED);
            given(paymentRepository.save(argThat(p -> p != null && p.getStatus() == PaymentStatus.FAILED)))
                    .willReturn(failedPayment);

            PaymentResponseDto result = paymentService.initiatePayment(buildRequest("pm_card_visa"));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("always saves a PENDING record before calling Stripe (audit safety)")
        void alwaysSavesPendingFirst() throws StripeException {
            given(stripePaymentGateway.toSmallestUnit(1499.00, "INR")).willReturn(149900L);

            PaymentIntent intent = mock(PaymentIntent.class);
            given(intent.getStatus()).willReturn("succeeded");
            given(intent.getId()).willReturn("pi_test");
            given(stripePaymentGateway.charge(anyLong(), anyString(), anyString())).willReturn(intent);

            Payment successPayment = savedPendingPayment();
            successPayment.setStatus(PaymentStatus.SUCCESS);
            given(paymentRepository.save(any())).willReturn(savedPendingPayment(), successPayment);

            paymentService.initiatePayment(buildRequest("pm_card_visa"));

            // Repository must be called twice: once for PENDING, once for final status
            verify(paymentRepository, times(2)).save(any(Payment.class));

            // First call must be with PENDING status
            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    // ── getPaymentById ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentById")
    class GetById {

        @Test
        @DisplayName("returns PaymentResponseDto when payment exists")
        void found() {
            Payment payment = savedPendingPayment();
            payment.setStatus(PaymentStatus.SUCCESS);
            given(paymentRepository.findById(42L)).willReturn(Optional.of(payment));

            PaymentResponseDto result = paymentService.getPaymentById(42L);

            assertThat(result.getId()).isEqualTo(42L);
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @DisplayName("throws PaymentNotFoundException when payment does not exist")
        void notFound() {
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(999L))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ── getPaymentsByOrder ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentsByOrder")
    class GetByOrder {

        @Test
        @DisplayName("returns list of payments for the given orderId")
        void returnsPaymentsForOrder() {
            Payment p1 = savedPendingPayment();
            p1.setStatus(PaymentStatus.SUCCESS);
            Payment p2 = savedPendingPayment();
            p2.setId(43L);
            p2.setStatus(PaymentStatus.REFUNDED);
            given(paymentRepository.findAllByOrderId(10L)).willReturn(List.of(p1, p2));

            List<PaymentResponseDto> result = paymentService.getPaymentsByOrder(10L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PaymentResponseDto::getStatus)
                    .containsExactly(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("returns empty list when no payments exist for order")
        void returnsEmptyList_whenNoPayments() {
            given(paymentRepository.findAllByOrderId(999L)).willReturn(List.of());

            List<PaymentResponseDto> result = paymentService.getPaymentsByOrder(999L);

            assertThat(result).isEmpty();
        }
    }

    // ── getPaymentsByUser ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentsByUser")
    class GetByUser {

        @Test
        @DisplayName("returns paginated payments for the given userId")
        void returnsPaginatedPayments() {
            Payment p = savedPendingPayment();
            p.setStatus(PaymentStatus.SUCCESS);
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Payment> page = new PageImpl<>(List.of(p), pageable, 1);
            given(paymentRepository.findAllByUserId(1L, pageable)).willReturn(page);

            Page<PaymentResponseDto> result = paymentService.getPaymentsByUser(1L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
        }
    }
}
