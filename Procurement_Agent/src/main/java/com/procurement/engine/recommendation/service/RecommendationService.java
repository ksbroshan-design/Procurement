package com.procurement.engine.recommendation.service;

import com.procurement.engine.approval.entity.Approval;
import com.procurement.engine.approval.entity.ApprovalStatus;
import com.procurement.engine.approval.repository.ApprovalRepository;
import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.procurement.entity.OfferStatus;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.ranking.model.ProcurementRankingResponse;
import com.procurement.engine.ranking.model.RankedOfferDto;
import com.procurement.engine.ranking.service.RankingService;
import com.procurement.engine.recommendation.model.RecommendationResponse;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.statemachine.ProcurementStateMachine;
import com.procurement.engine.tco.model.FalseEconomyResult;
import com.procurement.engine.tco.service.TcoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service generating explainable two-tier procurement recommendations.
 * <p>
 * Strictly isolates compliant eligible candidates from over-budget exception candidates.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RankingService rankingService;
    private final TcoService tcoService;
    private final ProcurementStateMachine stateMachine;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final ApprovalRepository approvalRepository;
    private final com.procurement.engine.authorization.service.EffectiveAuthorizationResolver effectiveAuthorizationResolver;

    public RecommendationService(RankingService rankingService,
                                 TcoService tcoService,
                                 ProcurementStateMachine stateMachine,
                                 ProcurementRequestRepository procurementRequestRepository,
                                 VendorOfferRepository vendorOfferRepository,
                                 ApprovalRepository approvalRepository,
                                 com.procurement.engine.authorization.service.EffectiveAuthorizationResolver effectiveAuthorizationResolver) {
        this.rankingService = rankingService;
        this.tcoService = tcoService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.approvalRepository = approvalRepository;
        this.effectiveAuthorizationResolver = effectiveAuthorizationResolver;
    }

    /**
     * Generates a two-tier explainable recommendation for a procurement request.
     */
    @Transactional
    public RecommendationResponse generateRecommendation(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // 1. Check if an approved Human-in-the-Loop approval exists for this procurement
        Optional<Approval> approvedOpt = approvalRepository.findTopByProcurementIdOrderByRequestedAtDesc(procurementId);
        boolean isApproved = approvedOpt.isPresent() && approvedOpt.get().getStatus() == ApprovalStatus.APPROVED;

        // 2. Perform Ranking & TCO Analysis
        ProcurementRankingResponse rankingResponse = rankingService.rankOffers(procurementId);
        TcoService.TcoAnalysisResult tcoResult = tcoService.calculateTcoForProcurement(procurementId);
        List<FalseEconomyResult> falseEconomies = tcoResult.falseEconomyResults();
        request = procurementRequestRepository.findById(procurementId).orElseThrow();

        List<RankedOfferDto> eligibleOffers = rankingResponse.getEligibleOffers();
        RankedOfferDto topException = rankingResponse.getTopExceptionOffer();

        // 3. Case: Zero Eligible Offers
        if (eligibleOffers.isEmpty()) {
            RankedOfferDto budgetException = null;
            if (topException != null && topException.isBudgetExceeded()) {
                budgetException = topException;
            } else {
                for (RankedOfferDto exc : rankingResponse.getExceptionOffers()) {
                    if (exc.isBudgetExceeded()) {
                        budgetException = exc;
                        break;
                    }
                }
            }

            // Check if there is an over-budget candidate requiring human approval
            if (budgetException != null) {
                BigDecimal authLimit = effectiveAuthorizationResolver.resolveEffectiveLimit(request);
                String explanation = String.format(
                        "Candidate product '%s' from %s achieves the highest multi-dimensional score (%.1f), but costs ₹%s total (₹%s/unit), " +
                                "which exceeds user authorization limit ₹%s by ₹%s. Escalated for human authorization.",
                        budgetException.getProductName(), budgetException.getVendorName(), budgetException.getTotalScore(),
                        budgetException.getPrice(), budgetException.getUnitPrice(), authLimit,
                        budgetException.getPrice().subtract(authLimit).max(BigDecimal.ZERO)
                );
                log.info("Procurement [{}] requires authorization for top exception candidate [{}] (₹{} > ₹{}).",
                        procurementId, budgetException.getProductName(), budgetException.getPrice(), authLimit);

                // Bind selected offer so approval and revalidation can reference it
                VendorOffer selectedOffer = vendorOfferRepository.findById(budgetException.getOfferId()).orElse(null);
                if (selectedOffer != null && request.getStatus() != ProcurementState.COMPLETED) {
                    request.setSelectedOffer(selectedOffer);
                    request.setSelectedProduct(selectedOffer.getProduct());
                    if (selectedOffer.getStatus() != OfferStatus.ACCEPTED) {
                        selectedOffer.setStatus(OfferStatus.RECOMMENDED);
                        vendorOfferRepository.save(selectedOffer);
                    }
                    procurementRequestRepository.save(request);
                }

                // Advance state to RECOMMENDED
                if (request.getStatus() == ProcurementState.TCO_ANALYSIS || request.getStatus() == ProcurementState.EVALUATING) {
                    stateMachine.transition(request, ProcurementState.RECOMMENDED, "RECOMMENDATION_SERVICE",
                            "Generated explainable recommendation: REQUIRES_AUTHORIZATION",
                            Map.of("recommendationType", "REQUIRES_AUTHORIZATION", "selectedOfferId", budgetException.getOfferId().toString()));
                }

                return new RecommendationResponse(
                        procurementId,
                        request.getCategory(),
                        "REQUIRES_AUTHORIZATION",
                        budgetException,
                        null,
                        budgetException,
                        null,
                        null,
                        explanation,
                        List.of("Purchase amount exceeds authorization limit", "Requires human manager approval to proceed"),
                        Collections.emptyList(),
                        falseEconomies
                );
            }

            String explanation = "No candidate products satisfied mandatory hard constraints.";
            if (topException != null) {
                explanation += String.format(" Closest exception candidate is '%s' (₹%s, TCO: ₹%s), which requires human approval to override constraints.",
                        topException.getProductName(), topException.getPrice(), topException.getTco());
            }

            log.info("Procurement [{}] produced NO_RECOMMENDATION (0 eligible offers).", procurementId);

            // Advance state to RECOMMENDED
            if (request.getStatus() == ProcurementState.TCO_ANALYSIS || request.getStatus() == ProcurementState.EVALUATING) {
                stateMachine.transition(request, ProcurementState.RECOMMENDED, "RECOMMENDATION_SERVICE",
                        "Generated explainable recommendation: NO_RECOMMENDATION",
                        Map.of("recommendationType", "NO_RECOMMENDATION"));
            }

            return new RecommendationResponse(
                    procurementId,
                    request.getCategory(),
                    "NO_RECOMMENDATION",
                    null,
                    topException,
                    topException,
                    null,
                    null,
                    explanation,
                    List.of("Zero compliant offers available for autonomous procurement", "Human review required to relax constraints or approve exception"),
                    Collections.emptyList(),
                    falseEconomies
            );
        }

        // 4. Case: Eligible Offers Exist -> Top Eligible is strictly the selected candidate
        RankedOfferDto bestEligible = eligibleOffers.get(0);
        List<RankedOfferDto> rankedAlternatives = eligibleOffers.size() > 1
                ? eligibleOffers.subList(1, eligibleOffers.size())
                : Collections.emptyList();

        // Bind executable selectedOffer in database
        VendorOffer selectedOffer = null;
        if (isApproved && approvedOpt.get().getProposedOffer() != null) {
            selectedOffer = approvedOpt.get().getProposedOffer();
        } else {
            selectedOffer = vendorOfferRepository.findById(bestEligible.getOfferId()).orElse(null);
        }

        if (selectedOffer != null && request.getStatus() != ProcurementState.COMPLETED) {
            request.setSelectedOffer(selectedOffer);
            request.setSelectedProduct(selectedOffer.getProduct());
            if (selectedOffer.getStatus() != OfferStatus.ACCEPTED) {
                selectedOffer.setStatus(OfferStatus.RECOMMENDED);
                vendorOfferRepository.save(selectedOffer);
            }
            procurementRequestRepository.save(request);
        }

        // 5. Evaluate recommendation type & trade-offs
        String recommendationType;
        RankedOfferDto proposedException = null;
        String explanation;
        List<String> tradeOffs = new ArrayList<>();

        if (isApproved) {
            BigDecimal approvedLimit = approvedOpt.get().getRequestedAmount();
            recommendationType = "AUTONOMOUS_PURCHASE_READY";
            explanation = String.format(
                    "Recommended offer '%s' from %s satisfies all constraints, has been authorized by management approval " +
                            "(approved budget: ₹%s), and achieves highest multi-dimensional score (%.1f) with projected 3-year TCO of ₹%s.",
                    selectedOffer != null && selectedOffer.getProduct() != null ? selectedOffer.getProduct().getName() : bestEligible.getProductName(),
                    selectedOffer != null && selectedOffer.getVendor() != null ? selectedOffer.getVendor().getName() : bestEligible.getVendorName(),
                    approvedLimit, bestEligible.getTotalScore(), bestEligible.getTco()
            );
            tradeOffs.add("Manager approval granted for budget exception amount: ₹" + approvedLimit);
            tradeOffs.add(String.format("3-Year Projected TCO: ₹%s (Purchase: ₹%s, Warranty: %d yrs, Delivery: %d days)",
                    bestEligible.getTco(), bestEligible.getPrice(), bestEligible.getWarrantyYears(), bestEligible.getDeliveryDays()));
        } else {
            boolean hasSuperiorException = topException != null
                    && topException.getPrice().compareTo(bestEligible.getPrice()) > 0
                    && topException.getTco().compareTo(bestEligible.getTco()) < 0;

            if (hasSuperiorException) {
                recommendationType = "BUDGET_OVERRIDE_RECOMMENDED";
                proposedException = topException;

                BigDecimal upfrontDiff = topException.getPrice().subtract(bestEligible.getPrice());
                BigDecimal tcoSavings = bestEligible.getTco().subtract(topException.getTco());

                explanation = String.format(
                        "Best compliant option is '%s' from %s (₹%s upfront, 3-yr TCO: ₹%s). " +
                                "However, exception candidate '%s' from %s (₹%s upfront) offers ₹%s lower projected TCO " +
                                "due to superior warranty (%d yrs vs %d yrs) and higher reliability (%.1f%% vs %.1f%%). " +
                                "Requires human authorization to override budget.",
                        bestEligible.getProductName(), bestEligible.getVendorName(), bestEligible.getPrice(), bestEligible.getTco(),
                        topException.getProductName(), topException.getVendorName(), topException.getPrice(), tcoSavings,
                        topException.getWarrantyYears(), bestEligible.getWarrantyYears(),
                        topException.getReliability().multiply(BigDecimal.valueOf(100)),
                        bestEligible.getReliability().multiply(BigDecimal.valueOf(100))
                );

                tradeOffs.add(String.format("Upfront Premium: Exception option requires ₹%s higher initial purchase budget", upfrontDiff));
                tradeOffs.add(String.format("Long-term Savings: Exception option provides ₹%s lower 3-year Total Cost of Ownership", tcoSavings));
                tradeOffs.add(String.format("Warranty Advantage: %d years vs %d years warranty coverage",
                        topException.getWarrantyYears(), bestEligible.getWarrantyYears()));
            } else {
                BigDecimal authLimit = effectiveAuthorizationResolver.resolveEffectiveLimit(request);
                boolean exceedsAuthLimit = authLimit != null && bestEligible.getPrice().compareTo(authLimit) > 0;

                if (exceedsAuthLimit) {
                    recommendationType = "REQUIRES_AUTHORIZATION";
                    explanation = String.format(
                            "Recommended offer '%s' from %s achieves the highest multi-dimensional score (%.1f) " +
                                    "with projected 3-year TCO of ₹%s. However, total purchase price (₹%s) exceeds user authorization limit (₹%s). " +
                                    "Requires managerial approval.",
                            bestEligible.getProductName(), bestEligible.getVendorName(), bestEligible.getTotalScore(),
                            bestEligible.getTco(), bestEligible.getPrice(), authLimit
                    );
                    tradeOffs.add(String.format("Price exceeds authorization limit by ₹%s", bestEligible.getPrice().subtract(authLimit)));
                } else {
                    recommendationType = "AUTONOMOUS_PURCHASE_READY";
                    explanation = String.format(
                            "Recommended offer '%s' from %s satisfies all constraints, falls within the ₹%s authorization limit, " +
                                    "and achieves the highest multi-dimensional score (%.1f) with lowest compliant 3-year TCO of ₹%s.",
                            bestEligible.getProductName(), bestEligible.getVendorName(), authLimit,
                            bestEligible.getTotalScore(), bestEligible.getTco()
                    );
                    tradeOffs.add("Satisfies all mandatory hard constraints and user authorization limits");
                }

                tradeOffs.add(String.format("3-Year Projected TCO: ₹%s (Purchase: ₹%s, Warranty: %d yrs, Delivery: %d days)",
                        bestEligible.getTco(), bestEligible.getPrice(), bestEligible.getWarrantyYears(), bestEligible.getDeliveryDays()));
            }
        }

        // 5. Advance State Machine to RECOMMENDED
        if (request.getStatus() == ProcurementState.TCO_ANALYSIS || request.getStatus() == ProcurementState.EVALUATING) {
            stateMachine.transition(request, ProcurementState.RECOMMENDED, "RECOMMENDATION_SERVICE",
                    "Generated explainable recommendation: " + recommendationType,
                    Map.of("recommendationType", recommendationType, "selectedOfferId", bestEligible.getOfferId().toString()));
        }

        log.info("Generated recommendation for procurement [{}] -> Type: [{}], Selected Offer: [{}]",
                procurementId, recommendationType, bestEligible.getProductName());

        return new RecommendationResponse(
                procurementId,
                request.getCategory(),
                recommendationType,
                bestEligible,
                topException,
                proposedException,
                bestEligible.getOfferId(),
                bestEligible.getProductId(),
                explanation,
                tradeOffs,
                rankedAlternatives,
                falseEconomies
        );
    }
}
