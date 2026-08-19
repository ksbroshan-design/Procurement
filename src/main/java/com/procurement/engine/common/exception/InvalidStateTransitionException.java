package com.procurement.engine.common.exception;

import com.procurement.engine.statemachine.ProcurementState;

public class InvalidStateTransitionException extends RuntimeException {

    private ProcurementState fromState;
    private ProcurementState toState;

    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(ProcurementState fromState, ProcurementState toState, String message) {
        super(message);
        this.fromState = fromState;
        this.toState = toState;
    }

    public ProcurementState getFromState() { return fromState; }
    public ProcurementState getToState() { return toState; }
}
