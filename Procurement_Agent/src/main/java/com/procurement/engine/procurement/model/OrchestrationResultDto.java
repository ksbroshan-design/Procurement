package com.procurement.engine.procurement.model;

import com.procurement.engine.statemachine.ProcurementState;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result returned by the ProcurementOrchestrator detailing workflow execution outcome.
 */
public class OrchestrationResultDto {

    private final UUID procurementId;
    private final ProcurementState initialState;
    private final ProcurementState finalState;
    private final String status; // "COMPLETED", "WAITING_APPROVAL", "NO_RECOMMENDATION", "SEARCHING", "FAILED"
    private final String decisionMessage;
    private final String recommendationType;
    private final UUID purchaseOrderId;
    private final BigDecimal totalAmount;

    public OrchestrationResultDto(UUID procurementId,
                                  ProcurementState initialState,
                                  ProcurementState finalState,
                                  String status,
                                  String decisionMessage,
                                  String recommendationType,
                                  UUID purchaseOrderId,
                                  BigDecimal totalAmount) {
        this.procurementId = procurementId;
        this.initialState = initialState;
        this.finalState = finalState;
        this.status = status;
        this.decisionMessage = decisionMessage;
        this.recommendationType = recommendationType;
        this.purchaseOrderId = purchaseOrderId;
        this.totalAmount = totalAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID procurementId;
        private ProcurementState initialState;
        private ProcurementState finalState;
        private String status;
        private String decisionMessage;
        private String recommendationType;
        private UUID purchaseOrderId;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        public Builder procurementId(UUID procurementId) { this.procurementId = procurementId; return this; }
        public Builder initialState(ProcurementState initialState) { this.initialState = initialState; return this; }
        public Builder finalState(ProcurementState finalState) { this.finalState = finalState; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder decisionMessage(String decisionMessage) { this.decisionMessage = decisionMessage; return this; }
        public Builder recommendationType(String recommendationType) { this.recommendationType = recommendationType; return this; }
        public Builder purchaseOrderId(UUID purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; return this; }
        public Builder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }

        public OrchestrationResultDto build() {
            return new OrchestrationResultDto(procurementId, initialState, finalState, status,
                    decisionMessage, recommendationType, purchaseOrderId, totalAmount);
        }
    }

    public UUID getProcurementId() { return procurementId; }
    public ProcurementState getInitialState() { return initialState; }
    public ProcurementState getFinalState() { return finalState; }
    public String getStatus() { return status; }
    public String getDecisionMessage() { return decisionMessage; }
    public String getRecommendationType() { return recommendationType; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
