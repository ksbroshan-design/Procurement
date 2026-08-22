package com.procurement.engine.procurement.entity;

import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.product.entity.Product;
import com.procurement.engine.statemachine.ProcurementState;
import com.procurement.engine.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "procurement_requests")
public class ProcurementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "raw_brief", length = 4000)
    private String rawBrief;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "authorization_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal authorizationLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProcurementState status = ProcurementState.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_product_id")
    private Product selectedProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_offer_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private VendorOffer selectedOffer;

    @Column(name = "revalidation_attempts", nullable = false)
    private int revalidationAttempts = 0;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "procurement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProcurementConstraint> constraints = new ArrayList<>();

    @OneToMany(mappedBy = "procurement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VendorOffer> offers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProcurementRequest() {}

    public ProcurementRequest(UUID id, User user, String rawBrief, String category, int quantity, BigDecimal authorizationLimit, ProcurementState status, Product selectedProduct, VendorOffer selectedOffer, int revalidationAttempts, Long version, List<ProcurementConstraint> constraints, List<VendorOffer> offers, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.rawBrief = rawBrief;
        this.category = category;
        this.quantity = quantity;
        this.authorizationLimit = authorizationLimit;
        this.status = status != null ? status : ProcurementState.SUBMITTED;
        this.selectedProduct = selectedProduct;
        this.selectedOffer = selectedOffer;
        this.revalidationAttempts = revalidationAttempts;
        this.version = version != null ? version : 0L;
        this.constraints = constraints != null ? constraints : new ArrayList<>();
        this.offers = offers != null ? offers : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private User user;
        private String rawBrief;
        private String category;
        private int quantity;
        private BigDecimal authorizationLimit;
        private ProcurementState status = ProcurementState.SUBMITTED;
        private Product selectedProduct;
        private VendorOffer selectedOffer;
        private int revalidationAttempts = 0;
        private Long version = 0L;
        private List<ProcurementConstraint> constraints = new ArrayList<>();
        private List<VendorOffer> offers = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder rawBrief(String rawBrief) { this.rawBrief = rawBrief; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder quantity(int quantity) { this.quantity = quantity; return this; }
        public Builder authorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; return this; }
        public Builder status(ProcurementState status) { this.status = status; return this; }
        public Builder selectedProduct(Product selectedProduct) { this.selectedProduct = selectedProduct; return this; }
        public Builder selectedOffer(VendorOffer selectedOffer) { this.selectedOffer = selectedOffer; return this; }
        public Builder revalidationAttempts(int revalidationAttempts) { this.revalidationAttempts = revalidationAttempts; return this; }
        public Builder version(Long version) { this.version = version; return this; }
        public Builder constraints(List<ProcurementConstraint> constraints) { this.constraints = constraints; return this; }
        public Builder offers(List<VendorOffer> offers) { this.offers = offers; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public ProcurementRequest build() {
            return new ProcurementRequest(id, user, rawBrief, category, quantity, authorizationLimit, status, selectedProduct, selectedOffer, revalidationAttempts, version, constraints, offers, createdAt, updatedAt);
        }
    }

    public void addConstraint(ProcurementConstraint constraint) {
        constraints.add(constraint);
        constraint.setProcurement(this);
    }

    public void removeConstraint(ProcurementConstraint constraint) {
        constraints.remove(constraint);
        constraint.setProcurement(null);
    }

    public void addOffer(VendorOffer offer) {
        offers.add(offer);
        offer.setProcurement(this);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getRawBrief() { return rawBrief; }
    public void setRawBrief(String rawBrief) { this.rawBrief = rawBrief; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public void setAuthorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; }
    public ProcurementState getStatus() { return status; }
    public void setStatus(ProcurementState status) { this.status = status; }
    public Product getSelectedProduct() { return selectedProduct; }
    public void setSelectedProduct(Product selectedProduct) { this.selectedProduct = selectedProduct; }
    public VendorOffer getSelectedOffer() { return selectedOffer; }
    public void setSelectedOffer(VendorOffer selectedOffer) { this.selectedOffer = selectedOffer; }
    public int getRevalidationAttempts() { return revalidationAttempts; }
    public void setRevalidationAttempts(int revalidationAttempts) { this.revalidationAttempts = revalidationAttempts; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<ProcurementConstraint> getConstraints() { return constraints; }
    public void setConstraints(List<ProcurementConstraint> constraints) { this.constraints = constraints; }
    public List<VendorOffer> getOffers() { return offers; }
    public void setOffers(List<VendorOffer> offers) { this.offers = offers; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
