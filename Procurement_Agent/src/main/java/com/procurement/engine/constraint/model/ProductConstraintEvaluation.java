package com.procurement.engine.constraint.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated evaluation of all constraints for a specific product.
 */
public class ProductConstraintEvaluation {

    private final UUID productId;
    private final String productName;
    private final String category;
    private final boolean eligible;
    private final int totalConstraints;
    private final int passedCount;
    private final int hardFailureCount;
    private final int softFailureCount;
    private final BigDecimal totalPenalty;
    private final List<String> failedMandatoryAttributes;
    private final List<SingleConstraintResult> constraintResults;
    private final String summary;

    public ProductConstraintEvaluation(UUID productId,
                                       String productName,
                                       String category,
                                       boolean eligible,
                                       int totalConstraints,
                                       int passedCount,
                                       int hardFailureCount,
                                       int softFailureCount,
                                       BigDecimal totalPenalty,
                                       List<String> failedMandatoryAttributes,
                                       List<SingleConstraintResult> constraintResults,
                                       String summary) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.eligible = eligible;
        this.totalConstraints = totalConstraints;
        this.passedCount = passedCount;
        this.hardFailureCount = hardFailureCount;
        this.softFailureCount = softFailureCount;
        this.totalPenalty = totalPenalty != null ? totalPenalty : BigDecimal.ZERO;
        this.failedMandatoryAttributes = failedMandatoryAttributes != null ? List.copyOf(failedMandatoryAttributes) : Collections.emptyList();
        this.constraintResults = constraintResults != null ? List.copyOf(constraintResults) : Collections.emptyList();
        this.summary = summary;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID productId;
        private String productName;
        private String category;
        private boolean eligible = true;
        private int totalConstraints;
        private int passedCount;
        private int hardFailureCount;
        private int softFailureCount;
        private BigDecimal totalPenalty = BigDecimal.ZERO;
        private List<String> failedMandatoryAttributes = new ArrayList<>();
        private List<SingleConstraintResult> constraintResults = new ArrayList<>();
        private String summary;

        public Builder productId(UUID productId) { this.productId = productId; return this; }
        public Builder productName(String productName) { this.productName = productName; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder eligible(boolean eligible) { this.eligible = eligible; return this; }
        public Builder totalConstraints(int totalConstraints) { this.totalConstraints = totalConstraints; return this; }
        public Builder passedCount(int passedCount) { this.passedCount = passedCount; return this; }
        public Builder hardFailureCount(int hardFailureCount) { this.hardFailureCount = hardFailureCount; return this; }
        public Builder softFailureCount(int softFailureCount) { this.softFailureCount = softFailureCount; return this; }
        public Builder totalPenalty(BigDecimal totalPenalty) { this.totalPenalty = totalPenalty; return this; }
        public Builder failedMandatoryAttributes(List<String> failedMandatoryAttributes) { this.failedMandatoryAttributes = failedMandatoryAttributes; return this; }
        public Builder constraintResults(List<SingleConstraintResult> constraintResults) { this.constraintResults = constraintResults; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }

        public ProductConstraintEvaluation build() {
            return new ProductConstraintEvaluation(
                    productId, productName, category, eligible,
                    totalConstraints, passedCount, hardFailureCount, softFailureCount,
                    totalPenalty, failedMandatoryAttributes, constraintResults, summary
            );
        }
    }

    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public boolean isEligible() { return eligible; }
    public int getTotalConstraints() { return totalConstraints; }
    public int getPassedCount() { return passedCount; }
    public int getHardFailureCount() { return hardFailureCount; }
    public int getSoftFailureCount() { return softFailureCount; }
    public BigDecimal getTotalPenalty() { return totalPenalty; }
    public List<String> getFailedMandatoryAttributes() { return failedMandatoryAttributes; }
    public List<SingleConstraintResult> getConstraintResults() { return constraintResults; }
    public String getSummary() { return summary; }
}
