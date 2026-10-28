package com.courier.tracking.controller;

import com.courier.tracking.dto.AssignAgentRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentAssignmentTests {

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
    private User agentUser;
    private User secondAgent;
    private User inactiveAgent;

    private String adminToken;
    private String customerToken;
    private String agentToken;

    private Shipment testShipment;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        adminUser = userRepository.save(new User(
                "assignment.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Dispatcher Admin",
                "+1-555-0701",
                adminRole
        ));

        customerUser = userRepository.save(new User(
                "assignment.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Parcel Sender",
                "+1-555-0702",
                customerRole
        ));

        agentUser = userRepository.save(new User(
                "agent.primary@example.com",
                passwordEncoder.encode("Password123!"),
                "Courier Bob",
                "+1-555-0703",
                agentRole
        ));

        secondAgent = userRepository.save(new User(
                "agent.secondary@example.com",
                passwordEncoder.encode("Password123!"),
                "Courier Charlie",
                "+1-555-0704",
                agentRole
        ));

        inactiveAgent = new User(
                "agent.inactive@example.com",
                passwordEncoder.encode("Password123!"),
                "Inactive Courier",
                "+1-555-0705",
                agentRole
        );
        inactiveAgent.setActive(false);
        inactiveAgent = userRepository.save(inactiveAgent);

        adminToken = jwtTokenProvider.generateToken(adminUser);
        customerToken = jwtTokenProvider.generateToken(customerUser);
        agentToken = jwtTokenProvider.generateToken(agentUser);

        testShipment = new Shipment(
                "TRK-20261028-ASSIGN01",
                customerUser,
                "Origin Sender",
                "100 Dispatch Way, Chicago, IL",
                "+1-555-1111",
                "Destination Recipient",
                "200 Target St, Austin, TX",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        testShipment.setStatus(ShipmentStatus.CREATED);
        testShipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400 * 3));
        testShipment = shipmentRepository.save(testShipment);
    }

    @Test
    @DisplayName("1. Administrator assigns eligible delivery agent to CREATED shipment (transitions to ASSIGNED)")
    void testAdminAssignsEligibleDeliveryAgentSuccessfully() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(agentUser.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery agent assigned successfully"))
                .andExpect(jsonPath("$.data.id").value(testShipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261028-ASSIGN01"))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agentUser.getId()))
                .andExpect(jsonPath("$.data.assignedAgentName").value("Courier Bob"));

        Shipment reloaded = shipmentRepository.findById(testShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.ASSIGNED, reloaded.getStatus());
        assertNotNull(reloaded.getAssignedAgent());
        assertEquals(agentUser.getId(), reloaded.getAssignedAgent().getId());
    }

    @Test
    @DisplayName("2. Administrator reassigns an already ASSIGNED shipment to a different delivery agent")
    void testAdminReassignsAlreadyAssignedShipment() throws Exception {
        // Pre-assign to agentUser
        testShipment.setAssignedAgent(agentUser);
        testShipment.setStatus(ShipmentStatus.ASSIGNED);
        shipmentRepository.save(testShipment);

        // Reassign to secondAgent
        AssignAgentRequest request = new AssignAgentRequest(secondAgent.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(secondAgent.getId()))
                .andExpect(jsonPath("$.data.assignedAgentName").value("Courier Charlie"));

        Shipment reloaded = shipmentRepository.findById(testShipment.getId()).orElseThrow();
        assertEquals(secondAgent.getId(), reloaded.getAssignedAgent().getId());
    }

    @Test
    @DisplayName("3. Non-existent shipment returns 404 Not Found")
    void testAssignmentFailsWhenShipmentNotFound() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(agentUser.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", 999999L)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipment not found with id: '999999'"));
    }

    @Test
    @DisplayName("4. Non-existent delivery agent returns 404 Not Found")
    void testAssignmentFailsWhenAgentNotFound() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(999999L);

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Delivery agent not found with id: '999999'"));
    }

    @Test
    @DisplayName("5. Assigning a user without ROLE_DELIVERY_AGENT is rejected with 400 Bad Request")
    void testAssignmentFailsWhenTargetUserIsNotDeliveryAgent() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(customerUser.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with id " + customerUser.getId() + " does not have the delivery agent role"));
    }

    @Test
    @DisplayName("6. Assigning an inactive delivery agent is rejected with 400 Bad Request")
    void testAssignmentFailsWhenAgentIsInactive() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(inactiveAgent.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Delivery agent with id " + inactiveAgent.getId() + " is inactive"));
    }

    @Test
    @DisplayName("7. Assignment on shipment in terminal status (DELIVERED) is rejected with 400 Bad Request")
    void testAssignmentFailsWhenShipmentIsInTerminalStatus() throws Exception {
        testShipment.setStatus(ShipmentStatus.DELIVERED);
        shipmentRepository.save(testShipment);

        AssignAgentRequest request = new AssignAgentRequest(agentUser.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Cannot assign delivery agent to shipment 'TRK-20261028-ASSIGN01' with terminal status 'DELIVERED'"));
    }

    @Test
    @DisplayName("8. Unauthenticated requests are rejected with 401 Unauthorized")
    void testUnauthenticatedRequestReceives401() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(agentUser.getId());

        // Without token
        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // With invalid token
        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer invalid.tampered.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("9. Customer account receives 403 Forbidden when attempting assignment")
    void testCustomerCannotAssignDeliveryAgent() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(agentUser.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("10. Delivery agent receives 403 Forbidden when attempting assignment")
    void testDeliveryAgentCannotAssignDeliveryAgent() throws Exception {
        AssignAgentRequest request = new AssignAgentRequest(agentUser.getId());

        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("11. Invalid payload (missing or negative agentId) is rejected with 400 Bad Request")
    void testInvalidPayloadRejectedWith400() throws Exception {
        // Missing agentId
        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.agentId").exists());

        // Negative agentId
        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", testShipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.agentId").exists());
    }
}
