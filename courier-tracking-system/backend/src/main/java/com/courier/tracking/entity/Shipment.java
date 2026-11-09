package com.courier.tracking.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JPA entity representing consignments and parcel shipments through the delivery state machine.
 */
@Entity
@Table(name = "shipments")
public class Shipment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 32)
    private String trackingNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName;

    @Column(name = "sender_address", nullable = false, columnDefinition = "TEXT")
    private String senderAddress;

    @Column(name = "sender_phone", nullable = false, length = 30)
    private String senderPhone;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_address", nullable = false, columnDefinition = "TEXT")
    private String recipientAddress;

    @Column(name = "recipient_phone", nullable = false, length = 30)
    private String recipientPhone;

    @Column(name = "recipient_latitude", precision = 10, scale = 7)
    private BigDecimal recipientLatitude;

    @Column(name = "recipient_longitude", precision = 10, scale = 7)
    private BigDecimal recipientLongitude;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "estimated_delivery_at")
    private Instant estimatedDeliveryAt;

    @Column(name = "actual_delivery_at")
    private Instant actualDeliveryAt;

    @Column(name = "delivery_otp_hash", length = 255)
    private String deliveryOtpHash;

    @Column(name = "delivery_otp_expires_at")
    private Instant deliveryOtpExpiresAt;

    @Column(name = "delivery_otp_attempts", nullable = false)
    private int deliveryOtpAttempts = 0;

    @Column(name = "delivery_otp_last_sent_at")
    private Instant deliveryOtpLastSentAt;

    @Column(name = "customer_delivery_pin", length = 10)
    private String customerDeliveryPin;

    @Column(name = "current_latitude", precision = 10, scale = 7)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 10, scale = 7)
    private BigDecimal currentLongitude;

    @Column(name = "last_location_updated_at")
    private Instant lastLocationUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "shipment", fetch = FetchType.LAZY)
    private List<TrackingEvent> trackingEvents = new ArrayList<>();

    @OneToOne(mappedBy = "shipment", fetch = FetchType.LAZY)
    private DeliveryProof deliveryProof;

    public Shipment() {
    }

    public Shipment(String trackingNumber, User customer, String senderName, String senderAddress,
                    String senderPhone, String recipientName, String recipientAddress,
                    String recipientPhone, BigDecimal weightKg, BigDecimal price, String currency) {
        this.trackingNumber = trackingNumber;
        this.customer = customer;
        this.senderName = senderName;
        this.senderAddress = senderAddress;
        this.senderPhone = senderPhone;
        this.recipientName = recipientName;
        this.recipientAddress = recipientAddress;
        this.recipientPhone = recipientPhone;
        this.weightKg = weightKg;
        this.price = price;
        this.currency = (currency != null && !currency.isBlank()) ? currency.toUpperCase().trim() : "USD";
        this.status = ShipmentStatus.CREATED;
    }

    public Shipment(String trackingNumber, User customer, String senderName, String senderAddress,
                    String senderPhone, String recipientName, String recipientAddress,
                    String recipientPhone, BigDecimal weightKg, BigDecimal price) {
        this(trackingNumber, customer, senderName, senderAddress, senderPhone,
             recipientName, recipientAddress, recipientPhone, weightKg, price, "USD");
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = ShipmentStatus.CREATED;
        }
        if (this.currency == null || this.currency.isBlank()) {
            this.currency = "USD";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getAssignedAgent() {
        return assignedAgent;
    }

    public void setAssignedAgent(User assignedAgent) {
        this.assignedAgent = assignedAgent;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String senderPhone) {
        this.senderPhone = senderPhone;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public void setRecipientAddress(String recipientAddress) {
        this.recipientAddress = recipientAddress;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public BigDecimal getRecipientLatitude() {
        return recipientLatitude;
    }

    public void setRecipientLatitude(BigDecimal recipientLatitude) {
        this.recipientLatitude = recipientLatitude;
    }

    public BigDecimal getRecipientLongitude() {
        return recipientLongitude;
    }

    public void setRecipientLongitude(BigDecimal recipientLongitude) {
        this.recipientLongitude = recipientLongitude;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = (currency != null && !currency.isBlank()) ? currency.toUpperCase().trim() : "USD";
    }

    public Instant getEstimatedDeliveryAt() {
        return estimatedDeliveryAt;
    }

    public void setEstimatedDeliveryAt(Instant estimatedDeliveryAt) {
        this.estimatedDeliveryAt = estimatedDeliveryAt;
    }

    public Instant getActualDeliveryAt() {
        return actualDeliveryAt;
    }

    public void setActualDeliveryAt(Instant actualDeliveryAt) {
        this.actualDeliveryAt = actualDeliveryAt;
    }

    public String getDeliveryOtpHash() {
        return deliveryOtpHash;
    }

    public void setDeliveryOtpHash(String deliveryOtpHash) {
        this.deliveryOtpHash = deliveryOtpHash;
    }

    public Instant getDeliveryOtpExpiresAt() {
        return deliveryOtpExpiresAt;
    }

    public void setDeliveryOtpExpiresAt(Instant deliveryOtpExpiresAt) {
        this.deliveryOtpExpiresAt = deliveryOtpExpiresAt;
    }

    public int getDeliveryOtpAttempts() {
        return deliveryOtpAttempts;
    }

    public void setDeliveryOtpAttempts(int deliveryOtpAttempts) {
        this.deliveryOtpAttempts = deliveryOtpAttempts;
    }

    public Instant getDeliveryOtpLastSentAt() {
        return deliveryOtpLastSentAt;
    }

    public void setDeliveryOtpLastSentAt(Instant deliveryOtpLastSentAt) {
        this.deliveryOtpLastSentAt = deliveryOtpLastSentAt;
    }

    public String getCustomerDeliveryPin() {
        return customerDeliveryPin;
    }

    public void setCustomerDeliveryPin(String customerDeliveryPin) {
        this.customerDeliveryPin = customerDeliveryPin;
    }

    public BigDecimal getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(BigDecimal currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public BigDecimal getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(BigDecimal currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public Instant getLastLocationUpdatedAt() {
        return lastLocationUpdatedAt;
    }

    public void setLastLocationUpdatedAt(Instant lastLocationUpdatedAt) {
        this.lastLocationUpdatedAt = lastLocationUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TrackingEvent> getTrackingEvents() {
        return trackingEvents;
    }

    public void setTrackingEvents(List<TrackingEvent> trackingEvents) {
        this.trackingEvents = trackingEvents;
    }

    public DeliveryProof getDeliveryProof() {
        return deliveryProof;
    }

    public void setDeliveryProof(DeliveryProof deliveryProof) {
        this.deliveryProof = deliveryProof;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shipment shipment = (Shipment) o;
        return Objects.equals(trackingNumber, shipment.trackingNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackingNumber);
    }

    @Override
    public String toString() {
        return "Shipment{" +
                "id=" + id +
                ", trackingNumber='" + trackingNumber + '\'' +
                ", status=" + status +
                ", recipientName='" + recipientName + '\'' +
                ", weightKg=" + weightKg +
                ", price=" + price +
                '}';
    }
}
