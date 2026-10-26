package com.courier.tracking.entity;

/**
 * Strict finite state machine status values for shipment progression.
 */
public enum ShipmentStatus {
    CREATED,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    ATTEMPT_FAILED,
    RETURNED,
    CANCELLED
}
