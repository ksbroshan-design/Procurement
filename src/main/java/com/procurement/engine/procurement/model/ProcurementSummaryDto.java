package com.procurement.engine.procurement.model;

import com.procurement.engine.statemachine.ProcurementState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary representation of a procurement request and its active status.
 */
public class ProcurementSummaryDto {

    private final UUID id;
    private final String category;
    private final int quantity;
    private final BigDecimal authorizationLimit;
    private final ProcurementState status;
    private final UUID selectedOfferId;
    private final String selectedProductName;
    private final String selectedVendorName;
    private final int constraintCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    public ProcurementSummaryDto(UUID id,
                                 String category,
                                 int quantity,
                                 BigDecimal authorizationLimit,
                                 ProcurementState status,
                                 UUID selectedOfferId,
                                 String selectedProductName,
                                 String selectedVendorName,
                                 int constraintCount,
                                 Instant createdAt,
                                 Instant updatedAt) {
        this.id = id;
        this.category = category;
        this.quantity = quantity;
        this.authorizationLimit = authorizationLimit;
        this.status = status;
        this.selectedOfferId = selectedOfferId;
        this.selectedProductName = selectedProductName;
        this.selectedVendorName = selectedVendorName;
        this.constraintCount = constraintCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public ProcurementState getStatus() { return status; }
    public UUID getSelectedOfferId() { return selectedOfferId; }
    public String getSelectedProductName() { return selectedProductName; }
    public String getSelectedVendorName() { return selectedVendorName; }
    public int getConstraintCount() { return constraintCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
