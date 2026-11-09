package com.courier.tracking.dto;

import java.time.Instant;

/**
 * Outbound DTO for customer-side delivery PIN presentation.
 * Strictly accessible only by authenticated owning customer.
 */
public record CustomerDeliveryPinResponse(
        Long shipmentId,
        String trackingNumber,
        String deliveryPin,
        Instant expiresAt,
        String message
) {}
