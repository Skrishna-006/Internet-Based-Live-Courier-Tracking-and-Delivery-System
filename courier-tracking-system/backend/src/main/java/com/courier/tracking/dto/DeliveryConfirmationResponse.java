package com.courier.tracking.dto;

import com.courier.tracking.entity.ProofType;
import com.courier.tracking.entity.ShipmentStatus;

import java.time.Instant;

/**
 * Outbound DTO returned upon successful electronic delivery confirmation.
 * Confirms parcel handover, proof type, and verification timestamp.
 */
public record DeliveryConfirmationResponse(
        Long shipmentId,
        String trackingNumber,
        ShipmentStatus status,
        Long deliveryProofId,
        ProofType proofType,
        String recipientName,
        Instant deliveredAt
) {
}
