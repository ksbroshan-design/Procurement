package com.procurement.engine.procurement.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload submitted by AI agent (Person B) or client to instantiate a new procurement request.
 */
public class CreateProcurementRequestDto {

    private String category;
    private int quantity = 1;
    private BigDecimal authorizationLimit = BigDecimal.ZERO;
    private List<ConstraintInputDto> constraints = new ArrayList<>();

    public CreateProcurementRequestDto() {}

    public CreateProcurementRequestDto(String category, int quantity, BigDecimal authorizationLimit, List<ConstraintInputDto> constraints) {
        this.category = category;
        this.quantity = quantity;
        this.authorizationLimit = authorizationLimit;
        this.constraints = constraints != null ? constraints : new ArrayList<>();
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAuthorizationLimit() { return authorizationLimit; }
    public void setAuthorizationLimit(BigDecimal authorizationLimit) { this.authorizationLimit = authorizationLimit; }
    public List<ConstraintInputDto> getConstraints() { return constraints; }
    public void setConstraints(List<ConstraintInputDto> constraints) { this.constraints = constraints; }
}
