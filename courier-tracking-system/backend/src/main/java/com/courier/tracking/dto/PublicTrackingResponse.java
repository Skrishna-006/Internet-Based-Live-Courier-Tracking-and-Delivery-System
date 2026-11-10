package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public-safe shipment tracking response DTO.
 * Excludes customer identities, recipient name, phone numbers, full physical addresses,
 * pricing, and internal database keys.
 * Destination GPS coordinates (latitude/longitude) are included to enable
 * route visualization on the public tracking map — no PII is attached.
 */
public record PublicTrackingResponse(
        String trackingNumber,
        ShipmentStatus status,
        Instant estimatedDeliveryAt,
        Instant actualDeliveryAt,
        Instant createdAt,
        List<PublicTrackingMilestoneDto> milestones,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude
) {
    /** Convenience constructor for responses without milestones or destination coordinates. */
    public PublicTrackingResponse(
            String trackingNumber,
            ShipmentStatus status,
            Instant estimatedDeliveryAt,
            Instant actualDeliveryAt,
            Instant createdAt
    ) {
        this(trackingNumber, status, estimatedDeliveryAt, actualDeliveryAt, createdAt, List.of(), null, null);
    }

    /** Convenience constructor for responses with milestones but without destination coordinates. */
    public PublicTrackingResponse(
            String trackingNumber,
            ShipmentStatus status,
            Instant estimatedDeliveryAt,
            Instant actualDeliveryAt,
            Instant createdAt,
            List<PublicTrackingMilestoneDto> milestones
    ) {
        this(trackingNumber, status, estimatedDeliveryAt, actualDeliveryAt, createdAt, milestones, null, null);
    }
}
