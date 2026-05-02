package com.scaler.orderservice.service;

import com.scaler.orderservice.dto.CreateOrderRequestDto;
import com.scaler.orderservice.dto.OrderItemRequestDto;
import com.scaler.orderservice.dto.UpdateOrderStatusDto;
import com.scaler.orderservice.exception.InvalidOrderStateException;
import com.scaler.orderservice.exception.OrderNotFoundException;
import com.scaler.orderservice.model.Order;
import com.scaler.orderservice.model.OrderItem;
import com.scaler.orderservice.model.OrderStatus;
import com.scaler.orderservice.producers.OrderEventProducer;
import com.scaler.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderServiceImpl")
class OrderServiceImplTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderEventProducer orderEventProducer;

    @InjectMocks OrderServiceImpl orderService;

    // ── Fixtures ───────────────────────────────────────────────────────────────

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

    private Order orderWithStatus(OrderStatus status) {
        List<OrderItem> items = new ArrayList<>();
        OrderItem item = OrderItem.builder()
                .productId(100L)
                .productTitle("iPhone 16 Pro")
                .quantity(2)
                .unitPrice(134900.00)
                .build();
        items.add(item);

        Order order = Order.builder()
                .userId(1L)
                .userEmail("harshil@example.com")
                .status(status)
                .totalAmount(269800.00)
                .build();
        order.getItems().addAll(items);
        // Simulate what the DB would return
        try {
            var idField = Order.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, 1L);
        } catch (Exception ignored) {
            // id may not be directly settable via reflection; use a simpler approach
        }
        return order;
    }

    // ── placeOrder ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("placeOrder")
    class PlaceOrder {

        @Test
        @DisplayName("creates order with PENDING status and correct total amount")
        void createsOrderWithPendingStatusAndTotal() {
            Order savedOrder = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            Order result = orderService.placeOrder(buildCreateRequest());

            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getUserEmail()).isEqualTo("harshil@example.com");
            assertThat(result.getTotalAmount()).isEqualTo(269800.00);
        }

        @Test
        @DisplayName("calculates total as sum of quantity × unitPrice across all items")
        void calculatesTotalCorrectly() {
            // Two items: 2 × 134900 = 269800
            Order savedOrder = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            orderService.placeOrder(buildCreateRequest());

            // Capture what was passed to save and verify totalAmount
            org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getTotalAmount()).isEqualTo(2 * 134900.00);
        }

        @Test
        @DisplayName("publishes order confirmation email event after placing order")
        void publishesConfirmationEmail() {
            Order savedOrder = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            orderService.placeOrder(buildCreateRequest());

            then(orderEventProducer).should().sendOrderConfirmationEmail(savedOrder);
        }

        @Test
        @DisplayName("calculates total correctly for multiple different items")
        void calculatesTotalForMultipleItems() {
            OrderItemRequestDto item1 = new OrderItemRequestDto();
            item1.setProductId(1L);
            item1.setProductTitle("Product A");
            item1.setQuantity(3);
            item1.setUnitPrice(100.00);

            OrderItemRequestDto item2 = new OrderItemRequestDto();
            item2.setProductId(2L);
            item2.setProductTitle("Product B");
            item2.setQuantity(2);
            item2.setUnitPrice(250.00);

            CreateOrderRequestDto dto = new CreateOrderRequestDto();
            dto.setUserId(1L);
            dto.setUserEmail("test@example.com");
            dto.setItems(List.of(item1, item2));

            Order savedOrder = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            orderService.placeOrder(dto);

            org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            // 3×100 + 2×250 = 300 + 500 = 800
            assertThat(captor.getValue().getTotalAmount()).isEqualTo(800.00);
        }
    }

    // ── cancelOrder ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("successfully cancels a PENDING order")
        void cancelsPendingOrder() {
            Order order = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Order result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderEventProducer).should().sendOrderCancellationEmail(any());
        }

        @Test
        @DisplayName("successfully cancels a CONFIRMED order")
        void cancelsConfirmedOrder() {
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Order result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("throws InvalidOrderStateException when order is already CANCELLED")
        void throwsWhenAlreadyCancelled() {
            Order order = orderWithStatus(OrderStatus.CANCELLED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("already cancelled");
        }

        @Test
        @DisplayName("throws InvalidOrderStateException when order is DELIVERED")
        void throwsWhenDelivered() {
            Order order = orderWithStatus(OrderStatus.DELIVERED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("delivered");
        }

        @Test
        @DisplayName("throws InvalidOrderStateException when order is SHIPPED")
        void throwsWhenShipped() {
            Order order = orderWithStatus(OrderStatus.SHIPPED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("shipped");
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void throwsWhenOrderNotFound() {
            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(999L))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    // ── updateOrderStatus ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateOrderStatus")
    class UpdateOrderStatus {

        private UpdateOrderStatusDto dto(OrderStatus status) {
            UpdateOrderStatusDto d = new UpdateOrderStatusDto();
            d.setStatus(status);
            return d;
        }

        @Test
        @DisplayName("transitions PENDING → CONFIRMED")
        void pendingToConfirmed() {
            Order order = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Order result = orderService.updateOrderStatus(1L, dto(OrderStatus.CONFIRMED));

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("transitions CONFIRMED → SHIPPED")
        void confirmedToShipped() {
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Order result = orderService.updateOrderStatus(1L, dto(OrderStatus.SHIPPED));

            assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("transitions SHIPPED → DELIVERED")
        void shippedToDelivered() {
            Order order = orderWithStatus(OrderStatus.SHIPPED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            Order result = orderService.updateOrderStatus(1L, dto(OrderStatus.DELIVERED));

            assertThat(result.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("throws InvalidOrderStateException on backward transition CONFIRMED → PENDING")
        void throwsOnBackwardTransition() {
            Order order = orderWithStatus(OrderStatus.CONFIRMED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, dto(OrderStatus.PENDING)))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("Invalid status transition");
        }

        @Test
        @DisplayName("throws InvalidOrderStateException when trying to set CANCELLED via status update")
        void throwsWhenTryingToCancelViaStatusUpdate() {
            Order order = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, dto(OrderStatus.CANCELLED)))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("cancel endpoint");
        }

        @Test
        @DisplayName("throws InvalidOrderStateException when order is already DELIVERED")
        void throwsWhenAlreadyDelivered() {
            Order order = orderWithStatus(OrderStatus.DELIVERED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, dto(OrderStatus.SHIPPED)))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("throws InvalidOrderStateException when order is CANCELLED")
        void throwsWhenCancelled() {
            Order order = orderWithStatus(OrderStatus.CANCELLED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L, dto(OrderStatus.CONFIRMED)))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("cancelled");
        }

        @Test
        @DisplayName("throws OrderNotFoundException when order does not exist")
        void throwsWhenOrderNotFound() {
            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.updateOrderStatus(999L, dto(OrderStatus.CONFIRMED)))
                    .isInstanceOf(OrderNotFoundException.class);
        }
    }

    // ── getOrderById ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrderById")
    class GetById {

        @Test
        @DisplayName("returns Optional containing the order when it exists")
        void returnsOrder_whenFound() {
            Order order = orderWithStatus(OrderStatus.PENDING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            Optional<Order> result = orderService.getOrderById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("returns empty Optional when order does not exist")
        void returnsEmpty_whenNotFound() {
            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            Optional<Order> result = orderService.getOrderById(999L);

            assertThat(result).isEmpty();
        }
    }

    // ── getOrdersByUser ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrdersByUser")
    class GetByUser {

        @Test
        @DisplayName("returns paginated orders for the given userId")
        void returnsPaginatedOrders() {
            Order order = orderWithStatus(OrderStatus.PENDING);
            PageRequest pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
            given(orderRepository.findAllByUserId(1L, pageable)).willReturn(page);

            Page<Order> result = orderService.getOrdersByUser(1L, pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(1L);
        }
    }
}
