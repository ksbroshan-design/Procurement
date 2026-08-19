package com.procurement.engine.procurement.service;

import com.procurement.engine.audit.entity.AuditEventType;
import com.procurement.engine.audit.service.AuditService;
import com.procurement.engine.authorization.model.AuthorizationDecisionDto;
import com.procurement.engine.authorization.service.AuthorizationService;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.discovery.service.DiscoveryService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.model.OrchestrationResultDto;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.purchase.model.PurchaseExecutionResultDto;
import com.procurement.engine.purchase.service.PurchaseExecutionService;
import com.procurement.engine.recommendation.model.RecommendationResponse;
import com.procurement.engine.recommendation.service.RecommendationService;
import com.procurement.engine.revalidation.model.RevalidationResultDto;
import com.procurement.engine.revalidation.service.RevalidationService;
import com.procurement.engine.statemachine.ProcurementState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Deterministic Backend Procurement Orchestrator.
 * <p>
 * Coordinates discovery, constraint evaluation, TCO calculation, ranking, recommendation,
 * authorization, pre-purchase revalidation, and purchase execution strictly through the state machine.
 */
@Service
public class ProcurementOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProcurementOrchestrator.class);

    private final ProcurementRequestRepository procurementRequestRepository;
    private final DiscoveryService discoveryService;
    private final RecommendationService recommendationService;
    private final AuthorizationService authorizationService;
    private final RevalidationService revalidationService;
    private final PurchaseExecutionService purchaseExecutionService;
    private final AuditService auditService;

    public ProcurementOrchestrator(ProcurementRequestRepository procurementRequestRepository,
                                   DiscoveryService discoveryService,
                                   RecommendationService recommendationService,
                                   AuthorizationService authorizationService,
                                   RevalidationService revalidationService,
                                   PurchaseExecutionService purchaseExecutionService,
                                   AuditService auditService) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.discoveryService = discoveryService;
        this.recommendationService = recommendationService;
        this.authorizationService = authorizationService;
        this.revalidationService = revalidationService;
        this.purchaseExecutionService = purchaseExecutionService;
        this.auditService = auditService;
    }

    /**
     * Executes the deterministic end-to-end procurement workflow.
     */
    @Transactional
    public OrchestrationResultDto orchestrate(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        ProcurementState initialState = request.getStatus();
        log.info("Starting deterministic orchestration for procurement [{}] in state [{}]", procurementId, initialState);

        // Step 1: Discover & Normalize (if in early submitted / validating / searching states)
        if (request.getStatus() == ProcurementState.SUBMITTED
                || request.getStatus() == ProcurementState.VALIDATING
                || request.getStatus() == ProcurementState.SEARCHING) {
            com.procurement.engine.discovery.model.DiscoveryResult discovery = discoveryService.discoverAndEvaluate(procurementId);
            request = refresh(procurementId);

            if ("NO_ELIGIBLE_PRODUCTS".equals(discovery.getStatus()) || discovery.getEligibleCandidatesCount() == 0) {
                log.info("Procurement [{}] produced 0 eligible offers. Stopping workflow.", procurementId);
                return OrchestrationResultDto.builder()
                        .procurementId(procurementId)
                        .initialState(initialState)
                        .finalState(request.getStatus())
                        .status("NO_ELIGIBLE_PRODUCTS")
                        .decisionMessage(discovery.getMessage())
                        .recommendationType("NO_RECOMMENDATION")
                        .build();
            }
        }

        // Step 2: Generate Recommendation & TCO
        RecommendationResponse recommendation = null;
        if (request.getStatus() == ProcurementState.EVALUATING
                || request.getStatus() == ProcurementState.TCO_ANALYSIS
                || request.getStatus() == ProcurementState.RECOMMENDED) {
            recommendation = recommendationService.generateRecommendation(procurementId);
            request = refresh(procurementId);

            if ("NO_RECOMMENDATION".equals(recommendation.getRecommendationType())) {
                log.warn("Procurement [{}] produced NO_RECOMMENDATION: {}", procurementId, recommendation.getExplanation());
                return OrchestrationResultDto.builder()
                        .procurementId(procurementId)
                        .initialState(initialState)
                        .finalState(request.getStatus())
                        .status("NO_RECOMMENDATION")
                        .decisionMessage(recommendation.getExplanation())
                        .recommendationType(recommendation.getRecommendationType())
                        .build();
            }
        }

        // Step 3: Authorization Check
        AuthorizationDecisionDto authDecision = null;
        if (request.getStatus() == ProcurementState.RECOMMENDED
                || request.getStatus() == ProcurementState.AUTHORIZATION_CHECK) {
            authDecision = authorizationService.checkAuthorization(procurementId);
            request = refresh(procurementId);
        }

        // Check if HITL Human Approval is required
        if (request.getStatus() == ProcurementState.WAITING_APPROVAL) {
            log.info("Procurement [{}] escalated to WAITING_APPROVAL. Pausing orchestration for human manager.", procurementId);
            return OrchestrationResultDto.builder()
                    .procurementId(procurementId)
                    .initialState(initialState)
                    .finalState(ProcurementState.WAITING_APPROVAL)
                    .status("WAITING_APPROVAL")
                    .decisionMessage(authDecision != null ? authDecision.getExplanation() : "Escalated for human approval.")
                    .recommendationType(recommendation != null ? recommendation.getRecommendationType() : "REQUIRES_AUTHORIZATION")
                    .totalAmount(authDecision != null ? authDecision.getTotalRequestedAmount() : request.getAuthorizationLimit())
                    .build();
        }

        // Step 4: Final Pre-Purchase Revalidation
        if (request.getStatus() == ProcurementState.REVALIDATING) {
            RevalidationResultDto revalResult = revalidationService.revalidate(procurementId);
            request = refresh(procurementId);

            if (!revalResult.isValid() || request.getStatus() != ProcurementState.PURCHASING) {
                log.warn("Procurement [{}] revalidation failed. State: [{}]", procurementId, request.getStatus());
                return OrchestrationResultDto.builder()
                        .procurementId(procurementId)
                        .initialState(initialState)
                        .finalState(request.getStatus())
                        .status(revalResult.getStatus())
                        .decisionMessage(revalResult.getMessage())
                        .recommendationType(recommendation != null ? recommendation.getRecommendationType() : "UNKNOWN")
                        .build();
            }
        }

        // Step 5: Purchase Execution
        if (request.getStatus() == ProcurementState.PURCHASING) {
            PurchaseExecutionResultDto purchaseResult = purchaseExecutionService.executePurchase(procurementId);
            request = refresh(procurementId);

            log.info("Procurement [{}] successfully COMPLETED with PO [{}]", procurementId, purchaseResult.getPurchaseOrderId());
            return OrchestrationResultDto.builder()
                    .procurementId(procurementId)
                    .initialState(initialState)
                    .finalState(ProcurementState.COMPLETED)
                    .status("COMPLETED")
                    .decisionMessage(purchaseResult.getConfirmationMessage())
                    .recommendationType(recommendation != null ? recommendation.getRecommendationType() : "AUTONOMOUS_PURCHASE_READY")
                    .purchaseOrderId(purchaseResult.getPurchaseOrderId())
                    .totalAmount(purchaseResult.getTotalAmount())
                    .build();
        }

        // Default outcome
        return OrchestrationResultDto.builder()
                .procurementId(procurementId)
                .initialState(initialState)
                .finalState(request.getStatus())
                .status(request.getStatus().name())
                .decisionMessage("Procurement workflow in state: " + request.getStatus())
                .build();
    }

    private ProcurementRequest refresh(UUID id) {
        return procurementRequestRepository.findById(id).orElseThrow();
    }
}
