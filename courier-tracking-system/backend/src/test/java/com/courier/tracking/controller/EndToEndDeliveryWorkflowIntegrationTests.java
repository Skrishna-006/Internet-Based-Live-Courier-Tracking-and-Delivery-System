package com.courier.tracking.controller;

import com.courier.tracking.dto.*;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.*;
import com.courier.tracking.security.JwtTokenProvider;
import com.courier.tracking.service.ShipmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive End-to-End System Integration Test Suite verifying all Phase 6 workflows:
 * Scenario A: Customer registration & authentication
 * Scenario B: Customer shipment booking & authoritative pricing
 * Scenario C: Admin dispatch assignment & STOMP notifications
 * Scenario D: Delivery agent queue & status progression
 * Scenario E: GPS live telemetry ingestion, filtering & WebSocket broadcasting
 * Scenario F: OTP delivery confirmation, tamper resistance & proof creation
 * Scenario G: Final customer milestone audit & admin analytics verification
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EndToEndDeliveryWorkflowIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private DeliveryProofRepository deliveryProofRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ShipmentService shipmentService;

    private Role customerRole;
    private Role adminRole;
    private Role agentRole;

    @BeforeEach
    void setUp() {
        cleanup();
        customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        trackingEventRepository.deleteAll();
        deliveryProofRepository.deleteAll();
        shipmentRepository.deleteAll();
        userRepository.deleteAll();
    }

    private WebSocketStompClient createStompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
        return stompClient;
    }

    private String getWsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private User createAdminUser(String suffix) {
        return userRepository.save(new User(
                "admin." + suffix + "@e2e-logistics.com",
                passwordEncoder.encode("AdminPass123!"),
                "Admin Operations",
                "+1-555-0101",
                adminRole
        ));
    }

    private User createAgentUser(String suffix) {
        return userRepository.save(new User(
                "agent." + suffix + "@e2e-logistics.com",
                passwordEncoder.encode("AgentPass123!"),
                "Courier Driver",
                "+1-555-0102",
                agentRole
        ));
    }

    private User createCustomerUser(String suffix, String name) {
        return userRepository.save(new User(
                "customer." + suffix + "@e2e-logistics.com",
                passwordEncoder.encode("CustomerPass123!"),
                name,
                "+1-555-0103",
                customerRole
        ));
    }

    // =========================================================================
    // SCENARIO A: CUSTOMER REGISTRATION AND AUTHENTICATION
    // =========================================================================
    @Test
    @DisplayName("Scenario A: Full Customer Registration and Authentication Lifecycle")
    void testScenarioA_CustomerRegistrationAndAuthentication() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String regEmail = "register.user." + suffix + "@e2e-logistics.com";
        String rawPassword = "StrongPassword123!";

        // 1. Customer registration with valid payload (fullName, email, password, phoneNumber)
        CustomerRegistrationRequest regRequest = new CustomerRegistrationRequest(
                "Alice NewCustomer",
                regEmail,
                rawPassword,
                "+1-555-4321"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(regEmail))
                .andExpect(jsonPath("$.data.fullName").value("Alice NewCustomer"))
                .andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"));

        // 2. Verify password hashing in DB
        User persistedUser = userRepository.findByEmail(regEmail).orElseThrow();
        assertNotEquals(rawPassword, persistedUser.getPasswordHash(), "Password must not be stored in plaintext");
        assertTrue(passwordEncoder.matches(rawPassword, persistedUser.getPasswordHash()), "Password hash must verify against raw password");
        assertEquals(RoleName.ROLE_CUSTOMER, persistedUser.getRole().getName());

        // 3. Login with valid credentials
        LoginRequest loginRequest = new LoginRequest(regEmail, rawPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.role").value("ROLE_CUSTOMER"))
                .andReturn();

        String jwt = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 4. Rejection of invalid credentials
        LoginRequest badPasswordRequest = new LoginRequest(regEmail, "WrongPassword999!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPasswordRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        LoginRequest nonExistentRequest = new LoginRequest("nonexistent@e2e-logistics.com", rawPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistentRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // 5. JWT validation and claims extraction
        assertTrue(jwtTokenProvider.validateToken(jwt));
        assertEquals(regEmail, jwtTokenProvider.extractEmail(jwt));

        // 6. Access to customer-protected endpoint with valid token
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 7. Rejection of unauthenticated requests
        mockMvc.perform(get("/api/v1/shipments"))
                .andExpect(status().isUnauthorized());

        // 8. Public registration cannot escalate privileges to ROLE_ADMIN or ROLE_DELIVERY_AGENT
        String exploitEmail = "exploit." + suffix + "@e2e-logistics.com";
        String exploitJson = """
                {
                    "fullName": "Hacker Malory",
                    "email": "%s",
                    "password": "Password123!",
                    "phoneNumber": "+1-555-9999",
                    "role": "ROLE_ADMIN"
                }
                """.formatted(exploitEmail);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exploitJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"));

        User exploitUser = userRepository.findByEmail(exploitEmail).orElseThrow();
        assertEquals(RoleName.ROLE_CUSTOMER, exploitUser.getRole().getName(),
                "Public registration must strictly assign ROLE_CUSTOMER regardless of payload role fields");
    }

    // =========================================================================
    // SCENARIO B: CUSTOMER SHIPMENT BOOKING & PRICE CALCULATION
    // =========================================================================
    @Test
    @DisplayName("Scenario B: Customer Shipment Booking, Authoritative Pricing and Isolation")
    void testScenarioB_CustomerShipmentBookingAndPriceCalculation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User customer1 = createCustomerUser(suffix + "1", "Alice Consignor");
        User customer2 = createCustomerUser(suffix + "2", "Eve Observer");
        String cust1Token = jwtTokenProvider.generateToken(customer1);
        String cust2Token = jwtTokenProvider.generateToken(customer2);

        // 1. Retrieve shipment price quote: 4.5 kg -> base 10.00 + 4.5 * 5.00 = 32.50 USD
        mockMvc.perform(get("/api/v1/shipments/quote")
                        .header("Authorization", "Bearer " + cust1Token)
                        .param("weightKg", "4.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weightKg").value(4.50))
                .andExpect(jsonPath("$.data.price").value(32.50))
                .andExpect(jsonPath("$.data.currency").value("USD"));

        // 2. Create shipment with client trying to inject spoofed price
        CreateShipmentRequest createRequest = new CreateShipmentRequest(
                "Alice Consignor",
                "100 Dispatch Lane, Austin, TX",
                "+1-555-1111",
                "Bob Recipient",
                "500 Target Blvd, Austin, TX",
                "+1-555-2222",
                new BigDecimal("30.2672"),
                new BigDecimal("-97.7431"),
                new BigDecimal("4.50"),
                new BigDecimal("1.00") // Spoofed price should be ignored
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + cust1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trackingNumber").value(matchesPattern("^TRK-[A-Z0-9-]+$")))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.price").value(32.50)) // Authoritative pricing
                .andExpect(jsonPath("$.data.customerEmail").value(customer1.getEmail()))
                .andReturn();

        JsonNode resData = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data");
        Long shipmentId = resData.path("id").asLong();
        String trackingNumber = resData.path("trackingNumber").asText();

        // 3. Retrieve customer's paginated shipment list
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].trackingNumber").value(trackingNumber));

        // 4. Retrieve shipment details by ID
        mockMvc.perform(get("/api/v1/shipments/{id}", shipmentId)
                        .header("Authorization", "Bearer " + cust1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shipmentId))
                .andExpect(jsonPath("$.data.recipientName").value("Bob Recipient"));

        // 5. Customer 2 cannot access Customer 1's shipment (403 Forbidden)
        mockMvc.perform(get("/api/v1/shipments/{id}", shipmentId)
                        .header("Authorization", "Bearer " + cust2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // Customer 2's shipment list must be empty
        mockMvc.perform(get("/api/v1/shipments")
                        .header("Authorization", "Bearer " + cust2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        // 6. Verify persisted shipment and initial state
        Shipment dbShipment = shipmentRepository.findById(shipmentId).orElseThrow();
        assertEquals(ShipmentStatus.CREATED, dbShipment.getStatus());
        assertEquals(new BigDecimal("32.50"), dbShipment.getPrice());

        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(shipmentId);
        assertEquals(0, events.size(), "Consignment created before dispatch transitions has no milestone events yet");
    }

    // =========================================================================
    // SCENARIO C: ADMIN ASSIGNMENT & REAL-TIME DISPATCH NOTIFICATIONS
    // =========================================================================
    @Test
    @DisplayName("Scenario C: Admin Oversight, Agent Assignment and STOMP Notification")
    void testScenarioC_AdminAssignmentAndStompNotifications() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = createAdminUser(suffix);
        User agent = createAgentUser(suffix);
        User otherAgent = createAgentUser(suffix + "_other");
        User customer = createCustomerUser(suffix, "Consignor Bob");

        String adminToken = jwtTokenProvider.generateToken(admin);
        String agentToken = jwtTokenProvider.generateToken(agent);
        String otherAgentToken = jwtTokenProvider.generateToken(otherAgent);
        String custToken = jwtTokenProvider.generateToken(customer);

        // Pre-create shipment in CREATED status
        Shipment shipment = new Shipment(
                "TRK-E2E-ASSIGN-" + suffix.toUpperCase(),
                customer,
                "Consignor Bob",
                "100 Dispatch Way",
                "+1-555-1111",
                "Recipient Alice",
                "200 Target St",
                "+1-555-2222",
                new BigDecimal("2.50"),
                new BigDecimal("22.50"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.CREATED);
        shipment = shipmentRepository.save(shipment);

        // 1. Admin overview metrics
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalShipments", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.unassignedShipments", greaterThanOrEqualTo(1)));

        // 2. Admin retrieves unassigned shipment list
        mockMvc.perform(get("/api/v1/admin/shipments")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("unassignedOnly", "true")
                        .param("search", shipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(shipment.getId()));

        // 3. Delivery Agent connects to STOMP and subscribes to /topic/agent/{agentId}/orders
        WebSocketStompClient stompClient = createStompClient();
        StompHeaders agentConnectHeaders = new StompHeaders();
        agentConnectHeaders.add("Authorization", "Bearer " + agentToken);

        StompSession agentSession = stompClient.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), agentConnectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<ShipmentResponse> orderNotificationFuture = new CompletableFuture<>();
        agentSession.subscribe("/topic/agent/" + agent.getId() + "/orders", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ShipmentResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                orderNotificationFuture.complete((ShipmentResponse) payload);
            }
        });

        Thread.sleep(300); // Wait for subscription registration

        // 4. Admin assigns shipment to agent
        AssignAgentRequest assignRequest = new AssignAgentRequest(agent.getId());
        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", shipment.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agent.getId()))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));

        // 5. Verify agent received STOMP notification
        ShipmentResponse notifiedOrder = orderNotificationFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(notifiedOrder);
        assertEquals(shipment.getTrackingNumber(), notifiedOrder.trackingNumber());
        assertEquals(ShipmentStatus.ASSIGNED, notifiedOrder.status());
        agentSession.disconnect();

        // 6. Verify assigned agent can retrieve the shipment
        mockMvc.perform(get("/api/v1/agents/me/assignments/{id}", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shipment.getId()));

        // 7. Security: Customers & agents cannot access admin endpoints
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        // Security: other agent cannot subscribe to agent's private order topic
        StompHeaders otherAgentConnectHeaders = new StompHeaders();
        otherAgentConnectHeaders.add("Authorization", "Bearer " + otherAgentToken);
        CompletableFuture<String> subscribeErrorFuture = new CompletableFuture<>();

        StompSession otherSession = stompClient.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), otherAgentConnectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                subscribeErrorFuture.complete(exception.getMessage());
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                subscribeErrorFuture.complete(exception.getMessage());
            }
        }).get(5, TimeUnit.SECONDS);

        otherSession.subscribe("/topic/agent/" + agent.getId() + "/orders", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        // Other agent is disconnected or receives transport error due to unauthorized destination
        String err = subscribeErrorFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(err);
        otherSession.disconnect();
    }

    // =========================================================================
    // SCENARIO D: DELIVERY AGENT WORKFLOW & FINITE STATE MACHINE
    // =========================================================================
    @Test
    @DisplayName("Scenario D: Delivery Agent Queue, Details, and Valid FSM Status Transitions")
    void testScenarioD_DeliveryAgentWorkflowAndStatusTransitions() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = createAdminUser(suffix);
        User agent = createAgentUser(suffix);
        User otherAgent = createAgentUser(suffix + "_other");
        User customer = createCustomerUser(suffix, "Consignor Carol");

        String agentToken = jwtTokenProvider.generateToken(agent);
        String otherAgentToken = jwtTokenProvider.generateToken(otherAgent);

        Shipment shipment = new Shipment(
                "TRK-E2E-FSM-" + suffix.toUpperCase(),
                customer,
                "Origin Depot",
                "100 Depot Road",
                "+1-555-1111",
                "Dest Recipient",
                "200 Dest Road",
                "+1-555-2222",
                new BigDecimal("3.00"),
                new BigDecimal("25.00"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.ASSIGNED);
        shipment.setAssignedAgent(agent);
        shipment = shipmentRepository.save(shipment);

        // Record initial ASSIGNED event
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.ASSIGNED, null, null, "Origin Depot", "Agent assigned"));

        // 1. Agent retrieves assigned shipments & summary
        mockMvc.perform(get("/api/v1/agents/me/assignments")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(shipment.getId()));

        mockMvc.perform(get("/api/v1/agents/me/assignments/summary")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.awaitingPickup").value(1));

        // 2. Perform valid transition: ASSIGNED -> PICKED_UP
        UpdateShipmentStatusRequest pickupRequest = new UpdateShipmentStatusRequest(
                ShipmentStatus.PICKED_UP,
                "Origin Fulfillment Center",
                "Package scanned and picked up by courier"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pickupRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"));

        // 3. Perform valid transition: PICKED_UP -> IN_TRANSIT
        UpdateShipmentStatusRequest transitRequest = new UpdateShipmentStatusRequest(
                ShipmentStatus.IN_TRANSIT,
                "Austin Metro Transit Corridor",
                "In transit to local delivery hub"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        // 4. Perform valid transition: IN_TRANSIT -> OUT_FOR_DELIVERY
        UpdateShipmentStatusRequest outForDeliveryRequest = new UpdateShipmentStatusRequest(
                ShipmentStatus.OUT_FOR_DELIVERY,
                "Austin Last Mile Station",
                "Loaded onto vehicle for delivery handover"
        );
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outForDeliveryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));

        // 5. Invalid transition: cannot transition OUT_FOR_DELIVERY directly to CREATED
        UpdateShipmentStatusRequest illegalRequest = new UpdateShipmentStatusRequest(ShipmentStatus.CREATED);
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 6. Another delivery agent cannot update this shipment
        UpdateShipmentStatusRequest otherAgentRequest = new UpdateShipmentStatusRequest(ShipmentStatus.ATTEMPT_FAILED);
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipment.getId())
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherAgentRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // 7. Verify tracking events are chronologically recorded
        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(shipment.getId());
        assertEquals(4, events.size());
        assertEquals(ShipmentStatus.ASSIGNED, events.get(0).getStatus());
        assertEquals(ShipmentStatus.PICKED_UP, events.get(1).getStatus());
        assertEquals(ShipmentStatus.IN_TRANSIT, events.get(2).getStatus());
        assertEquals(ShipmentStatus.OUT_FOR_DELIVERY, events.get(3).getStatus());
    }

    // =========================================================================
    // SCENARIO E: GPS TELEMETRY & LIVE TRACKING PIPELINE
    // =========================================================================
    @Test
    @DisplayName("Scenario E: GPS Telemetry Validation, STOMP Broadcast and Polling Fallback")
    void testScenarioE_GpsLiveTrackingAndTelemetryPipeline() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = createAdminUser(suffix);
        User agent = createAgentUser(suffix);
        User otherAgent = createAgentUser(suffix + "_other");
        User customer = createCustomerUser(suffix, "Customer Trackee");

        String adminToken = jwtTokenProvider.generateToken(admin);
        String agentToken = jwtTokenProvider.generateToken(agent);
        String otherAgentToken = jwtTokenProvider.generateToken(otherAgent);
        String custToken = jwtTokenProvider.generateToken(customer);

        Shipment shipment = new Shipment(
                "TRK-E2E-GPS-" + suffix.toUpperCase(),
                customer,
                "Origin Facility",
                "100 Waypoint Blvd",
                "+1-555-1111",
                "Recipient Location",
                "200 Target St",
                "+1-555-2222",
                new BigDecimal("2.00"),
                new BigDecimal("20.00"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        shipment.setAssignedAgent(agent);
        shipment = shipmentRepository.save(shipment);

        // 1. Connect Customer to STOMP and subscribe to /topic/track/{trackingNumber}
        WebSocketStompClient stompClient = createStompClient();
        StompHeaders custHeaders = new StompHeaders();
        custHeaders.add("Authorization", "Bearer " + custToken);
        StompSession custSession = stompClient.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), custHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<LiveTrackingBroadcast> customerBroadcastFuture = new CompletableFuture<>();
        custSession.subscribe("/topic/track/" + shipment.getTrackingNumber(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LiveTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                customerBroadcastFuture.complete((LiveTrackingBroadcast) payload);
            }
        });

        // 2. Connect Admin to STOMP and subscribe to /topic/admin/fleet
        StompHeaders adminHeaders = new StompHeaders();
        adminHeaders.add("Authorization", "Bearer " + adminToken);
        StompSession adminSession = stompClient.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), adminHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<FleetTrackingBroadcast> fleetBroadcastFuture = new CompletableFuture<>();
        adminSession.subscribe("/topic/admin/fleet", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return FleetTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                fleetBroadcastFuture.complete((FleetTrackingBroadcast) payload);
            }
        });

        Thread.sleep(300); // Allow broker subscriptions to settle

        // 3. Agent submits valid GPS coordinates
        Instant telemetryNow = Instant.now();
        LocationUpdateRequest validLoc = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("12.5"), // accuracy <= 30m
                new BigDecimal("45.0"), // speed <= 140 km/h
                new BigDecimal("90.0"), // bearing
                telemetryNow
        );

        mockMvc.perform(post("/api/v1/shipments/{id}/location", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(30.267200))
                .andExpect(jsonPath("$.data.longitude").value(-97.743100));

        // 4. Verify STOMP broadcasts received
        LiveTrackingBroadcast custBroadcast = customerBroadcastFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(custBroadcast);
        assertEquals(shipment.getTrackingNumber(), custBroadcast.trackingNumber());
        assertEquals(new BigDecimal("30.267200"), custBroadcast.latitude());
        assertEquals(new BigDecimal("-97.743100"), custBroadcast.longitude());
        assertEquals("OUT_FOR_DELIVERY", custBroadcast.status());

        FleetTrackingBroadcast adminBroadcast = fleetBroadcastFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(adminBroadcast);
        assertEquals(shipment.getId(), adminBroadcast.shipmentId());
        assertEquals(agent.getId(), adminBroadcast.agentId());
        assertEquals(new BigDecimal("30.267200"), adminBroadcast.latitude());

        custSession.disconnect();
        adminSession.disconnect();

        // 5. Telemetry validation: invalid coordinates rejected
        LocationUpdateRequest badCoords = new LocationUpdateRequest(
                new BigDecimal("95.000000"), // Invalid lat > 90
                new BigDecimal("-97.743100"),
                new BigDecimal("10.0"),
                telemetryNow
        );
        mockMvc.perform(post("/api/v1/shipments/{id}/location", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCoords)))
                .andExpect(status().isBadRequest());

        // 6. Telemetry validation: excessive accuracy radius rejected (> 30m)
        LocationUpdateRequest badAccuracy = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("45.0"), // accuracy > 30m
                telemetryNow
        );
        mockMvc.perform(post("/api/v1/shipments/{id}/location", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badAccuracy)))
                .andExpect(status().isBadRequest());

        // 7. Telemetry validation: throttling enforced (< 5s between updates)
        LocationUpdateRequest rapidLoc = new LocationUpdateRequest(
                new BigDecimal("30.267300"),
                new BigDecimal("-97.743200"),
                new BigDecimal("10.0"),
                Instant.now()
        );
        mockMvc.perform(post("/api/v1/shipments/{id}/location", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rapidLoc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Location updates must be at least 5 seconds apart")));

        // 8. Other agent cannot submit location telemetry
        mockMvc.perform(post("/api/v1/shipments/{id}/location", shipment.getId())
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoc)))
                .andExpect(status().isForbidden());

        // 9. Public polling fallback returns latest coordinates without authentication
        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}/location", shipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(30.267200))
                .andExpect(jsonPath("$.data.longitude").value(-97.743100))
                .andExpect(jsonPath("$.data.fresh").value(true));

        // 10. Public tracking summary displays current status without exposing private coordinates
        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}", shipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.data.trackingNumber").value(shipment.getTrackingNumber()));
    }

    // =========================================================================
    // SCENARIO F: OTP DELIVERY CONFIRMATION & PROOF OF DELIVERY
    // =========================================================================
    @Test
    @DisplayName("Scenario F: OTP Generation, Expiry, Retry Limit, Verification and Proof Creation")
    void testScenarioF_OtpDeliveryConfirmationAndProofCreation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = createAdminUser(suffix);
        User agent = createAgentUser(suffix);
        User otherAgent = createAgentUser(suffix + "_other");
        User customer = createCustomerUser(suffix, "Recipient David");

        String agentToken = jwtTokenProvider.generateToken(agent);
        String otherAgentToken = jwtTokenProvider.generateToken(otherAgent);
        String custToken = jwtTokenProvider.generateToken(customer);

        // Pre-create shipment in OUT_FOR_DELIVERY status
        Shipment shipment = new Shipment(
                "TRK-E2E-OTP-" + suffix.toUpperCase(),
                customer,
                "Distribution Center",
                "100 Logistics Way",
                "+1-555-1111",
                "Recipient David",
                "500 Elm Street, Austin, TX",
                "+1-555-9876",
                new BigDecimal("1.80"),
                new BigDecimal("19.00"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        shipment.setAssignedAgent(agent);
        shipment = shipmentRepository.save(shipment);

        // 1. Generate delivery OTP
        MvcResult otpGenResult = mockMvc.perform(post("/api/v1/shipments/{id}/delivery-otp", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn();

        JsonNode otpData = objectMapper.readTree(otpGenResult.getResponse().getContentAsString()).path("data");
        String generatedOtp = otpData.path("otp").asText();
        assertNotNull(generatedOtp);
        assertEquals(6, generatedOtp.length());

        // Verify OTP is hashed in database, not stored in plaintext
        Shipment dbShipment = shipmentRepository.findById(shipment.getId()).orElseThrow();
        assertNotNull(dbShipment.getDeliveryOtpHash());
        assertNotEquals(generatedOtp, dbShipment.getDeliveryOtpHash());
        assertTrue(passwordEncoder.matches(generatedOtp, dbShipment.getDeliveryOtpHash()));
        assertTrue(dbShipment.getDeliveryOtpExpiresAt().isAfter(Instant.now().plus(8, ChronoUnit.MINUTES)));

        // 2. Customer cannot verify OTP (only agent or admin can verify)
        VerifyDeliveryOtpRequest verifyRequest = new VerifyDeliveryOtpRequest(generatedOtp, "Recipient David");
        mockMvc.perform(post("/api/v1/shipments/{id}/delivery/verify-otp", shipment.getId())
                        .header("Authorization", "Bearer " + custToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isForbidden());

        // 3. Other agent cannot verify OTP for this shipment
        mockMvc.perform(post("/api/v1/shipments/{id}/delivery/verify-otp", shipment.getId())
                        .header("Authorization", "Bearer " + otherAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isForbidden());

        // 4. Incorrect OTP attempt is rejected and counts toward limit
        VerifyDeliveryOtpRequest badOtpRequest = new VerifyDeliveryOtpRequest("000000", "Recipient David");
        mockMvc.perform(post("/api/v1/shipments/{id}/delivery/verify-otp", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badOtpRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid delivery OTP")));

        dbShipment = shipmentRepository.findById(shipment.getId()).orElseThrow();
        assertEquals(1, dbShipment.getDeliveryOtpAttempts());

        // 5. Successful delivery confirmation with valid OTP
        mockMvc.perform(post("/api/v1/shipments/{id}/delivery/verify-otp", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.proofType").value("OTP"))
                .andExpect(jsonPath("$.data.recipientName").value("Recipient David"));

        // 6. Verify database records: Shipment DELIVERED, DeliveryProof created, TrackingEvent recorded
        Shipment deliveredShipment = shipmentRepository.findById(shipment.getId()).orElseThrow();
        assertEquals(ShipmentStatus.DELIVERED, deliveredShipment.getStatus());
        assertNotNull(deliveredShipment.getActualDeliveryAt());
        assertNull(deliveredShipment.getDeliveryOtpExpiresAt()); // Cleared upon verification

        List<DeliveryProof> proofs = deliveryProofRepository.findAll();
        assertFalse(proofs.isEmpty());
        DeliveryProof proof = proofs.stream().filter(p -> p.getShipment().getId().equals(deliveredShipment.getId())).findFirst().orElseThrow();
        assertEquals(ProofType.OTP, proof.getProofType());
        assertEquals("Recipient David", proof.getRecipientName());

        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(shipment.getId());
        boolean hasDeliveredEvent = events.stream().anyMatch(e -> e.getStatus() == ShipmentStatus.DELIVERED);
        assertTrue(hasDeliveredEvent, "DELIVERED tracking milestone must be recorded");

        // 7. OTP cannot be reused after delivery
        mockMvc.perform(post("/api/v1/shipments/{id}/delivery/verify-otp", shipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("already been delivered")));
    }

    // =========================================================================
    // SCENARIO G: FINAL CUSTOMER AND ADMIN VERIFICATION
    // =========================================================================
    @Test
    @DisplayName("Scenario G: Final Post-Delivery Audit by Customer and Administrator")
    void testScenarioG_FinalCustomerAndAdminVerification() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User admin = createAdminUser(suffix);
        User agent = createAgentUser(suffix);
        User customer = createCustomerUser(suffix, "Audit Consignor");

        String adminToken = jwtTokenProvider.generateToken(admin);
        String agentToken = jwtTokenProvider.generateToken(agent);
        String custToken = jwtTokenProvider.generateToken(customer);

        // Pre-create delivered shipment with full tracking history
        Shipment shipment = new Shipment(
                "TRK-E2E-AUDIT-" + suffix.toUpperCase(),
                customer,
                "Origin Facility",
                "100 Audit Lane",
                "+1-555-1111",
                "Final Recipient",
                "500 Target Place",
                "+1-555-2222",
                new BigDecimal("2.50"),
                new BigDecimal("22.50"),
                "USD"
        );
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setAssignedAgent(agent);
        shipment.setActualDeliveryAt(Instant.now());
        shipment = shipmentRepository.save(shipment);

        // Record full milestone audit events
        Instant t0 = Instant.now().minus(2, ChronoUnit.HOURS);
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.CREATED, null, null, "Origin Facility", "Shipment booked"));
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.ASSIGNED, null, null, "Origin Facility", "Assigned to driver"));
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.PICKED_UP, null, null, "Origin Hub", "Scanned at origin"));
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.IN_TRANSIT, null, null, "Metro Station", "Linehaul movement"));
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.OUT_FOR_DELIVERY, null, null, "Austin Central", "Dispatched for last mile"));
        trackingEventRepository.save(new TrackingEvent(shipment, ShipmentStatus.DELIVERED, null, null, "500 Target Place", "Handed over via OTP"));

        // 1. Customer retrieves delivered shipment details
        mockMvc.perform(get("/api/v1/shipments/{id}", shipment.getId())
                        .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.actualDeliveryAt").isNotEmpty());

        // 2. Customer inspects public tracking milestones (all 6 events in order)
        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}", shipment.getTrackingNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.milestones", hasSize(6)))
                .andExpect(jsonPath("$.data.milestones[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.milestones[5].status").value("DELIVERED"));

        // 3. Administrator retrieves shipment details & audit events
        mockMvc.perform(get("/api/v1/admin/shipments/{id}", shipment.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shipment.getId()))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agent.getId()));

        mockMvc.perform(get("/api/v1/admin/shipments/{id}/events", shipment.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data[5].status").value("DELIVERED"));

        // 4. Admin overview metrics reflect delivered consignment
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveredShipments", greaterThanOrEqualTo(1)));

        // 5. Admin agent directory reflects completed delivery count
        mockMvc.perform(get("/api/v1/admin/agents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem(allOf(
                        hasEntry("id", agent.getId().intValue()),
                        hasEntry("deliveredShipmentsCount", 1)
                ))));
    }

    // =========================================================================
    // UNIFIED FULL END-TO-END SYSTEM LIFECYCLE (HAPPY PATH PIPELINE)
    // =========================================================================
    @Test
    @DisplayName("Complete End-to-End Delivery Lifecycle: Booking -> Assignment -> Transit -> Telemetry -> OTP Delivery")
    void testCompleteEndToEndDeliveryLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String customerEmail = "pipeline.cust." + suffix + "@e2e-logistics.com";
        String customerPass = "CustomerSecret123!";

        // --- STEP 1: Customer registers and authenticates ---
        CustomerRegistrationRequest regReq = new CustomerRegistrationRequest("Pipeline Consignor", customerEmail, customerPass, "+1-555-7777");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(customerEmail, customerPass))))
                .andExpect(status().isOk())
                .andReturn();

        String custToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        // Create Admin and Agent actors
        User admin = createAdminUser(suffix);
        User agent = createAgentUser(suffix);
        String adminToken = jwtTokenProvider.generateToken(admin);
        String agentToken = jwtTokenProvider.generateToken(agent);

        // --- STEP 2: Customer retrieves price quote and books shipment ---
        mockMvc.perform(get("/api/v1/shipments/quote")
                        .header("Authorization", "Bearer " + custToken)
                        .param("weightKg", "3.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(25.00));

        CreateShipmentRequest bookReq = new CreateShipmentRequest(
                "Pipeline Consignor",
                "100 Industrial Road, Austin, TX",
                "+1-555-7777",
                "Pipeline Recipient",
                "900 Congress Ave, Austin, TX",
                "+1-555-8888",
                new BigDecimal("30.2700"),
                new BigDecimal("-97.7400"),
                new BigDecimal("3.00")
        );

        MvcResult bookRes = mockMvc.perform(post("/api/v1/shipments")
                        .header("Authorization", "Bearer " + custToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.price").value(25.00))
                .andReturn();

        JsonNode bookedData = objectMapper.readTree(bookRes.getResponse().getContentAsString()).path("data");
        Long shipmentId = bookedData.path("id").asLong();
        String trackingNumber = bookedData.path("trackingNumber").asText();

        // --- STEP 3: Admin assigns consignment to delivery agent ---
        mockMvc.perform(patch("/api/v1/shipments/{id}/assign", shipmentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignAgentRequest(agent.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agent.getId()));

        // --- STEP 4: Delivery agent advances lifecycle through FSM ---
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipmentId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(ShipmentStatus.PICKED_UP, "Austin Sort Facility", "Consignment collected"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"));

        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipmentId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(ShipmentStatus.IN_TRANSIT, "Austin Expressway", "Linehaul movement"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_TRANSIT"));

        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipmentId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateShipmentStatusRequest(ShipmentStatus.OUT_FOR_DELIVERY, "Downtown Austin", "Out for delivery with courier"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_FOR_DELIVERY"));

        // --- STEP 5: Delivery agent submits live GPS coordinate update ---
        LocationUpdateRequest locReq = new LocationUpdateRequest(
                new BigDecimal("30.269000"),
                new BigDecimal("-97.741000"),
                new BigDecimal("8.0"),
                new BigDecimal("30.0"),
                new BigDecimal("180.0"),
                Instant.now()
        );
        mockMvc.perform(post("/api/v1/shipments/{id}/location", shipmentId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(30.269000))
                .andExpect(jsonPath("$.data.longitude").value(-97.741000));

        // Public polling endpoint reflects live position
        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}/location", trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").value(30.269000))
                .andExpect(jsonPath("$.data.longitude").value(-97.741000))
                .andExpect(jsonPath("$.data.fresh").value(true));

        // --- STEP 6: OTP generation and delivery confirmation ---
        MvcResult otpRes = mockMvc.perform(post("/api/v1/shipments/{id}/delivery-otp", shipmentId)
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andReturn();

        String rawOtp = objectMapper.readTree(otpRes.getResponse().getContentAsString()).path("data").path("otp").asText();
        assertNotNull(rawOtp);
        assertEquals(6, rawOtp.length());

        VerifyDeliveryOtpRequest verifyReq = new VerifyDeliveryOtpRequest(rawOtp, "Pipeline Recipient", "Signed and verified at reception", new BigDecimal("30.2700"), new BigDecimal("-97.7400"), "Austin Congress Center");
        mockMvc.perform(post("/api/v1/shipments/{id}/delivery/verify-otp", shipmentId)
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.proofType").value("OTP"));

        // --- STEP 7: Final customer and admin verification ---
        // Customer view
        mockMvc.perform(get("/api/v1/shipments/{id}", shipmentId)
                        .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.actualDeliveryAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}", trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.milestones", hasSize(4)))
                .andExpect(jsonPath("$.data.milestones[0].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.milestones[3].status").value("DELIVERED"));

        // Admin view
        mockMvc.perform(get("/api/v1/admin/shipments/{id}", shipmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.assignedAgentId").value(agent.getId()));

        mockMvc.perform(get("/api/v1/admin/agents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem(allOf(
                        hasEntry("id", agent.getId().intValue()),
                        hasEntry("deliveredShipmentsCount", 1)
                ))));
    }
}
