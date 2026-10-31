package com.courier.tracking.repository;

import com.courier.tracking.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminRepositoryQueryTests {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User customer;
    private User agentAlpha;
    private User agentBeta;
    private User inactiveAgent;

    private Shipment unassignedCreated;
    private Shipment unassignedExpress;
    private Shipment assignedShipment;
    private Shipment inTransitShipment;
    private Shipment outForDeliveryShipment;
    private Shipment deliveredShipment;
    private Shipment failedAttemptShipment;
    private Shipment inTransitWithoutCoords;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        customer = userRepository.save(new User("admin.test.cust@example.com", "hash1", "Alice Customer", "555-0101", customerRole));
        agentAlpha = userRepository.save(new User("agent.alpha@example.com", "hash2", "Agent Alpha", "555-0201", agentRole));
        agentBeta = userRepository.save(new User("agent.beta@example.com", "hash3", "Agent Beta", "555-0202", agentRole));

        inactiveAgent = new User("agent.inactive@example.com", "hash4", "Agent Inactive", "555-0203", agentRole);
        inactiveAgent.setActive(false);
        inactiveAgent = userRepository.save(inactiveAgent);

        // 1. Unassigned CREATED
        unassignedCreated = createShipment("TRK-ADMIN-0001", customer, null, ShipmentStatus.CREATED, null, null);

        // 2. Unassigned CREATED with distinct tracking prefix for search testing
        unassignedExpress = createShipment("TRK-EXPRESS-9999", customer, null, ShipmentStatus.CREATED, null, null);

        // 3. Assigned to Agent Alpha
        assignedShipment = createShipment("TRK-ADMIN-0002", customer, agentAlpha, ShipmentStatus.ASSIGNED, null, null);

        // 4. In Transit with live GPS coordinates (Agent Alpha)
        inTransitShipment = createShipment("TRK-ADMIN-0003", customer, agentAlpha, ShipmentStatus.IN_TRANSIT,
                new BigDecimal("30.2672000"), new BigDecimal("-97.7431000"));

        // 5. Out For Delivery with live GPS coordinates (Agent Alpha)
        outForDeliveryShipment = createShipment("TRK-ADMIN-0004", customer, agentAlpha, ShipmentStatus.OUT_FOR_DELIVERY,
                new BigDecimal("30.2700000"), new BigDecimal("-97.7400000"));

        // 6. Delivered (Agent Alpha)
        deliveredShipment = createShipment("TRK-ADMIN-0005", customer, agentAlpha, ShipmentStatus.DELIVERED,
                new BigDecimal("30.2800000"), new BigDecimal("-97.7300000"));

        // 7. Attempt Failed (Agent Beta)
        failedAttemptShipment = createShipment("TRK-ADMIN-0006", customer, agentBeta, ShipmentStatus.ATTEMPT_FAILED, null, null);

        // 8. In Transit but without coordinates (null GPS coordinates)
        inTransitWithoutCoords = createShipment("TRK-ADMIN-0007", customer, agentBeta, ShipmentStatus.IN_TRANSIT, null, null);
    }

    private Shipment createShipment(String trk, User cust, User agent, ShipmentStatus status, BigDecimal lat, BigDecimal lon) {
        Shipment s = new Shipment(
                trk, cust,
                "Origin Sender", "100 Main St, Austin, TX", "+1-555-1111",
                "Jane Receiver", "200 South St, Austin, TX", "+1-555-2222",
                new BigDecimal("2.500"), new BigDecimal("25.00"), "USD"
        );
        s.setAssignedAgent(agent);
        s.setStatus(status);
        s.setCurrentLatitude(lat);
        s.setCurrentLongitude(lon);
        if (lat != null && lon != null) {
            s.setLastLocationUpdatedAt(Instant.now());
        }
        return shipmentRepository.save(s);
    }

    @Test
    @DisplayName("Overview Metrics: Count queries return exact database totals by status and unassigned state")
    void testShipmentCountsByStatusAndUnassigned() {
        assertEquals(8, shipmentRepository.count(), "Total shipment count should match total persisted");
        assertEquals(2, shipmentRepository.countByAssignedAgentIsNull(), "Two shipments should be unassigned");

        assertEquals(2, shipmentRepository.countByStatus(ShipmentStatus.CREATED));
        assertEquals(1, shipmentRepository.countByStatus(ShipmentStatus.ASSIGNED));
        assertEquals(2, shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT));
        assertEquals(1, shipmentRepository.countByStatus(ShipmentStatus.OUT_FOR_DELIVERY));
        assertEquals(1, shipmentRepository.countByStatus(ShipmentStatus.DELIVERED));
        assertEquals(1, shipmentRepository.countByStatus(ShipmentStatus.ATTEMPT_FAILED));
        assertEquals(0, shipmentRepository.countByStatus(ShipmentStatus.CANCELLED));
        assertEquals(0, shipmentRepository.countByStatus(ShipmentStatus.RETURNED));
    }

    @Test
    @DisplayName("Shipment Queries: Paginated retrieval and unassigned filtering operate correctly")
    void testPaginatedShipmentQueriesAndFilters() {
        // 1. Paginated system-wide query
        Page<Shipment> page1 = shipmentRepository.findAll(PageRequest.of(0, 5, Sort.by("id").ascending()));
        assertEquals(8, page1.getTotalElements());
        assertEquals(5, page1.getContent().size());
        assertEquals(2, page1.getTotalPages());

        // 2. Paginated status query
        Page<Shipment> inTransitPage = shipmentRepository.findByStatus(ShipmentStatus.IN_TRANSIT, PageRequest.of(0, 10));
        assertEquals(2, inTransitPage.getTotalElements());
        assertTrue(inTransitPage.getContent().stream().allMatch(s -> s.getStatus() == ShipmentStatus.IN_TRANSIT));

        // 3. Paginated unassigned query
        Page<Shipment> unassignedPage = shipmentRepository.findByAssignedAgentIsNull(PageRequest.of(0, 10));
        assertEquals(2, unassignedPage.getTotalElements());
        assertTrue(unassignedPage.getContent().stream().allMatch(s -> s.getAssignedAgent() == null));

        // 4. Paginated unassigned with status constraint
        Page<Shipment> unassignedCreatedPage = shipmentRepository.findByAssignedAgentIsNullAndStatus(ShipmentStatus.CREATED, PageRequest.of(0, 10));
        assertEquals(2, unassignedCreatedPage.getTotalElements());
    }

    @Test
    @DisplayName("Search: Tracking number case-insensitive substring search returns matching consignments")
    void testTrackingNumberSearchQueries() {
        // Substring "express" (lowercase) matching "TRK-EXPRESS-9999"
        Page<Shipment> expressResults = shipmentRepository.findByTrackingNumberContainingIgnoreCase("express", PageRequest.of(0, 10));
        assertEquals(1, expressResults.getTotalElements());
        assertEquals("TRK-EXPRESS-9999", expressResults.getContent().get(0).getTrackingNumber());

        // Substring "ADMIN" matching 7 consignments
        Page<Shipment> adminResults = shipmentRepository.findByTrackingNumberContainingIgnoreCase("ADMIN", PageRequest.of(0, 10));
        assertEquals(7, adminResults.getTotalElements());

        // Non-existent search
        Page<Shipment> noneResults = shipmentRepository.findByTrackingNumberContainingIgnoreCase("NONEXISTENT", PageRequest.of(0, 10));
        assertEquals(0, noneResults.getTotalElements());
    }

    @Test
    @DisplayName("Combined Search & Filter: Combined status and tracking number search narrows results precisely")
    void testCombinedStatusAndTrackingNumberSearch() {
        // IN_TRANSIT with "0003" -> TRK-ADMIN-0003
        Page<Shipment> matches = shipmentRepository.findByStatusAndTrackingNumberContainingIgnoreCase(
                ShipmentStatus.IN_TRANSIT, "0003", PageRequest.of(0, 10)
        );
        assertEquals(1, matches.getTotalElements());
        assertEquals("TRK-ADMIN-0003", matches.getContent().get(0).getTrackingNumber());

        // DELIVERED with "0003" -> 0 matches (TRK-ADMIN-0003 is IN_TRANSIT, not DELIVERED)
        Page<Shipment> mismatch = shipmentRepository.findByStatusAndTrackingNumberContainingIgnoreCase(
                ShipmentStatus.DELIVERED, "0003", PageRequest.of(0, 10)
        );
        assertEquals(0, mismatch.getTotalElements());
    }

    @Test
    @DisplayName("Fleet Query: Returns only active shipments with non-null GPS coordinates, excluding null coordinates and inactive statuses")
    void testFleetLocationQueryExcludingNullCoordinates() {
        List<ShipmentStatus> activeStatuses = List.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.OUT_FOR_DELIVERY);

        List<Shipment> fleet = shipmentRepository.findByStatusInAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull(activeStatuses);

        assertEquals(2, fleet.size(), "Should return exactly 2 active shipments with non-null GPS");
        List<String> trackingNumbers = fleet.stream().map(Shipment::getTrackingNumber).toList();

        // Must include inTransitShipment (TRK-ADMIN-0003) and outForDeliveryShipment (TRK-ADMIN-0004)
        assertTrue(trackingNumbers.contains("TRK-ADMIN-0003"));
        assertTrue(trackingNumbers.contains("TRK-ADMIN-0004"));

        // Must NOT include inTransitWithoutCoords (TRK-ADMIN-0007) because coordinates are null
        assertFalse(trackingNumbers.contains("TRK-ADMIN-0007"), "Shipments with null coordinates must be excluded");

        // Must NOT include deliveredShipment (TRK-ADMIN-0005) because DELIVERED is not an active delivery status
        assertFalse(trackingNumbers.contains("TRK-ADMIN-0005"), "Terminal delivered shipments must be excluded");
    }

    @Test
    @DisplayName("User Repository: Active delivery agent listing and count filter out inactive couriers")
    void testActiveDeliveryAgentListingAndCount() {
        long activeCount = userRepository.countByRoleNameAndActiveTrue(RoleName.ROLE_DELIVERY_AGENT);
        assertEquals(2, activeCount, "Should count only active delivery agents (Alpha and Beta, not Inactive)");

        List<User> activeAgents = userRepository.findByRoleNameAndActiveTrue(RoleName.ROLE_DELIVERY_AGENT);
        assertEquals(2, activeAgents.size());
        assertTrue(activeAgents.stream().allMatch(User::getActive));
        assertTrue(activeAgents.stream().allMatch(u -> RoleName.ROLE_DELIVERY_AGENT.equals(u.getRole().getName())));
    }

    @Test
    @DisplayName("Workload Queries: Agent assignment counts and status breakdown compute accurately")
    void testAgentWorkloadQueries() {
        // Agent Alpha: 4 total (ASSIGNED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED)
        assertEquals(4, shipmentRepository.countByAssignedAgentId(agentAlpha.getId()));
        assertEquals(1, shipmentRepository.countByAssignedAgentIdAndStatus(agentAlpha.getId(), ShipmentStatus.DELIVERED));

        // Active workload statuses: ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY
        List<ShipmentStatus> activeDeliveryStatuses = List.of(
                ShipmentStatus.ASSIGNED,
                ShipmentStatus.PICKED_UP,
                ShipmentStatus.IN_TRANSIT,
                ShipmentStatus.OUT_FOR_DELIVERY
        );
        long alphaActiveDeliveries = shipmentRepository.countByAssignedAgentIdAndStatusIn(agentAlpha.getId(), activeDeliveryStatuses);
        assertEquals(3, alphaActiveDeliveries, "Agent Alpha should have 3 active in-progress deliveries");

        // Agent Beta: 2 total (ATTEMPT_FAILED, IN_TRANSIT)
        assertEquals(2, shipmentRepository.countByAssignedAgentId(agentBeta.getId()));
        assertEquals(0, shipmentRepository.countByAssignedAgentIdAndStatus(agentBeta.getId(), ShipmentStatus.DELIVERED));
        long betaActiveDeliveries = shipmentRepository.countByAssignedAgentIdAndStatusIn(agentBeta.getId(), activeDeliveryStatuses);
        assertEquals(1, betaActiveDeliveries, "Agent Beta should have 1 active delivery (IN_TRANSIT)");
    }
}
