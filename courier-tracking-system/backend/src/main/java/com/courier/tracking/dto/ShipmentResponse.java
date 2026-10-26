package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO representing safe shipment details returned upon creation or query.
 * Excludes internal database security details, password hashes, and sensitive user secrets.
 */
public record ShipmentResponse(
        Long id,
        String trackingNumber,
        ShipmentStatus status,
        Long customerId,
        String customerEmail,
        String senderName,
        String senderAddress,
        String senderPhone,
        String recipientName,
        String recipientAddress,
        String recipientPhone,
        BigDecimal recipientLatitude,
        BigDecimal recipientLongitude,
        BigDecimal weightKg,
        BigDecimal price,
        String currency,
        Instant estimatedDeliveryAt,
        Instant createdAt
) {
}
