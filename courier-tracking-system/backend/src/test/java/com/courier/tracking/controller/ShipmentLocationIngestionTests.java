package com.courier.tracking.controller;

import com.courier.tracking.dto.LocationUpdateRequest;
import com.courier.tracking.dto.LocationUpdateResponse;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentLocationIngestionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User adminUser;
    private User customerUser;
    private User assignedAgent;
    private User otherAgent;

    private String adminToken;
    private String customerToken;
    private String assignedAgentToken;
    private String otherAgentToken;

    private Shipment outForDeliveryShipment;
    private Shipment createdShipment;
    private Shipment deliveredShipment;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        adminUser = userRepository.save(new User(
                "loc.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Dispatcher",
                "+1-555-0801",
                adminRole
        ));

        customerUser = userRepository.save(new User(
                "loc.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer Consignor",
                "+1-555-0802",
                customerRole
        ));

        assignedAgent = userRepository.save(new User(
                "loc.assigned.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Courier Assigned",
                "+1-555-0803",
                agentRole
        ));

        otherAgent = userRepository.save(new User(
                "loc.other.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Courier Secondary",
                "+1-555-0804",
                agentRole
        ));

        adminToken = jwtTokenProvider.generateToken(adminUser);
        customerToken = jwtTokenProvider.generateToken(customerUser);
        assignedAgentToken = jwtTokenProvider.generateToken(assignedAgent);
        otherAgentToken = jwtTokenProvider.generateToken(otherAgent);

        // 1. Shipment in OUT_FOR_DELIVERY status assigned to assignedAgent
        outForDeliveryShipment = new Shipment(
                "TRK-20261029-LOC001",
                customerUser,
                "Origin Sender",
                "100 Dispatch Way, Austin, TX",
                "+1-555-1111",
                "Target Recipient",
                "200 Destination Blvd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        outForDeliveryShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        outForDeliveryShipment.setAssignedAgent(assignedAgent);
        outForDeliveryShipment = shipmentRepository.save(outForDeliveryShipment);

        // 2. Shipment in CREATED status
        createdShipment = new Shipment(
                "TRK-20261029-LOC002",
                customerUser,
                "Origin Sender 2",
                "100 Dispatch Way, Austin, TX",
                "+1-555-1111",
                "Target Recipient 2",
                "300 Elm St, Dallas, TX",
                "+1-555-3333",
                new BigDecimal("1.500"),
                new BigDecimal("17.50"),
                "USD"
        );
        createdShipment.setStatus(ShipmentStatus.CREATED);
        createdShipment = shipmentRepository.save(createdShipment);

        // 3. Shipment in DELIVERED status
        deliveredShipment = new Shipment(
                "TRK-20261029-LOC003",
                customerUser,
                "Origin Sender 3",
                "100 Dispatch Way, Austin, TX",
                "+1-555-1111",
                "Target Recipient 3",
                "400 Oak Ave, Houston, TX",
                "+1-555-4444",
                new BigDecimal("3.000"),
                new BigDecimal("25.00"),
                "USD"
        );
        deliveredShipment.setStatus(ShipmentStatus.DELIVERED);
        deliveredShipment.setAssignedAgent(assignedAgent);
        deliveredShipment = shipmentRepository.save(deliveredShipment);
    }

    // =========================================================================
    // 1. SUCCESSFUL INGESTION
    // =========================================================================

    @Test
    @DisplayName("Location Ingestion: Assigned agent successfully submits valid GPS telemetry")
    void testUpdateLocation_byAssignedAgent_success() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("12.5"),
                new BigDecimal("42.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Telemetry coordinates accepted successfully"))
                .andExpect(jsonPath("$.data.shipmentId").value(outForDeliveryShipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value(outForDeliveryShipment.getTrackingNumber()))
                .andExpect(jsonPath("$.data.latitude").value(30.2671530))
                .andExpect(jsonPath("$.data.longitude").value(-97.7430608))
                .andExpect(jsonPath("$.data.recordedAt").isNotEmpty());

        // Verify in database: Shipment latest location fields updated
        Shipment updated = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("30.2671530").compareTo(updated.getCurrentLatitude()));
        assertEquals(0, new BigDecimal("-97.7430608").compareTo(updated.getCurrentLongitude()));
        assertNotNull(updated.getLastLocationUpdatedAt());
    }

    // =========================================================================
    // 2. AUTHENTICATION & ROLE AUTHORIZATION
    // =========================================================================

    @Test
    @DisplayName("Location Ingestion: Unauthenticated request receives 401 Unauthorized")
    void testUpdateLocation_unauthenticated_unauthorized() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Location Ingestion: Customer role receives 403 Forbidden")
    void testUpdateLocation_byCustomer_forbidden() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Location Ingestion: Admin role without agent assignment receives 403 Forbidden")
    void testUpdateLocation_byAdmin_forbidden() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Location Ingestion: Unassigned delivery agent receives 403 Forbidden")
    void testUpdateLocation_byOtherAgent_forbidden() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. SHIPMENT STATUS RESTRICTIONS & DELIVERED PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Location Ingestion: Fails with 400 when shipment is in CREATED status")
    void testUpdateLocation_statusNotOutForDelivery_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + createdShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden()); // Agent is also not assigned to createdShipment
    }

    @Test
    @DisplayName("Location Ingestion: Fails with 400 when shipment is in DELIVERED status")
    void testUpdateLocation_deliveredShipment_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + deliveredShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("delivered")));
    }

    // =========================================================================
    // 4. COORDINATE, ACCURACY & SPEED VALIDATION
    // =========================================================================

    @Test
    @DisplayName("Location Ingestion: Invalid latitude (> 90.0) fails validation with 400")
    void testUpdateLocation_invalidLatitude_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("95.0000000"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Location Ingestion: Invalid longitude (> 180.0) fails validation with 400")
    void testUpdateLocation_invalidLongitude_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("185.0000000"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Location Ingestion: Inaccurate GPS (> 30.0m) fails validation with 400")
    void testUpdateLocation_inaccurateLocation_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("35.0"), // exceeds 30m
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Location Ingestion: Unreasonable payload speed (> 140 km/h) fails validation with 400")
    void testUpdateLocation_excessiveSpeed_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                new BigDecimal("160.0"), // exceeds 140 km/h
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 5. TIMESTAMP FRESHNESS (STALE / FUTURE)
    // =========================================================================

    @Test
    @DisplayName("Location Ingestion: Stale timestamp (> 60s in past) fails with 400")
    void testUpdateLocation_staleTimestamp_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now().minus(90, ChronoUnit.SECONDS) // 90s ago
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("±60 seconds")));
    }

    @Test
    @DisplayName("Location Ingestion: Future timestamp (> 60s in future) fails with 400")
    void testUpdateLocation_futureTimestamp_badRequest() throws Exception {
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now().plus(90, ChronoUnit.SECONDS) // 90s in future
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("±60 seconds")));
    }

    // =========================================================================
    // 6. RATE LIMITING & IMPLAUSIBLE MOVEMENT
    // =========================================================================

    @Test
    @DisplayName("Location Ingestion: Minimum 5-second interval rejects rapid consecutive update with 400")
    void testUpdateLocation_rateLimitUnder5Seconds_badRequest() throws Exception {
        // First update succeeds
        LocationUpdateRequest request1 = new LocationUpdateRequest(
                new BigDecimal("30.2671530"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // Immediate second update fails (< 5 seconds)
        LocationUpdateRequest request2 = new LocationUpdateRequest(
                new BigDecimal("30.2672000"),
                new BigDecimal("-97.7431000"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("5 seconds")));
    }

    @Test
    @DisplayName("Location Ingestion: Implausible teleportation movement (> 140 km/h calculated) is rejected with 400")
    void testUpdateLocation_implausibleMovement_badRequest() throws Exception {
        // Set previous location 10 seconds ago in Austin, TX
        outForDeliveryShipment.setCurrentLatitude(new BigDecimal("30.2671530"));
        outForDeliveryShipment.setCurrentLongitude(new BigDecimal("-97.7430608"));
        outForDeliveryShipment.setLastLocationUpdatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        shipmentRepository.save(outForDeliveryShipment);

        // Submit new location 10km away (requires 3600 km/h speed in 10s)
        LocationUpdateRequest teleportRequest = new LocationUpdateRequest(
                new BigDecimal("30.3500000"), // ~10km north
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(teleportRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Implausible movement")));
    }

    @Test
    @DisplayName("Location Ingestion: Plausible movement after 10 seconds is accepted")
    void testUpdateLocation_plausibleMovement_success() throws Exception {
        // Set previous location 10 seconds ago
        outForDeliveryShipment.setCurrentLatitude(new BigDecimal("30.2671530"));
        outForDeliveryShipment.setCurrentLongitude(new BigDecimal("-97.7430608"));
        outForDeliveryShipment.setLastLocationUpdatedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        shipmentRepository.save(outForDeliveryShipment);

        // Move 100 meters in 10 seconds (speed = 36 km/h, well below 140 km/h)
        LocationUpdateRequest plausibleRequest = new LocationUpdateRequest(
                new BigDecimal("30.2680000"),
                new BigDecimal("-97.7430608"),
                new BigDecimal("10.0"),
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/location")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(plausibleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
