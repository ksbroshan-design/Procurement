package com.procurement.engine.discovery.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Normalized candidate offer DTO presented to clients and downstream engines.
 */
public class CandidateOfferDto {

    private final UUID offerId;
    private final UUID productId;
    private final String productName;
    private final String brand;
    private final String model;
    private final String category;
    private final UUID vendorId;
    private final String vendorName;
    private final String sourceName;
    private final BigDecimal price;
    private final String currency;
    private final int deliveryDays;
    private final int availableQuantity;
    private final int warrantyYears;
    private final String warrantyType;
    private final BigDecimal sellerRating;
    private final BigDecimal reliabilityScore;
    private final String returnPolicy;
    private final Map<String, Object> specifications;
    private final boolean eligible;
    private final BigDecimal softPreferencePenalty;
    private final String evaluationSummary;

    public CandidateOfferDto(UUID offerId,
                             UUID productId,
                             String productName,
                             String brand,
                             String model,
                             String category,
                             UUID vendorId,
                             String vendorName,
                             String sourceName,
                             BigDecimal price,
                             String currency,
                             int deliveryDays,
                             int availableQuantity,
                             int warrantyYears,
                             String warrantyType,
                             BigDecimal sellerRating,
                             BigDecimal reliabilityScore,
                             String returnPolicy,
                             Map<String, Object> specifications,
                             boolean eligible,
                             BigDecimal softPreferencePenalty,
                             String evaluationSummary) {
        this.offerId = offerId;
        this.productId = productId;
        this.productName = productName;
        this.brand = brand;
        this.model = model;
        this.category = category;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.sourceName = sourceName;
        this.price = price;
        this.currency = currency != null ? currency : "INR";
        this.deliveryDays = deliveryDays;
        this.availableQuantity = availableQuantity;
        this.warrantyYears = warrantyYears;
        this.warrantyType = warrantyType;
        this.sellerRating = sellerRating;
        this.reliabilityScore = reliabilityScore;
        this.returnPolicy = returnPolicy;
        this.specifications = specifications != null ? Map.copyOf(specifications) : Collections.emptyMap();
        this.eligible = eligible;
        this.softPreferencePenalty = softPreferencePenalty != null ? softPreferencePenalty : BigDecimal.ZERO;
        this.evaluationSummary = evaluationSummary;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID offerId;
        private UUID productId;
        private String productName;
        private String brand;
        private String model;
        private String category;
        private UUID vendorId;
        private String vendorName;
        private String sourceName;
        private BigDecimal price;
        private String currency = "INR";
        private int deliveryDays;
        private int availableQuantity;
        private int warrantyYears;
        private String warrantyType;
        private BigDecimal sellerRating;
        private BigDecimal reliabilityScore;
        private String returnPolicy;
        private Map<String, Object> specifications = new HashMap<>();
        private boolean eligible;
        private BigDecimal softPreferencePenalty = BigDecimal.ZERO;
        private String evaluationSummary;

        public Builder offerId(UUID offerId) { this.offerId = offerId; return this; }
        public Builder productId(UUID productId) { this.productId = productId; return this; }
        public Builder productName(String productName) { this.productName = productName; return this; }
        public Builder brand(String brand) { this.brand = brand; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder vendorId(UUID vendorId) { this.vendorId = vendorId; return this; }
        public Builder vendorName(String vendorName) { this.vendorName = vendorName; return this; }
        public Builder sourceName(String sourceName) { this.sourceName = sourceName; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder deliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; return this; }
        public Builder availableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; return this; }
        public Builder warrantyYears(int warrantyYears) { this.warrantyYears = warrantyYears; return this; }
        public Builder warrantyType(String warrantyType) { this.warrantyType = warrantyType; return this; }
        public Builder sellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; return this; }
        public Builder reliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; return this; }
        public Builder returnPolicy(String returnPolicy) { this.returnPolicy = returnPolicy; return this; }
        public Builder specifications(Map<String, Object> specifications) { this.specifications = specifications; return this; }
        public Builder eligible(boolean eligible) { this.eligible = eligible; return this; }
        public Builder softPreferencePenalty(BigDecimal softPreferencePenalty) { this.softPreferencePenalty = softPreferencePenalty; return this; }
        public Builder evaluationSummary(String evaluationSummary) { this.evaluationSummary = evaluationSummary; return this; }

        public CandidateOfferDto build() {
            return new CandidateOfferDto(offerId, productId, productName, brand, model, category, vendorId, vendorName,
                    sourceName, price, currency, deliveryDays, availableQuantity, warrantyYears, warrantyType,
                    sellerRating, reliabilityScore, returnPolicy, specifications, eligible, softPreferencePenalty, evaluationSummary);
        }
    }

    public UUID getOfferId() { return offerId; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public String getCategory() { return category; }
    public UUID getVendorId() { return vendorId; }
    public String getVendorName() { return vendorName; }
    public String getSourceName() { return sourceName; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public int getDeliveryDays() { return deliveryDays; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getWarrantyYears() { return warrantyYears; }
    public String getWarrantyType() { return warrantyType; }
    public BigDecimal getSellerRating() { return sellerRating; }
    public BigDecimal getReliabilityScore() { return reliabilityScore; }
    public String getReturnPolicy() { return returnPolicy; }
    public Map<String, Object> getSpecifications() { return specifications; }
    public boolean isEligible() { return eligible; }
    public BigDecimal getSoftPreferencePenalty() { return softPreferencePenalty; }
    public String getEvaluationSummary() { return evaluationSummary; }
}
