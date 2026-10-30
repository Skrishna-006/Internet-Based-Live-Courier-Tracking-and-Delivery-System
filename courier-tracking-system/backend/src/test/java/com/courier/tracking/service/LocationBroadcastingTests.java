package com.courier.tracking.service;

import com.courier.tracking.dto.FleetTrackingBroadcast;
import com.courier.tracking.dto.LiveTrackingBroadcast;
import com.courier.tracking.dto.LocationUpdateRequest;
import com.courier.tracking.dto.LocationUpdateResponse;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocationBroadcastingTests {

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private LocationBroadcastService broadcastService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User agentUser;
    private User customerUser;
    private Shipment activeShipment;

    @BeforeEach
    void setUp() {
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();

        agentUser = userRepository.save(new User(
                "agent.telemetry.test@example.com",
                passwordEncoder.encode("Password123!"),
                "Agent Bob",
                "+1-555-4321",
                agentRole
        ));

        customerUser = userRepository.save(new User(
                "customer.buyer.test@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer Alice",
                "+1-555-1234",
                customerRole
        ));

        activeShipment = new Shipment(
                "TRK-20261030-GPS001",
                customerUser,
                "Sender Consignor",
                "100 Main St, Austin, TX",
                "+1-555-1111",
                "Recipient Consignee",
                "200 Market St, Austin, TX",
                "+1-555-2222",
                new BigDecimal("3.500"),
                new BigDecimal("25.00"),
                "USD"
        );
        activeShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        activeShipment.setAssignedAgent(agentUser);
        activeShipment = shipmentRepository.save(activeShipment);
    }

    @Test
    @DisplayName("broadcastLocationUpdate dispatches sanitized customer payload and aggregated fleet payload")
    void broadcastLocationUpdate_dispatchesExpectedPayloads() {
        // Create an in-memory recording SimpMessagingTemplate using an anonymous subclass
        List<String> destinations = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();

        MessageChannel dummyChannel = (message, timeout) -> true;
        SimpMessagingTemplate recordingTemplate = new SimpMessagingTemplate(dummyChannel) {
            @Override
            public void convertAndSend(String destination, Object payload) {
                destinations.add(destination);
                payloads.add(payload);
            }
        };

        LocationBroadcastService recordingBroadcastService = new LocationBroadcastService(recordingTemplate);

        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("12.5"),
                new BigDecimal("45.0"),
                new BigDecimal("180.0"),
                now
        );

        activeShipment.setCurrentLatitude(new BigDecimal("30.267200"));
        activeShipment.setCurrentLongitude(new BigDecimal("-97.743100"));

        recordingBroadcastService.broadcastLocationUpdate(activeShipment, request, now);

        assertEquals(2, destinations.size());
        assertEquals("/topic/track/" + activeShipment.getTrackingNumber(), destinations.get(0));
        assertEquals("/topic/admin/fleet", destinations.get(1));

        // 1. Verify customer message does not expose PII
        LiveTrackingBroadcast customerMsg = (LiveTrackingBroadcast) payloads.get(0);
        assertEquals(activeShipment.getTrackingNumber(), customerMsg.trackingNumber());
        assertEquals(new BigDecimal("30.267200"), customerMsg.latitude());
        assertEquals(new BigDecimal("-97.743100"), customerMsg.longitude());
        assertEquals("OUT_FOR_DELIVERY", customerMsg.status());
        assertEquals(new BigDecimal("45.0"), customerMsg.speed());
        assertEquals(new BigDecimal("180.0"), customerMsg.bearing());
        assertEquals(now, customerMsg.timestamp());

        // 2. Verify fleet message
        FleetTrackingBroadcast fleetMsg = (FleetTrackingBroadcast) payloads.get(1);
        assertEquals(activeShipment.getId(), fleetMsg.shipmentId());
        assertEquals(activeShipment.getTrackingNumber(), fleetMsg.trackingNumber());
        assertEquals(agentUser.getId(), fleetMsg.agentId());
        assertEquals(new BigDecimal("30.267200"), fleetMsg.latitude());
        assertEquals(new BigDecimal("-97.743100"), fleetMsg.longitude());
    }

    @Test
    @DisplayName("broadcastLocationUpdate isolates broker failure and does not throw exception")
    void broadcastLocationUpdate_isolatesBrokerFailure() {
        MessageChannel dummyChannel = (message, timeout) -> true;
        SimpMessagingTemplate failingTemplate = new SimpMessagingTemplate(dummyChannel) {
            @Override
            public void convertAndSend(String destination, Object payload) {
                throw new org.springframework.messaging.MessagingException("Simulated broker transport failure");
            }
        };

        LocationBroadcastService failingBroadcastService = new LocationBroadcastService(failingTemplate);

        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("10.0"),
                new BigDecimal("30.0"),
                BigDecimal.ZERO,
                now
        );

        assertDoesNotThrow(() -> failingBroadcastService.broadcastLocationUpdate(activeShipment, request, now));
    }

    @Test
    @DisplayName("updateLiveLocation persists accepted coordinates and returns LocationUpdateResponse")
    void updateLiveLocation_successfulUpdate_persistsCoordinates() {
        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("15.0"),
                new BigDecimal("40.0"),
                new BigDecimal("90.0"),
                now
        );

        LocationUpdateResponse response = shipmentService.updateLiveLocation(
                activeShipment.getId(), request, agentUser.getEmail());

        assertNotNull(response);
        assertEquals(activeShipment.getId(), response.shipmentId());
        assertEquals(activeShipment.getTrackingNumber(), response.trackingNumber());
        assertEquals(new BigDecimal("30.267200"), response.latitude());
        assertEquals(new BigDecimal("-97.743100"), response.longitude());

        Shipment updated = shipmentRepository.findById(activeShipment.getId()).orElseThrow();
        assertEquals(new BigDecimal("30.267200"), updated.getCurrentLatitude());
        assertEquals(new BigDecimal("-97.743100"), updated.getCurrentLongitude());
        assertNotNull(updated.getLastLocationUpdatedAt());
    }

    @Test
    @DisplayName("updateLiveLocation rejects update when accuracy exceeds 30.0m")
    void updateLiveLocation_rejectedAccuracy_throwsException() {
        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("35.0"), // exceeds 30m
                new BigDecimal("40.0"),
                new BigDecimal("90.0"),
                now
        );

        assertThrows(IllegalArgumentException.class,
                () -> shipmentService.updateLiveLocation(activeShipment.getId(), request, agentUser.getEmail()));
    }

    @Test
    @DisplayName("updateLiveLocation rejects update when speed exceeds 140.0 km/h")
    void updateLiveLocation_rejectedSpeed_throwsException() {
        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("10.0"),
                new BigDecimal("150.0"), // exceeds 140 km/h
                new BigDecimal("90.0"),
                now
        );

        assertThrows(IllegalArgumentException.class,
                () -> shipmentService.updateLiveLocation(activeShipment.getId(), request, agentUser.getEmail()));
    }

    @Test
    @DisplayName("updateLiveLocation rejects stale timestamp (> 60s)")
    void updateLiveLocation_staleTimestamp_throwsException() {
        Instant stale = Instant.now().minusSeconds(120);
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("10.0"),
                new BigDecimal("30.0"),
                new BigDecimal("90.0"),
                stale
        );

        assertThrows(IllegalArgumentException.class,
                () -> shipmentService.updateLiveLocation(activeShipment.getId(), request, agentUser.getEmail()));
    }

    @Test
    @DisplayName("updateLiveLocation rejects update when shipment is not in OUT_FOR_DELIVERY status")
    void updateLiveLocation_wrongStatus_throwsException() {
        activeShipment.setStatus(ShipmentStatus.ASSIGNED);
        shipmentRepository.save(activeShipment);

        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("10.0"),
                new BigDecimal("30.0"),
                new BigDecimal("90.0"),
                now
        );

        assertThrows(IllegalStateException.class,
                () -> shipmentService.updateLiveLocation(activeShipment.getId(), request, agentUser.getEmail()));
    }

    @Test
    @DisplayName("updateLiveLocation rejects unauthorized agent update")
    void updateLiveLocation_unauthorizedAgent_throwsException() {
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
        User otherAgent = userRepository.save(new User(
                "other.agent.loc@example.com",
                passwordEncoder.encode("Password123!"),
                "Other Agent",
                "+1-555-9876",
                agentRole
        ));

        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("10.0"),
                new BigDecimal("30.0"),
                new BigDecimal("90.0"),
                now
        );

        assertThrows(AccessDeniedException.class,
                () -> shipmentService.updateLiveLocation(activeShipment.getId(), request, otherAgent.getEmail()));
    }
}
