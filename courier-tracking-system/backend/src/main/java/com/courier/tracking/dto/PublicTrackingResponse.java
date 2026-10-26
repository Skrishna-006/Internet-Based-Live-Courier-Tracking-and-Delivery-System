package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Public-safe shipment tracking response DTO.
 * Excludes customer identities, recipient coordinates, full physical addresses,
 * telephone numbers, pricing, and internal database keys.
 */
public record PublicTrackingResponse(
        String trackingNumber,
        ShipmentStatus status,
        Instant estimatedDeliveryAt,
        Instant actualDeliveryAt,
        Instant createdAt,
        List<PublicTrackingMilestoneDto> milestones
) {
    public PublicTrackingResponse(
            String trackingNumber,
            ShipmentStatus status,
            Instant estimatedDeliveryAt,
            Instant actualDeliveryAt,
            Instant createdAt
    ) {
        this(trackingNumber, status, estimatedDeliveryAt, actualDeliveryAt, createdAt, List.of());
    }
}
