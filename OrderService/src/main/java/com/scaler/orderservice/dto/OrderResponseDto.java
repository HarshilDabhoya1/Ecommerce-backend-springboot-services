package com.scaler.orderservice.dto;

import com.scaler.orderservice.model.Order;
import com.scaler.orderservice.model.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderResponseDto {

    private Long id;
    private Long userId;
    private String userEmail;
    private OrderStatus status;
    private double totalAmount;
    private List<OrderItemResponseDto> items;
    private Instant createdAt;
    private Instant updatedAt;

    public static OrderResponseDto from(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getItems()
                .stream()
                .map(OrderItemResponseDto::from)
                .toList();

        return OrderResponseDto.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(itemDtos)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
