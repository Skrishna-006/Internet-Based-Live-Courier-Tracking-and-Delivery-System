package com.courier.tracking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for user authentication.
 * Explicitly restricted to email and password; no role or privileged fields can be accepted.
 */
public record LoginRequest(
        @NotBlank(message = "Email address is required")
        @Email(message = "Email must be a valid email address format")
        @Size(max = 180, message = "Email address cannot exceed 180 characters")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
