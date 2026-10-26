package com.courier.tracking.repository;

import com.courier.tracking.entity.DeliveryProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryProofRepository extends JpaRepository<DeliveryProof, Long> {

    Optional<DeliveryProof> findByShipmentId(Long shipmentId);

    Optional<DeliveryProof> findByShipmentTrackingNumber(String trackingNumber);

    boolean existsByShipmentId(Long shipmentId);
}
