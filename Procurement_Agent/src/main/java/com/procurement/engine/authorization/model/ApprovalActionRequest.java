package com.procurement.engine.authorization.model;

import java.util.UUID;

/**
 * Request payload for approving or rejecting a pending human-in-the-loop approval.
 */
public class ApprovalActionRequest {

    private String comments;
    private UUID approvedOfferId; // Optional verification field; if provided, server asserts it matches pending proposedOfferId

    public ApprovalActionRequest() {}

    public ApprovalActionRequest(String comments, UUID approvedOfferId) {
        this.comments = comments;
        this.approvedOfferId = approvedOfferId;
    }

    public static ApprovalActionRequest ofComments(String comments) {
        return new ApprovalActionRequest(comments, null);
    }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public UUID getApprovedOfferId() { return approvedOfferId; }
    public void setApprovedOfferId(UUID approvedOfferId) { this.approvedOfferId = approvedOfferId; }
}
