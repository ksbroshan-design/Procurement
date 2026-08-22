package com.procurement.engine.discovery.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Heterogeneous product candidate returned by a discovery source before normalization.
 */
public class RawProductCandidate {

    private final String sourceName;
    private final UUID vendorId;
    private final String vendorName;
    private final String rawId;
    private final String rawName;
    private final String rawBrand;
    private final String rawModel;
    private final String rawCategory;
    private final Object rawPrice;
    private final String rawCurrency;
    private final Object rawAvailability;
    private final Object rawAvailableQuantity;
    private final Object rawDeliveryDays;
    private final Object rawWarrantyDuration;
    private final String rawWarrantyType;
    private final Object rawSellerRating;
    private final Object rawReliabilityScore;
    private final String rawReturnPolicy;
    private final Map<String, Object> rawSpecifications;

    public RawProductCandidate(String sourceName,
                               UUID vendorId,
                               String vendorName,
                               String rawId,
                               String rawName,
                               String rawBrand,
                               String rawModel,
                               String rawCategory,
                               Object rawPrice,
                               String rawCurrency,
                               Object rawAvailability,
                               Object rawAvailableQuantity,
                               Object rawDeliveryDays,
                               Object rawWarrantyDuration,
                               String rawWarrantyType,
                               Object rawSellerRating,
                               Object rawReliabilityScore,
                               String rawReturnPolicy,
                               Map<String, Object> rawSpecifications) {
        this.sourceName = sourceName;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.rawId = rawId;
        this.rawName = rawName;
        this.rawBrand = rawBrand;
        this.rawModel = rawModel;
        this.rawCategory = rawCategory;
        this.rawPrice = rawPrice;
        this.rawCurrency = rawCurrency;
        this.rawAvailability = rawAvailability;
        this.rawAvailableQuantity = rawAvailableQuantity;
        this.rawDeliveryDays = rawDeliveryDays;
        this.rawWarrantyDuration = rawWarrantyDuration;
        this.rawWarrantyType = rawWarrantyType;
        this.rawSellerRating = rawSellerRating;
        this.rawReliabilityScore = rawReliabilityScore;
        this.rawReturnPolicy = rawReturnPolicy;
        this.rawSpecifications = rawSpecifications != null ? Map.copyOf(rawSpecifications) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceName;
        private UUID vendorId;
        private String vendorName;
        private String rawId;
        private String rawName;
        private String rawBrand;
        private String rawModel;
        private String rawCategory;
        private Object rawPrice;
        private String rawCurrency;
        private Object rawAvailability;
        private Object rawAvailableQuantity;
        private Object rawDeliveryDays;
        private Object rawWarrantyDuration;
        private String rawWarrantyType;
        private Object rawSellerRating;
        private Object rawReliabilityScore;
        private String rawReturnPolicy;
        private Map<String, Object> rawSpecifications = new HashMap<>();

        public Builder sourceName(String sourceName) { this.sourceName = sourceName; return this; }
        public Builder vendorId(UUID vendorId) { this.vendorId = vendorId; return this; }
        public Builder vendorName(String vendorName) { this.vendorName = vendorName; return this; }
        public Builder rawId(String rawId) { this.rawId = rawId; return this; }
        public Builder rawName(String rawName) { this.rawName = rawName; return this; }
        public Builder rawBrand(String rawBrand) { this.rawBrand = rawBrand; return this; }
        public Builder rawModel(String rawModel) { this.rawModel = rawModel; return this; }
        public Builder rawCategory(String rawCategory) { this.rawCategory = rawCategory; return this; }
        public Builder rawPrice(Object rawPrice) { this.rawPrice = rawPrice; return this; }
        public Builder rawCurrency(String rawCurrency) { this.rawCurrency = rawCurrency; return this; }
        public Builder rawAvailability(Object rawAvailability) { this.rawAvailability = rawAvailability; return this; }
        public Builder rawAvailableQuantity(Object rawAvailableQuantity) { this.rawAvailableQuantity = rawAvailableQuantity; return this; }
        public Builder rawDeliveryDays(Object rawDeliveryDays) { this.rawDeliveryDays = rawDeliveryDays; return this; }
        public Builder rawWarrantyDuration(Object rawWarrantyDuration) { this.rawWarrantyDuration = rawWarrantyDuration; return this; }
        public Builder rawWarrantyType(String rawWarrantyType) { this.rawWarrantyType = rawWarrantyType; return this; }
        public Builder rawSellerRating(Object rawSellerRating) { this.rawSellerRating = rawSellerRating; return this; }
        public Builder rawReliabilityScore(Object rawReliabilityScore) { this.rawReliabilityScore = rawReliabilityScore; return this; }
        public Builder rawReturnPolicy(String rawReturnPolicy) { this.rawReturnPolicy = rawReturnPolicy; return this; }
        public Builder rawSpecifications(Map<String, Object> rawSpecifications) { this.rawSpecifications = rawSpecifications; return this; }

        public RawProductCandidate build() {
            return new RawProductCandidate(sourceName, vendorId, vendorName, rawId, rawName, rawBrand, rawModel, rawCategory,
                    rawPrice, rawCurrency, rawAvailability, rawAvailableQuantity, rawDeliveryDays, rawWarrantyDuration,
                    rawWarrantyType, rawSellerRating, rawReliabilityScore, rawReturnPolicy, rawSpecifications);
        }
    }

    public String getSourceName() { return sourceName; }
    public UUID getVendorId() { return vendorId; }
    public String getVendorName() { return vendorName; }
    public String getRawId() { return rawId; }
    public String getRawName() { return rawName; }
    public String getRawBrand() { return rawBrand; }
    public String getRawModel() { return rawModel; }
    public String getRawCategory() { return rawCategory; }
    public Object getRawPrice() { return rawPrice; }
    public String getRawCurrency() { return rawCurrency; }
    public Object getRawAvailability() { return rawAvailability; }
    public Object getRawAvailableQuantity() { return rawAvailableQuantity; }
    public Object getRawDeliveryDays() { return rawDeliveryDays; }
    public Object getRawWarrantyDuration() { return rawWarrantyDuration; }
    public String getRawWarrantyType() { return rawWarrantyType; }
    public Object getRawSellerRating() { return rawSellerRating; }
    public Object getRawReliabilityScore() { return rawReliabilityScore; }
    public String getRawReturnPolicy() { return rawReturnPolicy; }
    public Map<String, Object> getRawSpecifications() { return rawSpecifications; }
}
