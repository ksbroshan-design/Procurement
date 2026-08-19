package com.procurement.engine.tco.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Detailed, decomposable Total Cost of Ownership (TCO) breakdown for a candidate offer.
 */
public class TcoBreakdownDto {

    private final UUID offerId;
    private final UUID productId;
    private final String productName;
    private final String vendorName;
    private final int quantity;
    private final int horizonYears;

    // Unit costs
    private final BigDecimal unitPurchaseCost;
    private final BigDecimal unitMaintenanceCost;
    private final BigDecimal unitExpectedRepairCost;
    private final BigDecimal unitExpectedDowntimeCost;
    private final BigDecimal unitReplacementCost;
    private final BigDecimal unitWarrantyBenefit;
    private final BigDecimal unitTco;

    // Total procurement costs (unit * quantity)
    private final BigDecimal totalPurchaseCost;
    private final BigDecimal totalMaintenanceCost;
    private final BigDecimal totalExpectedRepairCost;
    private final BigDecimal totalExpectedDowntimeCost;
    private final BigDecimal totalReplacementCost;
    private final BigDecimal totalWarrantyBenefit;
    private final BigDecimal totalTco;

    // Reliability & Warranty Context
    private final BigDecimal failureRate;
    private final BigDecimal averageRepairCost;
    private final BigDecimal averageDowntimeCost;
    private final int warrantyYears;
    private final String warrantyType;
    private final boolean dataGrounded;
    private final List<String> assumptions;

    public TcoBreakdownDto(UUID offerId,
                           UUID productId,
                           String productName,
                           String vendorName,
                           int quantity,
                           int horizonYears,
                           BigDecimal unitPurchaseCost,
                           BigDecimal unitMaintenanceCost,
                           BigDecimal unitExpectedRepairCost,
                           BigDecimal unitExpectedDowntimeCost,
                           BigDecimal unitReplacementCost,
                           BigDecimal unitWarrantyBenefit,
                           BigDecimal unitTco,
                           BigDecimal totalPurchaseCost,
                           BigDecimal totalMaintenanceCost,
                           BigDecimal totalExpectedRepairCost,
                           BigDecimal totalExpectedDowntimeCost,
                           BigDecimal totalReplacementCost,
                           BigDecimal totalWarrantyBenefit,
                           BigDecimal totalTco,
                           BigDecimal failureRate,
                           BigDecimal averageRepairCost,
                           BigDecimal averageDowntimeCost,
                           int warrantyYears,
                           String warrantyType,
                           boolean dataGrounded,
                           List<String> assumptions) {
        this.offerId = offerId;
        this.productId = productId;
        this.productName = productName;
        this.vendorName = vendorName;
        this.quantity = quantity;
        this.horizonYears = horizonYears;
        this.unitPurchaseCost = unitPurchaseCost;
        this.unitMaintenanceCost = unitMaintenanceCost;
        this.unitExpectedRepairCost = unitExpectedRepairCost;
        this.unitExpectedDowntimeCost = unitExpectedDowntimeCost;
        this.unitReplacementCost = unitReplacementCost;
        this.unitWarrantyBenefit = unitWarrantyBenefit;
        this.unitTco = unitTco;
        this.totalPurchaseCost = totalPurchaseCost;
        this.totalMaintenanceCost = totalMaintenanceCost;
        this.totalExpectedRepairCost = totalExpectedRepairCost;
        this.totalExpectedDowntimeCost = totalExpectedDowntimeCost;
        this.totalReplacementCost = totalReplacementCost;
        this.totalWarrantyBenefit = totalWarrantyBenefit;
        this.totalTco = totalTco;
        this.failureRate = failureRate;
        this.averageRepairCost = averageRepairCost;
        this.averageDowntimeCost = averageDowntimeCost;
        this.warrantyYears = warrantyYears;
        this.warrantyType = warrantyType;
        this.dataGrounded = dataGrounded;
        this.assumptions = assumptions != null ? List.copyOf(assumptions) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID offerId;
        private UUID productId;
        private String productName;
        private String vendorName;
        private int quantity = 1;
        private int horizonYears = 3;
        private BigDecimal unitPurchaseCost = BigDecimal.ZERO;
        private BigDecimal unitMaintenanceCost = BigDecimal.ZERO;
        private BigDecimal unitExpectedRepairCost = BigDecimal.ZERO;
        private BigDecimal unitExpectedDowntimeCost = BigDecimal.ZERO;
        private BigDecimal unitReplacementCost = BigDecimal.ZERO;
        private BigDecimal unitWarrantyBenefit = BigDecimal.ZERO;
        private BigDecimal unitTco = BigDecimal.ZERO;
        private BigDecimal totalPurchaseCost = BigDecimal.ZERO;
        private BigDecimal totalMaintenanceCost = BigDecimal.ZERO;
        private BigDecimal totalExpectedRepairCost = BigDecimal.ZERO;
        private BigDecimal totalExpectedDowntimeCost = BigDecimal.ZERO;
        private BigDecimal totalReplacementCost = BigDecimal.ZERO;
        private BigDecimal totalWarrantyBenefit = BigDecimal.ZERO;
        private BigDecimal totalTco = BigDecimal.ZERO;
        private BigDecimal failureRate = BigDecimal.ZERO;
        private BigDecimal averageRepairCost = BigDecimal.ZERO;
        private BigDecimal averageDowntimeCost = BigDecimal.ZERO;
        private int warrantyYears = 1;
        private String warrantyType = "STANDARD";
        private boolean dataGrounded = true;
        private List<String> assumptions = Collections.emptyList();

        public Builder offerId(UUID offerId) { this.offerId = offerId; return this; }
        public Builder productId(UUID productId) { this.productId = productId; return this; }
        public Builder productName(String productName) { this.productName = productName; return this; }
        public Builder vendorName(String vendorName) { this.vendorName = vendorName; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder horizonYears(int horizonYears) { this.horizonYears = horizonYears; return this; }
        public Builder unitPurchaseCost(BigDecimal unitPurchaseCost) { this.unitPurchaseCost = unitPurchaseCost; return this; }
        public Builder unitMaintenanceCost(BigDecimal unitMaintenanceCost) { this.unitMaintenanceCost = unitMaintenanceCost; return this; }
        public Builder unitExpectedRepairCost(BigDecimal unitExpectedRepairCost) { this.unitExpectedRepairCost = unitExpectedRepairCost; return this; }
        public Builder unitExpectedDowntimeCost(BigDecimal unitExpectedDowntimeCost) { this.unitExpectedDowntimeCost = unitExpectedDowntimeCost; return this; }
        public Builder unitReplacementCost(BigDecimal unitReplacementCost) { this.unitReplacementCost = unitReplacementCost; return this; }
        public Builder unitWarrantyBenefit(BigDecimal unitWarrantyBenefit) { this.unitWarrantyBenefit = unitWarrantyBenefit; return this; }
        public Builder unitTco(BigDecimal unitTco) { this.unitTco = unitTco; return this; }
        public Builder totalPurchaseCost(BigDecimal totalPurchaseCost) { this.totalPurchaseCost = totalPurchaseCost; return this; }
        public Builder totalMaintenanceCost(BigDecimal totalMaintenanceCost) { this.totalMaintenanceCost = totalMaintenanceCost; return this; }
        public Builder totalExpectedRepairCost(BigDecimal totalExpectedRepairCost) { this.totalExpectedRepairCost = totalExpectedRepairCost; return this; }
        public Builder totalExpectedDowntimeCost(BigDecimal totalExpectedDowntimeCost) { this.totalExpectedDowntimeCost = totalExpectedDowntimeCost; return this; }
        public Builder totalReplacementCost(BigDecimal totalReplacementCost) { this.totalReplacementCost = totalReplacementCost; return this; }
        public Builder totalWarrantyBenefit(BigDecimal totalWarrantyBenefit) { this.totalWarrantyBenefit = totalWarrantyBenefit; return this; }
        public Builder totalTco(BigDecimal totalTco) { this.totalTco = totalTco; return this; }
        public Builder failureRate(BigDecimal failureRate) { this.failureRate = failureRate; return this; }
        public Builder averageRepairCost(BigDecimal averageRepairCost) { this.averageRepairCost = averageRepairCost; return this; }
        public Builder averageDowntimeCost(BigDecimal averageDowntimeCost) { this.averageDowntimeCost = averageDowntimeCost; return this; }
        public Builder warrantyYears(int warrantyYears) { this.warrantyYears = warrantyYears; return this; }
        public Builder warrantyType(String warrantyType) { this.warrantyType = warrantyType; return this; }
        public Builder dataGrounded(boolean dataGrounded) { this.dataGrounded = dataGrounded; return this; }
        public Builder assumptions(List<String> assumptions) { this.assumptions = assumptions; return this; }

        public TcoBreakdownDto build() {
            return new TcoBreakdownDto(offerId, productId, productName, vendorName, quantity, horizonYears,
                    unitPurchaseCost, unitMaintenanceCost, unitExpectedRepairCost, unitExpectedDowntimeCost,
                    unitReplacementCost, unitWarrantyBenefit, unitTco, totalPurchaseCost, totalMaintenanceCost,
                    totalExpectedRepairCost, totalExpectedDowntimeCost, totalReplacementCost, totalWarrantyBenefit,
                    totalTco, failureRate, averageRepairCost, averageDowntimeCost, warrantyYears, warrantyType,
                    dataGrounded, assumptions);
        }
    }

    public UUID getOfferId() { return offerId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getVendorName() { return vendorName; }
    public int getQuantity() { return quantity; }
    public int getHorizonYears() { return horizonYears; }
    public BigDecimal getUnitPurchaseCost() { return unitPurchaseCost; }
    public BigDecimal getUnitMaintenanceCost() { return unitMaintenanceCost; }
    public BigDecimal getUnitExpectedRepairCost() { return unitExpectedRepairCost; }
    public BigDecimal getUnitExpectedDowntimeCost() { return unitExpectedDowntimeCost; }
    public BigDecimal getUnitReplacementCost() { return unitReplacementCost; }
    public BigDecimal getUnitWarrantyBenefit() { return unitWarrantyBenefit; }
    public BigDecimal getUnitTco() { return unitTco; }
    public BigDecimal getTotalPurchaseCost() { return totalPurchaseCost; }
    public BigDecimal getTotalMaintenanceCost() { return totalMaintenanceCost; }
    public BigDecimal getTotalExpectedRepairCost() { return totalExpectedRepairCost; }
    public BigDecimal getTotalExpectedDowntimeCost() { return totalExpectedDowntimeCost; }
    public BigDecimal getTotalReplacementCost() { return totalReplacementCost; }
    public BigDecimal getTotalWarrantyBenefit() { return totalWarrantyBenefit; }
    public BigDecimal getTotalTco() { return totalTco; }
    public BigDecimal getFailureRate() { return failureRate; }
    public BigDecimal getAverageRepairCost() { return averageRepairCost; }
    public BigDecimal getAverageDowntimeCost() { return averageDowntimeCost; }
    public int getWarrantyYears() { return warrantyYears; }
    public String getWarrantyType() { return warrantyType; }
    public boolean isDataGrounded() { return dataGrounded; }
    public List<String> getAssumptions() { return assumptions; }
}
