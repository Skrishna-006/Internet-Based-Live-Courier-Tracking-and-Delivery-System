package com.courier.tracking.service;

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
 * Service managing shipment creation and business lifecycle workflows.
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);
    private static final String TRACKING_PREFIX = "TRK";
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    public ShipmentService(ShipmentRepository shipmentRepository, UserRepository userRepository) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new shipment for the authenticated customer.
     * Enforces customer role verification, server-side tracking number generation,
     * status initialization to CREATED, and price calculation if not provided.
     */
    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request, String authenticatedEmail) {
        String normalizedEmail = authenticatedEmail != null ? authenticatedEmail.toLowerCase(Locale.ROOT).trim() : "";
        User customer = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found for email: " + normalizedEmail));

        // Enforce ROLE_CUSTOMER authority
        if (!RoleName.ROLE_CUSTOMER.equals(customer.getRole().getName())) {
            log.warn("Shipment creation denied: user '{}' does not have ROLE_CUSTOMER", normalizedEmail);
            throw new AccessDeniedException("Only customers with ROLE_CUSTOMER are authorized to create shipments");
        }

        if (!Boolean.TRUE.equals(customer.getActive())) {
            log.warn("Shipment creation denied: customer account '{}' is inactive", normalizedEmail);
            throw new AccessDeniedException("Account is deactivated");
        }

        String trackingNumber = generateUniqueTrackingNumber();

        // Calculate standard rate if not provided: base rate $10.00 + $5.00/kg
        BigDecimal finalPrice = request.price();
        if (finalPrice == null) {
            finalPrice = calculateStandardRate(request.weightKg());
        }

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
                finalPrice
        );

        shipment.setStatus(ShipmentStatus.CREATED);
        if (request.recipientLatitude() != null && request.recipientLongitude() != null) {
            shipment.setRecipientLatitude(request.recipientLatitude());
            shipment.setRecipientLongitude(request.recipientLongitude());
        }
        // Default estimated delivery: 3 days from now
        shipment.setEstimatedDeliveryAt(Instant.now().plusSeconds(3 * 86400));

        Shipment saved = shipmentRepository.save(shipment);
        log.info("Shipment created successfully: id={}, trackingNumber={}, customerId={}",
                saved.getId(), saved.getTrackingNumber(), customer.getId());

        return mapToResponse(saved);
    }

    private BigDecimal calculateStandardRate(BigDecimal weightKg) {
        BigDecimal baseRate = new BigDecimal("10.00");
        BigDecimal perKgRate = new BigDecimal("5.00");
        return baseRate.add(weightKg.multiply(perKgRate)).setScale(2, RoundingMode.HALF_UP);
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
                s.getEstimatedDeliveryAt(),
                s.getCreatedAt()
        );
    }
}
