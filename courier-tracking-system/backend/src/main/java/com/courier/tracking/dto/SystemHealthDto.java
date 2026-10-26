package com.courier.tracking.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Diagnostic DTO reporting system health, versioning, and environment details.
 */
public record SystemHealthDto(
        String status,
        String systemName,
        String version,
        String environment,
        Instant timestamp,
        Map<String, Object> components
) {}
