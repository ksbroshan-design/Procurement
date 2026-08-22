package com.procurement.engine.vendor.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendors")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(name = "seller_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal sellerRating;

    @Column(name = "reliability_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal reliabilityScore;

    @Column(name = "return_policy", nullable = false, length = 255)
    private String returnPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VendorStatus status = VendorStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Vendor() {}

    public Vendor(UUID id, String name, String source, BigDecimal sellerRating, BigDecimal reliabilityScore, String returnPolicy, VendorStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.sellerRating = sellerRating;
        this.reliabilityScore = reliabilityScore;
        this.returnPolicy = returnPolicy;
        this.status = status != null ? status : VendorStatus.ACTIVE;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String name;
        private String source;
        private BigDecimal sellerRating;
        private BigDecimal reliabilityScore;
        private String returnPolicy;
        private VendorStatus status = VendorStatus.ACTIVE;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder sellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; return this; }
        public Builder reliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; return this; }
        public Builder returnPolicy(String returnPolicy) { this.returnPolicy = returnPolicy; return this; }
        public Builder status(VendorStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Vendor build() {
            return new Vendor(id, name, source, sellerRating, reliabilityScore, returnPolicy, status, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public BigDecimal getSellerRating() { return sellerRating; }
    public void setSellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; }
    public BigDecimal getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; }
    public String getReturnPolicy() { return returnPolicy; }
    public void setReturnPolicy(String returnPolicy) { this.returnPolicy = returnPolicy; }
    public VendorStatus getStatus() { return status; }
    public void setStatus(VendorStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
