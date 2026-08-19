package com.procurement.engine.ranking.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response model containing ranked eligible offers and evaluated exception offers.
 */
public class ProcurementRankingResponse {

    private final UUID procurementId;
    private final String category;
    private final List<RankedOfferDto> eligibleOffers;
    private final List<RankedOfferDto> exceptionOffers;
    private final Map<String, BigDecimal> weightsUsed;
    private final RankedOfferDto topRankedOffer;
    private final RankedOfferDto topExceptionOffer;

    public ProcurementRankingResponse(UUID procurementId,
                                      String category,
                                      List<RankedOfferDto> eligibleOffers,
                                      List<RankedOfferDto> exceptionOffers,
                                      Map<String, BigDecimal> weightsUsed,
                                      RankedOfferDto topRankedOffer,
                                      RankedOfferDto topExceptionOffer) {
        this.procurementId = procurementId;
        this.category = category;
        this.eligibleOffers = eligibleOffers != null ? List.copyOf(eligibleOffers) : Collections.emptyList();
        this.exceptionOffers = exceptionOffers != null ? List.copyOf(exceptionOffers) : Collections.emptyList();
        this.weightsUsed = weightsUsed != null ? Map.copyOf(weightsUsed) : Collections.emptyMap();
        this.topRankedOffer = topRankedOffer;
        this.topExceptionOffer = topExceptionOffer;
    }

    public UUID getProcurementId() { return procurementId; }
    public String getCategory() { return category; }
    public List<RankedOfferDto> getEligibleOffers() { return eligibleOffers; }
    public List<RankedOfferDto> getExceptionOffers() { return exceptionOffers; }
    public Map<String, BigDecimal> getWeightsUsed() { return weightsUsed; }
    public RankedOfferDto getTopRankedOffer() { return topRankedOffer; }
    public RankedOfferDto getTopExceptionOffer() { return topExceptionOffer; }
}
