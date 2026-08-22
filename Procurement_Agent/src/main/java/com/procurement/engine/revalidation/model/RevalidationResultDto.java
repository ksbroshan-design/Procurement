package com.procurement.engine.revalidation.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Summary DTO of the pre-purchase revalidation outcome for a procurement request.
 */
public class RevalidationResultDto {

    private final UUID procurementId;
    private final UUID offerId;
    private final String productName;
    private final String vendorName;
    private final String status; // "VALID", "STALE", "INVALID", "EXHAUSTED"
    private final boolean valid;
    private final int revalidationAttempts;
    private final int maxRetryAttempts;
    private final List<RevalidationCheckDto> checks;
    private final String message;
    private final String nextState;

    public RevalidationResultDto(UUID procurementId,
                                 UUID offerId,
                                 String productName,
                                 String vendorName,
                                 String status,
                                 boolean valid,
                                 int revalidationAttempts,
                                 int maxRetryAttempts,
                                 List<RevalidationCheckDto> checks,
                                 String message,
                                 String nextState) {
        this.procurementId = procurementId;
        this.offerId = offerId;
        this.productName = productName;
        this.vendorName = vendorName;
        this.status = status;
        this.valid = valid;
        this.revalidationAttempts = revalidationAttempts;
        this.maxRetryAttempts = maxRetryAttempts;
        this.checks = checks != null ? List.copyOf(checks) : Collections.emptyList();
        this.message = message;
        this.nextState = nextState;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID procurementId;
        private UUID offerId;
        private String productName;
        private String vendorName;
        private String status = "VALID";
        private boolean valid = true;
        private int revalidationAttempts = 0;
        private int maxRetryAttempts = 3;
        private List<RevalidationCheckDto> checks = Collections.emptyList();
        private String message;
        private String nextState;

        public Builder procurementId(UUID procurementId) { this.procurementId = procurementId; return this; }
        public Builder offerId(UUID offerId) { this.offerId = offerId; return this; }
        public Builder productName(String productName) { this.productName = productName; return this; }
        public Builder vendorName(String vendorName) { this.vendorName = vendorName; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder valid(boolean valid) { this.valid = valid; return this; }
        public Builder revalidationAttempts(int revalidationAttempts) { this.revalidationAttempts = revalidationAttempts; return this; }
        public Builder maxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; return this; }
        public Builder checks(List<RevalidationCheckDto> checks) { this.checks = checks; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder nextState(String nextState) { this.nextState = nextState; return this; }

        public RevalidationResultDto build() {
            return new RevalidationResultDto(procurementId, offerId, productName, vendorName, status,
                    valid, revalidationAttempts, maxRetryAttempts, checks, message, nextState);
        }
    }

    public UUID getProcurementId() { return procurementId; }
    public UUID getOfferId() { return offerId; }
    public String getProductName() { return productName; }
    public String getVendorName() { return vendorName; }
    public String getStatus() { return status; }
    public boolean isValid() { return valid; }
    public int getRevalidationAttempts() { return revalidationAttempts; }
    public int getMaxRetryAttempts() { return maxRetryAttempts; }
    public List<RevalidationCheckDto> getChecks() { return checks; }
    public String getMessage() { return message; }
    public String getNextState() { return nextState; }
}
