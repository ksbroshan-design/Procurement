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
 * <p>
 * Authoritatively evaluates purchase amounts against authorization limits,
 * manages approval generation, and drives state transitions to REVALIDATING or WAITING_APPROVAL.
 */
@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final RecommendationService recommendationService;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final ApprovalRepository approvalRepository;

    public AuthorizationService(RecommendationService recommendationService,
                                ProcurementStateMachine stateMachine,
                                ProcurementRequestRepository procurementRequestRepository,
                                VendorOfferRepository vendorOfferRepository,
                                ApprovalRepository approvalRepository) {
        this.recommendationService = recommendationService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.approvalRepository = approvalRepository;
    }

    /**
     * Checks authorization for a procurement decision.
     */
    @Transactional
    public AuthorizationDecisionDto checkAuthorization(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // Advance to recommended if not yet evaluated
        RecommendationResponse recommendation = recommendationService.generateRecommendation(procurementId);

        if ("NO_RECOMMENDATION".equals(recommendation.getRecommendationType())) {
            return AuthorizationDecisionDto.builder()
                    .procurementId(procurementId)
                    .withinAuthorization(false)
                    .decision("NO_RECOMMENDATION")
                    .nextState("NONE")
                    .explanation(recommendation.getExplanation())
                    .build();
        }

        // Advance state from RECOMMENDED to AUTHORIZATION_CHECK if needed
        if (request.getStatus() == ProcurementState.RECOMMENDED) {
            stateMachine.transition(request, ProcurementState.AUTHORIZATION_CHECK, "AUTHORIZATION_SERVICE",
                    "Checking authorization limits and policy compliance", Map.of());
        }

        int quantity = request.getQuantity();
        BigDecimal effectiveLimit = resolveEffectiveLimit(request);

        // Case 1: Budget Override Exception Candidate
        if ("BUDGET_OVERRIDE_RECOMMENDED".equals(recommendation.getRecommendationType())
                && recommendation.getProposedExceptionOffer() != null) {

            RankedOfferDto exceptionOffer = recommendation.getProposedExceptionOffer();
            RankedOfferDto bestEligible = recommendation.getBestEligibleOption();

            BigDecimal requestedAmount = exceptionOffer.getPrice();
            BigDecimal excessAmount = requestedAmount.subtract(effectiveLimit).max(BigDecimal.ZERO);
            BigDecimal tcoSavings = bestEligible != null
                    ? bestEligible.getTco().subtract(exceptionOffer.getTco())
                    : BigDecimal.ZERO;

            String explanation = String.format(
                    "Exception candidate '%s' from %s costs ₹%s total (₹%s/unit for %d units), " +
                            "exceeding the ₹%s limit by ₹%s. However, its projected 3-year TCO of ₹%s provides ₹%s savings " +
                            "over compliant option '%s' (TCO: ₹%s) with %d-year warranty (vs %d-year). " +
                            "Requires managerial authorization to approve budget exception.",
                    exceptionOffer.getProductName(), exceptionOffer.getVendorName(),
                    requestedAmount, exceptionOffer.getUnitPrice(), quantity,
                    effectiveLimit, excessAmount, exceptionOffer.getTco(), tcoSavings,
                    bestEligible != null ? bestEligible.getProductName() : "Compliant Option",
                    bestEligible != null ? bestEligible.getTco() : BigDecimal.ZERO,
                    exceptionOffer.getWarrantyYears(),
                    bestEligible != null ? bestEligible.getWarrantyYears() : 1
            );

            VendorOffer offerEntity = vendorOfferRepository.findById(exceptionOffer.getOfferId()).orElse(null);

            // Create or update pending Approval entity idempotently
            createOrUpdatePendingApproval(request, offerEntity, requestedAmount, effectiveLimit, excessAmount, "BUDGET_OVERRIDE", explanation);

            // Transition state to WAITING_APPROVAL
            if (request.getStatus() == ProcurementState.AUTHORIZATION_CHECK) {
                stateMachine.transition(request, ProcurementState.WAITING_APPROVAL, "AUTHORIZATION_SERVICE",
                        "Budget exception recommendation escalated for human approval",
                        Map.of("exceptionType", "BUDGET_OVERRIDE", "excessAmount", excessAmount.toString()));
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

        // Case 2: Standard Compliant Recommendation
        RankedOfferDto selectedOffer = recommendation.getBestEligibleOption();
        BigDecimal totalAmount = selectedOffer.getPrice();
        boolean withinLimit = totalAmount.compareTo(effectiveLimit) <= 0;

        if (withinLimit) {
            String explanation = String.format(
                    "Procurement of %d unit(s) of '%s' from %s at ₹%s total (₹%s/unit) is within the ₹%s authorization limit. " +
                            "Projected 3-year TCO is ₹%s. Auto-authorized for revalidation.",
                    quantity, selectedOffer.getProductName(), selectedOffer.getVendorName(),
                    totalAmount, selectedOffer.getUnitPrice(), effectiveLimit, selectedOffer.getTco()
            );

            if (request.getStatus() == ProcurementState.AUTHORIZATION_CHECK) {
                stateMachine.transition(request, ProcurementState.REVALIDATING, "AUTHORIZATION_SERVICE",
                        "Auto-authorized: Purchase amount within authorization limit",
                        Map.of("requestedAmount", totalAmount.toString(), "limit", effectiveLimit.toString()));
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
        } else {
            BigDecimal excessAmount = totalAmount.subtract(effectiveLimit);
            String explanation = String.format(
                    "Procurement of %d unit(s) of '%s' from %s at ₹%s total (₹%s/unit) exceeds the ₹%s authorization limit by ₹%s. " +
                            "Projected 3-year TCO is ₹%s. Escalated for human approval.",
                    quantity, selectedOffer.getProductName(), selectedOffer.getVendorName(),
                    totalAmount, selectedOffer.getUnitPrice(), effectiveLimit, excessAmount, selectedOffer.getTco()
            );

            VendorOffer offerEntity = vendorOfferRepository.findById(selectedOffer.getOfferId()).orElse(null);

            createOrUpdatePendingApproval(request, offerEntity, totalAmount, effectiveLimit, excessAmount, "LIMIT_EXCEEDED", explanation);

            if (request.getStatus() == ProcurementState.AUTHORIZATION_CHECK) {
                stateMachine.transition(request, ProcurementState.WAITING_APPROVAL, "AUTHORIZATION_SERVICE",
                        "Purchase amount exceeds authorization limit. Escalated for approval.",
                        Map.of("requestedAmount", totalAmount.toString(), "limit", effectiveLimit.toString(), "excessAmount", excessAmount.toString()));
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
    }

    private BigDecimal resolveEffectiveLimit(ProcurementRequest request) {
        if (request.getAuthorizationLimit() != null && request.getAuthorizationLimit().compareTo(BigDecimal.ZERO) > 0) {
            return request.getAuthorizationLimit().setScale(2, RoundingMode.HALF_UP);
        }
        if (request.getUser() != null && request.getUser().getAuthorizationLimit() != null) {
            return request.getUser().getAuthorizationLimit().setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal("500000.00");
    }

    private void createOrUpdatePendingApproval(ProcurementRequest request,
                                               VendorOffer proposedOffer,
                                               BigDecimal requestedAmount,
                                               BigDecimal authorizationLimit,
                                               BigDecimal excessAmount,
                                               String exceptionType,
                                               String reason) {
        Optional<Approval> existingOpt = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(request.getId());
        if (existingOpt.isPresent() && existingOpt.get().getStatus() == ApprovalStatus.PENDING) {
            Approval existing = existingOpt.get();
            existing.setProposedOffer(proposedOffer);
            existing.setRequestedAmount(requestedAmount);
            existing.setAuthorizationLimit(authorizationLimit);
            existing.setDifference(excessAmount);
            existing.setExceptionType(exceptionType);
            existing.setReason(reason);
            approvalRepository.save(existing);
            return;
        }

        Approval approval = Approval.builder()
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
    }
}
