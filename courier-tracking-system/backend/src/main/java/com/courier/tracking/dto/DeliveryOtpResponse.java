package com.courier.tracking.dto;

import java.time.Instant;

/**
 * Outbound DTO returned upon delivery OTP generation.
 * Contains dispatch tracking details and expiration metadata.
 */
public record DeliveryOtpResponse(
        Long shipmentId,
        String trackingNumber,
        String otp,
        Instant expiresAt,
        String message
) {
}
