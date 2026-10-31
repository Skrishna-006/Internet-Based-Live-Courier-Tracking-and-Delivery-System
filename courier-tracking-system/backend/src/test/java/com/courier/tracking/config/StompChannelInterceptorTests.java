package com.courier.tracking.config;

import com.courier.tracking.entity.Role;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.RoleRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StompChannelInterceptorTests {

    @Autowired
    private StompChannelInterceptor interceptor;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customerUser1;
    private User customerUser2;
    private User adminUser;
    private User agentUser;
    private User deactivatedUser;

    private String customer1Token;
    private String adminToken;
    private String agentToken;
    private String deactivatedToken;

    private Shipment shipment1;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER).orElseThrow();
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();

        customerUser1 = userRepository.save(new User(
                "stomp.customer1@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer One",
                "+1-555-0101",
                customerRole
        ));

        customerUser2 = userRepository.save(new User(
                "stomp.customer2@example.com",
                passwordEncoder.encode("Password123!"),
                "Customer Two",
                "+1-555-0102",
                customerRole
        ));

        adminUser = userRepository.save(new User(
                "stomp.admin@example.com",
                passwordEncoder.encode("Password123!"),
                "Admin Dispatcher",
                "+1-555-0103",
                adminRole
        ));

        agentUser = userRepository.save(new User(
                "stomp.agent@example.com",
                passwordEncoder.encode("Password123!"),
                "Agent Courier",
                "+1-555-0104",
                agentRole
        ));

        deactivatedUser = new User(
                "stomp.deactivated@example.com",
                passwordEncoder.encode("Password123!"),
                "Deactivated User",
                "+1-555-0105",
                customerRole
        );
        deactivatedUser.setActive(false);
        deactivatedUser = userRepository.save(deactivatedUser);

        customer1Token = jwtTokenProvider.generateToken(customerUser1);
        adminToken = jwtTokenProvider.generateToken(adminUser);
        agentToken = jwtTokenProvider.generateToken(agentUser);
        deactivatedToken = jwtTokenProvider.generateToken(deactivatedUser);

        shipment1 = new Shipment(
                "TRK-20261030-INT001",
                customerUser1,
                "Sender Consignor",
                "100 First St",
                "+1-555-1111",
                "Recipient",
                "200 Second St",
                "+1-555-2222",
                new BigDecimal("2.500"),
                new BigDecimal("25.00"),
                "USD"
        );
        shipment1 = shipmentRepository.save(shipment1);
    }

    @Test
    @DisplayName("CONNECT with valid Bearer JWT authenticates user and sets Principal")
    void connect_withValidBearerJwt_authenticatesUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + customer1Token);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
        Principal user = StompHeaderAccessor.wrap(result).getUser();
        assertNotNull(user);
        assertEquals(customerUser1.getEmail(), user.getName());
        assertTrue(((UsernamePasswordAuthenticationToken) user).getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
    }

    @Test
    @DisplayName("CONNECT with token in 'token' native header authenticates user")
    void connect_withTokenHeader_authenticatesUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("token", adminToken);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertNotNull(result);
        Principal user = StompHeaderAccessor.wrap(result).getUser();
        assertNotNull(user);
        assertEquals(adminUser.getEmail(), user.getName());
    }

    @Test
    @DisplayName("CONNECT without token throws BadCredentialsException")
    void connect_withoutToken_throwsBadCredentialsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("missing token"));
    }

    @Test
    @DisplayName("CONNECT with invalid or expired token throws BadCredentialsException")
    void connect_withInvalidToken_throwsBadCredentialsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer invalid.jwt.signature");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("invalid or expired token"));
    }

    @Test
    @DisplayName("CONNECT with deactivated user account throws DisabledException")
    void connect_withDeactivatedUser_throwsDisabledException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + deactivatedToken);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        DisabledException ex = assertThrows(DisabledException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("deactivated"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/track/{trackingNumber} by owner customer succeeds")
    void subscribe_toCustomerTrackingTopic_byOwnerCustomer_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/track/" + shipment1.getTrackingNumber());
        accessor.setUser(createAuth(customerUser1.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/track/{trackingNumber} by another customer throws AccessDeniedException")
    void subscribe_toCustomerTrackingTopic_byDifferentCustomer_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/track/" + shipment1.getTrackingNumber());
        accessor.setUser(createAuth(customerUser2.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/track/{trackingNumber} for non-existent shipment throws AccessDeniedException")
    void subscribe_toCustomerTrackingTopic_nonExistentShipment_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/track/TRK-NONEXISTENT-999");
        accessor.setUser(createAuth(customerUser1.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("not found or unauthorized"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/track/{trackingNumber} by delivery agent throws AccessDeniedException")
    void subscribe_toCustomerTrackingTopic_byDeliveryAgent_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/track/" + shipment1.getTrackingNumber());
        accessor.setUser(createAuth(agentUser.getEmail(), "ROLE_DELIVERY_AGENT"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Only authorized customers can subscribe"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/track/{trackingNumber} by ROLE_ADMIN succeeds")
    void subscribe_toCustomerTrackingTopic_byAdmin_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/track/" + shipment1.getTrackingNumber());
        accessor.setUser(createAuth(adminUser.getEmail(), "ROLE_ADMIN"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/admin/fleet by ROLE_ADMIN succeeds")
    void subscribe_toFleetTopic_byAdmin_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/fleet");
        accessor.setUser(createAuth(adminUser.getEmail(), "ROLE_ADMIN"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/admin/fleet by customer throws AccessDeniedException")
    void subscribe_toFleetTopic_byCustomer_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/fleet");
        accessor.setUser(createAuth(customerUser1.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Only administrators may subscribe to the fleet topic"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/admin/fleet by delivery agent throws AccessDeniedException")
    void subscribe_toFleetTopic_byAgent_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/admin/fleet");
        accessor.setUser(createAuth(agentUser.getEmail(), "ROLE_DELIVERY_AGENT"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Only administrators may subscribe to the fleet topic"));
    }

    @Test
    @DisplayName("SUBSCRIBE to arbitrary /topic destination throws AccessDeniedException")
    void subscribe_toArbitraryTopic_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/unauthorized/stream");
        accessor.setUser(createAuth(customerUser1.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Unauthorized topic destination"));
    }

    @Test
    @DisplayName("SUBSCRIBE without authenticated Principal throws AccessDeniedException")
    void subscribe_withoutAuthentication_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/track/" + shipment1.getTrackingNumber());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("authentication required"));
    }

    @Test
    @DisplayName("SEND directly to /topic/** throws AccessDeniedException (message spoofing protection)")
    void send_directlyToTopic_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/track/" + shipment1.getTrackingNumber());
        accessor.setUser(createAuth(customerUser1.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Direct publishing to topic destinations is forbidden"));
    }

    @Test
    @DisplayName("SEND without authenticated Principal throws AccessDeniedException")
    void send_withoutAuthentication_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/agent/location");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Authentication required to send messages"));
    }


    @Test
    @DisplayName("SUBSCRIBE to /topic/agent/{agentId}/orders by assigned delivery agent succeeds")
    void subscribe_toAgentOrdersTopic_byAssignedAgent_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/agent/" + agentUser.getId() + "/orders");
        accessor.setUser(createAuth(agentUser.getEmail(), "ROLE_DELIVERY_AGENT"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/agent/{agentId}/orders by another delivery agent throws AccessDeniedException")
    void subscribe_toAgentOrdersTopic_byDifferentAgent_rejected() {
        // Create second agent
        Role agentRole = roleRepository.findByName(RoleName.ROLE_DELIVERY_AGENT).orElseThrow();
        User secondAgent = userRepository.save(new User(
                "stomp.otheragent@example.com",
                passwordEncoder.encode("Password123!"),
                "Other Agent",
                "+1-555-0999",
                agentRole
        ));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        // Attempting to subscribe to agentUser's topic
        accessor.setDestination("/topic/agent/" + agentUser.getId() + "/orders");
        accessor.setUser(createAuth(secondAgent.getEmail(), "ROLE_DELIVERY_AGENT"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("not authorized to subscribe to another agent"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/agent/{agentId}/orders by customer throws AccessDeniedException")
    void subscribe_toAgentOrdersTopic_byCustomer_rejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/agent/" + agentUser.getId() + "/orders");
        accessor.setUser(createAuth(customerUser1.getEmail(), "ROLE_CUSTOMER"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, null));
        assertTrue(ex.getMessage().contains("Only assigned delivery agents may subscribe"));
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/agent/{agentId}/orders by admin succeeds")
    void subscribe_toAgentOrdersTopic_byAdmin_allowed() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/agent/" + agentUser.getId() + "/orders");
        accessor.setUser(createAuth(adminUser.getEmail(), "ROLE_ADMIN"));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);
        assertNotNull(result);
    }

    private UsernamePasswordAuthenticationToken createAuth(String email, String role) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("hash")
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .build();
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
