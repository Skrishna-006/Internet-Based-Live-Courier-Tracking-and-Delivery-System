package com.courier.tracking.controller;

import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.CreateShipmentRequest;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller handling customer shipment creation and consignment operations.
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
}
