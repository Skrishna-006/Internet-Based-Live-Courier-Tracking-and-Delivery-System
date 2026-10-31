package com.courier.tracking.controller;

import com.courier.tracking.entity.*;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User adminUser;
    private User customerUser;
    private User agentAlpha;
    private User agentBeta;
    private User inactiveAgent;

    private String adminToken;
    private String customerToken;
    private String agentToken;

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
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        adminUser = userRepository.save(new User(
                "ctrl.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Director",
                "+1-555-0100",
                adminRole
        ));

        customerUser = userRepository.save(new User(
                "ctrl.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer Consignor",
                "+1-555-0200",
                customerRole
        ));

        agentAlpha = userRepository.save(new User(
                "ctrl.agent.alpha@example.com",
                passwordEncoder.encode("Password123!"),
                "Driver Alpha",
                "+1-555-0301",
                agentRole
        ));

        agentBeta = userRepository.save(new User(
                "ctrl.agent.beta@example.com",
                passwordEncoder.encode("Password123!"),
                "Driver Beta",
                "+1-555-0302",
                agentRole
        ));

        inactiveAgent = new User(
                "ctrl.agent.inactive@example.com",
                passwordEncoder.encode("Password123!"),
                "Driver Inactive",
                "+1-555-0303",
                agentRole
        );
        inactiveAgent.setActive(false);
        inactiveAgent = userRepository.save(inactiveAgent);

        adminToken = jwtTokenProvider.generateToken(adminUser);
        customerToken = jwtTokenProvider.generateToken(customerUser);
        agentToken = jwtTokenProvider.generateToken(agentAlpha);

        // 1. Unassigned CREATED
        unassignedCreated = createShipment("TRK-CTRL-0001", customerUser, null, ShipmentStatus.CREATED, null, null);

        // 2. Unassigned CREATED with distinct EXPRESS prefix for search testing
        unassignedExpress = createShipment("TRK-EXPRESS-7777", customerUser, null, ShipmentStatus.CREATED, null, null);

        // 3. Assigned to Agent Alpha
        assignedShipment = createShipment("TRK-CTRL-0002", customerUser, agentAlpha, ShipmentStatus.ASSIGNED, null, null);

        // 4. In Transit with live GPS coordinates (Agent Alpha)
        inTransitShipment = createShipment("TRK-CTRL-0003", customerUser, agentAlpha, ShipmentStatus.IN_TRANSIT,
                new BigDecimal("30.2672000"), new BigDecimal("-97.7431000"));

        // 5. Out For Delivery with live GPS coordinates (Agent Alpha)
        outForDeliveryShipment = createShipment("TRK-CTRL-0004", customerUser, agentAlpha, ShipmentStatus.OUT_FOR_DELIVERY,
                new BigDecimal("30.2700000"), new BigDecimal("-97.7400000"));

        // 6. Delivered (Agent Alpha)
        deliveredShipment = createShipment("TRK-CTRL-0005", customerUser, agentAlpha, ShipmentStatus.DELIVERED,
                new BigDecimal("30.2800000"), new BigDecimal("-97.7300000"));

        // 7. Attempt Failed (Agent Beta)
        failedAttemptShipment = createShipment("TRK-CTRL-0006", customerUser, agentBeta, ShipmentStatus.ATTEMPT_FAILED, null, null);

        // 8. In Transit without coordinates (Agent Beta)
        inTransitWithoutCoords = createShipment("TRK-CTRL-0007", customerUser, agentBeta, ShipmentStatus.IN_TRANSIT, null, null);

        // Add tracking event milestones to inTransitShipment
        TrackingEvent event1 = new TrackingEvent(
                inTransitShipment,
                ShipmentStatus.CREATED,
                new BigDecimal("30.2500000"),
                new BigDecimal("-97.7500000"),
                "Austin Central Depot",
                "Consignment registered"
        );
        trackingEventRepository.save(event1);

        TrackingEvent event2 = new TrackingEvent(
                inTransitShipment,
                ShipmentStatus.IN_TRANSIT,
                new BigDecimal("30.2672000"),
                new BigDecimal("-97.7431000"),
                "Highway 35 Interchange",
                "Consignment in transit between distribution hubs"
        );
        trackingEventRepository.save(event2);
    }

    private Shipment createShipment(String trk, User cust, User agent, ShipmentStatus status, BigDecimal lat, BigDecimal lon) {
        Shipment s = new Shipment(
                trk, cust,
                "Origin Sender", "100 Congress Ave, Austin, TX", "+1-555-1111",
                "Target Recipient", "500 E 4th St, Austin, TX", "+1-555-2222",
                new BigDecimal("3.200"), new BigDecimal("32.00"), "USD"
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

    // --- OVERVIEW TESTS ---

    @Test
    @DisplayName("Admin can retrieve overview metrics successfully")
    void adminCanRetrieveOverviewSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Admin dashboard overview retrieved successfully"))
                .andExpect(jsonPath("$.data.totalShipments").value(greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.data.unassignedShipments").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.assignedShipments").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.inTransitShipments").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.outForDeliveryShipments").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.deliveredShipments").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.failedDeliveryShipments").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.activeAgents").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("Customer is forbidden from retrieving admin overview")
    void customerCannotRetrieveOverviewForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Delivery agent is forbidden from retrieving admin overview")
    void deliveryAgentCannotRetrieveOverviewForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + agentToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unauthenticated request to overview returns 401 Unauthorized")
    void unauthenticatedRequestOverviewUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- SHIPMENT LISTING & FILTERING TESTS ---

    @Test
    @DisplayName("Admin can list shipments with pagination")
    void adminCanListShipmentsWithPagination() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.data.totalPages").value(greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("Admin can filter shipments by status")
    void adminCanFilterShipmentsByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "IN_TRANSIT")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].status", everyItem(is("IN_TRANSIT"))));
    }

    @Test
    @DisplayName("Admin can search shipments by tracking number substring")
    void adminCanSearchShipmentsByTrackingNumberSubstring() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "EXPRESS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value("TRK-EXPRESS-7777"));
    }

    @Test
    @DisplayName("Admin can combine status and tracking number search")
    void adminCanCombineStatusAndTrackingSearch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "OUT_FOR_DELIVERY")
                        .param("search", "CTRL-0004")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value("TRK-CTRL-0004"))
                .andExpect(jsonPath("$.data.content[0].status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    @DisplayName("Admin can filter by unassigned only")
    void adminCanFilterByUnassignedOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("unassignedOnly", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].assignedAgentId", everyItem(nullValue())));
    }

    @Test
    @DisplayName("Admin can filter by unassigned only combined with status")
    void adminCanFilterByUnassignedOnlyWithStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("unassignedOnly", "true")
                        .param("status", "CREATED")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].status", everyItem(is("CREATED"))))
                .andExpect(jsonPath("$.data.content[*].assignedAgentId", everyItem(nullValue())));
    }

    @Test
    @DisplayName("Non-admin user cannot list shipments via admin endpoint")
    void nonAdminCannotListShipmentsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Invalid pagination inputs are clamped and handled safely")
    void invalidPaginationInputsHandledSafely() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "-5")
                        .param("size", "500")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    // --- SHIPMENT DETAIL & EVENT TESTS ---

    @Test
    @DisplayName("Admin can retrieve full shipment detail by ID")
    void adminCanRetrieveShipmentDetailById() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}", outForDeliveryShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(outForDeliveryShipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-CTRL-0004"))
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agentAlpha.getId()))
                .andExpect(jsonPath("$.data.assignedAgentName").value(agentAlpha.getFullName()))
                .andExpect(jsonPath("$.data.senderName").value("Origin Sender"))
                .andExpect(jsonPath("$.data.recipientName").value("Target Recipient"))
                .andExpect(jsonPath("$.data.price").value(32.00))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    @DisplayName("Non-admin user cannot retrieve shipment detail via admin endpoint")
    void nonAdminCannotRetrieveShipmentDetailForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}", outForDeliveryShipment.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Missing shipment ID returns standard 404 response")
    void missingShipmentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Admin can retrieve chronological tracking events for a shipment")
    void adminCanRetrieveShipmentTrackingEvents() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}/events", inTransitShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[0].locationName").value("Austin Central Depot"))
                .andExpect(jsonPath("$.data[1].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data[1].locationName").value("Highway 35 Interchange"));
    }

    @Test
    @DisplayName("Missing shipment ID for events returns standard 404 response")
    void missingShipmentEventsReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}/events", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Non-admin user cannot retrieve shipment events via admin endpoint")
    void nonAdminCannotRetrieveShipmentEventsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}/events", inTransitShipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- AGENTS DIRECTORY TESTS ---

    @Test
    @DisplayName("Admin can retrieve agent directory with workload metrics")
    void adminCanRetrieveAgentSummaries() throws Exception {
        mockMvc.perform(get("/api/v1/admin/agents")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))))
                // Check Agent Alpha: 4 total assigned (ASSIGNED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED)
                // 3 active deliveries (ASSIGNED, IN_TRANSIT, OUT_FOR_DELIVERY), 1 delivered
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.alpha@example.com')].totalAssignedShipments").value(contains(4)))
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.alpha@example.com')].activeDeliveriesCount").value(contains(3)))
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.alpha@example.com')].deliveredShipmentsCount").value(contains(1)))
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.alpha@example.com')].active").value(contains(true)))
                // Check Agent Beta: 2 total assigned (ATTEMPT_FAILED, IN_TRANSIT)
                // 1 active delivery (IN_TRANSIT), 0 delivered
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.beta@example.com')].totalAssignedShipments").value(contains(2)))
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.beta@example.com')].activeDeliveriesCount").value(contains(1)))
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.beta@example.com')].deliveredShipmentsCount").value(contains(0)))
                // Check Inactive Agent: 0 total assigned, active = false
                .andExpect(jsonPath("$.data[?(@.email == 'ctrl.agent.inactive@example.com')].active").value(contains(false)));
    }

    @Test
    @DisplayName("Delivery agent cannot access agent directory")
    void nonAdminCannotRetrieveAgentSummariesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/agents")
                        .header("Authorization", "Bearer " + agentToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- FLEET TELEMETRY TESTS ---

    @Test
    @DisplayName("Admin can retrieve active fleet locations with coordinates")
    void adminCanRetrieveFleetLocations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fleet")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // Only inTransitShipment and outForDeliveryShipment are IN_TRANSIT / OUT_FOR_DELIVERY with non-null coords
                // (deliveredShipment is DELIVERED; inTransitWithoutCoords has null coords)
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].trackingNumber", containsInAnyOrder("TRK-CTRL-0003", "TRK-CTRL-0004")))
                .andExpect(jsonPath("$.data[*].latitude", everyItem(notNullValue())))
                .andExpect(jsonPath("$.data[*].longitude", everyItem(notNullValue())))
                .andExpect(jsonPath("$.data[*].agentName", everyItem(is("Driver Alpha"))))
                .andExpect(jsonPath("$.data[*].recordedAt", everyItem(notNullValue())));
    }

    @Test
    @DisplayName("Customer cannot access fleet telemetry")
    void nonAdminCannotRetrieveFleetForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fleet")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    // --- TASK 3 VERIFICATION & HARDENING TESTS ---

    @Test
    @DisplayName("Admin can filter by unassigned only combined with tracking number search")
    void adminCanFilterByUnassignedOnlyWithSearch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("unassignedOnly", "true")
                        .param("search", "EXPRESS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value("TRK-EXPRESS-7777"))
                .andExpect(jsonPath("$.data.content[0].assignedAgentId").value(nullValue()));
    }

    @Test
    @DisplayName("Admin can filter by unassigned only combined with status and tracking number search")
    void adminCanFilterByUnassignedOnlyWithStatusAndSearch() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("unassignedOnly", "true")
                        .param("status", "CREATED")
                        .param("search", "EXPRESS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value("TRK-EXPRESS-7777"))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.content[0].assignedAgentId").value(nullValue()));
    }

    @Test
    @DisplayName("Invalid status parameter returns controlled 400 Bad Request response")
    void invalidStatusParameterReturnsControlledBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "NON_EXISTENT_STATUS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid parameter value for 'status'"));
    }

    @Test
    @DisplayName("Invalid sort property returns controlled 400 Bad Request response")
    void invalidSortPropertyReturnsControlledBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("sort", "unrecognizedProperty,asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Invalid sort property")));
    }

    @Test
    @DisplayName("Invalid sort direction returns controlled 400 Bad Request response")
    void invalidSortDirectionReturnsControlledBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("sort", "createdAt,invalidDirection")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Shipment tracking events are returned in strict chronological order")
    void shipmentTrackingEventsAreInStrictChronologicalOrder() throws Exception {
        TrackingEvent event3 = new TrackingEvent(
                inTransitShipment,
                ShipmentStatus.OUT_FOR_DELIVERY,
                new BigDecimal("30.2700000"),
                new BigDecimal("-97.7400000"),
                "Austin Local Delivery Van",
                "Out for final delivery"
        );
        trackingEventRepository.save(event3);

        mockMvc.perform(get("/api/v1/admin/shipments/{id}/events", inTransitShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[1].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data[2].status").value("OUT_FOR_DELIVERY"));
    }

    @Test
    @DisplayName("Large pagination page size is safely clamped to maximum boundary")
    void largePaginationBoundariesAreClamped() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "5000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("Unauthenticated request to list shipments returns 401 Unauthorized")
    void unauthenticatedRequestShipmentsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unauthenticated request to agents directory returns 401 Unauthorized")
    void unauthenticatedRequestAgentsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/agents")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Unauthenticated request to fleet telemetry returns 401 Unauthorized")
    void unauthenticatedRequestFleetUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fleet")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Customer is forbidden from retrieving agent directory")
    void customerCannotRetrieveAgentSummariesForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/agents")
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Delivery agent is forbidden from retrieving shipment detail via admin endpoint")
    void agentCannotRetrieveShipmentDetailForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}", outForDeliveryShipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Customer is forbidden from retrieving shipment tracking events via admin endpoint")
    void customerCannotRetrieveShipmentEventsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shipments/{id}/events", inTransitShipment.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Fleet telemetry strictly excludes customer PII and authentication data")
    void fleetTelemetryExcludesCustomerPii() throws Exception {
        mockMvc.perform(get("/api/v1/admin/fleet")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].senderName").doesNotExist())
                .andExpect(jsonPath("$.data[*].recipientName").doesNotExist())
                .andExpect(jsonPath("$.data[*].senderPhone").doesNotExist())
                .andExpect(jsonPath("$.data[*].recipientPhone").doesNotExist())
                .andExpect(jsonPath("$.data[*].password").doesNotExist())
                .andExpect(jsonPath("$.data[*].token").doesNotExist());
    }
}
