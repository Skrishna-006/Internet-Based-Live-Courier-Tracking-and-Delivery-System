package com.courier.tracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound payload for customer shipment creation.
 * Exposes only necessary shipment attributes. Privileged fields (shipment ID, tracking number,
 * customer ID, workflow status, agent assignment, creation timestamps) cannot be bound.
 */
public record CreateShipmentRequest(
        @NotBlank(message = "Sender name is required")
        @Size(min = 2, max = 120, message = "Sender name must be between 2 and 120 characters")
        String senderName,

        @NotBlank(message = "Sender address is required")
        @Size(min = 5, max = 1000, message = "Sender address must be between 5 and 1000 characters")
        String senderAddress,

        @NotBlank(message = "Sender phone number is required")
        @Pattern(regexp = "^[+0-9\\-\\s()]{7,30}$", message = "Sender phone number must be a valid telephone format")
        String senderPhone,

        @NotBlank(message = "Recipient name is required")
        @Size(min = 2, max = 120, message = "Recipient name must be between 2 and 120 characters")
        String recipientName,

        @NotBlank(message = "Recipient address is required")
        @Size(min = 5, max = 1000, message = "Recipient address must be between 5 and 1000 characters")
        String recipientAddress,

        @NotBlank(message = "Recipient phone number is required")
        @Pattern(regexp = "^[+0-9\\-\\s()]{7,30}$", message = "Recipient phone number must be a valid telephone format")
        String recipientPhone,

        @DecimalMin(value = "-90.0000000", message = "Recipient latitude must be >= -90.0")
        @DecimalMax(value = "90.0000000", message = "Recipient latitude must be <= 90.0")
        BigDecimal recipientLatitude,

        @DecimalMin(value = "-180.0000000", message = "Recipient longitude must be >= -180.0")
        @DecimalMax(value = "180.0000000", message = "Recipient longitude must be <= 180.0")
        BigDecimal recipientLongitude,

        @NotNull(message = "Package weight in kg is required")
        @DecimalMin(value = "0.050", message = "Package weight must be at least 0.050 kg (50g)")
        @DecimalMax(value = "1000.000", message = "Package weight cannot exceed 1000.000 kg")
        BigDecimal weightKg,

        @DecimalMin(value = "0.01", message = "Price must be greater than zero if specified")
        BigDecimal price
) {
    public CreateShipmentRequest {
        if (senderName != null) senderName = senderName.trim();
        if (senderAddress != null) senderAddress = senderAddress.trim();
        if (senderPhone != null) senderPhone = senderPhone.trim();
        if (recipientName != null) recipientName = recipientName.trim();
        if (recipientAddress != null) recipientAddress = recipientAddress.trim();
        if (recipientPhone != null) recipientPhone = recipientPhone.trim();
    }
}
