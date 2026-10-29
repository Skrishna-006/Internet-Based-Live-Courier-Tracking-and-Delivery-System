package com.courier.tracking.controller;

import com.courier.tracking.dto.DeliveryConfirmationResponse;
import com.courier.tracking.dto.DeliveryOtpResponse;
import com.courier.tracking.dto.VerifyDeliveryOtpRequest;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.DeliveryProofRepository;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentDeliveryOtpTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private DeliveryProofRepository deliveryProofRepository;

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
    private User otherCustomerUser;
    private User assignedAgent;
    private User otherAgent;

    private String adminToken;
    private String customerToken;
    private String otherCustomerToken;
    private String assignedAgentToken;
    private String otherAgentToken;

    private Shipment outForDeliveryShipment;
    private Shipment createdShipment;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        adminUser = userRepository.save(new User(
                "otp.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Dispatcher",
                "+1-555-0701",
                adminRole
        ));

        customerUser = userRepository.save(new User(
                "otp.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Shipment Owner",
                "+1-555-0702",
                customerRole
        ));

        otherCustomerUser = userRepository.save(new User(
                "otp.othercustomer@example.com",
                passwordEncoder.encode("Password123!"),
                "Unrelated Customer",
                "+1-555-0703",
                customerRole
        ));

        assignedAgent = userRepository.save(new User(
                "otp.assigned.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Assigned Courier",
                "+1-555-0704",
                agentRole
        ));

        otherAgent = userRepository.save(new User(
                "otp.other.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Other Courier",
                "+1-555-0705",
                agentRole
        ));

        adminToken = jwtTokenProvider.generateToken(adminUser);
        customerToken = jwtTokenProvider.generateToken(customerUser);
        otherCustomerToken = jwtTokenProvider.generateToken(otherCustomerUser);
        assignedAgentToken = jwtTokenProvider.generateToken(assignedAgent);
        otherAgentToken = jwtTokenProvider.generateToken(otherAgent);

        // 1. Shipment ready for delivery (OUT_FOR_DELIVERY)
        outForDeliveryShipment = new Shipment(
                "TRK-20261029-OTP0001",
                customerUser,
                "Origin Sender",
                "100 Dispatch Blvd, Austin, TX",
                "+1-555-1111",
                "Jane Recipient",
                "200 Target Ave, Austin, TX",
                "+1-555-2222",
                new BigDecimal("3.500"),
                new BigDecimal("27.50"),
                "USD"
        );
        outForDeliveryShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        outForDeliveryShipment.setAssignedAgent(assignedAgent);
        outForDeliveryShipment = shipmentRepository.save(outForDeliveryShipment);

        // 2. Shipment still in CREATED status
        createdShipment = new Shipment(
                "TRK-20261029-OTP0002",
                customerUser,
                "Origin Sender 2",
                "100 Dispatch Blvd, Austin, TX",
                "+1-555-1111",
                "Bob Recipient",
                "300 Elm St, Dallas, TX",
                "+1-555-3333",
                new BigDecimal("1.500"),
                new BigDecimal("17.50"),
                "USD"
        );
        createdShipment.setStatus(ShipmentStatus.CREATED);
        createdShipment = shipmentRepository.save(createdShipment);
    }

    // =========================================================================
    // OTP GENERATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Generate OTP: Assigned agent successfully generates OTP; BCrypt hash stored in DB, raw OTP returned")
    void testGenerateOtp_byAssignedAgent_success() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery OTP generated successfully"))
                .andExpect(jsonPath("$.data.shipmentId").value(outForDeliveryShipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value(outForDeliveryShipment.getTrackingNumber()))
                .andExpect(jsonPath("$.data.otp").value(matchesPattern("^\\d{6}$")))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn();

        String rawOtp = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("otp").asText();
        assertNotNull(rawOtp);
        assertEquals(6, rawOtp.length());

        // Verify in DB: raw OTP is NEVER stored; hash starts with BCrypt prefix
        Shipment updated = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertNotNull(updated.getDeliveryOtpHash());
        assertTrue(updated.getDeliveryOtpHash().startsWith("$2a$") || updated.getDeliveryOtpHash().startsWith("$2b$"));
        assertNotEquals(rawOtp, updated.getDeliveryOtpHash());
        assertTrue(passwordEncoder.matches(rawOtp, updated.getDeliveryOtpHash()));
        assertNotNull(updated.getDeliveryOtpExpiresAt());
        assertTrue(updated.getDeliveryOtpExpiresAt().isAfter(Instant.now()));
        assertEquals(0, updated.getDeliveryOtpAttempts());
    }

    @Test
    @DisplayName("Generate OTP: Owning customer successfully requests delivery OTP")
    void testGenerateOtp_byCustomer_success() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.otp").value(matchesPattern("^\\d{6}$")));
    }

    @Test
    @DisplayName("Generate OTP: Administrator successfully requests delivery OTP")
    void testGenerateOtp_byAdmin_success() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.otp").value(matchesPattern("^\\d{6}$")));
    }

    @Test
    @DisplayName("Generate OTP: Unrelated customer receives 403 Forbidden")
    void testGenerateOtp_byOtherCustomer_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Generate OTP: Unassigned delivery agent receives 403 Forbidden")
    void testGenerateOtp_byOtherAgent_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Generate OTP: Unauthenticated request receives 401 Unauthorized")
    void testGenerateOtp_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Generate OTP: Fails with 400 when shipment is not in OUT_FOR_DELIVERY status")
    void testGenerateOtp_shipmentNotInStatus_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + createdShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("OUT_FOR_DELIVERY")));
    }

    @Test
    @DisplayName("Generate OTP: Rate limit cooldown rejects rapid second request within 60s")
    void testGenerateOtp_rateLimitCooldown_badRequest() throws Exception {
        // First generation succeeds
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Immediate second generation fails
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("60 seconds")));
    }

    // =========================================================================
    // OTP VERIFICATION AND DELIVERY CONFIRMATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Confirm Delivery: Assigned agent successfully confirms delivery with valid OTP")
    void testConfirmDelivery_byAssignedAgent_success() throws Exception {
        // 1. Generate OTP
        MvcResult genResult = mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String rawOtp = objectMapper.readTree(genResult.getResponse().getContentAsString()).path("data").path("otp").asText();

        // 2. Verify OTP
        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest(
                rawOtp,
                "Jane Recipient Handover",
                "Delivered at recipient front door",
                new BigDecimal("30.2672"),
                new BigDecimal("-97.7431"),
                "123 Delivery St, Austin, TX"
        );

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Delivery confirmed successfully"))
                .andExpect(jsonPath("$.data.shipmentId").value(outForDeliveryShipment.getId()))
                .andExpect(jsonPath("$.data.trackingNumber").value(outForDeliveryShipment.getTrackingNumber()))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.proofType").value("OTP"))
                .andExpect(jsonPath("$.data.recipientName").value("Jane Recipient Handover"))
                .andExpect(jsonPath("$.data.deliveryProofId").isNumber())
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());

        // 3. Database verification
        Shipment delivered = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, delivered.getStatus());
        assertNotNull(delivered.getActualDeliveryAt());
        assertNull(delivered.getDeliveryOtpHash(), "OTP hash must be consumed/cleared");
        assertNull(delivered.getDeliveryOtpExpiresAt(), "OTP expiration must be cleared");
        assertEquals(0, delivered.getDeliveryOtpAttempts());

        // Verify DeliveryProof record
        assertTrue(deliveryProofRepository.existsByShipmentId(delivered.getId()));
        DeliveryProof proof = deliveryProofRepository.findByShipmentId(delivered.getId()).orElseThrow();
        assertEquals(ProofType.OTP, proof.getProofType());
        assertEquals("OTP_VERIFIED", proof.getProofValue());
        assertEquals("Jane Recipient Handover", proof.getRecipientName());
        assertEquals("Delivered at recipient front door", proof.getVerificationNotes());
        assertEquals(assignedAgent.getId(), proof.getCapturedByAgent().getId());

        // Verify TrackingEvent record
        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(delivered.getId());
        assertFalse(events.isEmpty());
        TrackingEvent latestEvent = events.get(events.size() - 1);
        assertEquals(ShipmentStatus.DELIVERED, latestEvent.getStatus());
        assertEquals("123 Delivery St, Austin, TX", latestEvent.getLocationName());
    }

    @Test
    @DisplayName("Confirm Delivery: Admin can also verify OTP and confirm delivery")
    void testConfirmDelivery_byAdmin_success() throws Exception {
        MvcResult genResult = mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String rawOtp = objectMapper.readTree(genResult.getResponse().getContentAsString()).path("data").path("otp").asText();

        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest(rawOtp);

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    @DisplayName("Confirm Delivery: Customer role is strictly forbidden from verifying delivery (403)")
    void testConfirmDelivery_byCustomer_forbidden() throws Exception {
        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest("123456");

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Confirm Delivery: Unassigned courier is forbidden from verifying delivery (403)")
    void testConfirmDelivery_byOtherAgent_forbidden() throws Exception {
        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest("123456");

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Confirm Delivery: Unauthenticated request returns 401 Unauthorized")
    void testConfirmDelivery_unauthenticated_unauthorized() throws Exception {
        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest("123456");

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Confirm Delivery: Invalid OTP increments failed attempt count and returns 400")
    void testConfirmDelivery_invalidOtp_incrementsAttempts() throws Exception {
        // Generate valid OTP
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Submit wrong OTP
        VerifyDeliveryOtpRequest wrongRequest = new VerifyDeliveryOtpRequest("000000");

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Invalid delivery OTP")));

        Shipment shipment = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertEquals(1, shipment.getDeliveryOtpAttempts());
        assertNotNull(shipment.getDeliveryOtpHash(), "Hash must remain active after 1st failure");
    }

    @Test
    @DisplayName("Confirm Delivery: Exceeding 3 failed attempts invalidates OTP")
    void testConfirmDelivery_maxAttempts_invalidatesOtp() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        VerifyDeliveryOtpRequest wrongRequest = new VerifyDeliveryOtpRequest("000000");

        // Attempt 1 -> 400
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isBadRequest());

        // Attempt 2 -> 400
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isBadRequest());

        // Attempt 3 -> 400, hash invalidated
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isBadRequest());

        Shipment shipment = shipmentRepository.findById(outForDeliveryShipment.getId()).orElseThrow();
        assertEquals(3, shipment.getDeliveryOtpAttempts());
        assertNull(shipment.getDeliveryOtpHash(), "Hash must be invalidated after 3 failed attempts");

        // Attempt 4 -> 400
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Confirm Delivery: Expired OTP is rejected with 400")
    void testConfirmDelivery_expiredOtp_rejected() throws Exception {
        // Set up pre-expired OTP on shipment
        outForDeliveryShipment.setDeliveryOtpHash(passwordEncoder.encode("123456"));
        outForDeliveryShipment.setDeliveryOtpExpiresAt(Instant.now().minusSeconds(60)); // expired 1 minute ago
        outForDeliveryShipment.setDeliveryOtpAttempts(0);
        shipmentRepository.save(outForDeliveryShipment);

        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest("123456");

        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("expired")));
    }

    @Test
    @DisplayName("Confirm Delivery: Cannot reuse consumed OTP on already DELIVERED shipment")
    void testConfirmDelivery_replayReusedOtp_rejected() throws Exception {
        // 1. Generate OTP
        MvcResult genResult = mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String rawOtp = objectMapper.readTree(genResult.getResponse().getContentAsString()).path("data").path("otp").asText();
        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest(rawOtp);

        // 2. First verify succeeds
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 3. Second verify with same OTP fails
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Confirm Delivery: Non-existent shipment ID returns 404 Not Found")
    void testConfirmDelivery_nonExistentShipment_notFound() throws Exception {
        VerifyDeliveryOtpRequest request = new VerifyDeliveryOtpRequest("123456");

        mockMvc.perform(post("/api/v1/shipments/999999/delivery/verify-otp")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Confirm Delivery: Invalid input format (non-6 digits, blank) fails validation (400)")
    void testConfirmDelivery_invalidFormat_badRequest() throws Exception {
        // Less than 6 digits
        VerifyDeliveryOtpRequest shortRequest = new VerifyDeliveryOtpRequest("12345");
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortRequest)))
                .andExpect(status().isBadRequest());

        // Non-digit characters
        VerifyDeliveryOtpRequest alphaRequest = new VerifyDeliveryOtpRequest("abcdef");
        mockMvc.perform(post("/api/v1/shipments/" + outForDeliveryShipment.getId() + "/delivery/verify-otp")
                        .header("Authorization", "Bearer " + assignedAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alphaRequest)))
                .andExpect(status().isBadRequest());
    }
}
