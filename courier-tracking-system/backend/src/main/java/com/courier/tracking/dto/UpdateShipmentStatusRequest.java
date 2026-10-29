package com.courier.tracking.dto;

import com.courier.tracking.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound request DTO for updating a shipment's lifecycle status.
 * Accepts the desired target status and optional waypoint scan information.
 */
public record UpdateShipmentStatusRequest(
        @NotNull(message = "Status is required")
        ShipmentStatus status,

        @Size(max = 255, message = "Location name cannot exceed 255 characters")
        String locationName,

        @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
        String remarks,

        BigDecimal latitude,

        BigDecimal longitude
) {
    public UpdateShipmentStatusRequest(ShipmentStatus status) {
        this(status, null, null, null, null);
    }

    public UpdateShipmentStatusRequest(ShipmentStatus status, String locationName, String remarks) {
        this(status, locationName, remarks, null, null);
    }
}
