package com.procurement.engine.approval.entity;

import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_id", nullable = false)
    private ProcurementRequest procurement;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "authorization_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal authorizationLimit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal difference;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(length = 2000)
    private String comments;

    public Approval() {}

    public Approval(UUID id, ProcurementRequest procurement, BigDecimal requestedAmount, BigDecimal authorizationLimit, BigDecimal difference, String reason, ApprovalStatus status, Instant requestedAt, Instant decidedAt, User decidedBy, String comments) {
        this.id = id;
        this.procurement = procurement;
        this.requestedAmount = requestedAmount;
        this.authorizationLimit = authorizationLimit;
        this.difference = difference;
        this.reason = reason;
        this.status = status != null ? status : ApprovalStatus.PENDING;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.comments = comments;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private ProcurementRequest procurement;
        private BigDecimal requestedAmount;
        private BigDecimal authorizationLimit;
        private BigDecimal difference;
        private String reason;
        private ApprovalStatus status = ApprovalStatus.PENDING;
        private Instant requestedAt;
        private Instant decidedAt;
        private User decidedBy;
        private String comments;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder procurement(ProcurementRequest procurement) { this.procurement = procurement; return this; }
        public Builder requestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; return this; }
        public Builder authorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; return this; }
        public Builder difference(BigDecimal difference) { this.difference = difference; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder status(ApprovalStatus status) { this.status = status; return this; }
        public Builder requestedAt(Instant requestedAt) { this.requestedAt = requestedAt; return this; }
        public Builder decidedAt(Instant decidedAt) { this.decidedAt = decidedAt; return this; }
        public Builder decidedBy(User decidedBy) { this.decidedBy = decidedBy; return this; }
        public Builder comments(String comments) { this.comments = comments; return this; }

        public Approval build() {
            return new Approval(id, procurement, requestedAmount, authorizationLimit, difference, reason, status, requestedAt, decidedAt, decidedBy, comments);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProcurementRequest getProcurement() { return procurement; }
    public void setProcurement(ProcurementRequest procurement) { this.procurement = procurement; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public void setAuthorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}
