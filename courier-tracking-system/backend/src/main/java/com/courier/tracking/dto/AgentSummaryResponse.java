package com.courier.tracking.dto;

/**
 * Summary metrics of shipments assigned to an authenticated delivery agent.
 * Reflects accurate counts across all pages of the agent's delivery queue.
 */
public record AgentSummaryResponse(
        long totalAssigned,
        long awaitingPickup,
        long inTransit,
        long outForDelivery,
        long completed
) {}
