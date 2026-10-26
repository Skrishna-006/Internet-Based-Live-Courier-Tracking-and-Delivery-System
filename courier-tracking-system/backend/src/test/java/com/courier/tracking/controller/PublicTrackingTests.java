package com.courier.tracking.controller;

import com.courier.tracking.entity.*;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import com.courier.tracking.repository.UserRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicTrackingTests {

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

    private Shipment testShipment;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();

        User customer = userRepository.save(new User(
                "public.tracker.owner@example.com",
                passwordEncoder.encode("Password123!"),
                "Private Consignor",
                "+1-555-0999",
                customerRole
        ));

        testShipment = new Shipment(
                "TRK-20261026-PUBLIC01",
                customer,
                "Secret Sender Corp",
                "999 Confidential Origin Way, Chicago, IL",
                "+1-555-1111",
                "Secret Recipient Inc",
                "777 Private Destination Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("3.500"),
                new BigDecimal("27.50"),
                "USD"
        );
        testShipment.setStatus(ShipmentStatus.CREATED);
        testShipment.setRecipientLatitude(new BigDecimal("30.2672000"));
        testShipment.setRecipientLongitude(new BigDecimal("-97.7431000"));
        testShipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(86400 * 2));
        testShipment = shipmentRepository.save(testShipment);

        // Seed 2 waypoint milestones
        TrackingEvent event1 = new TrackingEvent(
                testShipment,
                ShipmentStatus.CREATED,
                new BigDecimal("41.8781000"),
                new BigDecimal("-87.6298000"),
                "Chicago Origin Distribution Hub",
                "Consignment manifest created and barcode printed"
        );
        trackingEventRepository.save(event1);

        TrackingEvent event2 = new TrackingEvent(
                testShipment,
                ShipmentStatus.PICKED_UP,
                new BigDecimal("41.9000000"),
                new BigDecimal("-87.6500000"),
                "Chicago Regional Sorting Facility",
                "Parcel collected by regional carrier and sorted"
        );
        trackingEventRepository.save(event2);
    }

    @Test
    @DisplayName("1. Public tracking succeeds without any Authorization header")
    void testPublicTrackingSucceedsWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261026-PUBLIC01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tracking summary retrieved successfully"))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261026-PUBLIC01"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    @DisplayName("2. Valid tracking number returns the correct summary and chronologically ordered milestones")
    void testValidTrackingNumberReturnsCorrectSummaryAndMilestones() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261026-PUBLIC01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261026-PUBLIC01"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.estimatedDeliveryAt").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.milestones", hasSize(2)))
                .andExpect(jsonPath("$.data.milestones[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.milestones[0].locationName").value("Chicago Origin Distribution Hub"))
                .andExpect(jsonPath("$.data.milestones[0].remarks").value("Consignment manifest created and barcode printed"))
                .andExpect(jsonPath("$.data.milestones[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.milestones[1].locationName").value("Chicago Regional Sorting Facility"))
                .andExpect(jsonPath("$.data.milestones[1].remarks").value("Parcel collected by regional carrier and sorted"));
    }

    @Test
    @DisplayName("3. Case-insensitive tracking number lookup works correctly")
    void testCaseInsensitiveTrackingNumberLookup() throws Exception {
        // Lowercase query parameter matches uppercase stored tracking number
        mockMvc.perform(get("/api/v1/tracking/public/trk-20261026-public01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261026-PUBLIC01"));
    }

    @Test
    @DisplayName("4. Unknown tracking number returns 404 Not Found")
    void testUnknownTrackingNumberReturns404NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-NONEXISTENT-9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipment not found with trackingNumber: 'TRK-NONEXISTENT-9999'"));
    }

    @Test
    @DisplayName("5. Malformed or excessively long tracking numbers return 400 Bad Request")
    void testMalformedOrExcessivelyLongTrackingNumberReturns400() throws Exception {
        // Special character injection
        mockMvc.perform(get("/api/v1/tracking/public/TRK@INVALID$NUM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tracking number format is invalid: must contain only alphanumeric characters, dashes, and underscores"));

        // Whitespace in parameter
        mockMvc.perform(get("/api/v1/tracking/public/TRK INVALID NUMBER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Excessively long tracking number (> 64 characters)
        String tooLong = "TRK-" + "A".repeat(70);
        mockMvc.perform(get("/api/v1/tracking/public/" + tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tracking number exceeds maximum length of 64 characters"));

        // Blank/spaces only
        mockMvc.perform(get("/api/v1/tracking/public/   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tracking number must not be blank"));
    }

    @Test
    @DisplayName("6. Public response strictly excludes customer email, phone, addresses, coordinates and PII")
    void testPublicResponseExcludesAllPrivateFieldsAndPII() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tracking/public/TRK-20261026-PUBLIC01"))
                .andExpect(status().isOk())
                // Ensure internal DB IDs are not leaked
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.assignedAgentId").doesNotExist())
                // Ensure customer credentials and emails are not leaked
                .andExpect(jsonPath("$.data.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.data.customer").doesNotExist())
                // Ensure phone numbers are not leaked
                .andExpect(jsonPath("$.data.senderPhone").doesNotExist())
                .andExpect(jsonPath("$.data.recipientPhone").doesNotExist())
                // Ensure physical addresses are not leaked
                .andExpect(jsonPath("$.data.senderAddress").doesNotExist())
                .andExpect(jsonPath("$.data.recipientAddress").doesNotExist())
                // Ensure coordinates are not leaked
                .andExpect(jsonPath("$.data.recipientLatitude").doesNotExist())
                .andExpect(jsonPath("$.data.recipientLongitude").doesNotExist())
                .andExpect(jsonPath("$.data.milestones[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.data.milestones[0].longitude").doesNotExist())
                // Ensure price and financial details are not leaked
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.currency").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Deep content verification: ensure no sensitive raw strings leaked anywhere in the response JSON
        assertFalse(responseBody.contains("public.tracker.owner@example.com"), "Customer email must not appear in response");
        assertFalse(responseBody.contains("+1-555-1111"), "Sender phone must not appear in response");
        assertFalse(responseBody.contains("+1-555-2222"), "Recipient phone must not appear in response");
        assertFalse(responseBody.contains("999 Confidential Origin Way"), "Sender address must not appear in response");
        assertFalse(responseBody.contains("777 Private Destination Rd"), "Recipient address must not appear in response");
        assertFalse(responseBody.contains("Secret Sender Corp"), "Sender name must not appear in response");
        assertFalse(responseBody.contains("Secret Recipient Inc"), "Recipient name must not appear in response");
        assertFalse(responseBody.contains("30.2672"), "Recipient latitude must not appear in response");
        assertFalse(responseBody.contains("-97.7431"), "Recipient longitude must not appear in response");
        assertFalse(responseBody.contains("41.8781"), "Milestone waypoint latitude must not appear in response");
        assertFalse(responseBody.contains("-87.6298"), "Milestone waypoint longitude must not appear in response");
    }

    @Test
    @DisplayName("7. Existing customer shipment endpoints remain strictly protected against unauthenticated calls")
    void testExistingCustomerShipmentEndpointsRemainProtected() throws Exception {
        // GET /api/v1/shipments
        mockMvc.perform(get("/api/v1/shipments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // GET /api/v1/shipments/{id}
        mockMvc.perform(get("/api/v1/shipments/" + testShipment.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // POST /api/v1/shipments
        mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
