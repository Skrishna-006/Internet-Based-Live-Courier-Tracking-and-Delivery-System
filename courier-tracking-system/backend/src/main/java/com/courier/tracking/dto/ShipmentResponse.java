package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbound DTO representing safe shipment details returned upon creation, query, assignment, or transition.
 * Excludes internal database security details, password hashes, and sensitive user secrets.
 */
public record ShipmentResponse(
        Long id,
        String trackingNumber,
        ShipmentStatus status,
        Long customerId,
        String customerEmail,
        Long assignedAgentId,
        String assignedAgentName,
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
        Instant actualDeliveryAt,
        Instant createdAt
) {
    /**
     * Backward-compatible constructor for scenarios where actualDeliveryAt is omitted.
     */
    public ShipmentResponse(
            Long id,
            String trackingNumber,
            ShipmentStatus status,
            Long customerId,
            String customerEmail,
            Long assignedAgentId,
            String assignedAgentName,
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
        this(
                id,
                trackingNumber,
                status,
                customerId,
                customerEmail,
                assignedAgentId,
                assignedAgentName,
                senderName,
                senderAddress,
                senderPhone,
                recipientName,
                recipientAddress,
                recipientPhone,
                recipientLatitude,
                recipientLongitude,
                weightKg,
                price,
                currency,
                estimatedDeliveryAt,
                null,
                createdAt
        );
    }

    /**
     * Backward-compatible constructor for scenarios where assigned agent information and actual delivery are omitted.
     */
    public ShipmentResponse(
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
        this(
                id,
                trackingNumber,
                status,
                customerId,
                customerEmail,
                null,
                null,
                senderName,
                senderAddress,
                senderPhone,
                recipientName,
                recipientAddress,
                recipientPhone,
                recipientLatitude,
                recipientLongitude,
                weightKg,
                price,
                currency,
                estimatedDeliveryAt,
                null,
                createdAt
        );
    }
}
