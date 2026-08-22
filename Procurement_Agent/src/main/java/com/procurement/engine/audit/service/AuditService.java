package com.procurement.engine.audit.service;

import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.audit.entity.AuditLog;
import com.procurement.engine.audit.model.AuditLogDto;
import com.procurement.engine.audit.model.ProcurementAuditResponse;
import com.procurement.engine.audit.repository.AuditLogRepository;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.model.StateTransitionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized Audit Service.
 * <p>
 * Central gateway for recording and querying structured, chronological procurement audit trails.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ProcurementRequestRepository procurementRequestRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        ProcurementRequestRepository procurementRequestRepository) {
        this.auditLogRepository = auditLogRepository;
        this.procurementRequestRepository = procurementRequestRepository;
    }

    /**
     * Records a structured audit event.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(UUID procurementId,
                           AuditEventType eventType,
                           ProcurementState state,
                           String actor,
                           String description,
                           Map<String, Object> metadata) {
        if (procurementId == null) {
            log.warn("Cannot record audit event [{}] without procurementId", eventType);
            return null;
        }

        AuditLog entry = AuditLog.builder()
                .procurementId(procurementId)
                .eventType(eventType != null ? eventType : AuditEventType.STATE_TRANSITION)
                .state(state != null ? state : ProcurementState.SUBMITTED)
                .actor(actor != null ? actor : "SYSTEM")
                .description(description != null ? description : "")
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .timestamp(Instant.now())
                .build();

        AuditLog saved = auditLogRepository.save(entry);
        log.debug("Recorded audit event [{}] for procurement [{}] in state [{}] by [{}]",
                eventType, procurementId, state, actor);
        return saved;
    }

    /**
     * Records a structured audit event resolving current state from database if available.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(UUID procurementId,
                           AuditEventType eventType,
                           String actor,
                           String description,
                           Map<String, Object> metadata) {
        ProcurementState state = ProcurementState.SUBMITTED;
        Optional<ProcurementRequest> reqOpt = procurementRequestRepository.findById(procurementId);
        if (reqOpt.isPresent()) {
            state = reqOpt.get().getStatus();
        }
        return record(procurementId, eventType, state, actor, description, metadata);
    }

    /**
     * Listens to decoupled StateTransitionEvents and records them in the audit trail.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void onStateTransition(StateTransitionEvent event) {
        if (event == null || event.getProcurementId() == null) {
            return;
        }

        Map<String, Object> meta = new HashMap<>();
        if (event.getMetadata() != null) {
            meta.putAll(event.getMetadata());
        }
        meta.put("fromState", event.getPreviousState() != null ? event.getPreviousState().name() : "NONE");
        meta.put("toState", event.getNewState() != null ? event.getNewState().name() : "NONE");

        record(
                event.getProcurementId(),
                AuditEventType.STATE_TRANSITION,
                event.getNewState(),
                event.getActor(),
                event.getReason(),
                meta
        );
    }

    /**
     * Retrieves the chronological audit trail for a procurement request.
     */
    @Transactional(readOnly = true)
    public ProcurementAuditResponse getAuditTrail(UUID procurementId) {
        if (!procurementRequestRepository.existsById(procurementId)) {
            throw new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId);
        }

        List<AuditLog> logs = auditLogRepository.findByProcurementIdOrderByTimestampAsc(procurementId);
        List<AuditLogDto> dtos = logs.stream().map(this::toDto).collect(Collectors.toList());

        return new ProcurementAuditResponse(procurementId, dtos);
    }

    private AuditLogDto toDto(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getProcurementId(),
                log.getTimestamp(),
                log.getEventType(),
                log.getState(),
                log.getActor(),
                log.getDescription(),
                log.getMetadata()
        );
    }
}
