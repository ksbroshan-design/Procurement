package com.procurement.engine.authorization.service;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.authorization.model.AuthorizationDecisionDto;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.ranking.model.RankedOfferDto;
import com.procurement.engine.recommendation.model.RecommendationResponse;
import com.procurement.engine.recommendation.service.RecommendationService;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic Authorization Service.
 *
 * <p>
 * Authoritatively evaluates purchase amounts against the authenticated
 * user's authorization limit, manages approval generation, and drives
 * state transitions to REVALIDATING or WAITING_APPROVAL.
 * </p>
 *
 * <p>
 * IMPORTANT:
 * The authorization limit is sourced from the authenticated user's
 * configured authorization policy. The ProcurementRequest's
 * authorizationLimit field is NOT trusted as an authorization override.
 * </p>
 */
@Service
public class AuthorizationService {

    private static final Logger log =
            LoggerFactory.getLogger(AuthorizationService.class);

    private final RecommendationService recommendationService;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final ApprovalRepository approvalRepository;
    private final EffectiveAuthorizationResolver effectiveAuthorizationResolver;

    public AuthorizationService(
            RecommendationService recommendationService,
            ProcurementStateMachine stateMachine,
            ProcurementRequestRepository procurementRequestRepository,
            VendorOfferRepository vendorOfferRepository,
            ApprovalRepository approvalRepository,
            EffectiveAuthorizationResolver effectiveAuthorizationResolver) {

        this.recommendationService = recommendationService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.approvalRepository = approvalRepository;
        this.effectiveAuthorizationResolver = effectiveAuthorizationResolver;
    }

    /**
     * Checks authorization for a procurement decision.
     *
     * <p>
     * Flow:
     *
     * <ul>
     *     <li>Generate authoritative recommendation.</li>
     *     <li>Resolve authorization limit from authenticated user.</li>
     *     <li>If within limit -> automatically authorize.</li>
     *     <li>If over limit -> create pending approval and move to WAITING_APPROVAL.</li>
     * </ul>
     */
    @Transactional
    public AuthorizationDecisionDto checkAuthorization(UUID procurementId) {

        ProcurementRequest request = procurementRequestRepository
                .findById(procurementId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "ProcurementRequest not found with id: "
                                        + procurementId));

        /*
         * Generate the authoritative recommendation first.
         */
        RecommendationResponse recommendation =
                recommendationService.generateRecommendation(procurementId);
        request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        /*
         * No eligible recommendation means there is nothing to authorize.
         */
        if ("NO_RECOMMENDATION".equals(
                recommendation.getRecommendationType())) {

            if (request.getStatus() == ProcurementState.RECOMMENDED) {
                stateMachine.transition(
                        request,
                        ProcurementState.WAITING_USER,
                        "AUTHORIZATION_SERVICE",
                        "No eligible recommendation available. Human review required to relax constraints.",
                        Map.of("recommendationType", "NO_RECOMMENDATION")
                );
            }

            return AuthorizationDecisionDto.builder()
                    .procurementId(procurementId)
                    .withinAuthorization(false)
                    .decision("NO_RECOMMENDATION")
                    .nextState("WAITING_USER")
                    .explanation(recommendation.getExplanation())
                    .build();
        }

        /*
         * Advance state from RECOMMENDED to AUTHORIZATION_CHECK.
         */
        if (request.getStatus() == ProcurementState.RECOMMENDED) {

            stateMachine.transition(
                    request,
                    ProcurementState.AUTHORIZATION_CHECK,
                    "AUTHORIZATION_SERVICE",
                    "Checking authorization limits and policy compliance",
                    Map.of()
            );
        }

        int quantity = request.getQuantity();

        /*
         * IMPORTANT:
         * Resolve the authorization limit ONLY from the authenticated user.
         */
        BigDecimal effectiveLimit = resolveEffectiveLimit(request);

        log.info(
                "Authorization check for procurement [{}]: user=[{}], limit=[{}]",
                procurementId,
                request.getUser() != null
                        ? request.getUser().getEmail()
                        : "UNKNOWN",
                effectiveLimit
        );

        /*
         * ================================================================
         * CASE 1: BUDGET OVERRIDE RECOMMENDATION
         * ================================================================
         *
         * This is a special recommendation where an exception candidate
         * is more economically attractive than the best compliant option.
         *
         * It still requires human approval.
         */
        if ("BUDGET_OVERRIDE_RECOMMENDED".equals(
                recommendation.getRecommendationType())
                && recommendation.getProposedExceptionOffer() != null) {

            RankedOfferDto exceptionOffer =
                    recommendation.getProposedExceptionOffer();

            RankedOfferDto bestEligible =
                    recommendation.getBestEligibleOption();

            BigDecimal requestedAmount =
                    exceptionOffer.getPrice();

            BigDecimal excessAmount =
                    requestedAmount
                            .subtract(effectiveLimit)
                            .max(BigDecimal.ZERO);

            BigDecimal tcoSavings =
                    bestEligible != null
                            ? bestEligible.getTco()
                              .subtract(exceptionOffer.getTco())
                            : BigDecimal.ZERO;

            String explanation = String.format(
                    "Exception candidate '%s' from %s costs ₹%s total "
                            + "(₹%s/unit for %d units), exceeding the "
                            + "₹%s limit by ₹%s. However, its projected "
                            + "3-year TCO of ₹%s provides ₹%s savings over "
                            + "compliant option '%s' (TCO: ₹%s) with "
                            + "%d-year warranty (vs %d-year). "
                            + "Requires managerial authorization to "
                            + "approve budget exception.",

                    exceptionOffer.getProductName(),
                    exceptionOffer.getVendorName(),
                    requestedAmount,
                    exceptionOffer.getUnitPrice(),
                    quantity,
                    effectiveLimit,
                    excessAmount,
                    exceptionOffer.getTco(),
                    tcoSavings,

                    bestEligible != null
                            ? bestEligible.getProductName()
                            : "Compliant Option",

                    bestEligible != null
                            ? bestEligible.getTco()
                            : BigDecimal.ZERO,

                    exceptionOffer.getWarrantyYears(),

                    bestEligible != null
                            ? bestEligible.getWarrantyYears()
                            : 1
            );

            VendorOffer offerEntity =
                    vendorOfferRepository
                            .findById(exceptionOffer.getOfferId())
                            .orElse(null);

            /*
             * Create or update pending approval idempotently.
             */
            createOrUpdatePendingApproval(
                    request,
                    offerEntity,
                    requestedAmount,
                    effectiveLimit,
                    excessAmount,
                    "BUDGET_OVERRIDE",
                    explanation
            );

            /*
             * Move procurement into HITL approval state.
             */
            if (request.getStatus()
                    == ProcurementState.AUTHORIZATION_CHECK) {

                stateMachine.transition(
                        request,
                        ProcurementState.WAITING_APPROVAL,
                        "AUTHORIZATION_SERVICE",
                        "Budget exception recommendation escalated for human approval",
                        Map.of(
                                "exceptionType",
                                "BUDGET_OVERRIDE",

                                "excessAmount",
                                excessAmount.toString()
                        )
                );
            }

            return AuthorizationDecisionDto.builder()
                    .procurementId(procurementId)
                    .selectedOfferId(exceptionOffer.getOfferId())
                    .selectedProductName(exceptionOffer.getProductName())
                    .selectedVendorName(exceptionOffer.getVendorName())
                    .quantity(quantity)
                    .unitPrice(exceptionOffer.getUnitPrice())
                    .totalRequestedAmount(requestedAmount)
                    .authorizationLimit(effectiveLimit)
                    .excessAmount(excessAmount)
                    .withinAuthorization(false)
                    .decision("REQUIRES_APPROVAL")
                    .nextState("WAITING_APPROVAL")
                    .exceptionType("BUDGET_OVERRIDE")
                    .explanation(explanation)
                    .build();
        }

        /*
         * ================================================================
         * CASE 2: STANDARD COMPLIANT RECOMMENDATION
         * ================================================================
         */
        RankedOfferDto selectedOffer =
                recommendation.getBestEligibleOption();

        /*
         * Defensive check.
         */
        if (selectedOffer == null) {

            return AuthorizationDecisionDto.builder()
                    .procurementId(procurementId)
                    .withinAuthorization(false)
                    .decision("NO_RECOMMENDATION")
                    .nextState("NONE")
                    .explanation(
                            "No eligible offer is available for authorization."
                    )
                    .build();
        }

        BigDecimal totalAmount =
                selectedOffer.getPrice();

        boolean withinLimit =
                totalAmount.compareTo(effectiveLimit) <= 0;

        /*
         * ================================================================
         * CASE 2A: WITHIN AUTHORIZATION LIMIT
         * ================================================================
         */
        if (withinLimit) {

            String explanation = String.format(
                    "Procurement of %d unit(s) of '%s' from %s "
                            + "at ₹%s total (₹%s/unit) is within the "
                            + "₹%s authorization limit. Projected 3-year "
                            + "TCO is ₹%s. Auto-authorized for revalidation.",

                    quantity,
                    selectedOffer.getProductName(),
                    selectedOffer.getVendorName(),
                    totalAmount,
                    selectedOffer.getUnitPrice(),
                    effectiveLimit,
                    selectedOffer.getTco()
            );

            /*
             * Auto-authorize and proceed to revalidation.
             */
            if (request.getStatus()
                    == ProcurementState.AUTHORIZATION_CHECK) {

                stateMachine.transition(
                        request,
                        ProcurementState.REVALIDATING,
                        "AUTHORIZATION_SERVICE",
                        "Auto-authorized: Purchase amount within authorization limit",
                        Map.of(
                                "requestedAmount",
                                totalAmount.toString(),

                                "limit",
                                effectiveLimit.toString()
                        )
                );
            }

            return AuthorizationDecisionDto.builder()
                    .procurementId(procurementId)
                    .selectedOfferId(selectedOffer.getOfferId())
                    .selectedProductName(selectedOffer.getProductName())
                    .selectedVendorName(selectedOffer.getVendorName())
                    .quantity(quantity)
                    .unitPrice(selectedOffer.getUnitPrice())
                    .totalRequestedAmount(totalAmount)
                    .authorizationLimit(effectiveLimit)
                    .excessAmount(BigDecimal.ZERO)
                    .withinAuthorization(true)
                    .decision("AUTO_AUTHORIZED")
                    .nextState("REVALIDATING")
                    .exceptionType("NONE")
                    .explanation(explanation)
                    .build();
        }

        /*
         * ================================================================
         * CASE 2B: LIMIT EXCEEDED -> HUMAN APPROVAL
         * ================================================================
         */
        BigDecimal excessAmount =
                totalAmount.subtract(effectiveLimit);

        String explanation = String.format(
                "Procurement of %d unit(s) of '%s' from %s "
                        + "at ₹%s total (₹%s/unit) exceeds the "
                        + "₹%s authorization limit by ₹%s. "
                        + "Projected 3-year TCO is ₹%s. "
                        + "Escalated for human approval.",

                quantity,
                selectedOffer.getProductName(),
                selectedOffer.getVendorName(),
                totalAmount,
                selectedOffer.getUnitPrice(),
                effectiveLimit,
                excessAmount,
                selectedOffer.getTco()
        );

        VendorOffer offerEntity =
                vendorOfferRepository
                        .findById(selectedOffer.getOfferId())
                        .orElse(null);

        /*
         * Create/update the pending HITL approval.
         */
        createOrUpdatePendingApproval(
                request,
                offerEntity,
                totalAmount,
                effectiveLimit,
                excessAmount,
                "LIMIT_EXCEEDED",
                explanation
        );

        /*
         * IMPORTANT:
         *
         * This is the branch Demo 4 should hit.
         *
         * ₹4,68,000 purchase
         * >
         * ₹4,50,000 user authorization limit
         *
         * Therefore:
         *
         * AUTHORIZATION_CHECK
         *          ↓
         * WAITING_APPROVAL
         */
        if (request.getStatus()
                == ProcurementState.AUTHORIZATION_CHECK) {

            stateMachine.transition(
                    request,
                    ProcurementState.WAITING_APPROVAL,
                    "AUTHORIZATION_SERVICE",
                    "Purchase amount exceeds authorization limit. Escalated for approval.",
                    Map.of(
                            "requestedAmount",
                            totalAmount.toString(),

                            "limit",
                            effectiveLimit.toString(),

                            "excessAmount",
                            excessAmount.toString()
                    )
            );
        }

        return AuthorizationDecisionDto.builder()
                .procurementId(procurementId)
                .selectedOfferId(selectedOffer.getOfferId())
                .selectedProductName(selectedOffer.getProductName())
                .selectedVendorName(selectedOffer.getVendorName())
                .quantity(quantity)
                .unitPrice(selectedOffer.getUnitPrice())
                .totalRequestedAmount(totalAmount)
                .authorizationLimit(effectiveLimit)
                .excessAmount(excessAmount)
                .withinAuthorization(false)
                .decision("REQUIRES_APPROVAL")
                .nextState("WAITING_APPROVAL")
                .exceptionType("LIMIT_EXCEEDED")
                .explanation(explanation)
                .build();
    }

    /**
     * Resolves the authoritative authorization limit via EffectiveAuthorizationResolver.
     */
    private BigDecimal resolveEffectiveLimit(
            ProcurementRequest request) {

        return effectiveAuthorizationResolver.resolveEffectiveLimit(request);
    }

    /**
     * Creates a new pending approval or updates an existing pending approval.
     *
     * <p>
     * This operation is idempotent for a procurement with an existing
     * PENDING approval.
     * </p>
     */
    private void createOrUpdatePendingApproval(
            ProcurementRequest request,
            VendorOffer proposedOffer,
            BigDecimal requestedAmount,
            BigDecimal authorizationLimit,
            BigDecimal excessAmount,
            String exceptionType,
            String reason) {

        Optional<Approval> existingOpt =
                approvalRepository
                        .findTopByProcurementIdOrderByRequestedAtDesc(
                                request.getId()
                        );

        /*
         * Existing pending approval -> update it rather than creating
         * duplicate approval records.
         */
        if (existingOpt.isPresent()
                && existingOpt.get().getStatus()
                == ApprovalStatus.PENDING) {

            Approval existing =
                    existingOpt.get();

            existing.setProposedOffer(proposedOffer);
            existing.setRequestedAmount(requestedAmount);
            existing.setAuthorizationLimit(authorizationLimit);
            existing.setDifference(excessAmount);
            existing.setExceptionType(exceptionType);
            existing.setReason(reason);

            approvalRepository.save(existing);

            log.info(
                    "Updated existing pending approval for procurement [{}] "
                            + "with exception type [{}]",
                    request.getId(),
                    exceptionType
            );

            return;
        }

        /*
         * No pending approval exists -> create one.
         */
        Approval approval =
                Approval.builder()
                        .procurement(request)
                        .proposedOffer(proposedOffer)
                        .requestedAmount(requestedAmount)
                        .authorizationLimit(authorizationLimit)
                        .difference(excessAmount)
                        .exceptionType(exceptionType)
                        .reason(reason)
                        .status(ApprovalStatus.PENDING)
                        .build();

        approvalRepository.save(approval);

        log.info(
                "Created pending approval for procurement [{}]: "
                        + "requested=[{}], limit=[{}], excess=[{}], "
                        + "exceptionType=[{}]",
                request.getId(),
                requestedAmount,
                authorizationLimit,
                excessAmount,
                exceptionType
        );
    }
}