package com.scaler.orderservice.security;

import com.scaler.orderservice.exception.ForbiddenException;
import org.springframework.stereotype.Component;

/**
 * Reads the X-User-Role header injected by the API Gateway and throws
 * ForbiddenException if the caller does not have the required role.
 */
@Component
public class RoleValidator {

    public void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("This action requires ADMIN privileges.");
        }
    }
}
