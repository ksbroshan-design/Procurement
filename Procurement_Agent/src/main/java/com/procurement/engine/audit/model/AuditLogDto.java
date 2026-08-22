package com.procurement.engine.audit.model;

import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.statemachine.ProcurementState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for an individual audit log entry.
 */
public class AuditLogDto {

    private final UUID id;
    private final UUID procurementId;
    private final Instant timestamp;
    private final AuditEventType eventType;
    private final ProcurementState state;
    private final String actor;
    private final String description;
    private final Map<String, Object> metadata;

    public AuditLogDto(UUID id,
                       UUID procurementId,
                       Instant timestamp,
                       AuditEventType eventType,
                       ProcurementState state,
                       String actor,
                       String description,
                       Map<String, Object> metadata) {
        this.id = id;
        this.procurementId = procurementId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.state = state;
        this.actor = actor;
        this.description = description;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public UUID getProcurementId() { return procurementId; }
    public Instant getTimestamp() { return timestamp; }
    public AuditEventType getEventType() { return eventType; }
    public ProcurementState getState() { return state; }
    public String getActor() { return actor; }
    public String getDescription() { return description; }
    public Map<String, Object> getMetadata() { return metadata; }
}
