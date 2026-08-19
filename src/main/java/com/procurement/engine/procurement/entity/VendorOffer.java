package com.procurement.engine.procurement.entity;

import com.procurement.engine.product.entity.Product;
import com.procurement.engine.vendor.entity.Vendor;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_offers")
public class VendorOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_id", nullable = false)
    private ProcurementRequest procurement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "original_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "negotiated_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal negotiatedPrice;

    @Column(name = "delivery_days", nullable = false)
    private int deliveryDays;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "warranty_years", nullable = false)
    private int warrantyYears;

    @Column(name = "tco", nullable = false, precision = 15, scale = 2)
    private BigDecimal tco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OfferStatus status = OfferStatus.EVALUATING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VendorOffer() {}

    public VendorOffer(UUID id, ProcurementRequest procurement, Vendor vendor, Product product, BigDecimal originalPrice, BigDecimal negotiatedPrice, int deliveryDays, int availableQuantity, int warrantyYears, BigDecimal tco, OfferStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.procurement = procurement;
        this.vendor = vendor;
        this.product = product;
        this.originalPrice = originalPrice;
        this.negotiatedPrice = negotiatedPrice;
        this.deliveryDays = deliveryDays;
        this.availableQuantity = availableQuantity;
        this.warrantyYears = warrantyYears;
        this.tco = tco;
        this.status = status != null ? status : OfferStatus.EVALUATING;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private ProcurementRequest procurement;
        private Vendor vendor;
        private Product product;
        private BigDecimal originalPrice;
        private BigDecimal negotiatedPrice;
        private int deliveryDays;
        private int availableQuantity;
        private int warrantyYears;
        private BigDecimal tco;
        private OfferStatus status = OfferStatus.EVALUATING;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder procurement(ProcurementRequest procurement) { this.procurement = procurement; return this; }
        public Builder vendor(Vendor vendor) { this.vendor = vendor; return this; }
        public Builder product(Product product) { this.product = product; return this; }
        public Builder originalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; return this; }
        public Builder negotiatedPrice(BigDecimal negotiatedPrice) { this.negotiatedPrice = negotiatedPrice; return this; }
        public Builder deliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; return this; }
        public Builder availableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; return this; }
        public Builder warrantyYears(int warrantyYears) { this.warrantyYears = warrantyYears; return this; }
        public Builder tco(BigDecimal tco) { this.tco = tco; return this; }
        public Builder status(OfferStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public VendorOffer build() {
            return new VendorOffer(id, procurement, vendor, product, originalPrice, negotiatedPrice, deliveryDays, availableQuantity, warrantyYears, tco, status, createdAt, updatedAt);
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ProcurementRequest getProcurement() { return procurement; }
    public void setProcurement(ProcurementRequest procurement) { this.procurement = procurement; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public BigDecimal getNegotiatedPrice() { return negotiatedPrice; }
    public void setNegotiatedPrice(BigDecimal negotiatedPrice) { this.negotiatedPrice = negotiatedPrice; }
    public int getDeliveryDays() { return deliveryDays; }
    public void setDeliveryDays(int deliveryDays) { this.deliveryDays = deliveryDays; }
    public int getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(int availableQuantity) { this.availableQuantity = availableQuantity; }
    public int getWarrantyYears() { return warrantyYears; }
    public void setWarrantyYears(int warrantyYears) { this.warrantyYears = warrantyYears; }
    public BigDecimal getTco() { return tco; }
    public void setTco(BigDecimal tco) { this.tco = tco; }
    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
