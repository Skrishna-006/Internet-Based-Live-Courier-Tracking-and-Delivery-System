package com.courier.tracking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request payload for assigning or reassigning a delivery agent to a shipment.
 */
public record AssignAgentRequest(
        @NotNull(message = "Delivery agent ID is required")
        @Positive(message = "Delivery agent ID must be a positive number")
        Long agentId
) {
}
