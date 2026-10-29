package com.courier.tracking.controller;

import com.courier.tracking.dto.UpdateShipmentStatusRequest;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentStatusTransitionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

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

    private Shipment assignedShipment;
    private Shipment unassignedShipment;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        adminUser = userRepository.save(new User(
                "transition.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Dispatcher Admin",
                "+1-555-0801",
                adminRole
        ));

        customerUser = userRepository.save(new User(
                "transition.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Parcel Customer",
                "+1-555-0802",
                customerRole
        ));

        assignedAgent = userRepository.save(new User(
                "transition.assigned.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Courier Primary",
                "+1-555-0803",
                agentRole
        ));

        otherAgent = userRepository.save(new User(
                "transition.other.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Courier Secondary",
                "+1-555-0804",
                agentRole
        ));

        adminToken = jwtTokenProvider.generateToken(adminUser);
        customerToken = jwtTokenProvider.generateToken(customerUser);
        assignedAgentToken = jwtTokenProvider.generateToken(assignedAgent);
        otherAgentToken = jwtTokenProvider.generateToken(otherAgent);

        // 1. Shipment in ASSIGNED status with assignedAgent
        assignedShipment = new Shipment(
                "TRK-20261029-STATUS01",
                customerUser,
                "Sender Corp",
                "100 Dispatch Way, Chicago, IL",
                "+1-555-1111",
                "Recipient LLC",
                "200 Target St, Austin, TX",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        assignedShipment.setStatus(ShipmentStatus.ASSIGNED);
        assignedShipment.setAssignedAgent(assignedAgent);
        assignedShipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400 * 3));
        assignedShipment = shipmentRepository.save(assignedShipment);

        // 2. Shipment in CREATED status without assignedAgent
        unassignedShipment = new Shipment(
                "TRK-20261029-STATUS02",
                customerUser,
                "Sender Corp",
                "100 Dispatch Way, Chicago, IL",
                "+1-555-1111",
                "Recipient LLC",
                "200 Target St, Austin, TX",
                "+1-555-2222",
                new BigDecimal("1.500"),
                new BigDecimal("17.50"),
                "USD"
        );
        unassignedShipment.setStatus(ShipmentStatus.CREATED);
        unassignedShipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400 * 3));
        unassignedShipment = shipmentRepository.save(unassignedShipment);
    }

    @Test
    @DisplayName("1. Assigned delivery agent can transition status from ASSIGNED to PICKED_UP with tracking event")
    void testAssignedAgentTransitionsToPickedUp() throws Exception {
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(
                ShipmentStatus.PICKED_UP,
                "Chicago Central Hub",
                "Parcel collected from consignor warehouse"
        );

        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipment status updated successfully"))
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(assignedAgent.getId()));

        // Verify DB shipment status
        Shipment updated = shipmentRepository.findById(assignedShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.PICKED_UP, updated.getStatus());

        // Verify tracking event
        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(assignedShipment.getId());
        assertEquals(1, events.size());
        TrackingEvent event = events.getFirst();
        assertEquals(ShipmentStatus.PICKED_UP, event.getStatus());
        assertEquals("Chicago Central Hub", event.getLocationName());
        assertEquals("Parcel collected from consignor warehouse", event.getRemarks());
        assertNotNull(event.getEventTimestamp());
    }

    @Test
    @DisplayName("2. Administrator can transition status across allowed lifecycle states")
    void testAdminTransitionsStatus() throws Exception {
        // Transition ASSIGNED -> PICKED_UP
        UpdateShipmentStatusRequest req1 = new UpdateShipmentStatusRequest(
                ShipmentStatus.PICKED_UP,
                "Chicago Hub",
                "Package picked up"
        );
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"));

        // Transition PICKED_UP -> IN_TRANSIT
        UpdateShipmentStatusRequest req2 = new UpdateShipmentStatusRequest(
                ShipmentStatus.IN_TRANSIT,
                "Highway Interstate-55",
                "En route to regional sorting facility"
        );
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(assignedShipment.getId());
        assertEquals(2, events.size());
        assertEquals(ShipmentStatus.PICKED_UP, events.get(0).getStatus());
        assertEquals(ShipmentStatus.IN_TRANSIT, events.get(1).getStatus());
    }

    @Test
    @DisplayName("3. Full lifecycle to DELIVERED sets actualDeliveryAt and records all tracking events")
    void testFullLifecycleToDeliveredSetsActualDeliveryAt() throws Exception {
        // ASSIGNED -> PICKED_UP
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP))))
                .andExpect(status().isOk());

        // PICKED_UP -> OUT_FOR_DELIVERY
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(ShipmentStatus.OUT_FOR_DELIVERY))))
                .andExpect(status().isOk());

        // OUT_FOR_DELIVERY -> DELIVERED
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(
                                ShipmentStatus.DELIVERED,
                                "Austin TX Residence",
                                "Package delivered to front porch"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.actualDeliveryAt").isNotEmpty());

        Shipment updated = shipmentRepository.findById(assignedShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, updated.getStatus());
        assertNotNull(updated.getActualDeliveryAt());

        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(assignedShipment.getId());
        assertEquals(3, events.size());
        assertEquals(ShipmentStatus.DELIVERED, events.get(2).getStatus());
    }

    @Test
    @DisplayName("4. Customer role is rejected with 403 Forbidden")
    void testCustomerIsForbiddenFromStatusTransition() throws Exception {
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP);

        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));

        // Status unchanged in database
        Shipment shipment = shipmentRepository.findById(assignedShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.ASSIGNED, shipment.getStatus());
        assertEquals(0, trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(assignedShipment.getId()).size());
    }

    @Test
    @DisplayName("5. Unrelated delivery agent cannot transition another agent's shipment (403 Forbidden)")
    void testUnrelatedAgentIsForbiddenFromTransitioningOtherAgentShipment() throws Exception {
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP);

        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));

        Shipment shipment = shipmentRepository.findById(assignedShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.ASSIGNED, shipment.getStatus());
        assertEquals(0, trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(assignedShipment.getId()).size());
    }

    @Test
    @DisplayName("6. Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedRequestReturns401() throws Exception {
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP);

        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("7. State machine rejects invalid transitions (e.g. CREATED -> DELIVERED, ASSIGNED -> ASSIGNED)")
    void testStateMachineRejectsInvalidTransitions() throws Exception {
        // Arbitrary jump: ASSIGNED -> DELIVERED
        UpdateShipmentStatusRequest invalidJump = new UpdateShipmentStatusRequest(ShipmentStatus.DELIVERED);
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidJump)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid status transition from 'ASSIGNED' to 'DELIVERED'"));

        // Redundant transition: ASSIGNED -> ASSIGNED
        UpdateShipmentStatusRequest sameStatus = new UpdateShipmentStatusRequest(ShipmentStatus.ASSIGNED);
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameStatus)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipment is already in status 'ASSIGNED'"));

        // Backwards transition: ASSIGNED -> CREATED
        UpdateShipmentStatusRequest backwards = new UpdateShipmentStatusRequest(ShipmentStatus.CREATED);
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(backwards)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid status transition from 'ASSIGNED' to 'CREATED'"));
    }

    @Test
    @DisplayName("8. Transitions on terminal statuses (DELIVERED, CANCELLED, RETURNED) are rejected with 400 Bad Request")
    void testTerminalStatusCannotBeTransitioned() throws Exception {
        assignedShipment.setStatus(ShipmentStatus.DELIVERED);
        shipmentRepository.save(assignedShipment);

        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.OUT_FOR_DELIVERY);

        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot transition shipment from terminal status 'DELIVERED'"));
    }

    @Test
    @DisplayName("9. Missing shipment ID returns 404 Not Found")
    void testNonExistentShipmentReturns404() throws Exception {
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.CANCELLED);

        mockMvc.perform(patch("/api/v1/shipments/999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipment not found with id: '999999'"));
    }

    @Test
    @DisplayName("10. Missing status field in request body returns 400 Bad Request with validation errors")
    void testMissingStatusFieldReturns400() throws Exception {
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.status").value("Status is required"));
    }

    @Test
    @DisplayName("11. Malformed or invalid status string returns 400 Bad Request")
    void testMalformedStatusStringReturns400() throws Exception {
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"NON_EXISTENT_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Malformed request payload or invalid parameter value"));
    }

    @Test
    @DisplayName("12. Transition requiring an assigned agent fails when shipment is unassigned (400 Bad Request)")
    void testTransitionRequiringAssignedAgentFailsWhenUnassigned() throws Exception {
        // unassignedShipment is in CREATED with no assignedAgent
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP);

        mockMvc.perform(patch("/api/v1/shipments/" + unassignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        Shipment shipment = shipmentRepository.findById(unassignedShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus());
    }

    @Test
    @DisplayName("13. Delivery agent cannot cancel shipment (403 Forbidden)")
    void testAgentCannotCancelShipment() throws Exception {
        UpdateShipmentStatusRequest request = new UpdateShipmentStatusRequest(ShipmentStatus.CANCELLED);

        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("14. Public tracking endpoint reflects persisted status events in chronological order")
    void testPublicTrackingReflectsPersistedStatusEventsInChronologicalOrder() throws Exception {
        // Step 1: PICKED_UP
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(
                                ShipmentStatus.PICKED_UP,
                                "Chicago Origin Hub",
                                "Scanned and collected"
                        ))))
                .andExpect(status().isOk());

        // Step 2: IN_TRANSIT
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(
                                ShipmentStatus.IN_TRANSIT,
                                "St. Louis Transit Center",
                                "Transferred to linehaul container"
                        ))))
                .andExpect(status().isOk());

        // Step 3: OUT_FOR_DELIVERY
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(
                                ShipmentStatus.OUT_FOR_DELIVERY,
                                "Austin Final Delivery Depot",
                                "Loaded onto local courier van"
                        ))))
                .andExpect(status().isOk());

        // Step 4: DELIVERED
        mockMvc.perform(patch("/api/v1/shipments/" + assignedShipment.getId() + "/status")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(
                                ShipmentStatus.DELIVERED,
                                "Austin TX Front Door",
                                "Left at door by courier"
                        ))))
                .andExpect(status().isOk());

        // Query public tracking endpoint (unauthenticated)
        mockMvc.perform(get("/api/v1/tracking/public/" + assignedShipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value(assignedShipment.getTrackingNumber()))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.actualDeliveryAt").isNotEmpty())
                .andExpect(jsonPath("$.data.milestones", hasSize(4)))
                .andExpect(jsonPath("$.data.milestones[0].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.milestones[0].locationName").value("Chicago Origin Hub"))
                .andExpect(jsonPath("$.data.milestones[0].remarks").value("Scanned and collected"))
                .andExpect(jsonPath("$.data.milestones[1].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.milestones[1].locationName").value("St. Louis Transit Center"))
                .andExpect(jsonPath("$.data.milestones[2].status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.milestones[2].locationName").value("Austin Final Delivery Depot"))
                .andExpect(jsonPath("$.data.milestones[3].status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.milestones[3].locationName").value("Austin TX Front Door"));
    }
}
