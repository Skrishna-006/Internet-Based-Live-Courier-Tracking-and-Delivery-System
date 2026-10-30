package com.courier.tracking.controller;

import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.AssignAgentRequest;
import com.courier.tracking.dto.CreateShipmentRequest;
import com.courier.tracking.dto.DeliveryConfirmationResponse;
import com.courier.tracking.dto.DeliveryOtpResponse;
import com.courier.tracking.dto.LocationUpdateRequest;
import com.courier.tracking.dto.LocationUpdateResponse;
import com.courier.tracking.dto.PriceEstimateResponse;
import com.courier.tracking.entity.ShipmentStatus;
import java.math.BigDecimal;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.dto.UpdateShipmentStatusRequest;
import com.courier.tracking.dto.VerifyDeliveryOtpRequest;
import com.courier.tracking.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller handling customer shipment creation, retrieval, and consignment operations.
 */
@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    /**
     * Endpoint allowing an authenticated customer to create a new shipment.
     * Enforces ROLE_CUSTOMER authority via Spring Security.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> createShipment(
            @Valid @RequestBody CreateShipmentRequest request,
            Authentication authentication
    ) {
        ShipmentResponse response = shipmentService.createShipment(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Shipment created successfully", response));
    }

    /**
     * Endpoint providing server-authoritative shipping price estimate based on package weight.
     * Enforces ROLE_CUSTOMER authority.
     */
    @GetMapping("/quote")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<PriceEstimateResponse>> getPriceQuote(
            @RequestParam("weightKg") BigDecimal weightKg
    ) {
        PriceEstimateResponse response = shipmentService.getPriceEstimate(weightKg);
        return ResponseEntity.ok(ApiResponse.success("Price quote calculated successfully", response));
    }

    /**
     * Endpoint allowing an authenticated customer to retrieve their paginated shipments,
     * with optional status filtering.
     * Enforces ROLE_CUSTOMER authority via Spring Security.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<ShipmentResponse>>> getCustomerShipments(
            @RequestParam(value = "status", required = false) ShipmentStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<ShipmentResponse> response = shipmentService.getCustomerShipments(authentication.getName(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Shipments retrieved successfully", response));
    }

    /**
     * Endpoint allowing an authenticated customer to retrieve a single shipment by ID.
     * Enforces ROLE_CUSTOMER authority via Spring Security.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getShipmentById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        ShipmentResponse response = shipmentService.getShipmentById(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Shipment retrieved successfully", response));
    }

    /**
     * Endpoint allowing an administrator/dispatcher to assign or reassign a delivery agent to a shipment.
     * Enforces ROLE_ADMIN authority via Spring Security.
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> assignDeliveryAgent(
            @PathVariable Long id,
            @Valid @RequestBody AssignAgentRequest request,
            Authentication authentication
    ) {
        ShipmentResponse response = shipmentService.assignDeliveryAgent(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Delivery agent assigned successfully", response));
    }

    /**
     * Endpoint allowing an administrator or the assigned delivery agent to transition a shipment's lifecycle status.
     * Automatically records an immutable tracking event in the same transaction.
     * Enforces ROLE_ADMIN or ROLE_DELIVERY_AGENT authority via Spring Security.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> updateShipmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShipmentStatusRequest request,
            Authentication authentication
    ) {
        ShipmentResponse response = shipmentService.updateShipmentStatus(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Shipment status updated successfully", response));
    }

    /**
     * Endpoint allowing an authorized actor (admin, assigned delivery agent, or owning customer)
     * to request/generate a cryptographically secure delivery OTP.
     */
    @PostMapping("/{id}/delivery-otp")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DELIVERY_AGENT', 'ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<DeliveryOtpResponse>> generateDeliveryOtp(
            @PathVariable Long id,
            Authentication authentication
    ) {
        DeliveryOtpResponse response = shipmentService.generateDeliveryOtp(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Delivery OTP generated successfully", response));
    }

    /**
     * Endpoint allowing an authorized delivery agent or administrator to verify the recipient's OTP
     * and confirm shipment delivery. Customers are strictly forbidden from executing verification.
     */
    @PostMapping("/{id}/delivery/verify-otp")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<DeliveryConfirmationResponse>> confirmDeliveryWithOtp(
            @PathVariable Long id,
            @Valid @RequestBody VerifyDeliveryOtpRequest request,
            Authentication authentication
    ) {
        DeliveryConfirmationResponse response = shipmentService.confirmDeliveryWithOtp(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Delivery confirmed successfully", response));
    }

    /**
     * Endpoint allowing the assigned delivery agent to ingest live GPS location telemetry
     * for a shipment in OUT_FOR_DELIVERY status.
     * Enforces ROLE_DELIVERY_AGENT authority, coordinate validity, accuracy <= 30m,
     * speed <= 140 km/h, freshness <= 60s, and 5s minimum update interval.
     */
    @PostMapping("/{id}/location")
    @PreAuthorize("hasAuthority('ROLE_DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<LocationUpdateResponse>> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationUpdateRequest request,
            Authentication authentication
    ) {
        LocationUpdateResponse response = shipmentService.updateLiveLocation(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Telemetry coordinates accepted successfully", response));
    }
}
