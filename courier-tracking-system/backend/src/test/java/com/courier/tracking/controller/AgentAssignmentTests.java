package com.courier.tracking.controller;

import com.courier.tracking.dto.UpdateShipmentStatusRequest;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.User;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgentAssignmentTests {

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

    private User agentUser1;
    private User agentUser2;
    private User customerUser;
    private User adminUser;
    private User disabledAgent;

    private String agent1Token;
    private String agent2Token;
    private String customerToken;
    private String adminToken;
    private String disabledAgentToken;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        customerUser = userRepository.save(new User(
                "agenttest.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Sender Customer",
                "+1-555-2001",
                customerRole
        ));

        agentUser1 = userRepository.save(new User(
                "agent1.assignments@example.com",
                passwordEncoder.encode("Password123!"),
                "Primary Driver",
                "+1-555-2002",
                agentRole
        ));

        agentUser2 = userRepository.save(new User(
                "agent2.assignments@example.com",
                passwordEncoder.encode("Password123!"),
                "Secondary Driver",
                "+1-555-2003",
                agentRole
        ));

        adminUser = userRepository.save(new User(
                "agenttest.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Operations",
                "+1-555-2004",
                adminRole
        ));

        disabledAgent = new User(
                "disabled.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Inactive Driver",
                "+1-555-2005",
                agentRole
        );
        disabledAgent.setActive(false);
        disabledAgent = userRepository.save(disabledAgent);

        agent1Token = jwtTokenProvider.generateToken(agentUser1);
        agent2Token = jwtTokenProvider.generateToken(agentUser2);
        customerToken = jwtTokenProvider.generateToken(customerUser);
        adminToken = jwtTokenProvider.generateToken(adminUser);
        disabledAgentToken = jwtTokenProvider.generateToken(disabledAgent);
    }

    private Shipment createAssignedShipment(User owner, User agent, String suffix, ShipmentStatus status) {
        Shipment shipment = new Shipment(
                "TRK-AGENT-" + suffix,
                owner,
                "Origin " + suffix,
                "100 Origin St, City A",
                "+1-555-1111",
                "Recipient " + suffix,
                "200 Destination Ave, City B",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        shipment.setStatus(status);
        shipment.setAssignedAgent(agent);
        shipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400));
        return shipmentRepository.save(shipment);
    }

    @Test
    @DisplayName("1. Delivery agent successfully retrieves only their assigned shipments")
    void testAgentRetrievesOwnAssignments() throws Exception {
        createAssignedShipment(customerUser, agentUser1, "S1", ShipmentStatus.ASSIGNED);
        createAssignedShipment(customerUser, agentUser1, "S2", ShipmentStatus.PICKED_UP);
        createAssignedShipment(customerUser, agentUser2, "S3", ShipmentStatus.ASSIGNED);

        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agent1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].assignedAgentId").value(agentUser1.getId()))
                .andExpect(jsonPath("$.data.content[1].assignedAgentId").value(agentUser1.getId()));
    }

    @Test
    @DisplayName("2. Data isolation: Delivery agent cannot view another agent's assigned shipments")
    void testAgentDataIsolation() throws Exception {
        createAssignedShipment(customerUser, agentUser1, "A1", ShipmentStatus.ASSIGNED);
        createAssignedShipment(customerUser, agentUser2, "A2", ShipmentStatus.IN_TRANSIT);

        // Agent 2 sees only their 1 shipment
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agent2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value("TRK-AGENT-A2"))
                .andExpect(jsonPath("$.data.content[0].assignedAgentId").value(agentUser2.getId()));
    }

    @Test
    @DisplayName("3. Customer receives 403 Forbidden when attempting to access agent assignments")
    void testCustomerForbiddenFromAgentAssignments() throws Exception {
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("4. Admin receives 403 Forbidden when calling /me/assignments (cannot impersonate agent)")
    void testAdminForbiddenFromAgentMeEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("5. Deactivated agent receives 401 Unauthorized")
    void testDeactivatedAgentCannotAccessAssignments() throws Exception {
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + disabledAgentToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("6. Status filtering on assignments returns only matching shipments")
    void testStatusFiltering() throws Exception {
        createAssignedShipment(customerUser, agentUser1, "FILT1", ShipmentStatus.ASSIGNED);
        createAssignedShipment(customerUser, agentUser1, "FILT2", ShipmentStatus.OUT_FOR_DELIVERY);

        // Filter by OUT_FOR_DELIVERY
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agent1Token)
                        .param("status", "OUT_FOR_DELIVERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value("TRK-AGENT-FILT2"))
                .andExpect(jsonPath("$.data.content[0].status").value("OUT_FOR_DELIVERY"));

        // Filter by DELIVERED (none)
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agent1Token)
                        .param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("7. Pagination on assignments correctly slices pages")
    void testAssignmentPagination() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createAssignedShipment(customerUser, agentUser1, "PAGE-" + i, ShipmentStatus.ASSIGNED);
        }

        // Page 0, size 2
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agent1Token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        // Page 2, size 2 (last page)
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agent1Token)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("8. Summary endpoint returns accurate queue counts across entire assignment history")
    void testAgentSummaryMetrics() throws Exception {
        createAssignedShipment(customerUser, agentUser1, "SUM1", ShipmentStatus.ASSIGNED);
        createAssignedShipment(customerUser, agentUser1, "SUM2", ShipmentStatus.PICKED_UP);
        createAssignedShipment(customerUser, agentUser1, "SUM3", ShipmentStatus.IN_TRANSIT);
        createAssignedShipment(customerUser, agentUser1, "SUM4", ShipmentStatus.OUT_FOR_DELIVERY);
        createAssignedShipment(customerUser, agentUser1, "SUM5", ShipmentStatus.DELIVERED);

        mockMvc.perform(get("/api/v1/agents/me/assignments/summary")
                        .header("Authorization", "Bearer " + agent1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAssigned").value(5))
                .andExpect(jsonPath("$.data.awaitingPickup").value(1))
                .andExpect(jsonPath("$.data.inTransit").value(2)) // PICKED_UP + IN_TRANSIT
                .andExpect(jsonPath("$.data.outForDelivery").value(1))
                .andExpect(jsonPath("$.data.completed").value(1));
    }

    @Test
    @DisplayName("9. Single assignment retrieval by ID works for assigned agent and rejects unassigned agent")
    void testGetAssignmentById() throws Exception {
        Shipment s = createAssignedShipment(customerUser, agentUser1, "ID1", ShipmentStatus.ASSIGNED);

        // Assigned agent succeeds
        mockMvc.perform(get("/api/v1/agents/me/assignments/{id}", s.getId())
                        .header("Authorization", "Bearer " + agent1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-AGENT-ID1"));

        // Unassigned agent receives 403
        mockMvc.perform(get("/api/v1/agents/me/assignments/{id}", s.getId())
                        .header("Authorization", "Bearer " + agent2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("10. Delivery agent can transition shipment through permitted lifecycle statuses")
    void testAgentPermittedStatusTransitions() throws Exception {
        Shipment s = createAssignedShipment(customerUser, agentUser1, "TRANS1", ShipmentStatus.ASSIGNED);

        // 1. ASSIGNED -> PICKED_UP
        UpdateShipmentStatusRequest req1 = new UpdateShipmentStatusRequest(
                ShipmentStatus.PICKED_UP,
                "Origin Warehouse A",
                "Package collected from consignor"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", s.getId())
                        .header("Authorization", "Bearer " + agent1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"));

        // 2. PICKED_UP -> IN_TRANSIT
        UpdateShipmentStatusRequest req2 = new UpdateShipmentStatusRequest(
                ShipmentStatus.IN_TRANSIT,
                "City Logistics Hub",
                "Moving to local delivery dispatch center"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", s.getId())
                        .header("Authorization", "Bearer " + agent1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        // 3. IN_TRANSIT -> OUT_FOR_DELIVERY
        UpdateShipmentStatusRequest req3 = new UpdateShipmentStatusRequest(
                ShipmentStatus.OUT_FOR_DELIVERY,
                "Final Delivery Van 12",
                "Driver en route to recipient"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", s.getId())
                        .header("Authorization", "Bearer " + agent1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));

        // 4. OUT_FOR_DELIVERY -> ATTEMPT_FAILED
        UpdateShipmentStatusRequest req4 = new UpdateShipmentStatusRequest(
                ShipmentStatus.ATTEMPT_FAILED,
                "Recipient Residence Gate",
                "Recipient not available at address"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", s.getId())
                        .header("Authorization", "Bearer " + agent1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ATTEMPT_FAILED"));
    }

    @Test
    @DisplayName("11. Delivery agent cannot update shipment assigned to another agent")
    void testAgentCannotUpdateAnotherAgentsShipment() throws Exception {
        Shipment s = createAssignedShipment(customerUser, agentUser1, "NOTMY1", ShipmentStatus.ASSIGNED);

        UpdateShipmentStatusRequest req = new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP);
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", s.getId())
                        .header("Authorization", "Bearer " + agent2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("12. Invalid status transitions by delivery agent are rejected with 400 Bad Request")
    void testAgentInvalidStatusTransition() throws Exception {
        Shipment s = createAssignedShipment(customerUser, agentUser1, "INVALIDTRANS", ShipmentStatus.ASSIGNED);

        // Attempt ASSIGNED -> OUT_FOR_DELIVERY (skipping PICKED_UP)
        UpdateShipmentStatusRequest req = new UpdateShipmentStatusRequest(ShipmentStatus.OUT_FOR_DELIVERY);
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", s.getId())
                        .header("Authorization", "Bearer " + agent1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid status transition from 'ASSIGNED' to 'OUT_FOR_DELIVERY'"));
    }
}
