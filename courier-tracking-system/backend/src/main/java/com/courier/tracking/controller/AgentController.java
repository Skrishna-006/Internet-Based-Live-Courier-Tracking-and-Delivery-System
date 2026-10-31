package com.courier.tracking.controller;

import com.courier.tracking.dto.AgentSummaryResponse;
import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.service.ShipmentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller dedicated to authenticated delivery agents.
 * Derives agent identity strictly from the authenticated security context.
 * All endpoints enforce ROLE_DELIVERY_AGENT authority.
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final ShipmentService shipmentService;

    public AgentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    /**
     * Retrieves paginated shipments assigned to the currently authenticated delivery agent,
     * with optional status filtering.
     * Enforces ROLE_DELIVERY_AGENT authority via Spring Security.
     */
    @GetMapping("/me/assignments")
    @PreAuthorize("hasAuthority('ROLE_DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<Page<ShipmentResponse>>> getMyAssignments(
            @RequestParam(value = "status", required = false) ShipmentStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<ShipmentResponse> response = shipmentService.getAgentAssignments(authentication.getName(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Assigned shipments retrieved successfully", response));
    }

    /**
     * Retrieves summary delivery queue metrics across all assigned shipments
     * for the currently authenticated delivery agent.
     * Enforces ROLE_DELIVERY_AGENT authority via Spring Security.
     */
    @GetMapping("/me/assignments/summary")
    @PreAuthorize("hasAuthority('ROLE_DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<AgentSummaryResponse>> getMySummary(
            Authentication authentication
    ) {
        AgentSummaryResponse response = shipmentService.getAgentSummary(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Agent summary retrieved successfully", response));
    }

    /**
     * Retrieves details of a specific shipment assigned to the currently authenticated delivery agent.
     * Enforces ROLE_DELIVERY_AGENT authority via Spring Security.
     */
    @GetMapping("/me/assignments/{id}")
    @PreAuthorize("hasAuthority('ROLE_DELIVERY_AGENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getMyAssignmentById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        ShipmentResponse response = shipmentService.getAgentShipmentById(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Assignment details retrieved successfully", response));
    }
}
