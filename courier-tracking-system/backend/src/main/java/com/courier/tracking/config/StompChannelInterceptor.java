package com.courier.tracking.config;

import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.User;
import com.courier.tracking.repository.UserRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.security.JwtTokenProvider;
import com.courier.tracking.service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbound STOMP channel interceptor enforcing JWT authentication on CONNECT frames,
 * customer ownership authorization on /topic/track/{trackingNumber},
 * administrator authorization on /topic/admin/fleet,
 * and spoofing protections against direct publishing to broadcast topics.
 */
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompChannelInterceptor.class);
    private static final Pattern TRACKING_TOPIC_PATTERN = Pattern.compile("^/topic/track/([^/]+)$");
    private static final String FLEET_TOPIC = "/topic/admin/fleet";
    private static final Pattern AGENT_ORDERS_TOPIC_PATTERN = Pattern.compile("^/topic/agent/([^/]+)/orders$");

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    public StompChannelInterceptor(JwtTokenProvider jwtTokenProvider,
                                   CustomUserDetailsService userDetailsService,
                                   ShipmentRepository shipmentRepository,
                                   UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
            default -> {
                // Other STOMP commands (DISCONNECT, UNSUBSCRIBE, etc.)
            }
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String token = extractJwt(accessor);
        if (!StringUtils.hasText(token)) {
            log.warn("STOMP CONNECT rejected: missing authentication token");
            throw new BadCredentialsException("Authentication required: missing token in CONNECT frame");
        }

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("STOMP CONNECT rejected: invalid or expired JWT token");
            throw new BadCredentialsException("Authentication failed: invalid or expired token");
        }

        String email = jwtTokenProvider.extractEmail(token);
        if (!StringUtils.hasText(email)) {
            log.warn("STOMP CONNECT rejected: JWT contains no subject email");
            throw new BadCredentialsException("Authentication failed: invalid token subject");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isEnabled()) {
            log.warn("STOMP CONNECT rejected: user account '{}' is disabled", email);
            throw new DisabledException("Account is deactivated");
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        accessor.setUser(authentication);
        log.info("STOMP connection established for user '{}' with authorities {}", email, userDetails.getAuthorities());
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            log.warn("STOMP SUBSCRIBE rejected: session is unauthenticated");
            throw new AccessDeniedException("Unauthorized subscription: authentication required");
        }

        Authentication authentication = (Authentication) principal;
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Invalid subscription: destination is required");
        }

        // 1. Check Admin Fleet Topic
        if (FLEET_TOPIC.equals(destination) || destination.startsWith("/topic/admin/")) {
            if (!hasRole(authentication, "ROLE_ADMIN")) {
                log.warn("Access denied: user '{}' without admin authority attempted subscription to fleet topic '{}'",
                        authentication.getName(), destination);
                throw new AccessDeniedException("Access denied: Only administrators may subscribe to the fleet topic");
            }
            log.info("Admin '{}' successfully subscribed to fleet topic '{}'", authentication.getName(), destination);
            return;
        }

        // 2. Check Customer Tracking Topic: /topic/track/{trackingNumber}
        Matcher matcher = TRACKING_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            String trackingNumber = matcher.group(1);
            authorizeTrackingTopicSubscription(authentication, trackingNumber);
            return;
        }

        // 3. Check Delivery Agent Orders Topic: /topic/agent/{agentId}/orders
        Matcher agentMatcher = AGENT_ORDERS_TOPIC_PATTERN.matcher(destination);
        if (agentMatcher.matches()) {
            String agentIdStr = agentMatcher.group(1);
            authorizeAgentOrdersSubscription(authentication, agentIdStr);
            return;
        }

        // 4. Reject any other unauthorized topic subscription
        if (destination.startsWith("/topic/")) {
            log.warn("Access denied: user '{}' attempted subscription to unrecognized topic '{}'",
                    authentication.getName(), destination);
            throw new AccessDeniedException("Access denied: Unauthorized topic destination");
        }
    }

    private void authorizeTrackingTopicSubscription(Authentication authentication, String trackingNumber) {
        Shipment shipment = shipmentRepository.findWithCustomerByTrackingNumber(trackingNumber)
                .orElseGet(() -> shipmentRepository.findByTrackingNumber(trackingNumber)
                        .orElseThrow(() -> {
                            log.warn("Access denied: tracking number '{}' not found for subscriber '{}'",
                                    trackingNumber, authentication.getName());
                            return new AccessDeniedException("Access denied: Shipment not found or unauthorized");
                        }));

        // Administrators are authorized to monitor any shipment topic
        if (hasRole(authentication, "ROLE_ADMIN")) {
            log.info("Admin '{}' authorized to subscribe to tracking topic for '{}'",
                    authentication.getName(), trackingNumber);
            return;
        }

        // Customers must own the shipment
        if (hasRole(authentication, "ROLE_CUSTOMER")) {
            if (shipment.getCustomer() != null &&
                    shipment.getCustomer().getEmail().equalsIgnoreCase(authentication.getName())) {
                log.info("Customer '{}' authorized to subscribe to their shipment topic for '{}'",
                        authentication.getName(), trackingNumber);
                return;
            }
            log.warn("Access denied: customer '{}' is not the owner of shipment '{}'",
                    authentication.getName(), trackingNumber);
            throw new AccessDeniedException("Access denied: You are not authorized to subscribe to tracking updates for this shipment");
        }

        // Delivery agents and all other roles are not authorized to subscribe to the customer topic
        log.warn("Access denied: user '{}' with authorities {} attempted subscription to customer topic for '{}'",
                authentication.getName(), authentication.getAuthorities(), trackingNumber);
        throw new AccessDeniedException("Access denied: Only authorized customers can subscribe to shipment tracking");
    }


    private void authorizeAgentOrdersSubscription(Authentication authentication, String agentIdStr) {
        Long requestedAgentId;
        try {
            requestedAgentId = Long.parseLong(agentIdStr);
        } catch (NumberFormatException e) {
            log.warn("Access denied: invalid agent id in destination '{}' for subscriber '{}'",
                    agentIdStr, authentication.getName());
            throw new AccessDeniedException("Access denied: Invalid agent ID destination");
        }

        // Administrators are authorized to monitor any agent order topic
        if (hasRole(authentication, "ROLE_ADMIN")) {
            log.info("Admin '{}' authorized to subscribe to agent orders topic for agent id={}",
                    authentication.getName(), requestedAgentId);
            return;
        }

        // Delivery agents must match the requestedAgentId
        if (hasRole(authentication, "ROLE_DELIVERY_AGENT")) {
            User user = userRepository.findByEmail(authentication.getName().toLowerCase(java.util.Locale.ROOT).trim())
                    .orElseThrow(() -> new AccessDeniedException("Access denied: Delivery agent account not found"));

            if (user.getId().equals(requestedAgentId)) {
                log.info("Delivery agent '{}' (id={}) authorized to subscribe to their orders topic",
                        authentication.getName(), requestedAgentId);
                return;
            }

            log.warn("Access denied: delivery agent '{}' (id={}) attempted subscription to agent id={}'s order topic",
                    authentication.getName(), user.getId(), requestedAgentId);
            throw new AccessDeniedException("Access denied: You are not authorized to subscribe to another agent's assignment notifications");
        }

        // Customers and other roles are rejected
        log.warn("Access denied: user '{}' with authorities {} attempted subscription to agent orders topic for id={}",
                authentication.getName(), authentication.getAuthorities(), requestedAgentId);
        throw new AccessDeniedException("Access denied: Only assigned delivery agents may subscribe to order notifications");
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith("/topic/")) {
            log.warn("Message spoofing rejected: client attempted direct SEND to topic destination '{}'", destination);
            throw new AccessDeniedException("Direct publishing to topic destinations is forbidden");
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            log.warn("STOMP SEND rejected: session is unauthenticated");
            throw new AccessDeniedException("Authentication required to send messages");
        }
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    private String extractJwt(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(authHeader)) {
            String trimmed = authHeader.trim();
            if (trimmed.startsWith("Bearer ")) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (StringUtils.hasText(tokenHeader)) {
            return tokenHeader.trim();
        }
        String passcode = accessor.getPasscode();
        if (StringUtils.hasText(passcode)) {
            return passcode.trim();
        }
        return null;
    }
}
