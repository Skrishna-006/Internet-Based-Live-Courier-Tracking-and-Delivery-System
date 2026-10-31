package com.courier.tracking.repository;

import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @EntityGraph(attributePaths = {"customer"})
    Optional<Shipment> findWithCustomerByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    List<Shipment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<Shipment> findByCustomerId(Long customerId, Pageable pageable);

    Page<Shipment> findByCustomerIdAndStatus(Long customerId, ShipmentStatus status, Pageable pageable);

    List<Shipment> findByAssignedAgentIdOrderByCreatedAtDesc(Long assignedAgentId);

    Page<Shipment> findByAssignedAgentId(Long assignedAgentId, Pageable pageable);

    List<Shipment> findByAssignedAgentIdAndStatus(Long assignedAgentId, ShipmentStatus status);

    Page<Shipment> findByAssignedAgentIdAndStatus(Long assignedAgentId, ShipmentStatus status, Pageable pageable);

    long countByAssignedAgentId(Long assignedAgentId);

    long countByAssignedAgentIdAndStatus(Long assignedAgentId, ShipmentStatus status);

    List<Shipment> findByStatus(ShipmentStatus status);

    long countByStatus(ShipmentStatus status);

    long countByAssignedAgentIsNull();

    long countByAssignedAgentIdAndStatusIn(Long assignedAgentId, Collection<ShipmentStatus> statuses);

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByAssignedAgentIsNull(Pageable pageable);

    Page<Shipment> findByAssignedAgentIsNullAndStatus(ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByTrackingNumberContainingIgnoreCase(String trackingNumber, Pageable pageable);

    Page<Shipment> findByStatusAndTrackingNumberContainingIgnoreCase(ShipmentStatus status, String trackingNumber, Pageable pageable);

    Page<Shipment> findByAssignedAgentIsNullAndTrackingNumberContainingIgnoreCase(String trackingNumber, Pageable pageable);

    Page<Shipment> findByAssignedAgentIsNullAndStatusAndTrackingNumberContainingIgnoreCase(ShipmentStatus status, String trackingNumber, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedAgent"})
    List<Shipment> findByStatusInAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull(Collection<ShipmentStatus> statuses);

    @Query("SELECT s.assignedAgent.id, s.status, COUNT(s) FROM Shipment s WHERE s.assignedAgent IS NOT NULL GROUP BY s.assignedAgent.id, s.status")
    List<Object[]> countShipmentsGroupByAgentAndStatus();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") Long id);
}
