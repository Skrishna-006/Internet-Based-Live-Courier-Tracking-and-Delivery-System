package com.courier.tracking.repository;

import com.courier.tracking.entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    List<TrackingEvent> findByShipmentIdOrderByEventTimestampAsc(Long shipmentId);

    List<TrackingEvent> findByShipmentTrackingNumberOrderByEventTimestampAsc(String trackingNumber);
}
