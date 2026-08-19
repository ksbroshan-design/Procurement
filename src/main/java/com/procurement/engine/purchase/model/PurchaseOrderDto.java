package com.procurement.engine.purchase.model;

import com.procurement.engine.purchase.entity.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representation of a generated Purchase Order.
 */
public class PurchaseOrderDto {

    private final UUID id;
    private final UUID procurementId;
    private final UUID vendorId;
    private final String vendorName;
    private final UUID productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalAmount;
    private final PurchaseOrderStatus status;
    private final Instant createdAt;
    private final Instant confirmedAt;

    public PurchaseOrderDto(UUID id,
                            UUID procurementId,
                            UUID vendorId,
                            String vendorName,
                            UUID productId,
                            String productName,
                            int quantity,
                            BigDecimal unitPrice,
                            BigDecimal totalAmount,
                            PurchaseOrderStatus status,
                            Instant createdAt,
                            Instant confirmedAt) {
        this.id = id;
        this.procurementId = procurementId;
        this.vendorId = vendorId;
        this.vendorName = vendorName;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
    }

    public UUID getId() { return id; }
    public UUID getProcurementId() { return procurementId; }
    public UUID getVendorId() { return vendorId; }
    public String getVendorName() { return vendorName; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public PurchaseOrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
