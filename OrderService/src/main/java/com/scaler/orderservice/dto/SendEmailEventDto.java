package com.scaler.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Matches the DTO consumed by EmailService from the "send-email" Kafka topic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SendEmailEventDto {
    private String to;
    private String subject;
    private String body;
}
