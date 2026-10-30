package com.courier.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time GPS location payload broadcast to the admin fleet topic:
 * /topic/admin/fleet
 * Contains aggregated shipment and fleet coordinates for dispatcher live mapping.
 * Restricted strictly to users with ROLE_ADMIN.
 */
public record FleetTrackingBroadcast(
        Long shipmentId,
        String trackingNumber,
        Long agentId,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        BigDecimal speed,
        BigDecimal bearing,
        Instant timestamp
) {}
