package com.procurement.engine.recommendation.model;

import com.procurement.engine.ranking.model.RankedOfferDto;
import com.procurement.engine.tco.model.FalseEconomyResult;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Two-tier explainable recommendation response.
 * <p>
 * Strictly separates best compliant option from best exception option.
 */
public class RecommendationResponse {

    private final UUID procurementId;
    private final String category;
    private final String recommendationType;

    // Two-tier options
    private final RankedOfferDto bestEligibleOption;
    private final RankedOfferDto bestExceptionOption;
    private final RankedOfferDto proposedExceptionOffer;

    // Executable binding (STRICTLY eligible offer only)
    private final UUID selectedOfferId;
    private final UUID selectedProductId;

    // Explainability & alternatives
    private final String explanation;
    private final List<String> tradeOffs;
    private final List<RankedOfferDto> rankedAlternatives;
    private final List<FalseEconomyResult> falseEconomyReport;

    public RecommendationResponse(UUID procurementId,
                                  String category,
                                  String recommendationType,
                                  RankedOfferDto bestEligibleOption,
                                  RankedOfferDto bestExceptionOption,
                                  RankedOfferDto proposedExceptionOffer,
                                  UUID selectedOfferId,
                                  UUID selectedProductId,
                                  String explanation,
                                  List<String> tradeOffs,
                                  List<RankedOfferDto> rankedAlternatives,
                                  List<FalseEconomyResult> falseEconomyReport) {
        this.procurementId = procurementId;
        this.category = category;
        this.recommendationType = recommendationType;
        this.bestEligibleOption = bestEligibleOption;
        this.bestExceptionOption = bestExceptionOption;
        this.proposedExceptionOffer = proposedExceptionOffer;
        this.selectedOfferId = selectedOfferId;
        this.selectedProductId = selectedProductId;
        this.explanation = explanation;
        this.tradeOffs = tradeOffs != null ? List.copyOf(tradeOffs) : Collections.emptyList();
        this.rankedAlternatives = rankedAlternatives != null ? List.copyOf(rankedAlternatives) : Collections.emptyList();
        this.falseEconomyReport = falseEconomyReport != null ? List.copyOf(falseEconomyReport) : Collections.emptyList();
    }

    public UUID getProcurementId() { return procurementId; }
    public String getCategory() { return category; }
    public String getRecommendationType() { return recommendationType; }
    public RankedOfferDto getBestEligibleOption() { return bestEligibleOption; }
    public RankedOfferDto getBestExceptionOption() { return bestExceptionOption; }
    public RankedOfferDto getProposedExceptionOffer() { return proposedExceptionOffer; }
    public UUID getSelectedOfferId() { return selectedOfferId; }
    public UUID getSelectedProductId() { return selectedProductId; }
    public String getExplanation() { return explanation; }
    public List<String> getTradeOffs() { return tradeOffs; }
    public List<RankedOfferDto> getRankedAlternatives() { return rankedAlternatives; }
    public List<FalseEconomyResult> getFalseEconomyReport() { return falseEconomyReport; }
}
