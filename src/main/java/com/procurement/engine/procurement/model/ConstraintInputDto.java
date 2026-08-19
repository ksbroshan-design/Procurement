package com.procurement.engine.procurement.model;

import com.procurement.engine.constraint.entity.ConstraintOperator;

/**
 * DTO for specifying procurement constraints in structured JSON briefs.
 */
public class ConstraintInputDto {

    private String attribute;
    private String operator; // e.g. ">=", "<=", "==", "CONTAINS", etc.
    private String value;
    private boolean mandatory = true;

    public ConstraintInputDto() {}

    public ConstraintInputDto(String attribute, String operator, String value, boolean mandatory) {
        this.attribute = attribute;
        this.operator = operator;
        this.value = value;
        this.mandatory = mandatory;
    }

    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public ConstraintOperator resolveOperator() {
        if (operator == null || operator.isBlank()) {
            return ConstraintOperator.EQUALS;
        }
        return switch (operator.trim().toUpperCase()) {
            case ">=", "GREATER_THAN_OR_EQUAL", "GTE" -> ConstraintOperator.GREATER_THAN_OR_EQUAL;
            case "<=", "LESS_THAN_OR_EQUAL", "LTE" -> ConstraintOperator.LESS_THAN_OR_EQUAL;
            case ">", "GREATER_THAN", "GT" -> ConstraintOperator.GREATER_THAN;
            case "<", "LESS_THAN", "LT" -> ConstraintOperator.LESS_THAN;
            case "!=", "NOT_EQUALS", "NEQ" -> ConstraintOperator.NOT_EQUALS;
            case "CONTAINS" -> ConstraintOperator.CONTAINS;
            case "IN" -> ConstraintOperator.IN;
            default -> ConstraintOperator.EQUALS;
        };
    }
}
