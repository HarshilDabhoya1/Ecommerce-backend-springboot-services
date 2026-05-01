package com.scaler.apigateway.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Mirrors the UserResponseDto returned by UserService /users/validate.
 * Used to extract userId and role so the gateway can forward them as
 * X-User-Id and X-User-Role headers to downstream services.
 */
@Getter
@Setter
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private String role;   // plain String — no dependency on UserService enum
}
