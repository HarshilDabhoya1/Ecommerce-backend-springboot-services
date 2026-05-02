package com.scaler.orderservice.controller;

import com.scaler.orderservice.dto.CreateOrderRequestDto;
import com.scaler.orderservice.dto.OrderResponseDto;
import com.scaler.orderservice.dto.UpdateOrderStatusDto;
import com.scaler.orderservice.exception.OrderNotFoundException;
import com.scaler.orderservice.security.RoleValidator;
import com.scaler.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final RoleValidator roleValidator;

    public OrderController(OrderService orderService, RoleValidator roleValidator) {
        this.orderService  = orderService;
        this.roleValidator = roleValidator;
    }

    // ── Any authenticated user ─────────────────────────────────────────────────

    /**
     * Place a new order.
     * POST /orders
     */
    @PostMapping
    public ResponseEntity<OrderResponseDto> placeOrder(@Valid @RequestBody CreateOrderRequestDto dto) {
        OrderResponseDto response = OrderResponseDto.from(orderService.placeOrder(dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get order by ID.
     * GET /orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(OrderResponseDto::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Get all orders for a specific user (paginated).
     * GET /orders/user/{userId}?page=0&size=10&sort=createdAt,desc
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponseDto>> getOrdersByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<OrderResponseDto> page = orderService
                .getOrdersByUser(userId, pageable)
                .map(OrderResponseDto::from);
        return ResponseEntity.ok(page);
    }

    /**
     * Cancel an order (only PENDING / CONFIRMED orders can be cancelled).
     * PATCH /orders/{id}/cancel
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(@PathVariable Long id) {
        OrderResponseDto response = OrderResponseDto.from(orderService.cancelOrder(id));
        return ResponseEntity.ok(response);
    }

    // ── ADMIN only ─────────────────────────────────────────────────────────────

    /**
     * Advance order status (PENDING → CONFIRMED → SHIPPED → DELIVERED).
     * PATCH /orders/{id}/status   — ADMIN only
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody UpdateOrderStatusDto dto) {
        roleValidator.requireAdmin(role);
        OrderResponseDto response = OrderResponseDto.from(orderService.updateOrderStatus(id, dto));
        return ResponseEntity.ok(response);
    }
}
