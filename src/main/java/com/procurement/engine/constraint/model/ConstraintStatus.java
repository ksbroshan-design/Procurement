package com.procurement.engine.constraint.model;

/**
 * Result status for an individual constraint evaluation.
 */
public enum ConstraintStatus {
    /**
     * Constraint is fully satisfied.
     */
    PASS,

    /**
     * Constraint was not satisfied (e.g. value out of range, condition false,
     * or missing mandatory attribute / mandatory type mismatch).
     */
    FAIL,

    /**
     * Optional attribute is missing or has an incompatible type for evaluation.
     * Product remains eligible, with appropriate soft preference penalty.
     */
    UNKNOWN
}
