package com.procurement.engine.purchase.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Result DTO for purchase execution operations.
 */
public class PurchaseExecutionResultDto {

    private final UUID purchaseOrderId;
    private final UUID procurementId;
    private final String vendorName;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalAmount;
    private final String status; // "CONFIRMED", "ALREADY_COMPLETED", "FAILED"
    private final Instant confirmedAt;
    private final String confirmationMessage;
    private final String nextState;

    public PurchaseExecutionResultDto(UUID purchaseOrderId,
                                      UUID procurementId,
                                      String vendorName,
                                      String productName,
                                      int quantity,
                                      BigDecimal unitPrice,
                                      BigDecimal totalAmount,
                                      String status,
                                      Instant confirmedAt,
                                      String confirmationMessage,
                                      String nextState) {
        this.purchaseOrderId = purchaseOrderId;
        this.procurementId = procurementId;
        this.vendorName = vendorName;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
        this.status = status;
        this.confirmedAt = confirmedAt;
        this.confirmationMessage = confirmationMessage;
        this.nextState = nextState;
    }

    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getProcurementId() { return procurementId; }
    public String getVendorName() { return vendorName; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public String getConfirmationMessage() { return confirmationMessage; }
    public String getNextState() { return nextState; }
}
