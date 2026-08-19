package com.procurement.engine.statemachine.model;

import com.procurement.engine.statemachine.ProcurementState;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event capturing state transition details for auditing and downstream listeners.
 * Decoupled from persistent repositories.
 */
public class StateTransitionEvent {

    private final UUID procurementId;
    private final ProcurementState previousState;
    private final ProcurementState newState;
    private final String actor;
    private final String reason;
    private final Instant timestamp;
    private final Map<String, Object> metadata;

    public StateTransitionEvent(UUID procurementId,
                                ProcurementState previousState,
                                ProcurementState newState,
                                String actor,
                                String reason,
                                Instant timestamp,
                                Map<String, Object> metadata) {
        this.procurementId = procurementId;
        this.previousState = previousState;
        this.newState = newState;
        this.actor = actor != null ? actor : "SYSTEM";
        this.reason = reason != null ? reason : "";
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID procurementId;
        private ProcurementState previousState;
        private ProcurementState newState;
        private String actor = "SYSTEM";
        private String reason = "";
        private Instant timestamp = Instant.now();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder procurementId(UUID procurementId) { this.procurementId = procurementId; return this; }
        public Builder previousState(ProcurementState previousState) { this.previousState = previousState; return this; }
        public Builder newState(ProcurementState newState) { this.newState = newState; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public StateTransitionEvent build() {
            return new StateTransitionEvent(procurementId, previousState, newState, actor, reason, timestamp, metadata);
        }
    }

    public UUID getProcurementId() { return procurementId; }
    public ProcurementState getPreviousState() { return previousState; }
    public ProcurementState getNewState() { return newState; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return "StateTransitionEvent{" +
                "procurementId=" + procurementId +
                ", previousState=" + previousState +
                ", newState=" + newState +
                ", actor='" + actor + '\'' +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
