package com.procurement.engine.ranking.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO representing an offer evaluated and ranked across multiple weighted dimensions.
 */
public class RankedOfferDto {

    private final int rank;
    private final UUID offerId;
    private final UUID productId;
    private final String productName;
    private final String vendorName;
    private final String category;

    // Financials
    private final BigDecimal price;
    private final BigDecimal unitPrice;
    private final BigDecimal tco;
    private final BigDecimal unitTco;

    // Dimension Scores (0 to 100)
    private final BigDecimal totalScore;
    private final BigDecimal tcoScore;
    private final BigDecimal priceScore;
    private final BigDecimal reliabilityScore;
    private final BigDecimal deliveryScore;
    private final BigDecimal warrantyScore;
    private final BigDecimal sellerRatingScore;
    private final BigDecimal returnPolicyScore;
    private final BigDecimal softPreferenceScore;

    // Contextual attributes
    private final int deliveryDays;
    private final int warrantyYears;
    private final BigDecimal reliability;
    private final BigDecimal sellerRating;
    private final String returnPolicy;

    // Flags
    private final boolean eligible;
    private final boolean budgetExceeded;
    private final boolean isExceptionOffer;

    public RankedOfferDto(int rank,
                          UUID offerId,
                          UUID productId,
                          String productName,
                          String vendorName,
                          String category,
                          BigDecimal price,
                          BigDecimal unitPrice,
                          BigDecimal tco,
                          BigDecimal unitTco,
                          BigDecimal totalScore,
                          BigDecimal tcoScore,
                          BigDecimal priceScore,
                          BigDecimal reliabilityScore,
                          BigDecimal deliveryScore,
                          BigDecimal warrantyScore,
                          BigDecimal sellerRatingScore,
                          BigDecimal returnPolicyScore,
                          BigDecimal softPreferenceScore,
                          int deliveryDays,
                          int warrantyYears,
                          BigDecimal reliability,
                          BigDecimal sellerRating,
                          String returnPolicy,
                          boolean eligible,
                          boolean budgetExceeded,
                          boolean isExceptionOffer) {
        this.rank = rank;
        this.offerId = offerId;
        this.productId = productId;
        this.productName = productName;
        this.vendorName = vendorName;
        this.category = category;
        this.price = price;
        this.unitPrice = unitPrice;
        this.tco = tco;
        this.unitTco = unitTco;
        this.totalScore = totalScore;
        this.tcoScore = tcoScore;
        this.priceScore = priceScore;
        this.reliabilityScore = reliabilityScore;
        this.deliveryScore = deliveryScore;
        this.warrantyScore = warrantyScore;
        this.sellerRatingScore = sellerRatingScore;
        this.returnPolicyScore = returnPolicyScore;
        this.softPreferenceScore = softPreferenceScore;
        this.deliveryDays = deliveryDays;
        this.warrantyYears = warrantyYears;
        this.reliability = reliability;
        this.sellerRating = sellerRating;
        this.returnPolicy = returnPolicy;
        this.eligible = eligible;
        this.budgetExceeded = budgetExceeded;
        this.isExceptionOffer = isExceptionOffer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int rank;
        private UUID offerId;
        private UUID productId;
        private String productName;
        private String vendorName;
        private String category;
        private BigDecimal price = BigDecimal.ZERO;
        private BigDecimal unitPrice = BigDecimal.ZERO;
        private BigDecimal tco = BigDecimal.ZERO;
        private BigDecimal unitTco = BigDecimal.ZERO;
        private BigDecimal totalScore = BigDecimal.ZERO;
        private BigDecimal tcoScore = BigDecimal.ZERO;
        private BigDecimal priceScore = BigDecimal.ZERO;
        private BigDecimal reliabilityScore = BigDecimal.ZERO;
        private BigDecimal deliveryScore = BigDecimal.ZERO;
        private BigDecimal warrantyScore = BigDecimal.ZERO;
        private BigDecimal sellerRatingScore = BigDecimal.ZERO;
        private BigDecimal returnPolicyScore = BigDecimal.ZERO;
        private BigDecimal softPreferenceScore = BigDecimal.ZERO;
        private int deliveryDays;
        private int warrantyYears;
        private BigDecimal reliability = BigDecimal.ZERO;
        private BigDecimal sellerRating = BigDecimal.ZERO;
        private String returnPolicy;
        private boolean eligible = true;
        private boolean budgetExceeded = false;
        private boolean isExceptionOffer = false;

        public Builder rank(int rank) { this.rank = rank; return this; }
        public Builder offerId(UUID offerId) { this.offerId = offerId; return this; }
        public Builder productId(UUID productId) { this.productId = productId; return this; }
        public Builder productName(String productName) { this.productName = productName; return this; }
        public Builder vendorName(String vendorName) { this.vendorName = vendorName; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public Builder tco(BigDecimal tco) { this.tco = tco; return this; }
        public Builder unitTco(BigDecimal unitTco) { this.unitTco = unitTco; return this; }
        public Builder totalScore(BigDecimal totalScore) { this.totalScore = totalScore; return this; }
        public Builder tcoScore(BigDecimal tcoScore) { this.tcoScore = tcoScore; return this; }
        public Builder priceScore(BigDecimal priceScore) { this.priceScore = priceScore; return this; }
        public Builder reliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; return this; }
        public Builder deliveryScore(BigDecimal deliveryScore) { this.deliveryScore = deliveryScore; return this; }
        public Builder warrantyScore(BigDecimal warrantyScore) { this.warrantyScore = warrantyScore; return this; }
        public Builder sellerRatingScore(BigDecimal sellerRatingScore) { this.sellerRatingScore = sellerRatingScore; return this; }
        public Builder returnPolicyScore(BigDecimal returnPolicyScore) { this.returnPolicyScore = returnPolicyScore; return this; }
        public Builder softPreferenceScore(BigDecimal softPreferenceScore) { this.softPreferenceScore = softPreferenceScore; return this; }
        public Builder deliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; return this; }
        public Builder warrantyYears(int warrantyYears) { this.warrantyYears = warrantyYears; return this; }
        public Builder reliability(BigDecimal reliability) { this.reliability = reliability; return this; }
        public Builder sellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; return this; }
        public Builder returnPolicy(String returnPolicy) { this.returnPolicy = returnPolicy; return this; }
        public Builder eligible(boolean eligible) { this.eligible = eligible; return this; }
        public Builder budgetExceeded(boolean budgetExceeded) { this.budgetExceeded = budgetExceeded; return this; }
        public Builder isExceptionOffer(boolean isExceptionOffer) { this.isExceptionOffer = isExceptionOffer; return this; }

        public RankedOfferDto build() {
            return new RankedOfferDto(rank, offerId, productId, productName, vendorName, category, price, unitPrice,
                    tco, unitTco, totalScore, tcoScore, priceScore, reliabilityScore, deliveryScore, warrantyScore,
                    sellerRatingScore, returnPolicyScore, softPreferenceScore, deliveryDays, warrantyYears,
                    reliability, sellerRating, returnPolicy, eligible, budgetExceeded, isExceptionOffer);
        }
    }

    public int getRank() { return rank; }
    public UUID getOfferId() { return offerId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getVendorName() { return vendorName; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTco() { return tco; }
    public BigDecimal getUnitTco() { return unitTco; }
    public BigDecimal getTotalScore() { return totalScore; }
    public BigDecimal getTcoScore() { return tcoScore; }
    public BigDecimal getPriceScore() { return priceScore; }
    public BigDecimal getReliabilityScore() { return reliabilityScore; }
    public BigDecimal getDeliveryScore() { return deliveryScore; }
    public BigDecimal getWarrantyScore() { return warrantyScore; }
    public BigDecimal getSellerRatingScore() { return sellerRatingScore; }
    public BigDecimal getReturnPolicyScore() { return returnPolicyScore; }
    public BigDecimal getSoftPreferenceScore() { return softPreferenceScore; }
    public int getDeliveryDays() { return deliveryDays; }
    public int getWarrantyYears() { return warrantyYears; }
    public BigDecimal getReliability() { return reliability; }
    public BigDecimal getSellerRating() { return sellerRating; }
    public String getReturnPolicy() { return returnPolicy; }
    public boolean isEligible() { return eligible; }
    public boolean isBudgetExceeded() { return budgetExceeded; }
    public boolean isExceptionOffer() { return isExceptionOffer; }
}
