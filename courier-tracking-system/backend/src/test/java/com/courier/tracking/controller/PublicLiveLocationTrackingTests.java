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

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicLiveLocationTrackingTests {

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

    private User testCustomer;
    private User testAgent;
    private Shipment activeShipment;
    private Shipment noLocationShipment;
    private Shipment inTransitShipment;
    private Shipment deliveredShipment;
    private Shipment cancelledShipment;
    private Shipment staleLocationShipment;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        testCustomer = userRepository.save(new User(
                "poll.customer.secret@example.com",
                passwordEncoder.encode("Password123!"),
                "Confidential Customer",
                "+1-555-8888",
                customerRole
        ));

        testAgent = userRepository.save(new User(
                "poll.agent.driver@example.com",
                passwordEncoder.encode("Password123!"),
                "Driver Bond",
                "+1-555-7777",
                agentRole
        ));

        // 1. Active shipment in OUT_FOR_DELIVERY with fresh live coordinates
        activeShipment = new Shipment(
                "TRK-20261030-LIVE01",
                testCustomer,
                "Sender HQ",
                "100 Secret Sender Way, Austin, TX",
                "+1-555-1111",
                "Recipient Client",
                "200 Confidential Delivery Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("25.00"),
                "USD"
        );
        activeShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        activeShipment.setAssignedAgent(testAgent);
        activeShipment.setCurrentLatitude(new BigDecimal("30.2672000"));
        activeShipment.setCurrentLongitude(new BigDecimal("-97.7431000"));
        activeShipment.setLastLocationUpdatedAt(Instant.now());
        activeShipment = shipmentRepository.save(activeShipment);

        // Seed milestone for active shipment
        TrackingEvent event = new TrackingEvent(
                activeShipment,
                ShipmentStatus.OUT_FOR_DELIVERY,
                new BigDecimal("30.2672000"),
                new BigDecimal("-97.7431000"),
                "Local Delivery Van",
                "Out for final delivery"
        );
        trackingEventRepository.save(event);

        // 2. Shipment in OUT_FOR_DELIVERY but without any recorded location
        noLocationShipment = new Shipment(
                "TRK-20261030-NOLOC02",
                testCustomer,
                "Sender HQ",
                "100 Secret Sender Way, Austin, TX",
                "+1-555-1111",
                "Recipient Client",
                "200 Confidential Delivery Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("1.500"),
                new BigDecimal("15.00"),
                "USD"
        );
        noLocationShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        noLocationShipment.setAssignedAgent(testAgent);
        noLocationShipment.setCurrentLatitude(null);
        noLocationShipment.setCurrentLongitude(null);
        noLocationShipment.setLastLocationUpdatedAt(null);
        noLocationShipment = shipmentRepository.save(noLocationShipment);

        // 3. Shipment NOT in OUT_FOR_DELIVERY (IN_TRANSIT)
        inTransitShipment = new Shipment(
                "TRK-20261030-TRANS03",
                testCustomer,
                "Sender HQ",
                "100 Secret Sender Way, Austin, TX",
                "+1-555-1111",
                "Recipient Client",
                "200 Confidential Delivery Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("4.000"),
                new BigDecimal("40.00"),
                "USD"
        );
        inTransitShipment.setStatus(ShipmentStatus.IN_TRANSIT);
        inTransitShipment.setCurrentLatitude(new BigDecimal("32.7767000"));
        inTransitShipment.setCurrentLongitude(new BigDecimal("-96.7970000"));
        inTransitShipment.setLastLocationUpdatedAt(Instant.now());
        inTransitShipment = shipmentRepository.save(inTransitShipment);

        // 4. Shipment DELIVERED (has historical coordinates in DB from transit)
        deliveredShipment = new Shipment(
                "TRK-20261030-DELIV04",
                testCustomer,
                "Sender HQ",
                "100 Secret Sender Way, Austin, TX",
                "+1-555-1111",
                "Recipient Client",
                "200 Confidential Delivery Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("3.000"),
                new BigDecimal("30.00"),
                "USD"
        );
        deliveredShipment.setStatus(ShipmentStatus.DELIVERED);
        deliveredShipment.setCurrentLatitude(new BigDecimal("30.2672000"));
        deliveredShipment.setCurrentLongitude(new BigDecimal("-97.7431000"));
        deliveredShipment.setLastLocationUpdatedAt(Instant.now().minusSeconds(1800));
        deliveredShipment = shipmentRepository.save(deliveredShipment);

        // 5. Shipment CANCELLED
        cancelledShipment = new Shipment(
                "TRK-20261030-CANCL05",
                testCustomer,
                "Sender HQ",
                "100 Secret Sender Way, Austin, TX",
                "+1-555-1111",
                "Recipient Client",
                "200 Confidential Delivery Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("1.000"),
                new BigDecimal("10.00"),
                "USD"
        );
        cancelledShipment.setStatus(ShipmentStatus.CANCELLED);
        cancelledShipment = shipmentRepository.save(cancelledShipment);

        // 6. Shipment OUT_FOR_DELIVERY with STALE coordinates (> 120 seconds old)
        staleLocationShipment = new Shipment(
                "TRK-20261030-STALE06",
                testCustomer,
                "Sender HQ",
                "100 Secret Sender Way, Austin, TX",
                "+1-555-1111",
                "Recipient Client",
                "200 Confidential Delivery Rd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("2.000"),
                new BigDecimal("20.00"),
                "USD"
        );
        staleLocationShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        staleLocationShipment.setAssignedAgent(testAgent);
        staleLocationShipment.setCurrentLatitude(new BigDecimal("30.2672000"));
        staleLocationShipment.setCurrentLongitude(new BigDecimal("-97.7431000"));
        // Recorded 10 minutes ago
        staleLocationShipment.setLastLocationUpdatedAt(Instant.now().minusSeconds(600));
        staleLocationShipment = shipmentRepository.save(staleLocationShipment);
    }

    @Test
    @DisplayName("1. Valid active shipment location retrieval succeeds and returns sanitized coordinates")
    void testValidActiveShipmentLocationRetrieval() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-LIVE01/location")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Live location retrieved successfully"))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-LIVE01"))
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.latitude").value(30.2672000))
                .andExpect(jsonPath("$.data.longitude").value(-97.7431000))
                .andExpect(jsonPath("$.data.recordedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.fresh").value(true));
    }

    @Test
    @DisplayName("2. Case-insensitive tracking number lookup retrieves active location")
    void testCaseInsensitiveTrackingNumberLookup() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/trk-20261030-live01/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-LIVE01"))
                .andExpect(jsonPath("$.data.fresh").value(true));
    }

    @Test
    @DisplayName("3. Unknown tracking number returns 404 Not Found")
    void testUnknownTrackingNumberReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-UNKNOWN-40404/location"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Shipment not found with trackingNumber: 'TRK-UNKNOWN-40404'"));
    }

    @Test
    @DisplayName("4. Malformed, blank, or excessively long tracking numbers return 400 Bad Request")
    void testInvalidTrackingNumberReturns400() throws Exception {
        // Special character injection
        mockMvc.perform(get("/api/v1/tracking/public/TRK@INVALID$NUM/location"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tracking number format is invalid: must contain only alphanumeric characters, dashes, and underscores"));

        // Excessively long tracking number (> 64 characters)
        String tooLong = "TRK-" + "Z".repeat(70);
        mockMvc.perform(get("/api/v1/tracking/public/" + tooLong + "/location"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tracking number exceeds maximum length of 64 characters"));

        // Whitespace only
        mockMvc.perform(get("/api/v1/tracking/public/   /location"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tracking number must not be blank"));
    }

    @Test
    @DisplayName("5. Shipment without location update returns status with null coordinates and fresh=false")
    void testShipmentWithoutLocationReturnsNoCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-NOLOC02/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-NOLOC02"))
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.recordedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.fresh").value(false));
    }

    @Test
    @DisplayName("6. Shipment not in OUT_FOR_DELIVERY status suppresses coordinates")
    void testShipmentNotOutForDeliverySuppressesCoordinates() throws Exception {
        // IN_TRANSIT shipment (has historical coordinates in DB)
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-TRANS03/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-TRANS03"))
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.recordedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.fresh").value(false));
    }

    @Test
    @DisplayName("7. Delivered and cancelled shipments do not expose coordinates")
    void testDeliveredAndCancelledShipmentsDoNotExposeCoordinates() throws Exception {
        // DELIVERED shipment
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-DELIV04/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-DELIV04"))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.recordedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.fresh").value(false));

        // CANCELLED shipment
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-CANCL05/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-CANCL05"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.recordedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.fresh").value(false));
    }

    @Test
    @DisplayName("8. Stale location coordinates (> 120s old) are suppressed and not exposed as live positions")
    void testStaleLocationSuppressedFromLivePositions() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-STALE06/location"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-STALE06"))
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.latitude").value(nullValue()))
                .andExpect(jsonPath("$.data.longitude").value(nullValue()))
                .andExpect(jsonPath("$.data.recordedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.fresh").value(false));
    }

    @Test
    @DisplayName("9. Live location polling strictly excludes customer, agent, pricing, and internal database keys")
    void testLiveLocationResponseExcludesPrivateCustomerAndAgentData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-LIVE01/location"))
                .andExpect(status().isOk())
                // Ensure internal DB IDs are not leaked
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.shipmentId").doesNotExist())
                .andExpect(jsonPath("$.data.customerId").doesNotExist())
                .andExpect(jsonPath("$.data.assignedAgentId").doesNotExist())
                // Ensure customer credentials, contacts, and names are not leaked
                .andExpect(jsonPath("$.data.customerEmail").doesNotExist())
                .andExpect(jsonPath("$.data.customer").doesNotExist())
                .andExpect(jsonPath("$.data.senderName").doesNotExist())
                .andExpect(jsonPath("$.data.senderPhone").doesNotExist())
                .andExpect(jsonPath("$.data.senderAddress").doesNotExist())
                .andExpect(jsonPath("$.data.recipientName").doesNotExist())
                .andExpect(jsonPath("$.data.recipientPhone").doesNotExist())
                .andExpect(jsonPath("$.data.recipientAddress").doesNotExist())
                // Ensure agent identities and contacts are not leaked
                .andExpect(jsonPath("$.data.agent").doesNotExist())
                .andExpect(jsonPath("$.data.assignedAgent").doesNotExist())
                .andExpect(jsonPath("$.data.driverName").doesNotExist())
                .andExpect(jsonPath("$.data.agentPhone").doesNotExist())
                // Ensure financial and OTP details are not leaked
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.currency").doesNotExist())
                .andExpect(jsonPath("$.data.deliveryOtpHash").doesNotExist())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Deep content verification: ensure sensitive raw strings are nowhere in response
        assertFalse(responseBody.contains("poll.customer.secret@example.com"), "Customer email must not appear in response");
        assertFalse(responseBody.contains("poll.agent.driver@example.com"), "Agent email must not appear in response");
        assertFalse(responseBody.contains("+1-555-8888"), "Customer phone must not appear in response");
        assertFalse(responseBody.contains("+1-555-7777"), "Agent phone must not appear in response");
        assertFalse(responseBody.contains("Driver Bond"), "Agent name must not appear in response");
        assertFalse(responseBody.contains("Confidential Customer"), "Customer name must not appear in response");
        assertFalse(responseBody.contains("100 Secret Sender Way"), "Sender address must not appear in response");
        assertFalse(responseBody.contains("200 Confidential Delivery Rd"), "Recipient address must not appear in response");
    }

    @Test
    @DisplayName("10. Existing public tracking summary endpoint remains completely unchanged")
    void testExistingPublicTrackingEndpointRemainsUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/TRK-20261030-LIVE01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tracking summary retrieved successfully"))
                .andExpect(jsonPath("$.data.trackingNumber").value("TRK-20261030-LIVE01"))
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.milestones").isArray());
    }
}
