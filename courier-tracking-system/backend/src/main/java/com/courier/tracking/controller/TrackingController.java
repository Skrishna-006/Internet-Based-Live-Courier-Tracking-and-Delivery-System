package com.courier.tracking.controller;

import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.PublicTrackingResponse;
import com.courier.tracking.service.TrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing public consignment tracking endpoints.
 * All routes in /public/** are accessible without authentication.
 */
@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /**
     * Retrieves a sanitized public tracking summary by tracking number.
     * Open to any client without requiring authentication.
     *
     * @param trackingNumber The public parcel consignment tracking number
     * @return Public tracking details and milestone history wrapped in standard ApiResponse envelope
     */
    @GetMapping("/public/{trackingNumber}")
    public ResponseEntity<ApiResponse<PublicTrackingResponse>> getPublicTracking(
            @PathVariable String trackingNumber
    ) {
        PublicTrackingResponse response = trackingService.getPublicTrackingSummary(trackingNumber);
        return ResponseEntity.ok(ApiResponse.success("Tracking summary retrieved successfully", response));
    }
}
