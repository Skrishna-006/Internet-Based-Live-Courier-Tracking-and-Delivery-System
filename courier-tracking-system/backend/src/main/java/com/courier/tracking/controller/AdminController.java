package com.courier.tracking.controller;

import com.courier.tracking.dto.AdminAgentSummaryResponse;
import com.courier.tracking.dto.AdminFleetMemberResponse;
import com.courier.tracking.dto.AdminOverviewResponse;
import com.courier.tracking.dto.ApiResponse;
import com.courier.tracking.dto.PublicTrackingMilestoneDto;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.service.AdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller providing administrative oversight, consignment search, dispatch assignment monitoring,
 * delivery-agent workload management, and fleet telemetry visualization.
 * All endpoints enforce ROLE_ADMIN authority via method security.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Retrieves high-level operational counts and consignment status metrics.
     * Enforces ROLE_ADMIN authority.
     */
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> getOverview() {
        AdminOverviewResponse overview = adminService.getOverview();
        return ResponseEntity.ok(ApiResponse.success("Admin dashboard overview retrieved successfully", overview));
    }

    /**
     * Retrieves paginated system-wide shipments with optional status filtering,
     * tracking-number substring search, and unassigned-only filter.
     * Enforces ROLE_ADMIN authority.
     */
    @GetMapping("/shipments")
    public ResponseEntity<ApiResponse<Page<ShipmentResponse>>> getShipments(
            @RequestParam(value = "status", required = false) ShipmentStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "unassignedOnly", required = false) Boolean unassignedOnly,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ShipmentResponse> response = adminService.getShipments(status, search, unassignedOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success("Shipments retrieved successfully", response));
    }

    /**
     * Retrieves full shipment details for a specific shipment by ID.
     * Enforces ROLE_ADMIN authority.
     */
    @GetMapping("/shipments/{id}")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getShipmentById(@PathVariable Long id) {
        ShipmentResponse response = adminService.getShipmentById(id);
        return ResponseEntity.ok(ApiResponse.success("Shipment details retrieved successfully", response));
    }

    /**
     * Retrieves chronological audit milestones and tracking events for a specific shipment.
     * Enforces ROLE_ADMIN authority.
     */
    @GetMapping("/shipments/{id}/events")
    public ResponseEntity<ApiResponse<List<PublicTrackingMilestoneDto>>> getShipmentEvents(@PathVariable Long id) {
        List<PublicTrackingMilestoneDto> response = adminService.getShipmentEvents(id);
        return ResponseEntity.ok(ApiResponse.success("Shipment tracking events retrieved successfully", response));
    }

    /**
     * Retrieves the delivery-agent directory with individual workload and delivery metrics.
     * Enforces ROLE_ADMIN authority.
     */
    @GetMapping("/agents")
    public ResponseEntity<ApiResponse<List<AdminAgentSummaryResponse>>> getAgents() {
        List<AdminAgentSummaryResponse> response = adminService.getAgents();
        return ResponseEntity.ok(ApiResponse.success("Agent directory retrieved successfully", response));
    }

    /**
     * Retrieves initial fleet telemetry coordinates for vehicles in active delivery states.
     * Enforces ROLE_ADMIN authority.
     */
    @GetMapping("/fleet")
    public ResponseEntity<ApiResponse<List<AdminFleetMemberResponse>>> getFleet() {
        List<AdminFleetMemberResponse> response = adminService.getFleet();
        return ResponseEntity.ok(ApiResponse.success("Fleet telemetry locations retrieved successfully", response));
    }
}
