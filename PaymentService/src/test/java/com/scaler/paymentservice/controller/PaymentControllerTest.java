package com.scaler.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaler.paymentservice.dto.InitiatePaymentRequestDto;
import com.scaler.paymentservice.dto.PaymentResponseDto;
import com.scaler.paymentservice.exception.PaymentNotFoundException;
import com.scaler.paymentservice.model.PaymentMethod;
import com.scaler.paymentservice.model.PaymentStatus;
import com.scaler.paymentservice.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PaymentService paymentService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentResponseDto buildResponse(PaymentStatus status) {
        PaymentResponseDto dto = new PaymentResponseDto();
        dto.setId(1L);
        dto.setOrderId(10L);
        dto.setUserId(1L);
        dto.setUserEmail("harshil@example.com");
        dto.setAmount(1499.00);
        dto.setCurrency("INR");
        dto.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        dto.setStatus(status);
        dto.setTransactionId(status == PaymentStatus.SUCCESS ? "pi_test_abc" : null);
        dto.setFailureReason(status == PaymentStatus.FAILED ? "Your card was declined." : null);
        dto.setCreatedAt(Instant.now());
        dto.setUpdatedAt(Instant.now());
        return dto;
    }

    private InitiatePaymentRequestDto buildRequest() {
        InitiatePaymentRequestDto req = new InitiatePaymentRequestDto();
        req.setOrderId(10L);
        req.setUserId(1L);
        req.setUserEmail("harshil@example.com");
        req.setAmount(1499.00);
        req.setCurrency("INR");
        req.setPaymentMethod(PaymentMethod.CREDIT_CARD);
        req.setStripePaymentMethodId("pm_card_visa");
        return req;
    }

    // ── POST /payments ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /payments")
    class InitiatePayment {

        @Test
        @DisplayName("returns 201 CREATED when payment succeeds")
        void returns201_whenSuccess() throws Exception {
            given(paymentService.initiatePayment(any())).willReturn(buildResponse(PaymentStatus.SUCCESS));

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.transactionId").value("pi_test_abc"));
        }

        @Test
        @DisplayName("returns 402 PAYMENT_REQUIRED when card is declined")
        void returns402_whenDeclined() throws Exception {
            given(paymentService.initiatePayment(any())).willReturn(buildResponse(PaymentStatus.FAILED));

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.failureReason").value("Your card was declined."));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when request body is missing required fields")
        void returns400_whenMissingAmount() throws Exception {
            InitiatePaymentRequestDto invalid = buildRequest();
            invalid.setAmount(null);  // amount is @NotNull

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when amount is negative")
        void returns400_whenAmountNegative() throws Exception {
            InitiatePaymentRequestDto invalid = buildRequest();
            invalid.setAmount(-100.0);  // @Positive fails

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when paymentMethod is null")
        void returns400_whenPaymentMethodNull() throws Exception {
            InitiatePaymentRequestDto invalid = buildRequest();
            invalid.setPaymentMethod(null);

            mockMvc.perform(post("/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /payments/{id} ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /payments/{id}")
    class GetById {

        @Test
        @DisplayName("returns 200 OK with payment details when found")
        void returns200_whenFound() throws Exception {
            given(paymentService.getPaymentById(1L)).willReturn(buildResponse(PaymentStatus.SUCCESS));

            mockMvc.perform(get("/payments/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.currency").value("INR"));
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when payment does not exist")
        void returns404_whenNotFound() throws Exception {
            given(paymentService.getPaymentById(999L))
                    .willThrow(new PaymentNotFoundException(999L));

            mockMvc.perform(get("/payments/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Payment not found with id: 999"));
        }
    }

    // ── GET /payments/order/{orderId} ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /payments/order/{orderId}")
    class GetByOrder {

        @Test
        @DisplayName("returns 200 OK with list of payments for the order")
        void returns200_withPaymentList() throws Exception {
            given(paymentService.getPaymentsByOrder(10L))
                    .willReturn(List.of(buildResponse(PaymentStatus.SUCCESS)));

            mockMvc.perform(get("/payments/order/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].orderId").value(10))
                    .andExpect(jsonPath("$[0].status").value("SUCCESS"));
        }

        @Test
        @DisplayName("returns 200 OK with empty array when no payments exist for order")
        void returns200_withEmptyArray() throws Exception {
            given(paymentService.getPaymentsByOrder(999L)).willReturn(List.of());

            mockMvc.perform(get("/payments/order/999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // ── GET /payments/user/{userId} ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /payments/user/{userId}")
    class GetByUser {

        @Test
        @DisplayName("returns 200 OK with paginated payment history")
        void returns200_withPage() throws Exception {
            PageRequest pageable = PageRequest.of(0, 10);
            given(paymentService.getPaymentsByUser(eq(1L), any()))
                    .willReturn(new PageImpl<>(List.of(buildResponse(PaymentStatus.SUCCESS)), pageable, 1));

            mockMvc.perform(get("/payments/user/1").param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].userId").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }
}
