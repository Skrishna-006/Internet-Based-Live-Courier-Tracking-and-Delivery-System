package com.courier.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO representing active fleet vehicle telemetry for dispatch map initialization.
 * Contains only actual verified data from the shipment and assigned agent entities.
 */
public record AdminFleetMemberResponse(
        Long agentId,
        String agentName,
        Long shipmentId,
        String trackingNumber,
        String status,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
