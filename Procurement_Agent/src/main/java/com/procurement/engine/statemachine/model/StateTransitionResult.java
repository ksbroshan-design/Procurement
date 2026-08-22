package com.procurement.engine.statemachine.model;

import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.statemachine.ProcurementState;

import java.util.UUID;

/**
 * Result returned by the ProcurementStateMachine after attempting or executing a state transition.
 */
public class StateTransitionResult {

    private final boolean success;
    private final UUID procurementId;
    private final ProcurementState previousState;
    private final ProcurementState newState;
    private final ProcurementRequest request;
    private final StateTransitionEvent event;
    private final String message;

    public StateTransitionResult(boolean success,
                                 UUID procurementId,
                                 ProcurementState previousState,
                                 ProcurementState newState,
                                 ProcurementRequest request,
                                 StateTransitionEvent event,
                                 String message) {
        this.success = success;
        this.procurementId = procurementId;
        this.previousState = previousState;
        this.newState = newState;
        this.request = request;
        this.event = event;
        this.message = message;
    }

    public static StateTransitionResult success(ProcurementRequest request, ProcurementState previousState, StateTransitionEvent event) {
        return new StateTransitionResult(
                true,
                request != null ? request.getId() : null,
                previousState,
                request != null ? request.getStatus() : null,
                request,
                event,
                "State transition successful"
        );
    }

    public static StateTransitionResult failure(UUID procurementId, ProcurementState previousState, ProcurementState targetState, String message) {
        return new StateTransitionResult(
                false,
                procurementId,
                previousState,
                targetState,
                null,
                null,
                message
        );
    }

    public boolean isSuccess() { return success; }
    public UUID getProcurementId() { return procurementId; }
    public ProcurementState getPreviousState() { return previousState; }
    public ProcurementState getNewState() { return newState; }
    public ProcurementRequest getRequest() { return request; }
    public StateTransitionEvent getEvent() { return event; }
    public String getMessage() { return message; }
}
