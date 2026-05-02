package com.scaler.orderservice.service;

import com.scaler.orderservice.dto.CreateOrderRequestDto;
import com.scaler.orderservice.dto.UpdateOrderStatusDto;
import com.scaler.orderservice.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface OrderService {

    Order placeOrder(CreateOrderRequestDto dto);

    Optional<Order> getOrderById(Long id);

    Page<Order> getOrdersByUser(Long userId, Pageable pageable);

    Order cancelOrder(Long id);

    Order updateOrderStatus(Long id, UpdateOrderStatusDto dto);
}
