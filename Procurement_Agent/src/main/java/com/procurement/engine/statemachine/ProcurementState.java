package com.procurement.engine.statemachine;

public enum ProcurementState {
    SUBMITTED,
    VALIDATING,
    SEARCHING,
    EVALUATING,
    TCO_ANALYSIS,
    RECOMMENDED,
    NEGOTIATING,
    AUTHORIZATION_CHECK,
    WAITING_APPROVAL,
    REVALIDATING,
    PURCHASING,
    COMPLETED,
    WAITING_USER,
    REJECTED,
    FAILED
}
