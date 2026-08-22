package com.procurement.engine.authorization.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Structured authorization decision returned when checking authority on a recommendation.
 */
public class AuthorizationDecisionDto {

    private final UUID procurementId;
    private final UUID selectedOfferId;
    private final String selectedProductName;
    private final String selectedVendorName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalRequestedAmount;
    private final BigDecimal authorizationLimit;
    private final BigDecimal excessAmount;
    private final boolean withinAuthorization;
    private final String decision; // "AUTO_AUTHORIZED", "REQUIRES_APPROVAL"
    private final String nextState; // "REVALIDATING", "WAITING_APPROVAL"
    private final String exceptionType; // "NONE", "BUDGET_OVERRIDE", "LIMIT_EXCEEDED"
    private final String explanation;

    public AuthorizationDecisionDto(UUID procurementId,
                                    UUID selectedOfferId,
                                    String selectedProductName,
                                    String selectedVendorName,
                                    int quantity,
                                    BigDecimal unitPrice,
                                    BigDecimal totalRequestedAmount,
                                    BigDecimal authorizationLimit,
                                    BigDecimal excessAmount,
                                    boolean withinAuthorization,
                                    String decision,
                                    String nextState,
                                    String exceptionType,
                                    String explanation) {
        this.procurementId = procurementId;
        this.selectedOfferId = selectedOfferId;
        this.selectedProductName = selectedProductName;
        this.selectedVendorName = selectedVendorName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalRequestedAmount = totalRequestedAmount;
        this.authorizationLimit = authorizationLimit;
        this.excessAmount = excessAmount;
        this.withinAuthorization = withinAuthorization;
        this.decision = decision;
        this.nextState = nextState;
        this.exceptionType = exceptionType;
        this.explanation = explanation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID procurementId;
        private UUID selectedOfferId;
        private String selectedProductName;
        private String selectedVendorName;
        private int quantity;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal totalRequestedAmount = BigDecimal.ZERO;
        private BigDecimal authorizationLimit = BigDecimal.ZERO;
        private BigDecimal excessAmount = BigDecimal.ZERO;
        private boolean withinAuthorization;
        private String decision;
        private String nextState;
        private String exceptionType = "NONE";
        private String explanation;

        public Builder procurementId(UUID procurementId) { this.procurementId = procurementId; return this; }
        public Builder selectedOfferId(UUID selectedOfferId) { this.selectedOfferId = selectedOfferId; return this; }
        public Builder selectedProductName(String selectedProductName) { this.selectedProductName = selectedProductName; return this; }
        public Builder selectedVendorName(String selectedVendorName) { this.selectedVendorName = selectedVendorName; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public Builder totalRequestedAmount(BigDecimal totalRequestedAmount) { this.totalRequestedAmount = totalRequestedAmount; return this; }
        public Builder authorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; return this; }
        public Builder excessAmount(BigDecimal excessAmount) { this.excessAmount = excessAmount; return this; }
        public Builder withinAuthorization(boolean withinAuthorization) { this.withinAuthorization = withinAuthorization; return this; }
        public Builder decision(String decision) { this.decision = decision; return this; }
        public Builder nextState(String nextState) { this.nextState = nextState; return this; }
        public Builder exceptionType(String exceptionType) { this.exceptionType = exceptionType; return this; }
        public Builder explanation(String explanation) { this.explanation = explanation; return this; }

        public AuthorizationDecisionDto build() {
            return new AuthorizationDecisionDto(procurementId, selectedOfferId, selectedProductName,
                    selectedVendorName, quantity, unitPrice, totalRequestedAmount, authorizationLimit,
                    excessAmount, withinAuthorization, decision, nextState, exceptionType, explanation);
        }
    }

    public UUID getProcurementId() { return procurementId; }
    public UUID getSelectedOfferId() { return selectedOfferId; }
    public String getSelectedProductName() { return selectedProductName; }
    public String getSelectedVendorName() { return selectedVendorName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalRequestedAmount() { return totalRequestedAmount; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public BigDecimal getExcessAmount() { return excessAmount; }
    public boolean isWithinAuthorization() { return withinAuthorization; }
    public String getDecision() { return decision; }
    public String getNextState() { return nextState; }
    public String getExceptionType() { return exceptionType; }
    public String getExplanation() { return explanation; }
}
