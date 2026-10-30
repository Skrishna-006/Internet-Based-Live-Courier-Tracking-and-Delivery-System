package com.courier.tracking.dto;

import java.math.BigDecimal;

/**
 * Server-authoritative price quote response DTO.
 * Exposes the calculated delivery cost, standard currency, and pricing parameters.
 */
public record PriceEstimateResponse(
        BigDecimal weightKg,
        BigDecimal price,
        String currency,
        BigDecimal baseRate,
        BigDecimal perKgRate
) {}
