package com.courier.tracking.dto;

/**
 * Outbound DTO representing high-level operational metrics and consignment counts
 * for the Administrator & Dispatch Control Dashboard.
 */
public record AdminOverviewResponse(
        long totalShipments,
        long unassignedShipments,
        long assignedShipments,
        long inTransitShipments,
        long outForDeliveryShipments,
        long deliveredShipments,
        long failedDeliveryShipments,
        long activeAgents
) {
}
