package com.courier.tracking.controller;

import com.courier.tracking.dto.FleetTrackingBroadcast;
import com.courier.tracking.dto.LiveTrackingBroadcast;
import com.courier.tracking.dto.LocationUpdateRequest;
import com.courier.tracking.entity.*;
import com.courier.tracking.repository.*;
import com.courier.tracking.security.JwtTokenProvider;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSocketLiveTrackingIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

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
    private ObjectMapper objectMapper;

    private User adminUser;
    private User customerUser1;
    private User customerUser2;
    private User deliveryAgent;

    private String adminToken;
    private String customer1Token;
    private String customer2Token;
    private String agentToken;

    private Shipment activeShipment;

    @BeforeEach
    void setUp() {
        cleanup();

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(new User(
                "ws.admin." + suffix + "@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin WS",
                "+1-555-0901",
                adminRole
        ));

        customerUser1 = userRepository.save(new User(
                "ws.cust1." + suffix + "@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer WS One",
                "+1-555-0902",
                customerRole
        ));

        customerUser2 = userRepository.save(new User(
                "ws.cust2." + suffix + "@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer WS Two",
                "+1-555-0903",
                customerRole
        ));

        deliveryAgent = userRepository.save(new User(
                "ws.agent." + suffix + "@example.com",
                passwordEncoder.encode("Password123!"),
                "Agent WS",
                "+1-555-0904",
                agentRole
        ));

        adminToken = jwtTokenProvider.generateToken(adminUser);
        customer1Token = jwtTokenProvider.generateToken(customerUser1);
        customer2Token = jwtTokenProvider.generateToken(customerUser2);
        agentToken = jwtTokenProvider.generateToken(deliveryAgent);

        activeShipment = new Shipment(
                "TRK-WS-" + suffix.toUpperCase(),
                customerUser1,
                "Sender Consignor",
                "100 First St, Austin, TX",
                "+1-555-1111",
                "Target Recipient",
                "200 Second St, Austin, TX",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("22.50"),
                "USD"
        );
        activeShipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        activeShipment.setAssignedAgent(deliveryAgent);
        activeShipment = shipmentRepository.save(activeShipment);
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

    @Test
    @DisplayName("Successful STOMP connection with valid JWT")
    void stompConnect_withValidJwt_succeeds() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + customer1Token);

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        assertNotNull(session);
        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    @DisplayName("Rejection of missing JWT on STOMP CONNECT frame")
    void stompConnect_withoutJwt_isRejected() {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS));

        assertNotNull(exception);
    }

    @Test
    @DisplayName("Rejection of invalid JWT on STOMP CONNECT frame")
    void stompConnect_withInvalidJwt_isRejected() {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer invalid.token.payload");

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
                client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS));

        assertNotNull(exception);
    }

    @Test
    @DisplayName("Customer access to own shipment topic: receives live broadcast upon accepted REST GPS update")
    void customer_subscribesToOwnShipment_receivesBroadcastOnGpsUpdate() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + customer1Token);

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<LiveTrackingBroadcast> messageFuture = new CompletableFuture<>();

        session.subscribe("/topic/track/" + activeShipment.getTrackingNumber(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LiveTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageFuture.complete((LiveTrackingBroadcast) payload);
            }
        });

        // Wait to ensure subscription is registered in broker
        Thread.sleep(300);

        // Submit accepted GPS update via existing REST endpoint
        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("15.0"),
                new BigDecimal("42.5"),
                new BigDecimal("185.0"),
                now
        );

        mockMvc.perform(post("/api/v1/shipments/{id}/location", activeShipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        LiveTrackingBroadcast broadcast = messageFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(broadcast);
        assertEquals(activeShipment.getTrackingNumber(), broadcast.trackingNumber());
        assertEquals(new BigDecimal("30.267200"), broadcast.latitude());
        assertEquals(new BigDecimal("-97.743100"), broadcast.longitude());
        assertEquals("OUT_FOR_DELIVERY", broadcast.status());
        assertEquals(new BigDecimal("42.5"), broadcast.speed());
        assertEquals(new BigDecimal("185.0"), broadcast.bearing());

        session.disconnect();
    }

    @Test
    @DisplayName("Customer rejection for another customer's shipment topic subscription")
    void customer_subscribingToAnotherCustomerShipment_isRejected() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + customer2Token); // Customer 2

        CompletableFuture<String> errorFuture = new CompletableFuture<>();

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String msg = headers.getFirst("message");
                if (msg == null && payload != null) {
                    msg = payload.toString();
                }
                errorFuture.complete(msg != null ? msg : "ERROR");
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                String msg = (headers != null) ? headers.getFirst("message") : null;
                if (msg == null && payload != null && payload.length > 0) {
                    msg = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                }
                if (msg == null && exception != null) {
                    msg = exception.getMessage();
                }
                errorFuture.complete(msg != null ? msg : "ERROR");
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception != null ? exception.getMessage() : "Connection closed");
            }
        }).get(5, TimeUnit.SECONDS);

        // Try subscribing to Customer 1's shipment
        session.subscribe("/topic/track/" + activeShipment.getTrackingNumber(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LiveTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        String error = errorFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(error);
        String errLower = error.toLowerCase();
        assertTrue(errLower.contains("denied")
                || errLower.contains("not authorized")
                || errLower.contains("administrators")
                || errLower.contains("error")
                || errLower.contains("closed")
                || !session.isConnected());
    }

    @Test
    @DisplayName("Admin fleet-topic authorization: Admin receives aggregated broadcast upon GPS update")
    void admin_subscribesToFleetTopic_receivesAggregatedBroadcast() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + adminToken);

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<FleetTrackingBroadcast> fleetFuture = new CompletableFuture<>();

        session.subscribe("/topic/admin/fleet", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return FleetTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                fleetFuture.complete((FleetTrackingBroadcast) payload);
            }
        });

        Thread.sleep(300);

        Instant now = Instant.now();
        LocationUpdateRequest request = new LocationUpdateRequest(
                new BigDecimal("30.270000"),
                new BigDecimal("-97.740000"),
                new BigDecimal("10.0"),
                new BigDecimal("35.0"),
                new BigDecimal("90.0"),
                now
        );

        mockMvc.perform(post("/api/v1/shipments/{id}/location", activeShipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        FleetTrackingBroadcast fleetBroadcast = fleetFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(fleetBroadcast);
        assertEquals(activeShipment.getId(), fleetBroadcast.shipmentId());
        assertEquals(activeShipment.getTrackingNumber(), fleetBroadcast.trackingNumber());
        assertEquals(deliveryAgent.getId(), fleetBroadcast.agentId());
        assertEquals(new BigDecimal("30.270000"), fleetBroadcast.latitude());
        assertEquals(new BigDecimal("-97.740000"), fleetBroadcast.longitude());

        session.disconnect();
    }

    @Test
    @DisplayName("Unauthorized fleet subscriptions: Customer is rejected from subscribing to /topic/admin/fleet")
    void customer_subscribingToFleetTopic_isRejected() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + customer1Token);

        CompletableFuture<String> errorFuture = new CompletableFuture<>();

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String msg = headers.getFirst("message");
                if (msg == null && payload != null) {
                    msg = payload.toString();
                }
                errorFuture.complete(msg != null ? msg : "ERROR");
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                String msg = (headers != null) ? headers.getFirst("message") : null;
                if (msg == null && payload != null && payload.length > 0) {
                    msg = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                }
                if (msg == null && exception != null) {
                    msg = exception.getMessage();
                }
                errorFuture.complete(msg != null ? msg : "ERROR");
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception != null ? exception.getMessage() : "Connection closed");
            }
        }).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/admin/fleet", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return FleetTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        String error = errorFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(error);
        String errLower = error.toLowerCase();
        assertTrue(errLower.contains("administrators") || errLower.contains("denied") || errLower.contains("not authorized") || errLower.contains("error") || errLower.contains("closed") || !session.isConnected());
    }

    @Test
    @DisplayName("Unauthorized fleet subscriptions: Delivery agent is rejected from subscribing to /topic/admin/fleet")
    void agent_subscribingToFleetTopic_isRejected() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + agentToken);

        CompletableFuture<String> errorFuture = new CompletableFuture<>();

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                String msg = headers.getFirst("message");
                if (msg == null && payload != null) {
                    msg = payload.toString();
                }
                errorFuture.complete(msg != null ? msg : "ERROR");
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                String msg = (headers != null) ? headers.getFirst("message") : null;
                if (msg == null && payload != null && payload.length > 0) {
                    msg = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                }
                if (msg == null && exception != null) {
                    msg = exception.getMessage();
                }
                errorFuture.complete(msg != null ? msg : "ERROR");
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception != null ? exception.getMessage() : "Connection closed");
            }
        }).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/admin/fleet", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return FleetTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {}
        });

        String error = errorFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(error);
        String errLower = error.toLowerCase();
        assertTrue(errLower.contains("administrators") || errLower.contains("denied") || errLower.contains("not authorized") || errLower.contains("error") || errLower.contains("closed") || !session.isConnected());
    }

    @Test
    @DisplayName("No broadcasting after rejected GPS updates (accuracy exceeds 30m)")
    void rejectedGpsUpdate_doesNotBroadcastToTopics() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + customer1Token);

        StompSession session = client.connectAsync(getWsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<LiveTrackingBroadcast> messageFuture = new CompletableFuture<>();

        session.subscribe("/topic/track/" + activeShipment.getTrackingNumber(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LiveTrackingBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messageFuture.complete((LiveTrackingBroadcast) payload);
            }
        });

        Thread.sleep(300);

        // Submit rejected GPS update (accuracy 45.0m > 30.0m threshold)
        Instant now = Instant.now();
        LocationUpdateRequest invalidRequest = new LocationUpdateRequest(
                new BigDecimal("30.267200"),
                new BigDecimal("-97.743100"),
                new BigDecimal("45.0"), // Invalid accuracy
                new BigDecimal("30.0"),
                new BigDecimal("90.0"),
                now
        );

        mockMvc.perform(post("/api/v1/shipments/{id}/location", activeShipment.getId())
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        // Assert that no broadcast was received on the customer topic
        assertThrows(TimeoutException.class, () -> messageFuture.get(1500, TimeUnit.MILLISECONDS));

        session.disconnect();
    }
}
