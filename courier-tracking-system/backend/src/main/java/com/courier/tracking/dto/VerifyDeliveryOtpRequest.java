package com.courier.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound request DTO for confirming delivery using the recipient's OTP.
 */
public record VerifyDeliveryOtpRequest(
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        String otp,

        @Size(max = 120, message = "Recipient name cannot exceed 120 characters")
        String recipientName,

        @Size(max = 500, message = "Verification notes cannot exceed 500 characters")
        String verificationNotes,

        BigDecimal latitude,

        BigDecimal longitude,

        @Size(max = 255, message = "Location name cannot exceed 255 characters")
        String locationName
) {
    public VerifyDeliveryOtpRequest(String otp) {
        this(otp, null, null, null, null, null);
    }

    public VerifyDeliveryOtpRequest(String otp, String recipientName) {
        this(otp, recipientName, null, null, null, null);
    }
}
