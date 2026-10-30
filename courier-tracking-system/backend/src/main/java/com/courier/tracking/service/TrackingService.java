package com.courier.tracking.service;

import com.courier.tracking.dto.PublicLiveLocationResponse;
import com.courier.tracking.dto.PublicTrackingMilestoneDto;
import com.courier.tracking.dto.PublicTrackingResponse;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.TrackingEvent;
import com.courier.tracking.exception.ResourceNotFoundException;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service providing public, unauthenticated shipment tracking, milestone summaries,
 * and real-time live location polling fallback.
 * Strictly guarantees that sensitive customer PII, courier contact details, and precise
 * off-duty/inactive coordinates are not exposed.
 */
@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);
    private static final int MAX_TRACKING_NUMBER_LENGTH = 64;
    private static final Pattern VALID_TRACKING_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    /**
     * Freshness threshold for live location telemetry polling.
     * Coordinates older than this duration are treated as stale and withheld from live positions.
     */
    public static final Duration LOCATION_FRESHNESS_THRESHOLD = Duration.ofSeconds(120);

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

    /**
     * Retrieves the latest public live location of an active shipment for polling fallback.
     * Strictly limits returned data to sanitized coordinates and status when active.
     * Coordinates are withheld if the shipment is not in OUT_FOR_DELIVERY status,
     * if no location update has been recorded, or if the recorded location is stale.
     *
     * @param trackingNumber The public parcel consignment tracking number
     * @return Sanitized public live location polling response
     * @throws IllegalArgumentException if tracking number is blank, too long, or malformed
     * @throws ResourceNotFoundException if no shipment matches the tracking number
     */
    @Transactional(readOnly = true)
    public PublicLiveLocationResponse getPublicLiveLocation(String trackingNumber) {
        return getPublicLiveLocation(trackingNumber, Instant.now());
    }

    /**
     * Overloaded method accepting a reference timestamp to allow deterministic testing of freshness.
     *
     * @param trackingNumber The public parcel consignment tracking number
     * @param referenceNow The reference timestamp against which freshness is evaluated
     * @return Sanitized public live location polling response
     */
    @Transactional(readOnly = true)
    public PublicLiveLocationResponse getPublicLiveLocation(String trackingNumber, Instant referenceNow) {
        validateTrackingNumberInput(trackingNumber);

        String normalizedTrackingNumber = trackingNumber.trim().toUpperCase(Locale.ROOT);

        Shipment shipment = shipmentRepository.findByTrackingNumber(normalizedTrackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "trackingNumber", normalizedTrackingNumber));

        // Return location only when live tracking is active and shipment is OUT_FOR_DELIVERY
        if (shipment.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            log.debug("Live location requested for shipment {} with inactive status {}",
                    normalizedTrackingNumber, shipment.getStatus());
            return new PublicLiveLocationResponse(
                    shipment.getTrackingNumber(),
                    null,
                    null,
                    null,
                    shipment.getStatus(),
                    false
            );
        }

        // Handle shipments without an available GPS update
        if (shipment.getCurrentLatitude() == null
                || shipment.getCurrentLongitude() == null
                || shipment.getLastLocationUpdatedAt() == null) {
            log.debug("Live location requested for OUT_FOR_DELIVERY shipment {} with no recorded coordinates",
                    normalizedTrackingNumber);
            return new PublicLiveLocationResponse(
                    shipment.getTrackingNumber(),
                    null,
                    null,
                    null,
                    shipment.getStatus(),
                    false
            );
        }

        // Check timestamp freshness: do not expose stale coordinates as current live positions
        Instant recordedAt = shipment.getLastLocationUpdatedAt();
        long ageSeconds = Math.abs(Duration.between(recordedAt, referenceNow).toSeconds());
        boolean isFresh = ageSeconds <= LOCATION_FRESHNESS_THRESHOLD.toSeconds();

        if (!isFresh) {
            log.debug("Live location requested for shipment {} with stale coordinates (age: {}s, threshold: {}s)",
                    normalizedTrackingNumber, ageSeconds, LOCATION_FRESHNESS_THRESHOLD.toSeconds());
            return new PublicLiveLocationResponse(
                    shipment.getTrackingNumber(),
                    null,
                    null,
                    null,
                    shipment.getStatus(),
                    false
            );
        }

        log.debug("Active live location served for shipment {}: lat={}, lon={}, recordedAt={}",
                normalizedTrackingNumber, shipment.getCurrentLatitude(), shipment.getCurrentLongitude(), recordedAt);

        return new PublicLiveLocationResponse(
                shipment.getTrackingNumber(),
                shipment.getCurrentLatitude(),
                shipment.getCurrentLongitude(),
                recordedAt,
                shipment.getStatus(),
                true
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
