package com.scaler.productservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ErrorResponseDto {

    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
}