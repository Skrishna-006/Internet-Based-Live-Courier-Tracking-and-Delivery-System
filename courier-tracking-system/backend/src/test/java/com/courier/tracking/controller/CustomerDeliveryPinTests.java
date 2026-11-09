package com.courier.tracking.controller;

import com.courier.tracking.dto.VerifyDeliveryOtpRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the customer-only delivery PIN endpoint.
 * Verifies authorization enforcement, PIN generation, PIN isolation from public APIs,
 * and the full college demonstration verbal handover workflow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerDeliveryPinTests {

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

    private User customerUser;
    private User otherCustomerUser;
    private User assignedAgent;

    private String customerToken;
    private String otherCustomerToken;
    private String assignedAgentToken;

    private Shipment outForDeliveryShipment;
    private Shipment createdShipment;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        customerUser = userRepository.save(new User(
                "pin.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Owning Customer",
                "+1-555-1111",
                customerRole
        ));

        otherCustomerUser = userRepository.save(new User(
                "other.pin.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Unrelated Customer",
                "+1-555-2222",
                customerRole
        ));

        assignedAgent = userRepository.save(new User(
                "pin.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Assigned Courier Agent",
                "+1-555-3333",
                agentRole
        ));

        customerToken = jwtTokenProvider.generateToken(customerUser.getId(), customerUser.getEmail(), "ROLE_CUSTOMER");
        otherCustomerToken = jwtTokenProvider.generateToken(otherCustomerUser.getId(), otherCustomerUser.getEmail(), "ROLE_CUSTOMER");
        assignedAgentToken = jwtTokenProvider.generateToken(assignedAgent.getId(), assignedAgent.getEmail(), "ROLE_DELIVERY_AGENT");

        outForDeliveryShipment = new Shipment(
                "TRK-20261109-PIN001",
                customerUser,
                "Origin Campus",
                "100 University Blvd, City",
                "+1-555-1111",
                "Campus Recipient",
                "100 College Ave, Hall A",
                "+1-555-9999",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        outForDeliveryShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        outForDeliveryShipment.setAssignedAgent(assignedAgent);
        outForDeliveryShipment = shipmentRepository.save(outForDeliveryShipment);

        createdShipment = new Shipment(
                "TRK-20261109-PIN002",
                customerUser,
                "Origin Campus 2",
                "100 University Blvd, City",
                "+1-555-1111",
                "Campus Recipient 2",
                "200 University Way",
                "+1-555-8888",
                new BigDecimal("1.000"),
                new BigDecimal("15.00"),
                "USD"
        );
        createdShipment.setStatus(ShipmentStatus.CREATED);
        createdShipment = shipmentRepository.save(createdShipment);
    }

    @Test
    @DisplayName("Owning customer successfully retrieves active delivery PIN for OUT_FOR_DELIVERY shipment")
    void testCustomerRetrievesDeliveryPin_success() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-pin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shipmentId").value(outForDeliveryShipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value(outForDeliveryShipment.getTrackingNumber()))
                .andExpect(jsonPath("$.data.deliveryPin").value(matchesPattern("^\\d{6}$")))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());

        Shipment updated = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertNotNull(updated.getDeliveryOtpHash());
        assertTrue(updated.getDeliveryOtpHash().startsWith("$2a$") || updated.getDeliveryOtpHash().startsWith("$2b$"));
        assertNotNull(updated.getCustomerDeliveryPin());
        assertTrue(passwordEncoder.matches(updated.getCustomerDeliveryPin(), updated.getDeliveryOtpHash()));
    }

    @Test
    @DisplayName("Delivery agent is strictly forbidden from accessing the customer delivery PIN endpoint")
    void testAgentForbiddenFromDeliveryPinEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-pin")
                        .header("Authorization", "Bearer " + assignedAgentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request is rejected with 401 Unauthorized")
    void testUnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-pin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Other customer is forbidden from accessing a shipment they do not own")
    void testOtherCustomerForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-pin")
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer PIN request rejected when shipment is not OUT_FOR_DELIVERY")
    void testDeliveryPinRejected_whenNotOutForDelivery() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + createdShipment.getId() + "/delivery-pin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("OUT_FOR_DELIVERY")));
    }

    @Test
    @DisplayName("Public tracking API never exposes delivery PIN or hash")
    void testPublicTrackingNeverExposesPin() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-pin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tracking/public/" + outForDeliveryShipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trackingNumber").value(outForDeliveryShipment.getTrackingNumber()))
                .andExpect(jsonPath("$.data.deliveryPin").doesNotExist())
                .andExpect(jsonPath("$.data.otp").doesNotExist())
                .andExpect(jsonPath("$.data.deliveryOtpHash").doesNotExist())
                .andExpect(jsonPath("$.data.customerDeliveryPin").doesNotExist());
    }

    @Test
    @DisplayName("Demonstration workflow: customer gets PIN verbally, agent verifies and completes delivery")
    void testVerbalPinSharingAndHandoverVerification() throws Exception {
        MvcResult pinResult = mockMvc.perform(get("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-pin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();

        String verballySharedPin = objectMapper.readTree(pinResult.getResponse().getContentAsString())
                .path("data").path("deliveryPin").asText();
        assertNotNull(verballySharedPin);
        assertEquals(6, verballySharedPin.length());

        VerifyDeliveryOtpRequest verifyRequest = new VerifyDeliveryOtpRequest(
                verballySharedPin,
                "Campus Recipient",
                "Verbal PIN handover verified",
                new BigDecimal("30.2672"),
                new BigDecimal("-97.7431"),
                "100 College Ave"
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        Shipment reloaded = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, reloaded.getStatus());
        assertNull(reloaded.getDeliveryOtpHash());
        assertNull(reloaded.getCustomerDeliveryPin());
    }
}
