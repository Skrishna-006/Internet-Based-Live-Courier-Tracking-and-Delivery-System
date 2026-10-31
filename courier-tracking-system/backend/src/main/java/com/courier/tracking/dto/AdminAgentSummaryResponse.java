package com.courier.tracking.dto;

/**
 * Outbound DTO representing delivery agent profile, contact details,
 * and workload metrics for dispatch management.
 */
public record AdminAgentSummaryResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        boolean active,
        long totalAssignedShipments,
        long activeDeliveriesCount,
        long deliveredShipmentsCount
) {
}
