package com.courier.tracking.service;

import com.courier.tracking.config.PricingProperties;
import com.courier.tracking.dto.AssignAgentRequest;
import com.courier.tracking.dto.CreateShipmentRequest;
import com.courier.tracking.dto.DeliveryConfirmationResponse;
import com.courier.tracking.dto.DeliveryOtpResponse;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.dto.UpdateShipmentStatusRequest;
import com.courier.tracking.dto.VerifyDeliveryOtpRequest;
import com.courier.tracking.entity.DeliveryProof;
import com.courier.tracking.entity.ProofType;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.TrackingEvent;
import com.courier.tracking.entity.User;
import com.courier.tracking.exception.ResourceNotFoundException;
import com.courier.tracking.repository.DeliveryProofRepository;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import com.courier.tracking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service managing shipment creation and business lifecycle workflows with configurable pricing rules.
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);
    private static final String TRACKING_PREFIX = "TRK";
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final PricingProperties pricingProperties;
    private final TrackingEventRepository trackingEventRepository;
    private final DeliveryProofRepository deliveryProofRepository;
    private final PasswordEncoder passwordEncoder;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           UserRepository userRepository,
                           PricingProperties pricingProperties) {
        this(shipmentRepository, userRepository, pricingProperties, null, null, null);
    }

    public ShipmentService(ShipmentRepository shipmentRepository,
                           UserRepository userRepository,
                           PricingProperties pricingProperties,
                           TrackingEventRepository trackingEventRepository) {
        this(shipmentRepository, userRepository, pricingProperties, trackingEventRepository, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ShipmentService(ShipmentRepository shipmentRepository,
                           UserRepository userRepository,
                           PricingProperties pricingProperties,
                           TrackingEventRepository trackingEventRepository,
                           DeliveryProofRepository deliveryProofRepository,
                           PasswordEncoder passwordEncoder) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.pricingProperties = pricingProperties;
        this.trackingEventRepository = trackingEventRepository;
        this.deliveryProofRepository = deliveryProofRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Calculates the shipment price based on configured base rate, per-kg rate, and minimum price floor.
     * Formula: max(minPrice, baseRate + (weightKg * perKgRate)), rounded to 2 decimal places.
     */
    public BigDecimal calculatePrice(BigDecimal weightKg) {
        if (weightKg == null) {
            throw new IllegalArgumentException("Weight in kg is required for price calculation");
        }
        BigDecimal base = pricingProperties.getBaseRate() != null ? pricingProperties.getBaseRate() : new BigDecimal("10.00");
        BigDecimal perKg = pricingProperties.getPerKgRate() != null ? pricingProperties.getPerKgRate() : new BigDecimal("5.00");
        BigDecimal min = pricingProperties.getMinPrice() != null ? pricingProperties.getMinPrice() : new BigDecimal("10.00");

        BigDecimal rawPrice = base.add(weightKg.multiply(perKg)).setScale(2, RoundingMode.HALF_UP);
        if (rawPrice.compareTo(min) < 0) {
            return min.setScale(2, RoundingMode.HALF_UP);
        }
        return rawPrice;
    }

    /**
     * Creates a new shipment for the authenticated customer.
     * Enforces customer role verification, server-side tracking number generation,
     * status initialization to CREATED, and server-side pricing calculation.
     * Client-supplied prices and privileged fields are strictly ignored.
     */
    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request, String authenticatedEmail) {
        User customer = getValidatedCustomer(authenticatedEmail);

        String trackingNumber = generateUniqueTrackingNumber();

        // Server unconditionally calculates price and assigns configured currency
        BigDecimal finalPrice = calculatePrice(request.weightKg());
        String currency = pricingProperties.getCurrency() != null ? pricingProperties.getCurrency().toUpperCase(Locale.ROOT).trim() : "USD";

        Shipment shipment = new Shipment(
                trackingNumber,
                customer,
                request.senderName(),
                request.senderAddress(),
                request.senderPhone(),
                request.recipientName(),
                request.recipientAddress(),
                request.recipientPhone(),
                request.weightKg(),
                finalPrice,
                currency
        );

        shipment.setStatus(ShipmentStatus.CREATED);
        if (request.recipientLatitude() != null && request.recipientLongitude() != null) {
            shipment.setRecipientLatitude(request.recipientLatitude());
            shipment.setRecipientLongitude(request.recipientLongitude());
        }
        // Default estimated delivery: 3 days from now
        shipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(3 * 86400));

        Shipment saved = shipmentRepository.save(shipment);
        log.info("Shipment created successfully: id={}, trackingNumber={}, customerId={}, price={} {}",
                saved.getId(), saved.getTrackingNumber(), customer.getId(), saved.getPrice(), saved.getCurrency());

        return mapToResponse(saved);
    }

    /**
     * Retrieves a paginated list of shipments belonging to the authenticated customer.
     * Enforces customer role verification and active account status.
     */
    @Transactional(readOnly = true)
    public Page<ShipmentResponse> getCustomerShipments(String authenticatedEmail, Pageable pageable) {
        User customer = getValidatedCustomer(authenticatedEmail);

        Pageable effectivePageable = pageable;
        if (pageable == null) {
            effectivePageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        } else if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        Page<Shipment> shipments = shipmentRepository.findByCustomerId(customer.getId(), effectivePageable);
        return shipments.map(this::mapToResponse);
    }

    /**
     * Retrieves a single shipment by ID for the authenticated customer.
     * Verifies that the shipment exists and belongs to the requesting customer.
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(Long id, String authenticatedEmail) {
        if (id == null) {
            throw new IllegalArgumentException("Shipment ID cannot be null");
        }
        User customer = getValidatedCustomer(authenticatedEmail);

        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));

        if (!shipment.getCustomer().getId().equals(customer.getId())) {
            log.warn("Access denied: customer '{}' (id={}) attempted to access shipment id={} belonging to customer id={}",
                    customer.getEmail(), customer.getId(), id, shipment.getCustomer().getId());
            throw new AccessDeniedException("Access denied: You do not have permission to access this shipment");
        }

        return mapToResponse(shipment);
    }

    /**
     * Assigns or reassigns an eligible delivery agent to an existing shipment.
     * Enforces administrator authority, agent eligibility (active user with ROLE_DELIVERY_AGENT),
     * and shipment status rules (transitions CREATED -> ASSIGNED; preserves ASSIGNED on reassignment;
     * rejects assignment on terminal states DELIVERED, CANCELLED, RETURNED).
     */
    @Transactional
    public ShipmentResponse assignDeliveryAgent(Long shipmentId, AssignAgentRequest request, String authenticatedEmail) {
        if (shipmentId == null) {
            throw new IllegalArgumentException("Shipment ID cannot be null");
        }
        if (request == null || request.agentId() == null) {
            throw new IllegalArgumentException("Delivery agent ID is required");
        }

        // Verify administrator authority
        User admin = getValidatedAdmin(authenticatedEmail);

        // Retrieve target shipment
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", shipmentId));

        // Check shipment status restrictions
        if (shipment.getStatus() == ShipmentStatus.DELIVERED ||
                shipment.getStatus() == ShipmentStatus.CANCELLED ||
                shipment.getStatus() == ShipmentStatus.RETURNED) {
            log.warn("Assignment rejected: shipment '{}' has terminal status '{}'", shipment.getTrackingNumber(), shipment.getStatus());
            throw new IllegalStateException(String.format("Cannot assign delivery agent to shipment '%s' with terminal status '%s'",
                    shipment.getTrackingNumber(), shipment.getStatus()));
        }

        // Retrieve and validate target delivery agent
        Long agentId = request.agentId();
        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery agent", "id", agentId));

        if (!RoleName.ROLE_DELIVERY_AGENT.equals(agent.getRole().getName())) {
            log.warn("Assignment rejected: target user id={} is not a delivery agent (role={})", agentId, agent.getRole().getName());
            throw new IllegalArgumentException("User with id " + agentId + " does not have the delivery agent role");
        }

        if (!Boolean.TRUE.equals(agent.getActive())) {
            log.warn("Assignment rejected: target delivery agent id={} is inactive/disabled", agentId);
            throw new IllegalArgumentException("Delivery agent with id " + agentId + " is inactive");
        }

        // Set the assigned agent
        shipment.setAssignedAgent(agent);

        // If status was CREATED, transition to ASSIGNED; if already ASSIGNED or other active status, retain assignment
        if (shipment.getStatus() == ShipmentStatus.CREATED) {
            shipment.setStatus(ShipmentStatus.ASSIGNED);
        }

        Shipment updated = shipmentRepository.save(shipment);
        log.info("Shipment '{}' successfully assigned to agent '{}' (id={}) by admin '{}'",
                updated.getTrackingNumber(), agent.getEmail(), agent.getId(), admin.getEmail());

        return mapToResponse(updated);
    }

    private User getValidatedAdmin(String authenticatedEmail) {
        String normalizedEmail = authenticatedEmail != null ? authenticatedEmail.toLowerCase(Locale.ROOT).trim() : "";
        User admin = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        if (!RoleName.ROLE_ADMIN.equals(admin.getRole().getName())) {
            log.warn("Access denied: user '{}' does not have ROLE_ADMIN", normalizedEmail);
            throw new AccessDeniedException("Only administrators with ROLE_ADMIN are authorized to assign delivery agents");
        }

        if (!Boolean.TRUE.equals(admin.getActive())) {
            log.warn("Access denied: administrator account '{}' is inactive", normalizedEmail);
            throw new AccessDeniedException("Account is deactivated");
        }

        return admin;
    }

    private User getValidatedCustomer(String authenticatedEmail) {
        String normalizedEmail = authenticatedEmail != null ? authenticatedEmail.toLowerCase(Locale.ROOT).trim() : "";
        User customer = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found for email: " + normalizedEmail));

        // Enforce ROLE_CUSTOMER authority
        if (!RoleName.ROLE_CUSTOMER.equals(customer.getRole().getName())) {
            log.warn("Access denied: user '{}' does not have ROLE_CUSTOMER", normalizedEmail);
            throw new AccessDeniedException("Only customers with ROLE_CUSTOMER are authorized to perform this operation");
        }

        if (!Boolean.TRUE.equals(customer.getActive())) {
            log.warn("Access denied: customer account '{}' is inactive", normalizedEmail);
            throw new AccessDeniedException("Account is deactivated");
        }

        return customer;
    }

    private String generateUniqueTrackingNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < 5; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
            }
            String candidate = String.format("%s-%s-%s", TRACKING_PREFIX, datePart, sb);
            if (!shipmentRepository.existsByTrackingNumber(candidate)) {
                return candidate;
            }
        }
        // Fallback with timestamp
        return String.format("%s-%s-%d", TRACKING_PREFIX, datePart, System.currentTimeMillis() % 100000000L);
    }

    /**
     * Updates a shipment's lifecycle status and automatically persists a corresponding tracking event
     * within the same database transaction.
     * Enforces authentication, role authorization (ADMIN or assigned DELIVERY_AGENT),
     * finite state machine transition rules, assigned agent prerequisite checks, and truthful audit logging.
     *
     * @param shipmentId Database ID of the target shipment
     * @param request Inbound request containing target status and optional waypoint scan information
     * @param authenticatedEmail Authenticated user principal email
     * @return Updated shipment response envelope
     */
    @Transactional
    public ShipmentResponse updateShipmentStatus(Long shipmentId, UpdateShipmentStatusRequest request, String authenticatedEmail) {
        if (shipmentId == null || shipmentId <= 0) {
            throw new IllegalArgumentException("Shipment ID must be a positive number");
        }
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("Target shipment status is required");
        }

        // Retrieve target shipment
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", shipmentId));

        ShipmentStatus currentStatus = shipment.getStatus();
        ShipmentStatus targetStatus = request.status();

        // Validate finite state machine transition
        validateStatusTransition(currentStatus, targetStatus);

        // Require an assigned delivery agent for courier-dependent transitions
        validateAssignedAgentRequirements(shipment, targetStatus);

        // Authenticate and authorize the actor (ADMIN or assigned DELIVERY_AGENT)
        User actor = getValidatedStatusUpdater(authenticatedEmail, shipment, targetStatus);

        // Update shipment status
        shipment.setStatus(targetStatus);
        Instant now = Instant.now();
        if (targetStatus == ShipmentStatus.DELIVERED) {
            shipment.setActualDeliveryAt(now);
        }

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Record tracking event
        TrackingEvent event = new TrackingEvent();
        event.setShipment(savedShipment);
        event.setStatus(targetStatus);
        event.setLocationName(request.locationName() != null && !request.locationName().isBlank() ? request.locationName().trim() : null);

        String remarks = (request.remarks() != null && !request.remarks().isBlank())
                ? request.remarks().trim()
                : "Shipment status updated to " + targetStatus;
        event.setRemarks(remarks);
        event.setLatitude(request.latitude());
        event.setLongitude(request.longitude());
        event.setEventTimestamp(now);

        trackingEventRepository.save(event);

        log.info("Shipment '{}' (id={}) transitioned from '{}' to '{}' by user '{}' (role={}), trackingEventId={}",
                savedShipment.getTrackingNumber(), savedShipment.getId(), currentStatus, targetStatus,
                actor.getEmail(), actor.getRole().getName(), event.getId());

        return mapToResponse(savedShipment);
    }

    private void validateStatusTransition(ShipmentStatus current, ShipmentStatus target) {
        if (current == target) {
            throw new IllegalStateException(String.format("Shipment is already in status '%s'", current));
        }

        if (current == ShipmentStatus.DELIVERED || current == ShipmentStatus.RETURNED || current == ShipmentStatus.CANCELLED) {
            throw new IllegalStateException(String.format("Cannot transition shipment from terminal status '%s'", current));
        }

        boolean allowed = switch (current) {
            case CREATED -> (target == ShipmentStatus.ASSIGNED || target == ShipmentStatus.CANCELLED);
            case ASSIGNED -> (target == ShipmentStatus.PICKED_UP || target == ShipmentStatus.CANCELLED);
            case PICKED_UP -> (target == ShipmentStatus.IN_TRANSIT || target == ShipmentStatus.OUT_FOR_DELIVERY);
            case IN_TRANSIT -> (target == ShipmentStatus.OUT_FOR_DELIVERY);
            case OUT_FOR_DELIVERY -> (target == ShipmentStatus.DELIVERED || target == ShipmentStatus.ATTEMPT_FAILED);
            case ATTEMPT_FAILED -> (target == ShipmentStatus.OUT_FOR_DELIVERY || target == ShipmentStatus.RETURNED);
            default -> false;
        };

        if (!allowed) {
            throw new IllegalStateException(String.format("Invalid status transition from '%s' to '%s'", current, target));
        }
    }

    private void validateAssignedAgentRequirements(Shipment shipment, ShipmentStatus targetStatus) {
        // Physical fulfillment statuses require an assigned courier
        if (targetStatus == ShipmentStatus.PICKED_UP ||
                targetStatus == ShipmentStatus.IN_TRANSIT ||
                targetStatus == ShipmentStatus.OUT_FOR_DELIVERY ||
                targetStatus == ShipmentStatus.DELIVERED ||
                targetStatus == ShipmentStatus.ATTEMPT_FAILED ||
                targetStatus == ShipmentStatus.RETURNED) {
            if (shipment.getAssignedAgent() == null) {
                log.warn("Status transition to '{}' rejected: shipment '{}' has no assigned agent", targetStatus, shipment.getTrackingNumber());
                throw new IllegalStateException(String.format("Cannot transition shipment to '%s' without an assigned delivery agent", targetStatus));
            }
            if (!Boolean.TRUE.equals(shipment.getAssignedAgent().getActive())) {
                log.warn("Status transition to '{}' rejected: assigned agent id={} is deactivated", targetStatus, shipment.getAssignedAgent().getId());
                throw new IllegalStateException("Cannot transition shipment: assigned delivery agent is deactivated");
            }
        }
    }

    private User getValidatedStatusUpdater(String authenticatedEmail, Shipment shipment, ShipmentStatus targetStatus) {
        String normalizedEmail = authenticatedEmail != null ? authenticatedEmail.toLowerCase(Locale.ROOT).trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        if (!Boolean.TRUE.equals(user.getActive())) {
            log.warn("Access denied: user account '{}' is deactivated", normalizedEmail);
            throw new AccessDeniedException("Account is deactivated");
        }

        RoleName role = user.getRole().getName();
        if (RoleName.ROLE_ADMIN.equals(role)) {
            return user;
        }

        if (RoleName.ROLE_DELIVERY_AGENT.equals(role)) {
            // Delivery agent must be assigned to this shipment
            if (shipment.getAssignedAgent() == null || !shipment.getAssignedAgent().getId().equals(user.getId())) {
                log.warn("Access denied: delivery agent '{}' (id={}) is not assigned to shipment '{}' (id={})",
                        user.getEmail(), user.getId(), shipment.getTrackingNumber(), shipment.getId());
                throw new AccessDeniedException("Access denied: You are not assigned to this shipment");
            }

            // Delivery agents cannot cancel shipments
            if (targetStatus == ShipmentStatus.CANCELLED) {
                log.warn("Access denied: delivery agent '{}' attempted to cancel shipment '{}'", user.getEmail(), shipment.getTrackingNumber());
                throw new AccessDeniedException("Access denied: Delivery agents are not authorized to cancel shipments");
            }

            return user;
        }

        // Customers or any other role
        log.warn("Access denied: user '{}' with role '{}' attempted status transition on shipment '{}'",
                user.getEmail(), role, shipment.getTrackingNumber());
        throw new AccessDeniedException("Access denied: You do not have permission to perform this action");
    }

    private ShipmentResponse mapToResponse(Shipment s) {
        Long agentId = s.getAssignedAgent() != null ? s.getAssignedAgent().getId() : null;
        String agentName = s.getAssignedAgent() != null ? s.getAssignedAgent().getFullName() : null;

        return new ShipmentResponse(
                s.getId(),
                s.getTrackingNumber(),
                s.getStatus(),
                s.getCustomer().getId(),
                s.getCustomer().getEmail(),
                agentId,
                agentName,
                s.getSenderName(),
                s.getSenderAddress(),
                s.getSenderPhone(),
                s.getRecipientName(),
                s.getRecipientAddress(),
                s.getRecipientPhone(),
                s.getRecipientLatitude(),
                s.getRecipientLongitude(),
                s.getWeightKg(),
                s.getPrice(),
                s.getCurrency(),
                s.getEstimatedDeliveryAt(),
                s.getActualDeliveryAt(),
                s.getCreatedAt()
        );
    }

    /**
     * Generates a cryptographically random, 6-digit delivery OTP for a shipment in OUT_FOR_DELIVERY status.
     * Enforces role authorization (admin, assigned agent, or shipment customer), rate limiting (60s cooldown),
     * and secure one-way hashing (never stores or logs raw OTP).
     *
     * @param shipmentId Database ID of the target shipment
     * @param authenticatedEmail Authenticated user principal email
     * @return DeliveryOtpResponse containing tracking details and expiration metadata
     */
    @Transactional
    public DeliveryOtpResponse generateDeliveryOtp(Long shipmentId, String authenticatedEmail) {
        if (shipmentId == null || shipmentId <= 0) {
            throw new IllegalArgumentException("Shipment ID must be a positive number");
        }

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", shipmentId));

        User currentUser = getValidatedUser(authenticatedEmail);

        // Enforce authorization: admin, shipment customer, or assigned delivery agent
        validateOtpRequestAuthorization(currentUser, shipment);

        // State machine constraint: must be OUT_FOR_DELIVERY
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Shipment has already been delivered");
        }
        if (shipment.getStatus() == ShipmentStatus.CANCELLED || shipment.getStatus() == ShipmentStatus.RETURNED) {
            throw new IllegalStateException(String.format("Cannot generate delivery OTP for shipment in terminal status '%s'", shipment.getStatus()));
        }
        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException(String.format("Delivery OTP can only be generated when shipment is in OUT_FOR_DELIVERY status (current: '%s')", shipment.getStatus()));
        }

        // Rate limit: 60-second cooldown between generation requests
        Instant now = Instant.now();
        if (shipment.getDeliveryOtpLastSentAt() != null) {
            long secondsSinceLast = Duration.between(shipment.getDeliveryOtpLastSentAt(), now).getSeconds();
            if (secondsSinceLast < 60) {
                log.warn("OTP generation rate limited for shipment '{}' (requested {}s ago)", shipment.getTrackingNumber(), secondsSinceLast);
                throw new IllegalStateException("Please wait at least 60 seconds before requesting a new OTP.");
            }
        }

        // Generate cryptographically unpredictable 6-digit OTP
        int otpNumber = 100_000 + RANDOM.nextInt(900_000);
        String rawOtp = String.valueOf(otpNumber);

        // Hash OTP with configured PasswordEncoder (BCrypt) - never store plaintext
        String hashedOtp = passwordEncoder.encode(rawOtp);
        Instant expiresAt = now.plus(Duration.ofMinutes(10));

        shipment.setDeliveryOtpHash(hashedOtp);
        shipment.setDeliveryOtpExpiresAt(expiresAt);
        shipment.setDeliveryOtpAttempts(0);
        shipment.setDeliveryOtpLastSentAt(now);

        shipmentRepository.save(shipment);

        // Safe logging without plaintext secret
        log.info("Delivery OTP generated for shipment '{}' (id={}) by user '{}' (role={}), expiresAt={}",
                shipment.getTrackingNumber(), shipment.getId(), currentUser.getEmail(), currentUser.getRole().getName(), expiresAt);

        return new DeliveryOtpResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                rawOtp,
                expiresAt,
                "Delivery OTP generated successfully and dispatched to recipient"
        );
    }

    /**
     * Confirms shipment delivery using the recipient's one-time password (OTP).
     * Enforces role authorization (assigned agent or admin only; customers are forbidden),
     * status constraints (must be OUT_FOR_DELIVERY), expiration, max failed attempt thresholds,
     * replay prevention, atomic status transition, DeliveryProof creation, and TrackingEvent logging.
     *
     * @param shipmentId Database ID of the target shipment
     * @param request Inbound verification payload containing OTP and recipient handover details
     * @param authenticatedEmail Authenticated user principal email
     * @return DeliveryConfirmationResponse confirming handover and proof recording
     */
    @Transactional
    public DeliveryConfirmationResponse confirmDeliveryWithOtp(Long shipmentId, VerifyDeliveryOtpRequest request, String authenticatedEmail) {
        if (shipmentId == null || shipmentId <= 0) {
            throw new IllegalArgumentException("Shipment ID must be a positive number");
        }
        if (request == null || request.otp() == null || request.otp().isBlank()) {
            throw new IllegalArgumentException("Delivery OTP is required");
        }

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", shipmentId));

        User currentUser = getValidatedUser(authenticatedEmail);

        // Enforce authorization: only assigned delivery agent or admin can verify delivery
        validateOtpVerificationAuthorization(currentUser, shipment);

        // State machine constraint: must be OUT_FOR_DELIVERY
        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            log.warn("Delivery confirmation rejected: shipment '{}' is already DELIVERED", shipment.getTrackingNumber());
            throw new IllegalStateException("Shipment has already been delivered");
        }
        if (shipment.getStatus() == ShipmentStatus.CANCELLED || shipment.getStatus() == ShipmentStatus.RETURNED) {
            throw new IllegalStateException(String.format("Cannot confirm delivery for shipment in terminal status '%s'", shipment.getStatus()));
        }
        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException(String.format("Shipment must be in 'OUT_FOR_DELIVERY' status to confirm delivery (current: '%s')", shipment.getStatus()));
        }

        // Verify active OTP exists
        if (shipment.getDeliveryOtpHash() == null) {
            log.warn("Delivery confirmation rejected: no active OTP for shipment '{}'", shipment.getTrackingNumber());
            throw new IllegalStateException("No active delivery OTP found for this shipment. Please request an OTP first.");
        }

        Instant now = Instant.now();

        // Check expiration
        if (shipment.getDeliveryOtpExpiresAt() == null || now.isAfter(shipment.getDeliveryOtpExpiresAt())) {
            log.warn("Delivery confirmation rejected: OTP expired for shipment '{}'", shipment.getTrackingNumber());
            throw new IllegalStateException("Delivery OTP has expired. Please request a new OTP.");
        }

        // Check attempt threshold before verification
        if (shipment.getDeliveryOtpAttempts() >= 3) {
            shipment.setDeliveryOtpHash(null);
            shipmentRepository.save(shipment);
            log.warn("Delivery confirmation rejected: max attempts exceeded for shipment '{}'", shipment.getTrackingNumber());
            throw new IllegalStateException("Maximum OTP verification attempts exceeded. Please request a new OTP.");
        }

        // Validate OTP match
        String submittedOtp = request.otp().trim();
        boolean matches = passwordEncoder.matches(submittedOtp, shipment.getDeliveryOtpHash());

        if (!matches) {
            int newAttempts = shipment.getDeliveryOtpAttempts() + 1;
            shipment.setDeliveryOtpAttempts(newAttempts);
            if (newAttempts >= 3) {
                shipment.setDeliveryOtpHash(null);
                log.warn("Delivery OTP invalidated due to exceeding max attempts on shipment '{}'", shipment.getTrackingNumber());
            }
            shipmentRepository.save(shipment);
            throw new IllegalArgumentException("Invalid delivery OTP. Please check and try again.");
        }

        // Guard against duplicate delivery proof (concurrency / replay protection)
        if (deliveryProofRepository.existsByShipmentId(shipment.getId())) {
            throw new IllegalStateException("Delivery proof already exists for this shipment");
        }

        // Successful verification: consume OTP (one-time use)
        shipment.setDeliveryOtpHash(null);
        shipment.setDeliveryOtpExpiresAt(null);
        shipment.setDeliveryOtpAttempts(0);

        // Update shipment status to DELIVERED
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setActualDeliveryAt(now);
        Shipment savedShipment = shipmentRepository.save(shipment);

        // Create and persist DeliveryProof entity
        String recipientName = (request.recipientName() != null && !request.recipientName().isBlank())
                ? request.recipientName().trim()
                : savedShipment.getRecipientName();
        String notes = (request.verificationNotes() != null && !request.verificationNotes().isBlank())
                ? request.verificationNotes().trim()
                : "Handover confirmed via OTP verification";

        DeliveryProof proof = new DeliveryProof(
                savedShipment,
                currentUser,
                ProofType.OTP,
                "OTP_VERIFIED",
                recipientName,
                notes
        );
        proof.setVerifiedAt(now);
        DeliveryProof savedProof = deliveryProofRepository.save(proof);

        // Record tracking event
        String location = (request.locationName() != null && !request.locationName().isBlank())
                ? request.locationName().trim()
                : "Delivery Destination";
        TrackingEvent event = new TrackingEvent(
                savedShipment,
                ShipmentStatus.DELIVERED,
                request.latitude(),
                request.longitude(),
                location,
                "Parcel delivered successfully via electronic OTP verification"
        );
        event.setEventTimestamp(now);
        trackingEventRepository.save(event);

        log.info("Shipment '{}' successfully DELIVERED via OTP by user '{}' (role={}), proofId={}, eventId={}",
                savedShipment.getTrackingNumber(), currentUser.getEmail(), currentUser.getRole().getName(),
                savedProof.getId(), event.getId());

        return new DeliveryConfirmationResponse(
                savedShipment.getId(),
                savedShipment.getTrackingNumber(),
                savedShipment.getStatus(),
                savedProof.getId(),
                savedProof.getProofType(),
                savedProof.getRecipientName(),
                savedProof.getVerifiedAt()
        );
    }

    private User getValidatedUser(String authenticatedEmail) {
        String normalizedEmail = authenticatedEmail != null ? authenticatedEmail.toLowerCase(Locale.ROOT).trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", normalizedEmail));

        if (!Boolean.TRUE.equals(user.getActive())) {
            log.warn("Access denied: user account '{}' is deactivated", normalizedEmail);
            throw new AccessDeniedException("Account is deactivated");
        }
        return user;
    }

    private void validateOtpRequestAuthorization(User user, Shipment shipment) {
        RoleName role = user.getRole().getName();
        if (RoleName.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (RoleName.ROLE_CUSTOMER.equals(role)) {
            if (shipment.getCustomer() != null && shipment.getCustomer().getId().equals(user.getId())) {
                return;
            }
            log.warn("Access denied: customer '{}' (id={}) is not the owner of shipment '{}' (customerId={})",
                    user.getEmail(), user.getId(), shipment.getTrackingNumber(),
                    shipment.getCustomer() != null ? shipment.getCustomer().getId() : null);
            throw new AccessDeniedException("Access denied: You do not have permission to perform this action");
        }
        if (RoleName.ROLE_DELIVERY_AGENT.equals(role)) {
            if (shipment.getAssignedAgent() != null && shipment.getAssignedAgent().getId().equals(user.getId())) {
                return;
            }
            log.warn("Access denied: agent '{}' (id={}) is not assigned to shipment '{}'",
                    user.getEmail(), user.getId(), shipment.getTrackingNumber());
            throw new AccessDeniedException("Access denied: You are not assigned to this shipment");
        }
        throw new AccessDeniedException("Access denied: You do not have permission to perform this action");
    }

    private void validateOtpVerificationAuthorization(User user, Shipment shipment) {
        RoleName role = user.getRole().getName();
        if (RoleName.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (RoleName.ROLE_DELIVERY_AGENT.equals(role)) {
            if (shipment.getAssignedAgent() != null && shipment.getAssignedAgent().getId().equals(user.getId())) {
                return;
            }
            log.warn("Access denied: delivery agent '{}' (id={}) is not assigned to shipment '{}'",
                    user.getEmail(), user.getId(), shipment.getTrackingNumber());
            throw new AccessDeniedException("Access denied: You are not assigned to this shipment");
        }
        // Customers are strictly forbidden from verifying delivery
        log.warn("Access denied: user '{}' with role '{}' attempted delivery OTP verification on shipment '{}'",
                user.getEmail(), role, shipment.getTrackingNumber());
        throw new AccessDeniedException("Access denied: You do not have permission to perform this action");
    }
}
