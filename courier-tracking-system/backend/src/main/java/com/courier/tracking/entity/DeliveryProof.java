package com.courier.tracking.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing proof of parcel handover (OTP, signature, photo).
 */
@Entity
@Table(name = "delivery_proofs")
public class DeliveryProof implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, unique = true)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captured_by_agent_id", nullable = false)
    private User capturedByAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "proof_type", nullable = false, length = 30)
    private ProofType proofType;

    @Column(name = "proof_value", nullable = false, columnDefinition = "TEXT")
    private String proofValue;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Column(name = "verification_notes", length = 500)
    private String verificationNotes;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    public DeliveryProof() {
    }

    public DeliveryProof(Shipment shipment, User capturedByAgent, ProofType proofType,
                         String proofValue, String recipientName, String verificationNotes) {
        this.shipment = shipment;
        this.capturedByAgent = capturedByAgent;
        this.proofType = proofType;
        this.proofValue = proofValue;
        this.recipientName = recipientName;
        this.verificationNotes = verificationNotes;
        this.verifiedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.verifiedAt == null) {
            this.verifiedAt = Instant.now();
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

    public User getCapturedByAgent() {
        return capturedByAgent;
    }

    public void setCapturedByAgent(User capturedByAgent) {
        this.capturedByAgent = capturedByAgent;
    }

    public ProofType getProofType() {
        return proofType;
    }

    public void setProofType(ProofType proofType) {
        this.proofType = proofType;
    }

    public String getProofValue() {
        return proofValue;
    }

    public void setProofValue(String proofValue) {
        this.proofValue = proofValue;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getVerificationNotes() {
        return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
        this.verificationNotes = verificationNotes;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeliveryProof that = (DeliveryProof) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DeliveryProof{" +
                "id=" + id +
                ", proofType=" + proofType +
                ", recipientName='" + recipientName + '\'' +
                ", verifiedAt=" + verifiedAt +
                '}';
    }
}
