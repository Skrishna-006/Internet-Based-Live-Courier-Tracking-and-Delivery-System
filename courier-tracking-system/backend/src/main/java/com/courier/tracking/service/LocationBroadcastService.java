package com.courier.tracking.service;

import com.courier.tracking.dto.FleetTrackingBroadcast;
import com.courier.tracking.dto.LiveTrackingBroadcast;
import com.courier.tracking.dto.LocationUpdateRequest;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.entity.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service responsible for real-time WebSocket STOMP broadcasting of accepted GPS location updates.
 * Broadcasts sanitized coordinates to the customer tracking topic (/topic/track/{trackingNumber})
 * and aggregated telemetry to the admin fleet topic (/topic/admin/fleet).
 *
 * Implements fault isolation so that any WebSocket broadcasting failure does not corrupt or roll back
 * the underlying transactional persistence of GPS updates.
 */
@Service
public class LocationBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(LocationBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public LocationBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Broadcasts the latest accepted GPS location telemetry to both the customer and admin topics.
     * Safely catches and logs any messaging exceptions to prevent transactional rollback.
     *
     * @param shipment The updated and persisted shipment entity
     * @param request The inbound GPS update request containing velocity and bearing
     * @param timestamp Server-accepted timestamp of the telemetry update
     */
    public void broadcastLocationUpdate(Shipment shipment, LocationUpdateRequest request, Instant timestamp) {
        if (shipment == null) {
            log.warn("Cannot broadcast location: shipment is null");
            return;
        }

        try {
            // 1. Broadcast to Customer Topic: /topic/track/{trackingNumber}
            String customerTopic = "/topic/track/" + shipment.getTrackingNumber();
            LiveTrackingBroadcast customerMessage = new LiveTrackingBroadcast(
                    shipment.getTrackingNumber(),
                    shipment.getCurrentLatitude(),
                    shipment.getCurrentLongitude(),
                    shipment.getStatus() != null ? shipment.getStatus().name() : null,
                    request != null ? request.speed() : null,
                    request != null ? request.bearing() : null,
                    timestamp
            );
            messagingTemplate.convertAndSend(customerTopic, customerMessage);

            // 2. Broadcast to Admin Fleet Topic: /topic/admin/fleet
            String adminTopic = "/topic/admin/fleet";
            Long agentId = (shipment.getAssignedAgent() != null) ? shipment.getAssignedAgent().getId() : null;
            FleetTrackingBroadcast adminMessage = new FleetTrackingBroadcast(
                    shipment.getId(),
                    shipment.getTrackingNumber(),
                    agentId,
                    shipment.getCurrentLatitude(),
                    shipment.getCurrentLongitude(),
                    shipment.getStatus() != null ? shipment.getStatus().name() : null,
                    request != null ? request.speed() : null,
                    request != null ? request.bearing() : null,
                    timestamp
            );
            messagingTemplate.convertAndSend(adminTopic, adminMessage);

            log.info("Broadcasted live GPS update for shipment '{}' to customer topic '{}' and admin topic '{}'",
                    shipment.getTrackingNumber(), customerTopic, adminTopic);
        } catch (Exception ex) {
            log.error("Failed to broadcast WebSocket location update for shipment '{}': {}",
                    shipment.getTrackingNumber(), ex.getMessage(), ex);
        }
    }

    /**
     * Broadcasts an assignment notification to the agent's private dispatch channel.
     * Channel: /topic/agent/{agentId}/orders
     */
    public void broadcastAssignment(Long agentId, ShipmentResponse shipmentResponse) {
        if (agentId == null || shipmentResponse == null) {
            log.warn("Cannot broadcast assignment: agentId or shipmentResponse is null");
            return;
        }

        try {
            String agentTopic = "/topic/agent/" + agentId + "/orders";
            messagingTemplate.convertAndSend(agentTopic, shipmentResponse);
            log.info("Broadcasted assignment notification for shipment '{}' to agent topic '{}'",
                    shipmentResponse.trackingNumber(), agentTopic);
        } catch (Exception ex) {
            log.error("Failed to broadcast assignment notification for agentId {}: {}",
                    agentId, ex.getMessage(), ex);
        }
    }
}
