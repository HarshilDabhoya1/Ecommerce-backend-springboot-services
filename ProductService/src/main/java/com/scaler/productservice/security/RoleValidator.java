package com.scaler.productservice.security;

import com.scaler.productservice.exception.ForbiddenException;
import org.springframework.stereotype.Component;

/**
 * Reads the X-User-Role header injected by the API Gateway and throws
 * ForbiddenException if the caller does not have the required role.
 *
 * The header is always present on authenticated requests because the
 * Gateway's AuthenticationFilter adds it after successful token validation.
 */
@Component
public class RoleValidator {

    public void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("This action requires ADMIN privileges.");
        }
    }
}
