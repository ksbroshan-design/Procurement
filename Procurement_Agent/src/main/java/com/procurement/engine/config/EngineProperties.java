package com.procurement.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "app.engine")
public class EngineProperties {

    private TcoProperties tco = new TcoProperties();
    private RevalidationProperties revalidation = new RevalidationProperties();
    private RankingProperties ranking = new RankingProperties();

    public TcoProperties getTco() {
        return tco;
    }

    public void setTco(TcoProperties tco) {
        this.tco = tco;
    }

    public RevalidationProperties getRevalidation() {
        return revalidation;
    }

    public void setRevalidation(RevalidationProperties revalidation) {
        this.revalidation = revalidation;
    }

    public RankingProperties getRanking() {
        return ranking;
    }

    public void setRanking(RankingProperties ranking) {
        this.ranking = ranking;
    }

    public static class TcoProperties {
        private int horizonYears = 3;
        private BigDecimal annualMaintenanceRate = new BigDecimal("0.02");
        private BigDecimal onsiteWarrantyCoverage = new BigDecimal("0.95");
        private BigDecimal onsiteDowntimeCoverage = new BigDecimal("0.40");
        private BigDecimal standardWarrantyCoverage = new BigDecimal("0.80");
        private BigDecimal limitedWarrantyCoverage = new BigDecimal("0.50");
        private BigDecimal defaultFailureRate = new BigDecimal("0.05");
        private BigDecimal defaultRepairCostFraction = new BigDecimal("0.15");
        private BigDecimal defaultDowntimeCostFraction = new BigDecimal("0.05");

        public int getHorizonYears() { return horizonYears; }
        public void setHorizonYears(int horizonYears) { this.horizonYears = horizonYears; }
        public BigDecimal getAnnualMaintenanceRate() { return annualMaintenanceRate; }
        public void setAnnualMaintenanceRate(BigDecimal annualMaintenanceRate) { this.annualMaintenanceRate = annualMaintenanceRate; }
        public BigDecimal getOnsiteWarrantyCoverage() { return onsiteWarrantyCoverage; }
        public void setOnsiteWarrantyCoverage(BigDecimal onsiteWarrantyCoverage) { this.onsiteWarrantyCoverage = onsiteWarrantyCoverage; }
        public BigDecimal getOnsiteDowntimeCoverage() { return onsiteDowntimeCoverage; }
        public void setOnsiteDowntimeCoverage(BigDecimal onsiteDowntimeCoverage) { this.onsiteDowntimeCoverage = onsiteDowntimeCoverage; }
        public BigDecimal getStandardWarrantyCoverage() { return standardWarrantyCoverage; }
        public void setStandardWarrantyCoverage(BigDecimal standardWarrantyCoverage) { this.standardWarrantyCoverage = standardWarrantyCoverage; }
        public BigDecimal getLimitedWarrantyCoverage() { return limitedWarrantyCoverage; }
        public void setLimitedWarrantyCoverage(BigDecimal limitedWarrantyCoverage) { this.limitedWarrantyCoverage = limitedWarrantyCoverage; }
        public BigDecimal getDefaultFailureRate() { return defaultFailureRate; }
        public void setDefaultFailureRate(BigDecimal defaultFailureRate) { this.defaultFailureRate = defaultFailureRate; }
        public BigDecimal getDefaultRepairCostFraction() { return defaultRepairCostFraction; }
        public void setDefaultRepairCostFraction(BigDecimal defaultRepairCostFraction) { this.defaultRepairCostFraction = defaultRepairCostFraction; }
        public BigDecimal getDefaultDowntimeCostFraction() { return defaultDowntimeCostFraction; }
        public void setDefaultDowntimeCostFraction(BigDecimal defaultDowntimeCostFraction) { this.defaultDowntimeCostFraction = defaultDowntimeCostFraction; }
    }

    public static class RevalidationProperties {
        private int maxRetryAttempts = 3;

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }
    }

    public static class RankingProperties {
        private Weights weights = new Weights();

        public Weights getWeights() {
            return weights;
        }

        public void setWeights(Weights weights) {
            this.weights = weights;
        }

        public static class Weights {
            private BigDecimal tco = new BigDecimal("0.30");
            private BigDecimal price = new BigDecimal("0.20");
            private BigDecimal reliability = new BigDecimal("0.15");
            private BigDecimal delivery = new BigDecimal("0.10");
            private BigDecimal warranty = new BigDecimal("0.10");
            private BigDecimal returnPolicy = new BigDecimal("0.05");
            private BigDecimal sellerRating = new BigDecimal("0.05");
            private BigDecimal softPreferences = new BigDecimal("0.05");

            public BigDecimal getTco() { return tco; }
            public void setTco(BigDecimal tco) { this.tco = tco; }
            public BigDecimal getPrice() { return price; }
            public void setPrice(BigDecimal price) { this.price = price; }
            public BigDecimal getReliability() { return reliability; }
            public void setReliability(BigDecimal reliability) { this.reliability = reliability; }
            public BigDecimal getDelivery() { return delivery; }
            public void setDelivery(BigDecimal delivery) { this.delivery = delivery; }
            public BigDecimal getWarranty() { return warranty; }
            public void setWarranty(BigDecimal warranty) { this.warranty = warranty; }
            public BigDecimal getReturnPolicy() { return returnPolicy; }
            public void setReturnPolicy(BigDecimal returnPolicy) { this.returnPolicy = returnPolicy; }
            public BigDecimal getSellerRating() { return sellerRating; }
            public void setSellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; }
            public BigDecimal getSoftPreferences() { return softPreferences; }
            public void setSoftPreferences(BigDecimal softPreferences) { this.softPreferences = softPreferences; }
        }
    }
}
