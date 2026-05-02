package com.scaler.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaler.orderservice.dto.CreateOrderRequestDto;
import com.scaler.orderservice.dto.OrderItemRequestDto;
import com.scaler.orderservice.dto.OrderResponseDto;
import com.scaler.orderservice.dto.UpdateOrderStatusDto;
import com.scaler.orderservice.exception.ForbiddenException;
import com.scaler.orderservice.exception.InvalidOrderStateException;
import com.scaler.orderservice.exception.OrderNotFoundException;
import com.scaler.orderservice.model.Order;
import com.scaler.orderservice.model.OrderStatus;
import com.scaler.orderservice.security.RoleValidator;
import com.scaler.orderservice.service.OrderService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean OrderService orderService;
    @MockitoBean RoleValidator roleValidator;

    // ── Fixtures ───────────────────────────────────────────────────────────────

    private Order buildOrder(OrderStatus status) {
        return Order.builder()
                .userId(1L)
                .userEmail("harshil@example.com")
                .status(status)
                .totalAmount(269800.00)
                .build();
    }

    private CreateOrderRequestDto buildCreateRequest() {
        OrderItemRequestDto item = new OrderItemRequestDto();
        item.setProductId(100L);
        item.setProductTitle("iPhone 16 Pro");
        item.setQuantity(2);
        item.setUnitPrice(134900.00);

        CreateOrderRequestDto dto = new CreateOrderRequestDto();
        dto.setUserId(1L);
        dto.setUserEmail("harshil@example.com");
        dto.setItems(List.of(item));
        return dto;
    }

    // ── POST /orders ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /orders")
    class PlaceOrder {

        @Test
        @DisplayName("returns 201 CREATED with order body on success")
        void returns201_onSuccess() throws Exception {
            Order order = buildOrder(OrderStatus.PENDING);
            given(orderService.placeOrder(any())).willReturn(order);

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.totalAmount").value(269800.00))
                    .andExpect(jsonPath("$.userEmail").value("harshil@example.com"));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when items list is empty")
        void returns400_whenItemsEmpty() throws Exception {
            CreateOrderRequestDto bad = buildCreateRequest();
            bad.setItems(List.of());

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when userId is missing")
        void returns400_whenUserIdMissing() throws Exception {
            CreateOrderRequestDto bad = buildCreateRequest();
            bad.setUserId(null);

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when userEmail is not a valid email")
        void returns400_whenEmailInvalid() throws Exception {
            CreateOrderRequestDto bad = buildCreateRequest();
            bad.setUserEmail("not-an-email");

            mockMvc.perform(post("/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /orders/{id} ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /orders/{id}")
    class GetById {

        @Test
        @DisplayName("returns 200 OK with order details when found")
        void returns200_whenFound() throws Exception {
            Order order = buildOrder(OrderStatus.CONFIRMED);
            given(orderService.getOrderById(1L)).willReturn(Optional.of(order));

            mockMvc.perform(get("/orders/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.userId").value(1));
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when order does not exist")
        void returns404_whenNotFound() throws Exception {
            given(orderService.getOrderById(999L)).willReturn(Optional.empty());

            mockMvc.perform(get("/orders/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /orders/user/{userId} ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /orders/user/{userId}")
    class GetByUser {

        @Test
        @DisplayName("returns 200 OK with paginated orders")
        void returns200_withPage() throws Exception {
            Order order = buildOrder(OrderStatus.PENDING);
            PageRequest pageable = PageRequest.of(0, 10);
            given(orderService.getOrdersByUser(eq(1L), any()))
                    .willReturn(new PageImpl<>(List.of(order), pageable, 1));

            mockMvc.perform(get("/orders/user/1").param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].userId").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ── PATCH /orders/{id}/cancel ──────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /orders/{id}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("returns 200 OK with CANCELLED status on success")
        void returns200_whenCancelled() throws Exception {
            Order cancelled = buildOrder(OrderStatus.CANCELLED);
            given(orderService.cancelOrder(1L)).willReturn(cancelled);

            mockMvc.perform(patch("/orders/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when order does not exist")
        void returns404_whenOrderNotFound() throws Exception {
            given(orderService.cancelOrder(999L)).willThrow(new OrderNotFoundException(999L));

            mockMvc.perform(patch("/orders/999/cancel"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 409 CONFLICT when order cannot be cancelled (e.g. already CANCELLED)")
        void returns409_whenInvalidState() throws Exception {
            given(orderService.cancelOrder(1L))
                    .willThrow(new InvalidOrderStateException("Order #1 is already cancelled."));

            mockMvc.perform(patch("/orders/1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Conflict"));
        }
    }

    // ── PATCH /orders/{id}/status (ADMIN) ─────────────────────────────────────

    @Nested
    @DisplayName("PATCH /orders/{id}/status")
    class UpdateStatus {

        private String statusBody(OrderStatus status) throws Exception {
            UpdateOrderStatusDto dto = new UpdateOrderStatusDto();
            dto.setStatus(status);
            return objectMapper.writeValueAsString(dto);
        }

        @Test
        @DisplayName("returns 200 OK when ADMIN advances order status")
        void returns200_forAdmin() throws Exception {
            Order confirmed = buildOrder(OrderStatus.CONFIRMED);
            given(orderService.updateOrderStatus(eq(1L), any())).willReturn(confirmed);
            // RoleValidator is a mock — by default requireAdmin() does nothing (no throw)

            mockMvc.perform(patch("/orders/1/status")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody(OrderStatus.CONFIRMED)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("returns 403 FORBIDDEN when non-ADMIN tries to update status")
        void returns403_forUser() throws Exception {
            // Configure RoleValidator mock to throw ForbiddenException for non-admin
            willThrow(new ForbiddenException("Access denied: ADMIN role required"))
                    .given(roleValidator).requireAdmin("USER");

            mockMvc.perform(patch("/orders/1/status")
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody(OrderStatus.CONFIRMED)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("returns 400 BAD_REQUEST when status field is missing")
        void returns400_whenStatusMissing() throws Exception {
            mockMvc.perform(patch("/orders/1/status")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 404 NOT_FOUND when order does not exist")
        void returns404_whenOrderNotFound() throws Exception {
            given(orderService.updateOrderStatus(eq(999L), any()))
                    .willThrow(new OrderNotFoundException(999L));

            mockMvc.perform(patch("/orders/999/status")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody(OrderStatus.CONFIRMED)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 409 CONFLICT on invalid state transition")
        void returns409_onInvalidTransition() throws Exception {
            given(orderService.updateOrderStatus(eq(1L), any()))
                    .willThrow(new InvalidOrderStateException("Invalid status transition from CONFIRMED to PENDING."));

            mockMvc.perform(patch("/orders/1/status")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(statusBody(OrderStatus.PENDING)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Invalid status transition from CONFIRMED to PENDING."));
        }
    }
}
