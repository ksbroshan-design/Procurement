package com.procurement.engine.statemachine;

import com.procurement.engine.common.exception.InvalidStateTransitionException;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.config.EngineProperties;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.statemachine.model.StateTransitionEvent;
import com.procurement.engine.statemachine.model.StateTransitionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Authoritative State Machine for Procurement Requests.
 * <p>
 * Enforces valid state transitions, prevents illegal bypasses, manages retry limits
 * for revalidation via {@link EngineProperties}, and emits transition events.
 */
@Service
public class ProcurementStateMachine {

    private static final Logger log = LoggerFactory.getLogger(ProcurementStateMachine.class);

    private static final Map<ProcurementState, Set<ProcurementState>> VALID_TRANSITIONS = new EnumMap<>(ProcurementState.class);

    static {
        // SUBMITTED -> VALIDATING, FAILED
        VALID_TRANSITIONS.put(ProcurementState.SUBMITTED, Set.of(
                ProcurementState.VALIDATING,
                ProcurementState.FAILED
        ));

        // VALIDATING -> SEARCHING, WAITING_USER, FAILED
        VALID_TRANSITIONS.put(ProcurementState.VALIDATING, Set.of(
                ProcurementState.SEARCHING,
                ProcurementState.WAITING_USER,
                ProcurementState.FAILED
        ));

        // SEARCHING -> EVALUATING, WAITING_USER, FAILED
        VALID_TRANSITIONS.put(ProcurementState.SEARCHING, Set.of(
                ProcurementState.EVALUATING,
                ProcurementState.WAITING_USER,
                ProcurementState.FAILED
        ));

        // EVALUATING -> TCO_ANALYSIS, WAITING_USER, FAILED
        VALID_TRANSITIONS.put(ProcurementState.EVALUATING, Set.of(
                ProcurementState.TCO_ANALYSIS,
                ProcurementState.WAITING_USER,
                ProcurementState.FAILED
        ));

        // TCO_ANALYSIS -> RECOMMENDED, FAILED
        VALID_TRANSITIONS.put(ProcurementState.TCO_ANALYSIS, Set.of(
                ProcurementState.RECOMMENDED,
                ProcurementState.FAILED
        ));

        // RECOMMENDED -> NEGOTIATING, AUTHORIZATION_CHECK, WAITING_USER, FAILED
        VALID_TRANSITIONS.put(ProcurementState.RECOMMENDED, Set.of(
                ProcurementState.NEGOTIATING,
                ProcurementState.AUTHORIZATION_CHECK,
                ProcurementState.WAITING_USER,
                ProcurementState.FAILED
        ));

        // NEGOTIATING -> AUTHORIZATION_CHECK, FAILED
        VALID_TRANSITIONS.put(ProcurementState.NEGOTIATING, Set.of(
                ProcurementState.AUTHORIZATION_CHECK,
                ProcurementState.FAILED
        ));

        // AUTHORIZATION_CHECK -> REVALIDATING (if within limit), WAITING_APPROVAL (if exceeded), FAILED
        VALID_TRANSITIONS.put(ProcurementState.AUTHORIZATION_CHECK, Set.of(
                ProcurementState.REVALIDATING,
                ProcurementState.WAITING_APPROVAL,
                ProcurementState.FAILED
        ));

        // WAITING_APPROVAL -> REVALIDATING (approved), REJECTED (human rejection), WAITING_USER, FAILED
        VALID_TRANSITIONS.put(ProcurementState.WAITING_APPROVAL, Set.of(
                ProcurementState.REVALIDATING,
                ProcurementState.REJECTED,
                ProcurementState.WAITING_USER,
                ProcurementState.FAILED
        ));

        // REVALIDATING -> PURCHASING (pass), SEARCHING (retry), WAITING_USER (max retries exhausted), FAILED
        VALID_TRANSITIONS.put(ProcurementState.REVALIDATING, Set.of(
                ProcurementState.PURCHASING,
                ProcurementState.SEARCHING,
                ProcurementState.WAITING_USER,
                ProcurementState.FAILED
        ));

        // PURCHASING -> COMPLETED, FAILED
        VALID_TRANSITIONS.put(ProcurementState.PURCHASING, Set.of(
                ProcurementState.COMPLETED,
                ProcurementState.FAILED
        ));

        // WAITING_USER -> VALIDATING, SEARCHING, REJECTED, FAILED
        VALID_TRANSITIONS.put(ProcurementState.WAITING_USER, Set.of(
                ProcurementState.VALIDATING,
                ProcurementState.SEARCHING,
                ProcurementState.REJECTED,
                ProcurementState.FAILED
        ));

        // FAILED -> SUBMITTED, VALIDATING
        VALID_TRANSITIONS.put(ProcurementState.FAILED, Set.of(
                ProcurementState.SUBMITTED,
                ProcurementState.VALIDATING
        ));

        // Terminal states
        VALID_TRANSITIONS.put(ProcurementState.REJECTED, Collections.emptySet());
        VALID_TRANSITIONS.put(ProcurementState.COMPLETED, Collections.emptySet());
    }

    private final ProcurementRequestRepository procurementRequestRepository;
    private final EngineProperties engineProperties;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    public ProcurementStateMachine(ProcurementRequestRepository procurementRequestRepository,
                                   EngineProperties engineProperties) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.engineProperties = engineProperties;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProcurementStateMachine(ProcurementRequestRepository procurementRequestRepository,
                                   EngineProperties engineProperties,
                                   @org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.engineProperties = engineProperties;
        this.eventPublisher = eventPublisher;
    }
    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Checks whether a transition between two states is valid.
     */
    public boolean isValidTransition(ProcurementState from, ProcurementState to) {
        if (from == null || to == null) {
            return false;
        }
        Set<ProcurementState> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Asserts that a state transition is valid, throwing InvalidStateTransitionException otherwise.
     */
    public void validateTransition(ProcurementState from, ProcurementState to) {
        if (!isValidTransition(from, to)) {
            String msg = String.format("Invalid state transition from [%s] to [%s]. Allowed next states: %s",
                    from, to, getValidNextStates(from));
            log.warn(msg);
            throw new InvalidStateTransitionException(from, to, msg);
        }
    }

    /**
     * Returns the set of valid next states from the given state.
     */
    public Set<ProcurementState> getValidNextStates(ProcurementState current) {
        if (current == null) {
            return Collections.emptySet();
        }
        return VALID_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }

    /**
     * Executes a validated state transition on a ProcurementRequest and persists the change.
     */
    @Transactional
    public StateTransitionResult transition(ProcurementRequest request,
                                            ProcurementState targetState,
                                            String actor,
                                            String reason,
                                            Map<String, Object> metadata) {
        if (request == null) {
            throw new IllegalArgumentException("ProcurementRequest cannot be null");
        }

        ProcurementState previousState = request.getStatus();
        validateTransition(previousState, targetState);

        request.setStatus(targetState);
        ProcurementRequest saved = procurementRequestRepository.save(request);

        StateTransitionEvent event = StateTransitionEvent.builder()
                .procurementId(saved.getId())
                .previousState(previousState)
                .newState(targetState)
                .actor(actor != null ? actor : "SYSTEM")
                .reason(reason != null ? reason : "")
                .timestamp(Instant.now())
                .metadata(metadata != null ? metadata : Collections.emptyMap())
                .build();

        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }

        log.info("Transitioned procurement [{}] from [{}] to [{}] by [{}] - Reason: {}",
                saved.getId(), previousState, targetState, actor, reason);

        return StateTransitionResult.success(saved, previousState, event);
    }

    /**
     * Executes a validated state transition by procurement ID.
     */
    @Transactional
    public StateTransitionResult transition(UUID procurementId,
                                            ProcurementState targetState,
                                            String actor,
                                            String reason,
                                            Map<String, Object> metadata) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));
        return transition(request, targetState, actor, reason, metadata);
    }

    /**
     * Handles authorization check outcome:
     * - Within autonomous limit: transitions to REVALIDATING
     * - Exceeds limit: transitions to WAITING_APPROVAL
     */
    @Transactional
    public StateTransitionResult handleAuthorizationOutcome(ProcurementRequest request,
                                                            boolean withinLimit,
                                                            String actor,
                                                            String reason,
                                                            Map<String, Object> metadata) {
        ProcurementState targetState = withinLimit ? ProcurementState.REVALIDATING : ProcurementState.WAITING_APPROVAL;
        String desc = reason != null ? reason : (withinLimit
                ? "Within autonomous authorization limit. Proceeding to revalidation."
                : "Exceeds autonomous authorization limit. Escalated for human approval.");

        return transition(request, targetState, actor, desc, metadata);
    }

    /**
     * Handles human approval decision:
     * - Approved: transitions to REVALIDATING
     * - Rejected: transitions to REJECTED
     */
    @Transactional
    public StateTransitionResult handleApprovalDecision(ProcurementRequest request,
                                                        boolean approved,
                                                        String actor,
                                                        String comments,
                                                        Map<String, Object> metadata) {
        ProcurementState targetState = approved ? ProcurementState.REVALIDATING : ProcurementState.REJECTED;
        String desc = comments != null ? comments : (approved
                ? "Human approval granted. Proceeding to revalidation."
                : "Human approval rejected.");

        return transition(request, targetState, actor, desc, metadata);
    }

    /**
     * Handles revalidation outcome:
     * - Success: transitions to PURCHASING
     * - Failure / Stale Offer: checks retry attempts against EngineProperties
     *   - If attempts < maxRetries: increments attempt count and transitions to SEARCHING
     *   - If attempts >= maxRetries: transitions to WAITING_USER
     */
    @Transactional
    public StateTransitionResult handleRevalidationOutcome(ProcurementRequest request,
                                                           boolean revalidationPassed,
                                                           String actor,
                                                           String reason,
                                                           Map<String, Object> metadata) {
        if (revalidationPassed) {
            return transition(request, ProcurementState.PURCHASING, actor,
                    reason != null ? reason : "Pre-purchase revalidation successful.", metadata);
        }

        int maxRetries = engineProperties.getRevalidation().getMaxRetryAttempts();
        int currentAttempts = request.getRevalidationAttempts();

        if (currentAttempts < maxRetries) {
            request.setRevalidationAttempts(currentAttempts + 1);
            String retryReason = String.format("Revalidation failed: %s. Initiating retry attempt (%d/%d).",
                    reason != null ? reason : "Stale offer/availability changed",
                    request.getRevalidationAttempts(), maxRetries);
            return transition(request, ProcurementState.SEARCHING, actor, retryReason, metadata);
        } else {
            String exhaustedReason = String.format("Revalidation failed: %s. Maximum retries (%d) exhausted.",
                    reason != null ? reason : "Offer persistently unavailable", maxRetries);
            return transition(request, ProcurementState.WAITING_USER, actor, exhaustedReason, metadata);
        }
    }

    /**
     * Handles purchase execution outcome:
     * - Success: transitions to COMPLETED
     * - Failure: transitions to FAILED
     */
    @Transactional
    public StateTransitionResult handlePurchaseOutcome(ProcurementRequest request,
                                                       boolean purchaseSuccess,
                                                       String actor,
                                                       String reason,
                                                       Map<String, Object> metadata) {
        ProcurementState targetState = purchaseSuccess ? ProcurementState.COMPLETED : ProcurementState.FAILED;
        String desc = reason != null ? reason : (purchaseSuccess
                ? "Purchase order placed and confirmed successfully."
                : "Purchase order execution failed.");

        return transition(request, targetState, actor, desc, metadata);
    }
}
