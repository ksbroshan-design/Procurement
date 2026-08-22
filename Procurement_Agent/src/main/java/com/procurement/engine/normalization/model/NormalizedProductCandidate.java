package com.procurement.engine.normalization.model;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Normalized canonical candidate product representation.
 */
public class NormalizedProductCandidate {

    private final String sourceName;
    private final UUID vendorId;
    private final String vendorName;
    private final String rawId;
    private final String name;
    private final String category;
    private final String brand;
    private final String model;
    private final BigDecimal price;
    private final String currency;
    private final boolean availability;
    private final int availableQuantity;
    private final int deliveryDays;
    private final int warrantyDuration;
    private final String warrantyType;
    private final BigDecimal sellerRating;
    private final BigDecimal reliabilityScore;
    private final String returnPolicy;
    private final Map<String, Object> specifications;

    public NormalizedProductCandidate(String sourceName,
                                      UUID vendorId,
                                      String vendorName,
                                      String rawId,
                                      String name,
                                      String category,
                                      String brand,
                                      String model,
                                      BigDecimal price,
                                      String currency,
                                      boolean availability,
                                      int availableQuantity,
                                      int deliveryDays,
                                      int warrantyDuration,
                                      String warrantyType,
                                      BigDecimal sellerRating,
                                      BigDecimal reliabilityScore,
                                      String returnPolicy,
                                      Map<String, Object> specifications) {
        this.sourceName = sourceName;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.rawId = rawId;
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.model = model;
        this.price = price;
        this.currency = currency != null ? currency : "INR";
        this.availability = availability;
        this.availableQuantity = availableQuantity;
        this.deliveryDays = deliveryDays;
        this.warrantyDuration = warrantyDuration;
        this.warrantyType = warrantyType != null ? warrantyType : "STANDARD";
        this.sellerRating = sellerRating;
        this.reliabilityScore = reliabilityScore;
        this.returnPolicy = returnPolicy;
        this.specifications = specifications != null ? Map.copyOf(specifications) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceName;
        private UUID vendorId;
        private String vendorName;
        private String rawId;
        private String name;
        private String category;
        private String brand;
        private String model;
        private BigDecimal price;
        private String currency = "INR";
        private boolean availability = true;
        private int availableQuantity = 0;
        private int deliveryDays = 0;
        private int warrantyDuration = 0;
        private String warrantyType = "STANDARD";
        private BigDecimal sellerRating = BigDecimal.ZERO;
        private BigDecimal reliabilityScore = BigDecimal.ZERO;
        private String returnPolicy;
        private Map<String, Object> specifications = new HashMap<>();

        public Builder sourceName(String sourceName) { this.sourceName = sourceName; return this; }
        public Builder vendorId(UUID vendorId) { this.vendorId = vendorId; return this; }
        public Builder vendorName(String vendorName) { this.vendorName = vendorName; return this; }
        public Builder rawId(String rawId) { this.rawId = rawId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder brand(String brand) { this.brand = brand; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder availability(boolean availability) { this.availability = availability; return this; }
        public Builder availableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; return this; }
        public Builder deliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; return this; }
        public Builder warrantyDuration(int warrantyDuration) { this.warrantyDuration = warrantyDuration; return this; }
        public Builder warrantyType(String warrantyType) { this.warrantyType = warrantyType; return this; }
        public Builder sellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; return this; }
        public Builder reliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; return this; }
        public Builder returnPolicy(String returnPolicy) { this.returnPolicy = returnPolicy; return this; }
        public Builder specifications(Map<String, Object> specifications) { this.specifications = specifications; return this; }

        public NormalizedProductCandidate build() {
            return new NormalizedProductCandidate(sourceName, vendorId, vendorName, rawId, name, category, brand, model,
                    price, currency, availability, availableQuantity, deliveryDays, warrantyDuration, warrantyType,
                    sellerRating, reliabilityScore, returnPolicy, specifications);
        }
    }

    public String getSourceName() { return sourceName; }
    public UUID getVendorId() { return vendorId; }
    public String getVendorName() { return vendorName; }
    public String getRawId() { return rawId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public boolean isAvailability() { return availability; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getDeliveryDays() { return deliveryDays; }
    public int getWarrantyDuration() { return warrantyDuration; }
    public String getWarrantyType() { return warrantyType; }
    public BigDecimal getSellerRating() { return sellerRating; }
    public BigDecimal getReliabilityScore() { return reliabilityScore; }
    public String getReturnPolicy() { return returnPolicy; }
    public Map<String, Object> getSpecifications() { return specifications; }
}
