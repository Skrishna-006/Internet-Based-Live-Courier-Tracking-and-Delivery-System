package com.courier.tracking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound request DTO for delivery agent live GPS telemetry ingestion.
 * Contains client-reported coordinates, device accuracy, speed, and timestamp.
 * Coordinates are treated as unverified client telemetry subject to server validation.
 */
public record LocationUpdateRequest(
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
        BigDecimal latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
        BigDecimal longitude,

        @NotNull(message = "Accuracy is required")
        @DecimalMin(value = "0.0", message = "Accuracy must be non-negative")
        @DecimalMax(value = "30.0", message = "Accuracy must not exceed 30.0 meters")
        BigDecimal accuracy,

        @DecimalMin(value = "0.0", message = "Speed must be non-negative")
        @DecimalMax(value = "140.0", message = "Speed must not exceed 140.0 km/h")
        BigDecimal speed,

        BigDecimal bearing,

        @NotNull(message = "Timestamp is required")
        Instant timestamp
) {
    public LocationUpdateRequest(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy, Instant timestamp) {
        this(latitude, longitude, accuracy, null, null, timestamp);
    }

    public LocationUpdateRequest(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy, BigDecimal speed, Instant timestamp) {
        this(latitude, longitude, accuracy, speed, null, timestamp);
    }
}
