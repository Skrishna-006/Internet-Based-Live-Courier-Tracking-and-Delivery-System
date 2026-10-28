package com.courier.tracking.controller;

import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.AssignAgentRequest;
import com.courier.tracking.dto.CreateShipmentRequest;
import com.courier.tracking.dto.ShipmentResponse;
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
     * Endpoint allowing an authenticated customer to retrieve their paginated shipments.
     * Enforces ROLE_CUSTOMER authority via Spring Security.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<ShipmentResponse>>> getCustomerShipments(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<ShipmentResponse> response = shipmentService.getCustomerShipments(authentication.getName(), pageable);
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
}
