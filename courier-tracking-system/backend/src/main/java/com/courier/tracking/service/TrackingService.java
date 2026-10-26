package com.courier.tracking.service;

import com.courier.tracking.dto.PublicTrackingMilestoneDto;
import com.courier.tracking.dto.PublicTrackingResponse;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.TrackingEvent;
import com.courier.tracking.exception.ResourceNotFoundException;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service providing public, unauthenticated shipment tracking and milestone summaries.
 * Strictly guarantees that sensitive customer PII, courier contact details, and precise coordinates are not exposed.
 */
@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);
    private static final int MAX_TRACKING_NUMBER_LENGTH = 64;
    private static final Pattern VALID_TRACKING_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final ShipmentRepository shipmentRepository;
    private final TrackingEventRepository trackingEventRepository;

    public TrackingService(ShipmentRepository shipmentRepository,
                           TrackingEventRepository trackingEventRepository) {
        this.shipmentRepository = shipmentRepository;
        this.trackingEventRepository = trackingEventRepository;
    }

    /**
     * Retrieves a sanitized public tracking summary for the specified tracking number.
     *
     * @param trackingNumber The public parcel tracking identifier
     * @return Sanitized tracking summary with public-safe milestones
     * @throws IllegalArgumentException if tracking number is blank, too long, or malformed
     * @throws ResourceNotFoundException if no shipment matches the tracking number
     */
    @Transactional(readOnly = true)
    public PublicTrackingResponse getPublicTrackingSummary(String trackingNumber) {
        validateTrackingNumberInput(trackingNumber);

        String normalizedTrackingNumber = trackingNumber.trim().toUpperCase(Locale.ROOT);

        Shipment shipment = shipmentRepository.findByTrackingNumber(normalizedTrackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "trackingNumber", normalizedTrackingNumber));

        List<TrackingEvent> events = trackingEventRepository
                .findByShipmentTrackingNumberOrderByEventTimestampAsc(normalizedTrackingNumber);

        List<PublicTrackingMilestoneDto> milestones = events.stream()
                .map(event -> new PublicTrackingMilestoneDto(
                        event.getStatus(),
                        event.getLocationName(),
                        event.getRemarks(),
                        event.getEventTimestamp()
                ))
                .toList();

        log.debug("Public tracking summary served for tracking number: {}", normalizedTrackingNumber);

        return new PublicTrackingResponse(
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                shipment.getEstimatedDeliveryAt(),
                shipment.getActualDeliveryAt(),
                shipment.getCreatedAt(),
                milestones
        );
    }

    private void validateTrackingNumberInput(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number must not be blank");
        }

        String trimmed = trackingNumber.trim();
        if (trimmed.length() > MAX_TRACKING_NUMBER_LENGTH) {
            throw new IllegalArgumentException("Tracking number exceeds maximum length of " + MAX_TRACKING_NUMBER_LENGTH + " characters");
        }

        if (!VALID_TRACKING_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Tracking number format is invalid: must contain only alphanumeric characters, dashes, and underscores");
        }
    }
}
