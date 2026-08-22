package com.procurement.engine.authorization.model;

import com.procurement.engine.approval.entity.ApprovalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Human-in-the-Loop Approval response DTO.
 */
public class ApprovalResponseDto {

    private final UUID approvalId;
    private final UUID procurementId;
    private final UUID proposedOfferId;
    private final String proposedProductName;
    private final String proposedVendorName;
    private final ApprovalStatus status;
    private final BigDecimal requestedAmount;
    private final BigDecimal authorizationLimit;
    private final BigDecimal difference;
    private final String exceptionType;
    private final String reason;
    private final String explanation;
    private final String comments;
    private final Instant requestedAt;
    private final Instant decidedAt;
    private final String decidedByName;

    public ApprovalResponseDto(UUID approvalId,
                               UUID procurementId,
                               UUID proposedOfferId,
                               String proposedProductName,
                               String proposedVendorName,
                               ApprovalStatus status,
                               BigDecimal requestedAmount,
                               BigDecimal authorizationLimit,
                               BigDecimal difference,
                               String exceptionType,
                               String reason,
                               String explanation,
                               String comments,
                               Instant requestedAt,
                               Instant decidedAt,
                               String decidedByName) {
        this.approvalId = approvalId;
        this.procurementId = procurementId;
        this.proposedOfferId = proposedOfferId;
        this.proposedProductName = proposedProductName;
        this.proposedVendorName = proposedVendorName;
        this.status = status;
        this.requestedAmount = requestedAmount;
        this.authorizationLimit = authorizationLimit;
        this.difference = difference;
        this.exceptionType = exceptionType;
        this.reason = reason;
        this.explanation = explanation;
        this.comments = comments;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.decidedByName = decidedByName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID approvalId;
        private UUID procurementId;
        private UUID proposedOfferId;
        private String proposedProductName;
        private String proposedVendorName;
        private ApprovalStatus status = ApprovalStatus.PENDING;
        private BigDecimal requestedAmount = BigDecimal.ZERO;
        private BigDecimal authorizationLimit = BigDecimal.ZERO;
        private BigDecimal difference = BigDecimal.ZERO;
        private String exceptionType = "LIMIT_EXCEEDED";
        private String reason;
        private String explanation;
        private String comments;
        private Instant requestedAt;
        private Instant decidedAt;
        private String decidedByName;

        public Builder approvalId(UUID approvalId) { this.approvalId = approvalId; return this; }
        public Builder procurementId(UUID procurementId) { this.procurementId = procurementId; return this; }
        public Builder proposedOfferId(UUID proposedOfferId) { this.proposedOfferId = proposedOfferId; return this; }
        public Builder proposedProductName(String proposedProductName) { this.proposedProductName = proposedProductName; return this; }
        public Builder proposedVendorName(String proposedVendorName) { this.proposedVendorName = proposedVendorName; return this; }
        public Builder status(ApprovalStatus status) { this.status = status; return this; }
        public Builder requestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; return this; }
        public Builder authorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; return this; }
        public Builder difference(BigDecimal difference) { this.difference = difference; return this; }
        public Builder exceptionType(String exceptionType) { this.exceptionType = exceptionType; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder explanation(String explanation) { this.explanation = explanation; return this; }
        public Builder comments(String comments) { this.comments = comments; return this; }
        public Builder requestedAt(Instant requestedAt) { this.requestedAt = requestedAt; return this; }
        public Builder decidedAt(Instant decidedAt) { this.decidedAt = decidedAt; return this; }
        public Builder decidedByName(String decidedByName) { this.decidedByName = decidedByName; return this; }

        public ApprovalResponseDto build() {
            return new ApprovalResponseDto(approvalId, procurementId, proposedOfferId, proposedProductName,
                    proposedVendorName, status, requestedAmount, authorizationLimit, difference, exceptionType,
                    reason, explanation, comments, requestedAt, decidedAt, decidedByName);
        }
    }

    public UUID getApprovalId() { return approvalId; }
    public UUID getProcurementId() { return procurementId; }
    public UUID getProposedOfferId() { return proposedOfferId; }
    public String getProposedProductName() { return proposedProductName; }
    public String getProposedVendorName() { return proposedVendorName; }
    public ApprovalStatus getStatus() { return status; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public BigDecimal getDifference() { return difference; }
    public String getExceptionType() { return exceptionType; }
    public String getReason() { return reason; }
    public String getExplanation() { return explanation; }
    public String getComments() { return comments; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedByName() { return decidedByName; }
}
