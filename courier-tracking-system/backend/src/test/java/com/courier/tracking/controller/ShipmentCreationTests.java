package com.courier.tracking.controller;

import com.courier.tracking.dto.CreateShipmentRequest;
import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentCreationTests {

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
    private User otherCustomer;
    private User agentUser;
    private User adminUser;
    private String customerToken;
    private String agentToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();

        customerUser = userRepository.save(new User(
                "shipment.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Shipment Consignor",
                "+1-555-0401",
                customerRole
        ));

        otherCustomer = userRepository.save(new User(
                "other.customer@example.com",
                passwordEncoder.encode("Password123!"),
                "Other Consignor",
                "+1-555-0402",
                customerRole
        ));

        agentUser = userRepository.save(new User(
                "shipment.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Delivery Agent Bob",
                "+1-555-0403",
                agentRole
        ));

        adminUser = userRepository.save(new User(
                "shipment.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Alice",
                "+1-555-0404",
                adminRole
        ));

        customerToken = jwtTokenProvider.generateToken(customerUser);
        agentToken = jwtTokenProvider.generateToken(agentUser);
        adminToken = jwtTokenProvider.generateToken(adminUser);
    }

    private CreateShipmentRequest createValidPayload() {
        return new CreateShipmentRequest(
                "John Doe",
                "123 Warehouse Way, Chicago, IL 60601",
                "+1-555-1111",
                "Alice Smith",
                "789 Residential Rd, Austin, TX 78701",
                "+1-555-2222",
                new BigDecimal("30.2672000"),
                new BigDecimal("-97.7431000"),
                new BigDecimal("2.500"),
                new BigDecimal("35.00")
        );
    }

    @Test
    @DisplayName("1. Authenticated customer can create a valid shipment")
    void testAuthenticatedCustomerCreatesShipmentSuccessfully() throws Exception {
        CreateShipmentRequest request = createValidPayload();

        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Shipment created successfully"))
                .andExpect(jsonPath("$.data.senderName").value("John Doe"))
                .andExpect(jsonPath("$.data.recipientName").value("Alice Smith"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.weightKg").value(2.5))
                .andExpect(jsonPath("$.data.price").value(22.50))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.customerId").value(customerUser.getId()))
                .andExpect(jsonPath("$.data.customerEmail").value("shipment.customer@example.com"));
    }

    @Test
    @DisplayName("2. Returned response includes generated shipment identifier and tracking reference")
    void testReturnedResponseIncludesGeneratedIdentifiers() throws Exception {
        CreateShipmentRequest request = createValidPayload();

        MvcResult result = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = root.get("data");

        assertNotNull(data.get("id"));
        assertTrue(data.get("id").asLong() > 0);

        String trackingNumber = data.get("trackingNumber").asText();
        assertNotNull(trackingNumber);
        assertTrue(trackingNumber.startsWith("TRK-"), "Tracking number must have TRK- prefix");

        // Verify entity in repository
        Optional<Shipment> opt = shipmentRepository.findByTrackingNumber(trackingNumber);
        assertTrue(opt.isPresent());
        assertEquals(data.get("id").asLong(), opt.get().getId());
        assertEquals("USD", opt.get().getCurrency());
        assertEquals("USD", data.get("currency").asText());
    }

    @Test
    @DisplayName("3. Stored shipment belongs to the authenticated customer in the database")
    void testStoredShipmentBelongsToAuthenticatedCustomer() throws Exception {
        CreateShipmentRequest request = createValidPayload();

        MvcResult result = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long shipmentId = root.get("data").get("id").asLong();

        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(customerUser.getId(), shipment.getCustomer().getId(), "Shipment must belong to authenticated customer");
        assertEquals(customerUser.getEmail(), shipment.getCustomer().getEmail());
    }

    @Test
    @DisplayName("4. Initial status is always CREATED regardless of client attempt to supply another status")
    void testInitialStatusIsAlwaysCreated() throws Exception {
        String hostilePayload = """
                {
                    "senderName": "Jane Consignor",
                    "senderAddress": "100 Logistics Blvd, Atlanta, GA",
                    "senderPhone": "+1-555-3333",
                    "recipientName": "Bob Receiver",
                    "recipientAddress": "200 Delivery Rd, Miami, FL",
                    "recipientPhone": "+1-555-4444",
                    "weightKg": 1.250,
                    "status": "DELIVERED",
                    "workflowStatus": "IN_TRANSIT"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hostilePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long shipmentId = root.get("data").get("id").asLong();

        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(ShipmentStatus.CREATED, shipment.getStatus());
    }

    @Test
    @DisplayName("5. Client cannot impersonate another customer by supplying a different customer ID")
    void testClientCannotImpersonateAnotherCustomer() throws Exception {
        String hostilePayload = String.format("""
                {
                    "customerId": %d,
                    "customer": {"id": %d},
                    "senderName": "Malicious Consignor",
                    "senderAddress": "666 Hacker Ln, Denver, CO",
                    "senderPhone": "+1-555-9999",
                    "recipientName": "Target Consignee",
                    "recipientAddress": "777 Victim Ave, Seattle, WA",
                    "recipientPhone": "+1-555-8888",
                    "weightKg": 3.000
                }
                """, otherCustomer.getId(), otherCustomer.getId());

        MvcResult result = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hostilePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value(customerUser.getId()))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long shipmentId = root.get("data").get("id").asLong();

        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(customerUser.getId(), shipment.getCustomer().getId(), "Shipment must belong to token identity, not body parameter");
        assertNotEquals(otherCustomer.getId(), shipment.getCustomer().getId());
    }

    @Test
    @DisplayName("6. Missing required fields are rejected with 400 Bad Request")
    void testMissingRequiredFieldsRejected() throws Exception {
        String incompletePayload = """
                {
                    "senderName": "",
                    "senderAddress": "123 Main St",
                    "recipientName": "Alice"
                }
                """;

        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompletePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.senderName").exists())
                .andExpect(jsonPath("$.data.senderPhone").exists())
                .andExpect(jsonPath("$.data.recipientAddress").exists())
                .andExpect(jsonPath("$.data.recipientPhone").exists())
                .andExpect(jsonPath("$.data.weightKg").exists());
    }

    @Test
    @DisplayName("7. Invalid field values (negative weight, out-of-range coordinates) are rejected with 400")
    void testInvalidFieldValuesRejected() throws Exception {
        // Negative weight, out-of-bound latitude (> 90.0), invalid phone
        String invalidPayload = """
                {
                    "senderName": "Valid Sender",
                    "senderAddress": "123 Valid St, Dallas, TX",
                    "senderPhone": "invalid-phone",
                    "recipientName": "Valid Recipient",
                    "recipientAddress": "456 Valid Ave, Houston, TX",
                    "recipientPhone": "bad",
                    "recipientLatitude": 95.0000000,
                    "weightKg": -5.000
                }
                """;

        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.weightKg").exists())
                .andExpect(jsonPath("$.data.recipientLatitude").exists())
                .andExpect(jsonPath("$.data.senderPhone").exists());
    }

    @Test
    @DisplayName("8. Requests without a valid JWT receive 401 Unauthorized")
    void testUnauthenticatedRequestReceives401() throws Exception {
        CreateShipmentRequest request = createValidPayload();

        // No token
        mockMvc.perform(post("/api/v1/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Invalid token
        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer invalid.tampered.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("9. A delivery-agent account cannot create a customer shipment (403 Forbidden)")
    void testDeliveryAgentCannotCreateCustomerShipment() throws Exception {
        CreateShipmentRequest request = createValidPayload();

        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("10. An administrator cannot create a customer shipment (403 Forbidden)")
    void testAdminCannotCreateCustomerShipment() throws Exception {
        CreateShipmentRequest request = createValidPayload();

        mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("11. Client attempt to supply custom price in payload is ignored; server calculates price")
    void testClientCannotOverrideServerCalculatedPrice() throws Exception {
        // Client attempts to pass price 0.01 for a 2.0 kg package (should be $10 base + 2*5 = $20.00)
        String payload = """
                {
                    "senderName": "Cheap Sender",
                    "senderAddress": "100 Bargain St, Dallas, TX",
                    "senderPhone": "+1-555-5555",
                    "recipientName": "Recipient Doe",
                    "recipientAddress": "200 Target Rd, Austin, TX",
                    "recipientPhone": "+1-555-6666",
                    "weightKg": 2.000,
                    "price": 0.01,
                    "currency": "EUR"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.weightKg").value(2.0))
                .andExpect(jsonPath("$.data.price").value(20.00))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long shipmentId = root.get("data").get("id").asLong();

        Shipment shipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(new BigDecimal("20.00"), shipment.getPrice(), "Server must calculate price ($10 base + 2kg * $5)");
        assertEquals("USD", shipment.getCurrency(), "Server must assign configured currency");
    }
}
