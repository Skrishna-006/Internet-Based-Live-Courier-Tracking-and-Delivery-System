package com.courier.tracking.service;

import com.courier.tracking.config.PricingProperties;
import com.courier.tracking.dto.CreateShipmentRequest;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.User;
import com.courier.tracking.exception.ResourceNotFoundException;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
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

    public ShipmentService(ShipmentRepository shipmentRepository,
                           UserRepository userRepository,
                           PricingProperties pricingProperties) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.pricingProperties = pricingProperties;
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

    private ShipmentResponse mapToResponse(Shipment s) {
        return new ShipmentResponse(
                s.getId(),
                s.getTrackingNumber(),
                s.getStatus(),
                s.getCustomer().getId(),
                s.getCustomer().getEmail(),
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
                s.getCreatedAt()
        );
    }
}
