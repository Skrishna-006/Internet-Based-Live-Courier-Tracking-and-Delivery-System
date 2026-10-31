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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") Long id);
}
