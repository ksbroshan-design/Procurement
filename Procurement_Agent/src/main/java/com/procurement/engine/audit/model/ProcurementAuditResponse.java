package com.procurement.engine.audit.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Chronological audit trail response for a procurement request.
 */
public class ProcurementAuditResponse {

    private final UUID procurementId;
    private final int totalEvents;
    private final List<AuditLogDto> events;

    public ProcurementAuditResponse(UUID procurementId, List<AuditLogDto> events) {
        this.procurementId = procurementId;
        this.events = events != null ? List.copyOf(events) : Collections.emptyList();
        this.totalEvents = this.events.size();
    }

    public UUID getProcurementId() { return procurementId; }
    public int getTotalEvents() { return totalEvents; }
    public List<AuditLogDto> getEvents() { return events; }
}
