package com.procurement.engine.product.entity;

import com.procurement.engine.vendor.entity.Vendor;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(nullable = false)
    private boolean availability = true;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity = 0;

    @Column(name = "delivery_days", nullable = false)
    private int deliveryDays;

    @Column(name = "seller_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal sellerRating;

    @Column(name = "reliability_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal reliabilityScore;

    @Column(name = "warranty_duration", nullable = false)
    private int warrantyDuration;

    @Column(name = "warranty_type", nullable = false, length = 50)
    private String warrantyType;

    @Column(name = "return_window", nullable = false)
    private int returnWindow;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specifications", nullable = false)
    private Map<String, Object> specifications = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Product() {}

    public Product(UUID id, Vendor vendor, String name, String category, String brand, String model, BigDecimal price, String currency, boolean availability, int availableQuantity, int deliveryDays, BigDecimal sellerRating, BigDecimal reliabilityScore, int warrantyDuration, String warrantyType, int returnWindow, Map<String, Object> specifications, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.vendor = vendor;
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.model = model;
        this.price = price;
        this.currency = currency != null ? currency : "INR";
        this.availability = availability;
        this.availableQuantity = availableQuantity;
        this.deliveryDays = deliveryDays;
        this.sellerRating = sellerRating;
        this.reliabilityScore = reliabilityScore;
        this.warrantyDuration = warrantyDuration;
        this.warrantyType = warrantyType;
        this.returnWindow = returnWindow;
        this.specifications = specifications != null ? specifications : new HashMap<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private Vendor vendor;
        private String name;
        private String category;
        private String brand;
        private String model;
        private BigDecimal price;
        private String currency = "INR";
        private boolean availability = true;
        private int availableQuantity = 0;
        private int deliveryDays;
        private BigDecimal sellerRating;
        private BigDecimal reliabilityScore;
        private int warrantyDuration;
        private String warrantyType;
        private int returnWindow;
        private Map<String, Object> specifications = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder vendor(Vendor vendor) { this.vendor = vendor; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder brand(String brand) { this.brand = brand; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder price(BigDecimal price) { this.price = price; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public Builder availability(boolean availability) { this.availability = availability; return this; }
        public Builder availableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; return this; }
        public Builder deliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; return this; }
        public Builder sellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; return this; }
        public Builder reliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; return this; }
        public Builder warrantyDuration(int warrantyDuration) { this.warrantyDuration = warrantyDuration; return this; }
        public Builder warrantyType(String warrantyType) { this.warrantyType = warrantyType; return this; }
        public Builder returnWindow(int returnWindow) { this.returnWindow = returnWindow; return this; }
        public Builder specifications(Map<String, Object> specifications) { this.specifications = specifications; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Product build() {
            return new Product(id, vendor, name, category, brand, model, price, currency, availability, availableQuantity, deliveryDays, sellerRating, reliabilityScore, warrantyDuration, warrantyType, returnWindow, specifications, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isAvailability() { return availability; }
    public void setAvailability(boolean availability) { this.availability = availability; }
    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }
    public int getDeliveryDays() { return deliveryDays; }
    public void setDeliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; }
    public BigDecimal getSellerRating() { return sellerRating; }
    public void setSellerRating(BigDecimal sellerRating) { this.sellerRating = sellerRating; }
    public BigDecimal getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(BigDecimal reliabilityScore) { this.reliabilityScore = reliabilityScore; }
    public int getWarrantyDuration() { return warrantyDuration; }
    public void setWarrantyDuration(int warrantyDuration) { this.warrantyDuration = warrantyDuration; }
    public String getWarrantyType() { return warrantyType; }
    public void setWarrantyType(String warrantyType) { this.warrantyType = warrantyType; }
    public int getReturnWindow() { return returnWindow; }
    public void setReturnWindow(int returnWindow) { this.returnWindow = returnWindow; }
    public Map<String, Object> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, Object> specifications) { this.specifications = specifications; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
