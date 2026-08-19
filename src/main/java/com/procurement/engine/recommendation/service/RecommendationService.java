package com.procurement.engine.recommendation.service;

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

    public RecommendationService(RankingService rankingService,
                                 TcoService tcoService,
                                 ProcurementStateMachine stateMachine,
                                 ProcurementRequestRepository procurementRequestRepository,
                                 VendorOfferRepository vendorOfferRepository) {
        this.rankingService = rankingService;
        this.tcoService = tcoService;
        this.stateMachine = stateMachine;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
    }

    /**
     * Generates a two-tier explainable recommendation for a procurement request.
     */
    @Transactional
    public RecommendationResponse generateRecommendation(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // 1. Perform Ranking & TCO Analysis
        ProcurementRankingResponse rankingResponse = rankingService.rankOffers(procurementId);
        TcoService.TcoAnalysisResult tcoResult = tcoService.calculateTcoForProcurement(procurementId);
        List<FalseEconomyResult> falseEconomies = tcoResult.falseEconomyResults();

        List<RankedOfferDto> eligibleOffers = rankingResponse.getEligibleOffers();
        RankedOfferDto topException = rankingResponse.getTopExceptionOffer();

        // 2. Case: Zero Eligible Offers
        if (eligibleOffers.isEmpty()) {
            String explanation = "No candidate products satisfied mandatory hard constraints.";
            if (topException != null) {
                explanation += String.format(" Closest exception candidate is '%s' (₹%s, TCO: ₹%s), which requires human approval to override constraints.",
                        topException.getProductName(), topException.getPrice(), topException.getTco());
            }

            log.info("Procurement [{}] produced NO_RECOMMENDATION (0 eligible offers).", procurementId);

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

        // 3. Case: Eligible Offers Exist -> Top Eligible is strictly the selected candidate
        RankedOfferDto bestEligible = eligibleOffers.get(0);
        List<RankedOfferDto> rankedAlternatives = eligibleOffers.size() > 1
                ? eligibleOffers.subList(1, eligibleOffers.size())
                : Collections.emptyList();

        // Bind executable selectedOffer in database ONLY to the best eligible candidate
        VendorOffer selectedOffer = vendorOfferRepository.findById(bestEligible.getOfferId()).orElse(null);
        if (selectedOffer != null) {
            request.setSelectedOffer(selectedOffer);
            request.setSelectedProduct(selectedOffer.getProduct());
            selectedOffer.setStatus(OfferStatus.RECOMMENDED);
            vendorOfferRepository.save(selectedOffer);
            procurementRequestRepository.save(request);
        }

        // 4. Evaluate whether an Exception Candidate provides superior TCO (higher upfront price but lower projected TCO)
        boolean hasSuperiorException = topException != null
                && topException.getPrice().compareTo(bestEligible.getPrice()) > 0
                && topException.getTco().compareTo(bestEligible.getTco()) < 0;

        String recommendationType;
        RankedOfferDto proposedException = null;
        String explanation;
        List<String> tradeOffs = new ArrayList<>();

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
            BigDecimal authLimit = request.getAuthorizationLimit();
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
