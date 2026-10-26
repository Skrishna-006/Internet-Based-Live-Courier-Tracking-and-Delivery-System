package com.courier.tracking.dto;

import java.time.Instant;

/**
 * Safe outbound DTO representing user account details.
 * Strictly omits password hashes and internal security credentials.
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String phoneNumber,
        String role,
        Boolean active,
        Instant createdAt
) {
}
