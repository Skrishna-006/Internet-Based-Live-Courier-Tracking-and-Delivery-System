package com.courier.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO returned upon successful delivery agent live telemetry ingestion.
 * Contains accepted coordinates and server-authoritative recording timestamp.
 * Deliberately excludes sensitive customer and courier PII.
 */
public record LocationUpdateResponse(
        Long shipmentId,
        String trackingNumber,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
