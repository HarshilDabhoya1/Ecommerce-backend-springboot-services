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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Override
    @Transactional
    public Order placeOrder(CreateOrderRequestDto dto) {
        // Build order items from request
        List<OrderItem> items = dto.getItems().stream()
                .map(this::toOrderItem)
                .toList();

        // Calculate total
        double total = items.stream()
                .mapToDouble(i -> i.getQuantity() * i.getUnitPrice())
                .sum();

        // Build and persist the order
        Order order = Order.builder()
                .userId(dto.getUserId())
                .userEmail(dto.getUserEmail())
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .build();

        // Link items to the order (needed for cascaded save)
        items.forEach(item -> item.setOrder(order));
        order.getItems().addAll(items);

        Order saved = orderRepository.save(order);

        // Publish confirmation email event via Kafka
        orderEventProducer.sendOrderConfirmationEmail(saved);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getOrdersByUser(Long userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable);
    }

    @Override
    @Transactional
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order #" + id + " is already cancelled.");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException("Cannot cancel a delivered order.");
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new InvalidOrderStateException("Cannot cancel an order that has already been shipped.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);

        // Notify customer of cancellation
        orderEventProducer.sendOrderCancellationEmail(saved);

        return saved;
    }

    @Override
    @Transactional
    public Order updateOrderStatus(Long id, UpdateOrderStatusDto dto) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        validateStatusTransition(order.getStatus(), dto.getStatus());

        order.setStatus(dto.getStatus());
        return orderRepository.save(order);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private OrderItem toOrderItem(OrderItemRequestDto dto) {
        return OrderItem.builder()
                .productId(dto.getProductId())
                .productTitle(dto.getProductTitle())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .build();
    }

    /**
     * Enforces a simple forward-only status machine:
     *   PENDING → CONFIRMED → SHIPPED → DELIVERED
     * Cancellation is handled separately via cancelOrder().
     */
    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Use the dedicated cancel endpoint to cancel an order.");
        }
        if (current == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(
                    "Cannot change the status of a cancelled order.");
        }
        if (current == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException(
                    "Order #" + current + " is already delivered and cannot be changed.");
        }

        int currentOrdinal = current.ordinal();
        int nextOrdinal    = next.ordinal();

        // Status must move forward (no going backwards)
        if (nextOrdinal <= currentOrdinal) {
            throw new InvalidOrderStateException(
                    "Invalid status transition from " + current + " to " + next + ".");
        }
    }
}
