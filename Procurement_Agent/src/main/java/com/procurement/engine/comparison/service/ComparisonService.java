package com.procurement.engine.comparison.service;

import com.procurement.engine.comparison.model.ProductComparisonItemDto;
import com.procurement.engine.comparison.model.ProcurementComparisonResponse;
import com.procurement.engine.discovery.model.CandidateOfferDto;
import com.procurement.engine.discovery.model.DiscoveryResult;
import com.procurement.engine.discovery.service.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Service producing side-by-side normalized comparison across candidate offers for a procurement.
 * <p>
 * Does NOT calculate TCO, rankings, or recommendations (deferred to Phase 4).
 */
@Service
public class ComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ComparisonService.class);

    private final DiscoveryService discoveryService;

    public ComparisonService(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Produces a normalized comparison response for all eligible candidate offers for a procurement request.
     */
    @Transactional
    public ProcurementComparisonResponse compareCandidates(UUID procurementId) {
        DiscoveryResult discoveryResult = discoveryService.discoverAndEvaluate(procurementId);

        List<CandidateOfferDto> eligibleOffers = discoveryResult.getEligibleOffers();
        int rejectionCount = discoveryResult.getRejectedCandidatesCount();

        if (eligibleOffers.isEmpty()) {
            return new ProcurementComparisonResponse(
                    procurementId,
                    discoveryResult.getCategory(),
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Collections.emptyList(),
                    rejectionCount
            );
        }

        List<ProductComparisonItemDto> comparisonItems = new ArrayList<>(eligibleOffers.size());

        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        Integer fastestDelivery = null;
        BigDecimal highestRating = null;
        BigDecimal highestReliability = null;

        for (CandidateOfferDto offer : eligibleOffers) {
            ProductComparisonItemDto item = ProductComparisonItemDto.builder()
                    .offerId(offer.getOfferId())
                    .productId(offer.getProductId())
                    .productName(offer.getProductName())
                    .brand(offer.getBrand())
                    .model(offer.getModel())
                    .category(offer.getCategory())
                    .vendorId(offer.getVendorId())
                    .vendorName(offer.getVendorName())
                    .source(offer.getSourceName())
                    .price(offer.getPrice())
                    .currency(offer.getCurrency())
                    .deliveryDays(offer.getDeliveryDays())
                    .availability(true)
                    .availableQuantity(offer.getAvailableQuantity())
                    .warrantyYears(offer.getWarrantyYears())
                    .warrantyType(offer.getWarrantyType())
                    .sellerRating(offer.getSellerRating())
                    .reliabilityScore(offer.getReliabilityScore())
                    .returnPolicy(offer.getReturnPolicy())
                    .specifications(offer.getSpecifications())
                    .softPreferencePenalty(offer.getSoftPreferencePenalty())
                    .eligible(offer.isEligible())
                    .build();

            comparisonItems.add(item);

            // Compute comparison bounds
            BigDecimal price = offer.getPrice();
            if (price != null) {
                if (minPrice == null || price.compareTo(minPrice) < 0) minPrice = price;
                if (maxPrice == null || price.compareTo(maxPrice) > 0) maxPrice = price;
            }

            int delivery = offer.getDeliveryDays();
            if (fastestDelivery == null || delivery < fastestDelivery) {
                fastestDelivery = delivery;
            }

            BigDecimal rating = offer.getSellerRating();
            if (rating != null) {
                if (highestRating == null || rating.compareTo(highestRating) > 0) highestRating = rating;
            }

            BigDecimal reliability = offer.getReliabilityScore();
            if (reliability != null) {
                if (highestReliability == null || reliability.compareTo(highestReliability) > 0) highestReliability = reliability;
            }
        }

        log.info("Compared {} eligible offers for procurement [{}]. Price range: [{} - {}]",
                comparisonItems.size(), procurementId, minPrice, maxPrice);

        return new ProcurementComparisonResponse(
                procurementId,
                discoveryResult.getCategory(),
                comparisonItems.size(),
                minPrice != null ? minPrice : BigDecimal.ZERO,
                maxPrice != null ? maxPrice : BigDecimal.ZERO,
                fastestDelivery != null ? fastestDelivery : 0,
                highestRating != null ? highestRating : BigDecimal.ZERO,
                highestReliability != null ? highestReliability : BigDecimal.ZERO,
                comparisonItems,
                rejectionCount
        );
    }
}
