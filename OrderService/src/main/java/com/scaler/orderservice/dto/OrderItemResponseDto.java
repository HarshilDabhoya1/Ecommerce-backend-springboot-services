package com.scaler.orderservice.dto;

import com.scaler.orderservice.model.OrderItem;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponseDto {

    private Long id;
    private Long productId;
    private String productTitle;
    private int quantity;
    private double unitPrice;
    private double subtotal;

    public static OrderItemResponseDto from(OrderItem item) {
        return OrderItemResponseDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productTitle(item.getProductTitle())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getQuantity() * item.getUnitPrice())
                .build();
    }
}
