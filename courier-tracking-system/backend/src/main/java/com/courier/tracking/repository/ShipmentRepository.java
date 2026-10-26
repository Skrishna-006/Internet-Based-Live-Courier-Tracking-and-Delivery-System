package com.courier.tracking.repository;

import com.courier.tracking.entity.Shipment;
import com.courier.tracking.entity.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumber(String trackingNumber);

    List<Shipment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<Shipment> findByCustomerId(Long customerId, Pageable pageable);

    List<Shipment> findByAssignedAgentIdOrderByCreatedAtDesc(Long assignedAgentId);

    List<Shipment> findByAssignedAgentIdAndStatus(Long assignedAgentId, ShipmentStatus status);

    List<Shipment> findByStatus(ShipmentStatus status);
}
