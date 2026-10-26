package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;

import java.time.Instant;

/**
 * Public-safe milestone summary representing a waypoint scan or delivery checkpoint.
 * Strictly excludes internal coordinates, courier identifiers, and raw database IDs.
 */
public record PublicTrackingMilestoneDto(
        ShipmentStatus status,
        String locationName,
        String remarks,
        Instant timestamp
) {
}
