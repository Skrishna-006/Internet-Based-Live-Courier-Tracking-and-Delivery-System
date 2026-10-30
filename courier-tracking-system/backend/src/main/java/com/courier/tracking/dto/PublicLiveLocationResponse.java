package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public live location polling response DTO for tracking fallback.
 * Strictly limited to sanitized telemetry coordinates, timestamp, status, and freshness.
 * Excludes customer identities, recipient coordinates, full physical addresses,
 * telephone numbers, agent personal details, and internal database keys.
 */
public record PublicLiveLocationResponse(
        String trackingNumber,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt,
        ShipmentStatus status,
        boolean fresh
) {
    public PublicLiveLocationResponse(String trackingNumber, ShipmentStatus status) {
        this(trackingNumber, null, null, null, status, false);
    }
}
