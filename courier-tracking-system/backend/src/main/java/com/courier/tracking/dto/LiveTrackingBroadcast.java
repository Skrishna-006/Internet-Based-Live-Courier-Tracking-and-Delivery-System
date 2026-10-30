package com.courier.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time GPS location payload broadcast to the customer tracking topic:
 * /topic/track/{trackingNumber}
 * Contains sanitized coordinates and movement telemetry without exposing private shipment details,
 * consignor/consignee phone numbers, addresses, or delivery agent identity.
 */
public record LiveTrackingBroadcast(
        String trackingNumber,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        BigDecimal speed,
        BigDecimal bearing,
        Instant timestamp
) {}
