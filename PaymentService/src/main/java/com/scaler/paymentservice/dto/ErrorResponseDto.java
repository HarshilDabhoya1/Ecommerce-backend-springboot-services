package com.scaler.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ErrorResponseDto {

    private int status;
    private String error;
    private String message;
    private Instant timestamp;
}
