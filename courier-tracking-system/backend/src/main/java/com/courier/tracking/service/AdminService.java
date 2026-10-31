package com.courier.tracking.service;

import com.courier.tracking.dto.AdminAgentSummaryResponse;
import com.courier.tracking.dto.AdminFleetMemberResponse;
import com.courier.tracking.dto.AdminOverviewResponse;
import com.courier.tracking.dto.PublicTrackingMilestoneDto;
import com.courier.tracking.dto.ShipmentResponse;
import com.courier.tracking.entity.RoleName;
import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import com.courier.tracking.entity.TrackingEvent;
import com.courier.tracking.entity.User;
import com.courier.tracking.exception.ResourceNotFoundException;
import com.courier.tracking.repository.ShipmentRepository;
import com.courier.tracking.repository.TrackingEventRepository;
import com.courier.tracking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service providing high-level operational metrics, system-wide shipment visibility,
 * delivery-agent workload directories, and active fleet telemetry for administrator and dispatch users.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<ShipmentStatus> ACTIVE_DELIVERY_STATUSES = Set.of(
            ShipmentStatus.ASSIGNED,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY
    );

    private static final List<ShipmentStatus> FLEET_ACTIVE_STATUSES = List.of(
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY
    );

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final TrackingEventRepository trackingEventRepository;

    public AdminService(ShipmentRepository shipmentRepository,
                        UserRepository userRepository,
                        TrackingEventRepository trackingEventRepository) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.trackingEventRepository = trackingEventRepository;
    }

    /**
     * Aggregates high-level operational counts directly from the database
     * without loading entity instances into memory.
     *
     * @return AdminOverviewResponse containing consignment and agent metrics
     */
    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        log.debug("Calculating admin dashboard operational metrics");
        long totalShipments = shipmentRepository.count();
        long unassignedShipments = shipmentRepository.countByAssignedAgentIsNull();
        long assignedShipments = shipmentRepository.countByStatus(ShipmentStatus.ASSIGNED);
        long inTransitShipments = shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT);
        long outForDeliveryShipments = shipmentRepository.countByStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        long deliveredShipments = shipmentRepository.countByStatus(ShipmentStatus.DELIVERED);
        long failedDeliveryShipments = shipmentRepository.countByStatus(ShipmentStatus.ATTEMPT_FAILED);
        long activeAgents = userRepository.countByRoleNameAndActiveTrue(RoleName.ROLE_DELIVERY_AGENT);

        return new AdminOverviewResponse(
                totalShipments,
                unassignedShipments,
                assignedShipments,
                inTransitShipments,
                outForDeliveryShipments,
                deliveredShipments,
                failedDeliveryShipments,
                activeAgents
        );
    }

    /**
     * Retrieves a paginated list of all system consignments with optional status filtering,
     * tracking-number substring search, and unassigned-only constraints.
     * Enforces pagination limits (1..100) to protect database and server memory.
     *
     * @param status Optional shipment status filter
     * @param search Optional tracking number substring search
     * @param unassignedOnly Optional flag to restrict to unassigned shipments
     * @param pageable Client-requested pagination parameters
     * @return Paginated ShipmentResponse items
     */
    @Transactional(readOnly = true)
    public Page<ShipmentResponse> getShipments(ShipmentStatus status, String search, Boolean unassignedOnly, Pageable pageable) {
        Pageable safePageable = sanitizePageable(pageable);

        boolean hasStatus = (status != null);
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        boolean isUnassigned = Boolean.TRUE.equals(unassignedOnly);
        String trimmedSearch = hasSearch ? search.trim() : null;

        Page<Shipment> shipments;
        if (isUnassigned) {
            if (hasStatus && hasSearch) {
                shipments = shipmentRepository.findByAssignedAgentIsNullAndStatusAndTrackingNumberContainingIgnoreCase(status, trimmedSearch, safePageable);
            } else if (hasStatus) {
                shipments = shipmentRepository.findByAssignedAgentIsNullAndStatus(status, safePageable);
            } else if (hasSearch) {
                shipments = shipmentRepository.findByAssignedAgentIsNullAndTrackingNumberContainingIgnoreCase(trimmedSearch, safePageable);
            } else {
                shipments = shipmentRepository.findByAssignedAgentIsNull(safePageable);
            }
        } else {
            if (hasStatus && hasSearch) {
                shipments = shipmentRepository.findByStatusAndTrackingNumberContainingIgnoreCase(status, trimmedSearch, safePageable);
            } else if (hasStatus) {
                shipments = shipmentRepository.findByStatus(status, safePageable);
            } else if (hasSearch) {
                shipments = shipmentRepository.findByTrackingNumberContainingIgnoreCase(trimmedSearch, safePageable);
            } else {
                shipments = shipmentRepository.findAll(safePageable);
            }
        }

        return shipments.map(this::mapToShipmentResponse);
    }

    /**
     * Retrieves full consignment details for an administrator by database shipment ID.
     *
     * @param id Database primary key of the shipment
     * @return ShipmentResponse with complete consignment and assignment information
     * @throws ResourceNotFoundException if no shipment matches the given ID
     */
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(Long id) {
        if (id == null || id <= 0) {
            throw new ResourceNotFoundException("Shipment", "id", id);
        }
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));
        return mapToShipmentResponse(shipment);
    }

    /**
     * Retrieves the chronological tracking audit milestones for a given shipment.
     *
     * @param id Database primary key of the shipment
     * @return List of PublicTrackingMilestoneDto in chronological order
     * @throws ResourceNotFoundException if no shipment matches the given ID
     */
    @Transactional(readOnly = true)
    public List<PublicTrackingMilestoneDto> getShipmentEvents(Long id) {
        if (id == null || id <= 0) {
            throw new ResourceNotFoundException("Shipment", "id", id);
        }
        if (!shipmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shipment", "id", id);
        }
        List<TrackingEvent> events = trackingEventRepository.findByShipmentIdOrderByEventTimestampAsc(id);
        return events.stream()
                .map(event -> new PublicTrackingMilestoneDto(
                        event.getStatus(),
                        event.getLocationName(),
                        event.getRemarks(),
                        event.getEventTimestamp()
                ))
                .toList();
    }

    /**
     * Retrieves the delivery-agent directory with aggregate workload metrics.
     * Executes single database group-by aggregation to prevent N+1 queries.
     *
     * @return List of AdminAgentSummaryResponse representing all delivery agents
     */
    @Transactional(readOnly = true)
    public List<AdminAgentSummaryResponse> getAgents() {
        List<User> agents = userRepository.findByRoleName(RoleName.ROLE_DELIVERY_AGENT);

        // Fetch aggregate shipment counts grouped by agent ID and status in a single query
        List<Object[]> rawCounts = shipmentRepository.countShipmentsGroupByAgentAndStatus();
        Map<Long, Map<ShipmentStatus, Long>> countsByAgent = new HashMap<>();
        for (Object[] row : rawCounts) {
            Long agentId = (Long) row[0];
            ShipmentStatus status = (ShipmentStatus) row[1];
            Long count = ((Number) row[2]).longValue();
            countsByAgent.computeIfAbsent(agentId, k -> new EnumMap<>(ShipmentStatus.class)).put(status, count);
        }

        return agents.stream().map(agent -> {
            Map<ShipmentStatus, Long> agentStats = countsByAgent.getOrDefault(agent.getId(), Collections.emptyMap());
            long totalAssigned = agentStats.values().stream().mapToLong(Long::longValue).sum();
            long activeDeliveries = agentStats.entrySet().stream()
                    .filter(e -> ACTIVE_DELIVERY_STATUSES.contains(e.getKey()))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            long deliveredCount = agentStats.getOrDefault(ShipmentStatus.DELIVERED, 0L);

            return new AdminAgentSummaryResponse(
                    agent.getId(),
                    agent.getFullName(),
                    agent.getEmail(),
                    agent.getPhoneNumber(),
                    Boolean.TRUE.equals(agent.getActive()),
                    totalAssigned,
                    activeDeliveries,
                    deliveredCount
            );
        }).toList();
    }

    /**
     * Retrieves verified active telemetry coordinates for vehicles in active delivery states.
     * Excludes shipments without valid GPS coordinates and includes no customer PII.
     *
     * @return List of AdminFleetMemberResponse with last recorded location and timestamp
     */
    @Transactional(readOnly = true)
    public List<AdminFleetMemberResponse> getFleet() {
        List<Shipment> fleetShipments = shipmentRepository
                .findByStatusInAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull(FLEET_ACTIVE_STATUSES);

        return fleetShipments.stream().map(s -> {
            Long agentId = s.getAssignedAgent() != null ? s.getAssignedAgent().getId() : null;
            String agentName = s.getAssignedAgent() != null ? s.getAssignedAgent().getFullName() : "Unassigned";

            return new AdminFleetMemberResponse(
                    agentId,
                    agentName,
                    s.getId(),
                    s.getTrackingNumber(),
                    s.getStatus().name(),
                    s.getCurrentLatitude(),
                    s.getCurrentLongitude(),
                    s.getLastLocationUpdatedAt()
            );
        }).toList();
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(pageNumber, pageSize, sort);
    }

    private ShipmentResponse mapToShipmentResponse(Shipment s) {
        Long agentId = s.getAssignedAgent() != null ? s.getAssignedAgent().getId() : null;
        String agentName = s.getAssignedAgent() != null ? s.getAssignedAgent().getFullName() : null;

        return new ShipmentResponse(
                s.getId(),
                s.getTrackingNumber(),
                s.getStatus(),
                s.getCustomer() != null ? s.getCustomer().getId() : null,
                s.getCustomer() != null ? s.getCustomer().getEmail() : null,
                agentId,
                agentName,
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
                s.getActualDeliveryAt(),
                s.getCreatedAt()
        );
    }
}
