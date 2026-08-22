package com.procurement.engine.ranking.service;

import com.procurement.engine.common.exception.ResourceNotFoundException;
import com.procurement.engine.config.EngineProperties;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.service.ConstraintService;
import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.procurement.repository.ProcurementRequestRepository;
import com.procurement.engine.procurement.repository.VendorOfferRepository;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.ranking.model.ProcurementRankingResponse;
import com.procurement.engine.ranking.model.RankedOfferDto;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import com.procurement.engine.tco.service.TcoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Multi-dimensional Ranking Engine.
 * <p>
 * Evaluates eligible offers across 8 weighted criteria using normalized (0-100) scores.
 * Strictly isolates eligible offers (Pool A) from exception offers (Pool B).
 */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final TcoService tcoService;
    private final ConstraintService constraintService;
    private final EngineProperties engineProperties;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final VendorOfferRepository vendorOfferRepository;
    private final com.procurement.engine.authorization.service.EffectiveAuthorizationResolver effectiveAuthorizationResolver;

    public RankingService(TcoService tcoService,
                          ConstraintService constraintService,
                          EngineProperties engineProperties,
                          ProcurementRequestRepository procurementRequestRepository,
                          VendorOfferRepository vendorOfferRepository,
                          com.procurement.engine.authorization.service.EffectiveAuthorizationResolver effectiveAuthorizationResolver) {
        this.tcoService = tcoService;
        this.constraintService = constraintService;
        this.engineProperties = engineProperties;
        this.procurementRequestRepository = procurementRequestRepository;
        this.vendorOfferRepository = vendorOfferRepository;
        this.effectiveAuthorizationResolver = effectiveAuthorizationResolver;
    }

    /**
     * Ranks all candidate offers for a procurement request.
     */
    @Transactional
    public ProcurementRankingResponse rankOffers(UUID procurementId) {
        ProcurementRequest request = procurementRequestRepository.findById(procurementId)
                .orElseThrow(() -> new ResourceNotFoundException("ProcurementRequest not found with id: " + procurementId));

        // 1. Obtain TCO Analysis Result
        TcoService.TcoAnalysisResult tcoResult = tcoService.calculateTcoForProcurement(procurementId);
        List<TcoBreakdownDto> allBreakdowns = tcoResult.allBreakdowns();

        if (allBreakdowns.isEmpty()) {
            return new ProcurementRankingResponse(
                    procurementId,
                    request.getCategory(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    getWeightsMap(),
                    null,
                    null
            );
        }

        // 2. Fetch constraint evaluation penalties
        List<VendorOffer> offers = vendorOfferRepository.findByProcurementId(procurementId);
        Map<UUID, VendorOffer> offerMap = new HashMap<>();
        Map<UUID, ProductConstraintEvaluation> evalMap = new HashMap<>();

        List<ProcurementConstraint> constraints = request.getConstraints();
        for (VendorOffer offer : offers) {
            offerMap.put(offer.getId(), offer);
            if (offer.getProduct() != null) {
                ProductConstraintEvaluation eval = constraintService.evaluateProduct(offer.getProduct(), constraints);
                evalMap.put(offer.getId(), eval);
            }
        }

        // Resolve authoritative effective authorization limit
        BigDecimal effectiveLimit = effectiveAuthorizationResolver.resolveEffectiveLimit(request);

        // 3. Separate into Pool A (Eligible) and Pool B (Exception)
        // Pool A admission requires:
        // - All mandatory hard constraints pass
        // - Product is available with sufficient inventory
        // - Total purchase cost <= effective authorization limit
        List<TcoBreakdownDto> poolA = new ArrayList<>();
        List<TcoBreakdownDto> poolB = new ArrayList<>();
        int requestedQuantity = request.getQuantity();

        for (TcoBreakdownDto breakdown : allBreakdowns) {
            ProductConstraintEvaluation eval = evalMap.get(breakdown.getOfferId());
            VendorOffer offer = offerMap.get(breakdown.getOfferId());
            Product product = offer != null ? offer.getProduct() : null;

            int availableStock = product != null ? product.getAvailableQuantity() : (offer != null ? offer.getAvailableQuantity() : 0);
            boolean isAvailable = product != null ? (product.isAvailability() && availableStock >= requestedQuantity)
                    : (availableStock >= requestedQuantity);

            boolean withinLimit = breakdown.getTotalPurchaseCost().compareTo(effectiveLimit) <= 0;
            boolean isEligible = eval != null && eval.isEligible() && isAvailable && withinLimit;
            if (isEligible) {
                poolA.add(breakdown);
            } else {
                poolB.add(breakdown);
            }
        }

        // 4. Compute bounds across all candidates for normalization
        Bounds bounds = computeBounds(allBreakdowns);

        // 5. Score and Rank Pool A (Eligible)
        List<RankedOfferDto> rankedEligible = scoreAndRankPool(poolA, bounds, offerMap, evalMap, effectiveLimit, false);

        // 6. Score and Rank Pool B (Exception)
        List<RankedOfferDto> rankedException = scoreAndRankPool(poolB, bounds, offerMap, evalMap, effectiveLimit, true);

        RankedOfferDto topEligible = rankedEligible.isEmpty() ? null : rankedEligible.get(0);
        RankedOfferDto topException = rankedException.isEmpty() ? null : rankedException.get(0);

        log.info("Ranked procurement [{}] - {} eligible offers, {} exception offers. Top eligible: [{}] (Score: {})",
                procurementId, rankedEligible.size(), rankedException.size(),
                topEligible != null ? topEligible.getProductName() : "NONE",
                topEligible != null ? topEligible.getTotalScore() : 0);

        return new ProcurementRankingResponse(
                procurementId,
                request.getCategory(),
                rankedEligible,
                rankedException,
                getWeightsMap(),
                topEligible,
                topException
        );
    }

    private List<RankedOfferDto> scoreAndRankPool(List<TcoBreakdownDto> pool,
                                                  Bounds bounds,
                                                  Map<UUID, VendorOffer> offerMap,
                                                  Map<UUID, ProductConstraintEvaluation> evalMap,
                                                  BigDecimal authLimit,
                                                  boolean isException) {
        if (pool.isEmpty()) {
            return Collections.emptyList();
        }

        EngineProperties.RankingProperties.Weights weights = engineProperties.getRanking().getWeights();
        List<RankedOfferDto> scoredList = new ArrayList<>();

        for (TcoBreakdownDto breakdown : pool) {
            VendorOffer offer = offerMap.get(breakdown.getOfferId());
            Product product = offer != null ? offer.getProduct() : null;
            ProductConstraintEvaluation eval = evalMap.get(breakdown.getOfferId());

            BigDecimal penalty = eval != null ? eval.getTotalPenalty() : BigDecimal.ZERO;
            boolean budgetExceeded = authLimit != null && breakdown.getTotalPurchaseCost().compareTo(authLimit) > 0;

            // Normalize individual dimensions (0 to 100)
            BigDecimal tcoScore = normalizeLowerIsBetter(breakdown.getTotalTco(), bounds.minTco, bounds.maxTco);
            BigDecimal priceScore = normalizeLowerIsBetter(breakdown.getTotalPurchaseCost(), bounds.minPrice, bounds.maxPrice);
            BigDecimal deliveryScore = normalizeLowerIsBetter(BigDecimal.valueOf(product != null ? product.getDeliveryDays() : 7),
                    BigDecimal.valueOf(bounds.minDeliveryDays), BigDecimal.valueOf(bounds.maxDeliveryDays));

            BigDecimal relScore = normalizeHigherIsBetter(breakdown.getFailureRate() != null
                            ? BigDecimal.ONE.subtract(breakdown.getFailureRate())
                            : (product != null ? product.getReliabilityScore() : new BigDecimal("0.85")),
                    BigDecimal.ZERO, BigDecimal.ONE);

            BigDecimal warrantyScore = normalizeHigherIsBetter(BigDecimal.valueOf(breakdown.getWarrantyYears()),
                    BigDecimal.valueOf(bounds.minWarrantyYears), BigDecimal.valueOf(bounds.maxWarrantyYears));

            BigDecimal sellerRating = product != null ? product.getSellerRating() : new BigDecimal("4.00");
            BigDecimal sellerRatingScore = sellerRating.divide(new BigDecimal("5.00"), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            String returnPolicy = product != null && product.getVendor() != null ? product.getVendor().getReturnPolicy() : "";
            int returnWindow = product != null ? product.getReturnWindow() : 15;
            BigDecimal returnPolicyScore = scoreReturnPolicy(returnWindow, returnPolicy);

            BigDecimal softPreferenceScore = BigDecimal.valueOf(100).subtract(penalty.multiply(BigDecimal.valueOf(20)))
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            // Compute Weighted Total Score (0 to 100)
            BigDecimal totalScore = tcoScore.multiply(weights.getTco())
                    .add(priceScore.multiply(weights.getPrice()))
                    .add(relScore.multiply(weights.getReliability()))
                    .add(deliveryScore.multiply(weights.getDelivery()))
                    .add(warrantyScore.multiply(weights.getWarranty()))
                    .add(sellerRatingScore.multiply(weights.getSellerRating()))
                    .add(returnPolicyScore.multiply(weights.getReturnPolicy()))
                    .add(softPreferenceScore.multiply(weights.getSoftPreferences()))
                    .setScale(2, RoundingMode.HALF_UP);

            RankedOfferDto rankedOffer = RankedOfferDto.builder()
                    .offerId(breakdown.getOfferId())
                    .productId(breakdown.getProductId())
                    .productName(breakdown.getProductName())
                    .vendorName(breakdown.getVendorName())
                    .category(product != null ? product.getCategory() : "General")
                    .price(breakdown.getTotalPurchaseCost())
                    .unitPrice(breakdown.getUnitPurchaseCost())
                    .tco(breakdown.getTotalTco())
                    .unitTco(breakdown.getUnitTco())
                    .totalScore(totalScore)
                    .tcoScore(tcoScore)
                    .priceScore(priceScore)
                    .reliabilityScore(relScore)
                    .deliveryScore(deliveryScore)
                    .warrantyScore(warrantyScore)
                    .sellerRatingScore(sellerRatingScore)
                    .returnPolicyScore(returnPolicyScore)
                    .softPreferenceScore(softPreferenceScore)
                    .deliveryDays(product != null ? product.getDeliveryDays() : 0)
                    .warrantyYears(breakdown.getWarrantyYears())
                    .reliability(product != null ? product.getReliabilityScore() : BigDecimal.ZERO)
                    .sellerRating(sellerRating)
                    .returnPolicy(returnPolicy)
                    .eligible(!isException)
                    .budgetExceeded(budgetExceeded)
                    .isExceptionOffer(isException)
                    .build();

            scoredList.add(rankedOffer);
        }

        // Sort descending by totalScore (tie-breakers: lower TCO, then lower price, then higher reliability)
        scoredList.sort((a, b) -> {
            if (isException) {
                ProductConstraintEvaluation evalA = evalMap.get(a.getOfferId());
                ProductConstraintEvaluation evalB = evalMap.get(b.getOfferId());
                boolean aCompliant = a.isBudgetExceeded() && evalA != null && evalA.isEligible();
                boolean bCompliant = b.isBudgetExceeded() && evalB != null && evalB.isEligible();
                if (aCompliant != bCompliant) {
                    return aCompliant ? -1 : 1;
                }
            }
            int cmp = b.getTotalScore().compareTo(a.getTotalScore());
            if (cmp != 0) return cmp;
            int tcoCmp = a.getTco().compareTo(b.getTco());
            if (tcoCmp != 0) return tcoCmp;
            int priceCmp = a.getPrice().compareTo(b.getPrice());
            if (priceCmp != 0) return priceCmp;
            return b.getReliability().compareTo(a.getReliability());
        });

        // Assign ranks 1, 2, 3...
        List<RankedOfferDto> resultWithRanks = new ArrayList<>();
        for (int i = 0; i < scoredList.size(); i++) {
            RankedOfferDto item = scoredList.get(i);
            RankedOfferDto withRank = RankedOfferDto.builder()
                    .rank(i + 1)
                    .offerId(item.getOfferId())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .vendorName(item.getVendorName())
                    .category(item.getCategory())
                    .price(item.getPrice())
                    .unitPrice(item.getUnitPrice())
                    .tco(item.getTco())
                    .unitTco(item.getUnitTco())
                    .totalScore(item.getTotalScore())
                    .tcoScore(item.getTcoScore())
                    .priceScore(item.getPriceScore())
                    .reliabilityScore(item.getReliabilityScore())
                    .deliveryScore(item.getDeliveryScore())
                    .warrantyScore(item.getWarrantyScore())
                    .sellerRatingScore(item.getSellerRatingScore())
                    .returnPolicyScore(item.getReturnPolicyScore())
                    .softPreferenceScore(item.getSoftPreferenceScore())
                    .deliveryDays(item.getDeliveryDays())
                    .warrantyYears(item.getWarrantyYears())
                    .reliability(item.getReliability())
                    .sellerRating(item.getSellerRating())
                    .returnPolicy(item.getReturnPolicy())
                    .eligible(item.isEligible())
                    .budgetExceeded(item.isBudgetExceeded())
                    .isExceptionOffer(item.isExceptionOffer())
                    .build();
            resultWithRanks.add(withRank);
        }

        return resultWithRanks;
    }

    private BigDecimal normalizeLowerIsBetter(BigDecimal val, BigDecimal min, BigDecimal max) {
        if (val == null || min == null || max == null || min.compareTo(max) == 0) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal range = max.subtract(min);
        BigDecimal diff = val.subtract(min);
        BigDecimal normalized = BigDecimal.ONE.subtract(diff.divide(range, 4, RoundingMode.HALF_UP));
        return normalized.multiply(BigDecimal.valueOf(100)).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeHigherIsBetter(BigDecimal val, BigDecimal min, BigDecimal max) {
        if (val == null || min == null || max == null || min.compareTo(max) == 0) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal range = max.subtract(min);
        BigDecimal diff = val.subtract(min);
        BigDecimal normalized = diff.divide(range, 4, RoundingMode.HALF_UP);
        return normalized.multiply(BigDecimal.valueOf(100)).max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreReturnPolicy(int returnWindow, String returnPolicy) {
        if (returnWindow >= 30 || (returnPolicy != null && returnPolicy.toLowerCase().contains("no-questions-asked"))) {
            return new BigDecimal("100.00");
        } else if (returnWindow >= 15) {
            return new BigDecimal("75.00");
        } else if (returnWindow >= 7) {
            return new BigDecimal("50.00");
        }
        return new BigDecimal("25.00");
    }

    private Bounds computeBounds(List<TcoBreakdownDto> breakdowns) {
        BigDecimal minPrice = null, maxPrice = null;
        BigDecimal minTco = null, maxTco = null;
        int minDelivery = Integer.MAX_VALUE, maxDelivery = Integer.MIN_VALUE;
        int minWarranty = Integer.MAX_VALUE, maxWarranty = Integer.MIN_VALUE;

        for (TcoBreakdownDto b : breakdowns) {
            if (minPrice == null || b.getTotalPurchaseCost().compareTo(minPrice) < 0) minPrice = b.getTotalPurchaseCost();
            if (maxPrice == null || b.getTotalPurchaseCost().compareTo(maxPrice) > 0) maxPrice = b.getTotalPurchaseCost();

            if (minTco == null || b.getTotalTco().compareTo(minTco) < 0) minTco = b.getTotalTco();
            if (maxTco == null || b.getTotalTco().compareTo(maxTco) > 0) maxTco = b.getTotalTco();

            int war = b.getWarrantyYears();
            if (war < minWarranty) minWarranty = war;
            if (war > maxWarranty) maxWarranty = war;
        }

        return new Bounds(minPrice != null ? minPrice : BigDecimal.ZERO,
                maxPrice != null ? maxPrice : BigDecimal.ZERO,
                minTco != null ? minTco : BigDecimal.ZERO,
                maxTco != null ? maxTco : BigDecimal.ZERO,
                minDelivery == Integer.MAX_VALUE ? 1 : minDelivery,
                maxDelivery == Integer.MIN_VALUE ? 7 : maxDelivery,
                minWarranty == Integer.MAX_VALUE ? 1 : minWarranty,
                maxWarranty == Integer.MIN_VALUE ? 3 : maxWarranty);
    }

    private Map<String, BigDecimal> getWeightsMap() {
        EngineProperties.RankingProperties.Weights w = engineProperties.getRanking().getWeights();
        return Map.of(
                "tco", w.getTco(),
                "price", w.getPrice(),
                "reliability", w.getReliability(),
                "delivery", w.getDelivery(),
                "warranty", w.getWarranty(),
                "returnPolicy", w.getReturnPolicy(),
                "sellerRating", w.getSellerRating(),
                "softPreferences", w.getSoftPreferences()
        );
    }

    private record Bounds(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minTco,
            BigDecimal maxTco,
            int minDeliveryDays,
            int maxDeliveryDays,
            int minWarrantyYears,
            int maxWarrantyYears
    ) {}
}
