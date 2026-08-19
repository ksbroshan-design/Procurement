package com.procurement.engine.comparison.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Normalized comparison response presenting side-by-side data across offers for a procurement.
 */
public class ProcurementComparisonResponse {

    private final UUID procurementId;
    private final String category;
    private final int totalCandidatesCompared;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final int fastestDeliveryDays;
    private final BigDecimal highestSellerRating;
    private final BigDecimal highestReliabilityScore;
    private final List<ProductComparisonItemDto> offers;
    private final int rejectionCount;

    public ProcurementComparisonResponse(UUID procurementId,
                                         String category,
                                         int totalCandidatesCompared,
                                         BigDecimal minPrice,
                                         BigDecimal maxPrice,
                                         int fastestDeliveryDays,
                                         BigDecimal highestSellerRating,
                                         BigDecimal highestReliabilityScore,
                                         List<ProductComparisonItemDto> offers,
                                         int rejectionCount) {
        this.procurementId = procurementId;
        this.category = category;
        this.totalCandidatesCompared = totalCandidatesCompared;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.fastestDeliveryDays = fastestDeliveryDays;
        this.highestSellerRating = highestSellerRating;
        this.highestReliabilityScore = highestReliabilityScore;
        this.offers = offers != null ? List.copyOf(offers) : Collections.emptyList();
        this.rejectionCount = rejectionCount;
    }

    public UUID getProcurementId() { return procurementId; }
    public String getCategory() { return category; }
    public int getTotalCandidatesCompared() { return totalCandidatesCompared; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public int getFastestDeliveryDays() { return fastestDeliveryDays; }
    public BigDecimal getHighestSellerRating() { return highestSellerRating; }
    public BigDecimal getHighestReliabilityScore() { return highestReliabilityScore; }
    public List<ProductComparisonItemDto> getOffers() { return offers; }
    public int getRejectionCount() { return rejectionCount; }
}
