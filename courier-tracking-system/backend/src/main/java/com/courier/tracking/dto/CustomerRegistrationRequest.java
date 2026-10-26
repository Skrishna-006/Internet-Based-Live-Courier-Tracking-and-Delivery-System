package com.courier.tracking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for public customer registration.
 * Explicitly exposes only necessary registration fields; privileged fields
 * (role, status, ID, password hash) cannot be bound.
 */
public record CustomerRegistrationRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
        String fullName,

        @NotBlank(message = "Email address is required")
        @Email(message = "Email must be a valid email address format")
        @Size(max = 180, message = "Email address cannot exceed 180 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @Pattern(regexp = "^$|^[+0-9\\-\\s()]{7,25}$", message = "Phone number must be a valid telephone format")
        @Size(max = 30, message = "Phone number cannot exceed 30 characters")
        String phoneNumber
) {
    public CustomerRegistrationRequest {
        if (fullName != null) {
            fullName = fullName.trim();
        }
        if (email != null) {
            email = email.trim();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
        }
    }
}
