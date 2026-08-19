package com.procurement.engine.tco.service;

import com.procurement.engine.config.EngineProperties;
import com.procurement.engine.procurement.entity.VendorOffer;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.product.entity.ReliabilityHistory;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic Total Cost of Ownership (TCO) Calculator.
 * <p>
 * Grounded in actual product pricing, vendor warranty terms, and historical reliability data.
 */
@Component
public class TcoCalculator {

    private final EngineProperties engineProperties;

    public TcoCalculator(EngineProperties engineProperties) {
        this.engineProperties = engineProperties;
    }

    /**
     * Calculates the deterministic TCO breakdown for a given product and vendor offer.
     *
     * @param product            The underlying product
     * @param offer              The vendor offer
     * @param reliabilityHistory Optional historical reliability data
     * @param quantity           The procurement quantity
     * @param customHorizonYears Optional custom horizon; if null or <= 0, uses EngineProperties default
     * @return Fully populated TcoBreakdownDto
     */
    public TcoBreakdownDto calculateTco(Product product,
                                       VendorOffer offer,
                                       Optional<ReliabilityHistory> reliabilityHistory,
                                       int quantity,
                                       Integer customHorizonYears) {
        if (product == null || offer == null) {
            throw new IllegalArgumentException("Product and VendorOffer cannot be null");
        }

        int qty = Math.max(1, quantity);
        int horizon = (customHorizonYears != null && customHorizonYears > 0)
                ? customHorizonYears
                : engineProperties.getTco().getHorizonYears();

        BigDecimal unitPrice = offer.getOriginalPrice() != null
                ? offer.getOriginalPrice().setScale(2, RoundingMode.HALF_UP)
                : (product.getPrice() != null ? product.getPrice().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        List<String> assumptions = new ArrayList<>();
        assumptions.add(String.format("TCO evaluated over a %d-year ownership horizon", horizon));

        // 1. Resolve Reliability Metrics
        boolean dataGrounded = reliabilityHistory.isPresent();
        BigDecimal failureRate;
        BigDecimal avgRepairCost;
        BigDecimal avgDowntimeCost;

        if (dataGrounded) {
            ReliabilityHistory rh = reliabilityHistory.get();
            failureRate = rh.getFailureRate() != null ? rh.getFailureRate() : engineProperties.getTco().getDefaultFailureRate();
            avgRepairCost = rh.getAverageRepairCost() != null ? rh.getAverageRepairCost() : unitPrice.multiply(engineProperties.getTco().getDefaultRepairCostFraction());
            avgDowntimeCost = rh.getAverageDowntimeCost() != null ? rh.getAverageDowntimeCost() : unitPrice.multiply(engineProperties.getTco().getDefaultDowntimeCostFraction());
            assumptions.add(String.format("Historical reliability data applied: annual failure rate = %.2f%%, avg repair cost = ₹%.2f, avg downtime cost = ₹%.2f (sample size: %d)",
                    failureRate.multiply(BigDecimal.valueOf(100)), avgRepairCost, avgDowntimeCost, rh.getSampleSize()));
        } else {
            // Fallback grounded in product reliability score
            BigDecimal relScore = product.getReliabilityScore() != null ? product.getReliabilityScore() : new BigDecimal("0.85");
            failureRate = BigDecimal.ONE.subtract(relScore).multiply(new BigDecimal("0.25")).setScale(4, RoundingMode.HALF_UP);
            avgRepairCost = unitPrice.multiply(engineProperties.getTco().getDefaultRepairCostFraction()).setScale(2, RoundingMode.HALF_UP);
            avgDowntimeCost = unitPrice.multiply(engineProperties.getTco().getDefaultDowntimeCostFraction()).setScale(2, RoundingMode.HALF_UP);
            assumptions.add(String.format("Estimated reliability parameters from catalog score (%.2f): estimated failure rate = %.2f%%",
                    relScore, failureRate.multiply(BigDecimal.valueOf(100))));
        }

        // 2. Maintenance Cost (2% per year)
        BigDecimal annualMaintRate = engineProperties.getTco().getAnnualMaintenanceRate();
        BigDecimal unitMaintenanceCost = unitPrice.multiply(annualMaintRate)
                .multiply(BigDecimal.valueOf(horizon))
                .setScale(2, RoundingMode.HALF_UP);
        assumptions.add(String.format("Annual baseline maintenance estimated at %.1f%% of unit price per year", annualMaintRate.multiply(BigDecimal.valueOf(100))));

        // 3. Gross Expected Repair & Downtime Costs over Horizon
        BigDecimal horizonBd = BigDecimal.valueOf(horizon);
        BigDecimal totalExpectedFailures = failureRate.multiply(horizonBd);
        BigDecimal grossRepairCost = totalExpectedFailures.multiply(avgRepairCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossDowntimeCost = totalExpectedFailures.multiply(avgDowntimeCost).setScale(2, RoundingMode.HALF_UP);

        // 4. Warranty Benefits
        int warrantyYears = offer.getWarrantyYears() > 0 ? offer.getWarrantyYears() : product.getWarrantyDuration();
        String warrantyType = product.getWarrantyType() != null ? product.getWarrantyType().toUpperCase() : "STANDARD";
        int coveredYears = Math.min(warrantyYears, horizon);

        BigDecimal repairCoverageRate;
        BigDecimal downtimeCoverageRate;

        if (warrantyType.contains("ONSITE") || warrantyType.contains("EXTENDED")) {
            repairCoverageRate = engineProperties.getTco().getOnsiteWarrantyCoverage();
            downtimeCoverageRate = engineProperties.getTco().getOnsiteDowntimeCoverage();
        } else if (warrantyType.contains("STANDARD")) {
            repairCoverageRate = engineProperties.getTco().getStandardWarrantyCoverage();
            downtimeCoverageRate = BigDecimal.ZERO;
        } else {
            repairCoverageRate = engineProperties.getTco().getLimitedWarrantyCoverage();
            downtimeCoverageRate = BigDecimal.ZERO;
        }

        BigDecimal coveredFailures = failureRate.multiply(BigDecimal.valueOf(coveredYears));
        BigDecimal warrantyRepairBenefit = coveredFailures.multiply(avgRepairCost).multiply(repairCoverageRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal warrantyDowntimeBenefit = coveredFailures.multiply(avgDowntimeCost).multiply(downtimeCoverageRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitWarrantyBenefit = warrantyRepairBenefit.add(warrantyDowntimeBenefit);

        assumptions.add(String.format("Warranty (%d-year %s) offsets ₹%.2f in repair/downtime costs during covered period",
                warrantyYears, warrantyType, unitWarrantyBenefit));

        // 5. Replacement Risk Cost (for high failure rates post-warranty)
        BigDecimal unitReplacementCost = BigDecimal.ZERO;
        if (failureRate.compareTo(new BigDecimal("0.10")) > 0 && warrantyYears < horizon) {
            BigDecimal excessFailureRate = failureRate.subtract(new BigDecimal("0.05"));
            BigDecimal postWarrantyRatio = BigDecimal.valueOf(horizon - warrantyYears).divide(horizonBd, 4, RoundingMode.HALF_UP);
            unitReplacementCost = unitPrice.multiply(excessFailureRate).multiply(postWarrantyRatio).multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
            assumptions.add(String.format("High failure rate exposure post-warranty adds ₹%.2f replacement risk", unitReplacementCost));
        }

        // 6. Net Unit TCO
        BigDecimal unitTco = unitPrice
                .add(unitMaintenanceCost)
                .add(grossRepairCost)
                .add(grossDowntimeCost)
                .add(unitReplacementCost)
                .subtract(unitWarrantyBenefit)
                .setScale(2, RoundingMode.HALF_UP);

        // 7. Quantity Scaling (Total Procurement TCO)
        BigDecimal qtyBd = BigDecimal.valueOf(qty);
        BigDecimal totalPurchaseCost = unitPrice.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalMaintenanceCost = unitMaintenanceCost.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExpectedRepairCost = grossRepairCost.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExpectedDowntimeCost = grossDowntimeCost.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalReplacementCost = unitReplacementCost.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalWarrantyBenefit = unitWarrantyBenefit.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTco = unitTco.multiply(qtyBd).setScale(2, RoundingMode.HALF_UP);

        return TcoBreakdownDto.builder()
                .offerId(offer.getId())
                .productId(product.getId())
                .productName(product.getName())
                .vendorName(offer.getVendor() != null ? offer.getVendor().getName() : "Vendor")
                .quantity(qty)
                .horizonYears(horizon)
                .unitPurchaseCost(unitPrice)
                .unitMaintenanceCost(unitMaintenanceCost)
                .unitExpectedRepairCost(grossRepairCost)
                .unitExpectedDowntimeCost(grossDowntimeCost)
                .unitReplacementCost(unitReplacementCost)
                .unitWarrantyBenefit(unitWarrantyBenefit)
                .unitTco(unitTco)
                .totalPurchaseCost(totalPurchaseCost)
                .totalMaintenanceCost(totalMaintenanceCost)
                .totalExpectedRepairCost(totalExpectedRepairCost)
                .totalExpectedDowntimeCost(totalExpectedDowntimeCost)
                .totalReplacementCost(totalReplacementCost)
                .totalWarrantyBenefit(totalWarrantyBenefit)
                .totalTco(totalTco)
                .failureRate(failureRate)
                .averageRepairCost(avgRepairCost)
                .averageDowntimeCost(avgDowntimeCost)
                .warrantyYears(warrantyYears)
                .warrantyType(warrantyType)
                .dataGrounded(dataGrounded)
                .assumptions(assumptions)
                .build();
    }
}
