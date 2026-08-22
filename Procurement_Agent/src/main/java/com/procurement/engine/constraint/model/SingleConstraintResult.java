package com.procurement.engine.constraint.model;

import com.procurement.engine.constraint.entity.ConstraintOperator;

import java.math.BigDecimal;

/**
 * Detailed outcome of evaluating a single constraint against a product attribute.
 */
public class SingleConstraintResult {

    private final String attribute;
    private final ConstraintOperator operator;
    private final String expectedValue;
    private final Object actualValue;
    private final String unit;
    private final boolean mandatory;
    private final BigDecimal weight;
    private final ConstraintStatus status;
    private final boolean passed;
    private final String reason;
    private final BigDecimal penaltyScore;

    public SingleConstraintResult(String attribute,
                                  ConstraintOperator operator,
                                  String expectedValue,
                                  Object actualValue,
                                  String unit,
                                  boolean mandatory,
                                  BigDecimal weight,
                                  ConstraintStatus status,
                                  boolean passed,
                                  String reason,
                                  BigDecimal penaltyScore) {
        this.attribute = attribute;
        this.operator = operator;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.unit = unit;
        this.mandatory = mandatory;
        this.weight = weight != null ? weight : BigDecimal.ONE;
        this.status = status;
        this.passed = passed;
        this.reason = reason;
        this.penaltyScore = penaltyScore != null ? penaltyScore : BigDecimal.ZERO;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String attribute;
        private ConstraintOperator operator;
        private String expectedValue;
        private Object actualValue;
        private String unit;
        private boolean mandatory = true;
        private BigDecimal weight = BigDecimal.ONE;
        private ConstraintStatus status;
        private boolean passed;
        private String reason;
        private BigDecimal penaltyScore = BigDecimal.ZERO;

        public Builder attribute(String attribute) { this.attribute = attribute; return this; }
        public Builder operator(ConstraintOperator operator) { this.operator = operator; return this; }
        public Builder expectedValue(String expectedValue) { this.expectedValue = expectedValue; return this; }
        public Builder actualValue(Object actualValue) { this.actualValue = actualValue; return this; }
        public Builder unit(String unit) { this.unit = unit; return this; }
        public Builder mandatory(boolean mandatory) { this.mandatory = mandatory; return this; }
        public Builder weight(BigDecimal weight) { this.weight = weight; return this; }
        public Builder status(ConstraintStatus status) { this.status = status; return this; }
        public Builder passed(boolean passed) { this.passed = passed; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder penaltyScore(BigDecimal penaltyScore) { this.penaltyScore = penaltyScore; return this; }

        public SingleConstraintResult build() {
            return new SingleConstraintResult(attribute, operator, expectedValue, actualValue, unit, mandatory, weight, status, passed, reason, penaltyScore);
        }
    }

    public String getAttribute() { return attribute; }
    public ConstraintOperator getOperator() { return operator; }
    public String getExpectedValue() { return expectedValue; }
    public Object getActualValue() { return actualValue; }
    public String getUnit() { return unit; }
    public boolean isMandatory() { return mandatory; }
    public BigDecimal getWeight() { return weight; }
    public ConstraintStatus getStatus() { return status; }
    public boolean isPassed() { return passed; }
    public String getReason() { return reason; }
    public BigDecimal getPenaltyScore() { return penaltyScore; }

    @Override
    public String toString() {
        return "SingleConstraintResult{" +
                "attribute='" + attribute + '\'' +
                ", operator=" + operator +
                ", expectedValue='" + expectedValue + '\'' +
                ", actualValue=" + actualValue +
                ", mandatory=" + mandatory +
                ", status=" + status +
                ", passed=" + passed +
                ", reason='" + reason + '\'' +
                '}';
    }
}
