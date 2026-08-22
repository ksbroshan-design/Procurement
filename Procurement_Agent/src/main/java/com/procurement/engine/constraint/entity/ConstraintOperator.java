package com.procurement.engine.constraint.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConstraintOperator {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    IN("IN"),
    CONTAINS("CONTAINS");

    private final String symbol;

    ConstraintOperator(String symbol) {
        this.symbol = symbol;
    }

    @JsonValue
    public String getSymbol() {
        return symbol;
    }

    @JsonCreator
    public static ConstraintOperator fromString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toUpperCase();
        for (ConstraintOperator op : values()) {
            if (op.symbol.equalsIgnoreCase(trimmed) || op.name().equalsIgnoreCase(trimmed)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown constraint operator: " + value);
    }
}
