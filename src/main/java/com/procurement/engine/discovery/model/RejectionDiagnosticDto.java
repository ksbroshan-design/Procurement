package com.procurement.engine.discovery.model;

import com.procurement.engine.constraint.entity.ConstraintOperator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Diagnostic information describing why a candidate product was rejected.
 */
public class RejectionDiagnosticDto {

    private final UUID productId;
    private final UUID offerId;
    private final String productName;
    private final String vendorName;
    private final String category;
    private final BigDecimal price;
    private final List<FailedConstraintDetail> failedConstraints;

    public RejectionDiagnosticDto(UUID productId,
                                  UUID offerId,
                                  String productName,
                                  String vendorName,
                                  String category,
                                  BigDecimal price,
                                  List<FailedConstraintDetail> failedConstraints) {
        this.productId = productId;
        this.offerId = offerId;
        this.productName = productName;
        this.vendorName = vendorName;
        this.category = category;
        this.price = price;
        this.failedConstraints = failedConstraints != null ? List.copyOf(failedConstraints) : Collections.emptyList();
    }

    public static class FailedConstraintDetail {
        private final String attribute;
        private final ConstraintOperator operator;
        private final String expectedValue;
        private final Object actualValue;
        private final String reason;

        public FailedConstraintDetail(String attribute, ConstraintOperator operator, String expectedValue, Object actualValue, String reason) {
            this.attribute = attribute;
            this.operator = operator;
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
            this.reason = reason;
        }

        public String getAttribute() { return attribute; }
        public ConstraintOperator getOperator() { return operator; }
        public String getExpectedValue() { return expectedValue; }
        public Object getActualValue() { return actualValue; }
        public String getReason() { return reason; }
    }

    public UUID getProductId() { return productId; }
    public UUID getOfferId() { return offerId; }
    public String getProductName() { return productName; }
    public String getVendorName() { return vendorName; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public List<FailedConstraintDetail> getFailedConstraints() { return failedConstraints; }
}
