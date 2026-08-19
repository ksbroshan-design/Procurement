package com.procurement.engine.purchase.entity;

import com.procurement.engine.procurement.entity.ProcurementRequest;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.vendor.entity.Vendor;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

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

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PurchaseOrderStatus status = PurchaseOrderStatus.CONFIRMED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    public PurchaseOrder() {}

    public PurchaseOrder(UUID id, ProcurementRequest procurement, Vendor vendor, Product product, int quantity, BigDecimal unitPrice, BigDecimal totalAmount, PurchaseOrderStatus status, Instant createdAt, Instant confirmedAt) {
        this.id = id;
        this.procurement = procurement;
        this.vendor = vendor;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status != null ? status : PurchaseOrderStatus.CONFIRMED;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private ProcurementRequest procurement;
        private Vendor vendor;
        private Product product;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
        private PurchaseOrderStatus status = PurchaseOrderStatus.CONFIRMED;
        private Instant createdAt;
        private Instant confirmedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder procurement(ProcurementRequest procurement) { this.procurement = procurement; return this; }
        public Builder vendor(Vendor vendor) { this.vendor = vendor; return this; }
        public Builder product(Product product) { this.product = product; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public Builder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public Builder status(PurchaseOrderStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder confirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; return this; }

        public PurchaseOrder build() {
            return new PurchaseOrder(id, procurement, vendor, product, quantity, unitPrice, totalAmount, status, createdAt, confirmedAt);
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
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public PurchaseOrderStatus getStatus() { return status; }
    public void setStatus(PurchaseOrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
