package com.procurement.engine.product.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reliability_history")
public class ReliabilityHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "failure_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal failureRate;

    @Column(name = "average_repair_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal averageRepairCost;

    @Column(name = "average_downtime_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal averageDowntimeCost;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public ReliabilityHistory() {}

    public ReliabilityHistory(UUID id, Product product, BigDecimal failureRate, BigDecimal averageRepairCost, BigDecimal averageDowntimeCost, int sampleSize, Instant recordedAt) {
        this.id = id;
        this.product = product;
        this.failureRate = failureRate;
        this.averageRepairCost = averageRepairCost;
        this.averageDowntimeCost = averageDowntimeCost;
        this.sampleSize = sampleSize;
        this.recordedAt = recordedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private Product product;
        private BigDecimal failureRate;
        private BigDecimal averageRepairCost;
        private BigDecimal averageDowntimeCost;
        private int sampleSize;
        private Instant recordedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder product(Product product) { this.product = product; return this; }
        public Builder failureRate(BigDecimal failureRate) { this.failureRate = failureRate; return this; }
        public Builder averageRepairCost(BigDecimal averageRepairCost) { this.averageRepairCost = averageRepairCost; return this; }
        public Builder averageDowntimeCost(BigDecimal averageDowntimeCost) { this.averageDowntimeCost = averageDowntimeCost; return this; }
        public Builder sampleSize(int sampleSize) { this.sampleSize = sampleSize; return this; }
        public Builder recordedAt(Instant recordedAt) { this.recordedAt = recordedAt; return this; }

        public ReliabilityHistory build() {
            return new ReliabilityHistory(id, product, failureRate, averageRepairCost, averageDowntimeCost, sampleSize, recordedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public BigDecimal getFailureRate() { return failureRate; }
    public void setFailureRate(BigDecimal failureRate) { this.failureRate = failureRate; }
    public BigDecimal getAverageRepairCost() { return averageRepairCost; }
    public void setAverageRepairCost(BigDecimal averageRepairCost) { this.averageRepairCost = averageRepairCost; }
    public BigDecimal getAverageDowntimeCost() { return averageDowntimeCost; }
    public void setAverageDowntimeCost(BigDecimal averageDowntimeCost) { this.averageDowntimeCost = averageDowntimeCost; }
    public int getSampleSize() { return sampleSize; }
    public void setSampleSize(int sampleSize) { this.sampleSize = sampleSize; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
