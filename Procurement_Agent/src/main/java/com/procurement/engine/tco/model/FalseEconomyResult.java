package com.procurement.engine.tco.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Diagnostic result representing an identified false economy situation or exception opportunity.
 */
public class FalseEconomyResult {

    private final boolean detected;
    private final UUID cheaperUpfrontOfferId;
    private final String cheaperUpfrontProductName;
    private final BigDecimal cheaperUpfrontPrice;
    private final BigDecimal cheaperUpfrontTco;

    private final UUID lowerTcoOfferId;
    private final String lowerTcoProductName;
    private final BigDecimal lowerTcoPrice;
    private final BigDecimal lowerTcoTco;

    private final BigDecimal upfrontDifference;
    private final BigDecimal tcoDifference;
    private final boolean exceptionOpportunity;
    private final String explanation;

    public FalseEconomyResult(boolean detected,
                              UUID cheaperUpfrontOfferId,
                              String cheaperUpfrontProductName,
                              BigDecimal cheaperUpfrontPrice,
                              BigDecimal cheaperUpfrontTco,
                              UUID lowerTcoOfferId,
                              String lowerTcoProductName,
                              BigDecimal lowerTcoPrice,
                              BigDecimal lowerTcoTco,
                              BigDecimal upfrontDifference,
                              BigDecimal tcoDifference,
                              boolean exceptionOpportunity,
                              String explanation) {
        this.detected = detected;
        this.cheaperUpfrontOfferId = cheaperUpfrontOfferId;
        this.cheaperUpfrontProductName = cheaperUpfrontProductName;
        this.cheaperUpfrontPrice = cheaperUpfrontPrice;
        this.cheaperUpfrontTco = cheaperUpfrontTco;
        this.lowerTcoOfferId = lowerTcoOfferId;
        this.lowerTcoProductName = lowerTcoProductName;
        this.lowerTcoPrice = lowerTcoPrice;
        this.lowerTcoTco = lowerTcoTco;
        this.upfrontDifference = upfrontDifference;
        this.tcoDifference = tcoDifference;
        this.exceptionOpportunity = exceptionOpportunity;
        this.explanation = explanation;
    }

    public static FalseEconomyResult none() {
        return new FalseEconomyResult(false, null, null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, false, "No false economy detected.");
    }

    public boolean isDetected() { return detected; }
    public UUID getCheaperUpfrontOfferId() { return cheaperUpfrontOfferId; }
    public String getCheaperUpfrontProductName() { return cheaperUpfrontProductName; }
    public BigDecimal getCheaperUpfrontPrice() { return cheaperUpfrontPrice; }
    public BigDecimal getCheaperUpfrontTco() { return cheaperUpfrontTco; }
    public UUID getLowerTcoOfferId() { return lowerTcoOfferId; }
    public String getLowerTcoProductName() { return lowerTcoProductName; }
    public BigDecimal getLowerTcoPrice() { return lowerTcoPrice; }
    public BigDecimal getLowerTcoTco() { return lowerTcoTco; }
    public BigDecimal getUpfrontDifference() { return upfrontDifference; }
    public BigDecimal getTcoDifference() { return tcoDifference; }
    public boolean isExceptionOpportunity() { return exceptionOpportunity; }
    public String getExplanation() { return explanation; }
}
