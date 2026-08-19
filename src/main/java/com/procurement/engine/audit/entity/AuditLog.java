package com.procurement.engine.audit.entity;

import com.procurement.engine.statemachine.ProcurementState;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "procurement_id", nullable = false)
    private UUID procurementId;

    @CreationTimestamp
    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProcurementState state;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(nullable = false, length = 4000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new HashMap<>();

    public AuditLog() {}

    public AuditLog(UUID id, UUID procurementId, Instant timestamp, AuditEventType eventType, ProcurementState state, String actor, String description, Map<String, Object> metadata) {
        this.id = id;
        this.procurementId = procurementId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.state = state;
        this.actor = actor;
        this.description = description;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID procurementId;
        private Instant timestamp;
        private AuditEventType eventType;
        private ProcurementState state;
        private String actor;
        private String description;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder procurementId(UUID procurementId) { this.procurementId = procurementId; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder eventType(AuditEventType eventType) { this.eventType = eventType; return this; }
        public Builder state(ProcurementState state) { this.state = state; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public AuditLog build() {
            return new AuditLog(id, procurementId, timestamp, eventType, state, actor, description, metadata);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProcurementId() { return procurementId; }
    public void setProcurementId(UUID procurementId) { this.procurementId = procurementId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public AuditEventType getEventType() { return eventType; }
    public void setEventType(AuditEventType eventType) { this.eventType = eventType; }
    public ProcurementState getState() { return state; }
    public void setState(ProcurementState state) { this.state = state; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
