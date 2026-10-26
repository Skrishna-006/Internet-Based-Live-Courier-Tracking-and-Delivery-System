package com.courier.tracking.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity recording immutable historical tracking events, waypoint scans, and GPS coordinates.
 */
@Entity
@Table(name = "tracking_events")
public class TrackingEvent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShipmentStatus status;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    public TrackingEvent() {
    }

    public TrackingEvent(Shipment shipment, ShipmentStatus status, BigDecimal latitude,
                         BigDecimal longitude, String locationName, String remarks) {
        this.shipment = shipment;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.remarks = remarks;
        this.eventTimestamp = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.eventTimestamp == null) {
            this.eventTimestamp = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Shipment getShipment() {
        return shipment;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackingEvent that = (TrackingEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TrackingEvent{" +
                "id=" + id +
                ", status=" + status +
                ", locationName='" + locationName + '\'' +
                ", eventTimestamp=" + eventTimestamp +
                '}';
    }
}
