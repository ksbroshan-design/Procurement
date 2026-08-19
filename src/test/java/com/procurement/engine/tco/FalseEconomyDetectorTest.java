package com.procurement.engine.tco;

import com.procurement.engine.tco.model.FalseEconomyResult;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import com.procurement.engine.tco.service.FalseEconomyDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FalseEconomyDetectorTest {

    private FalseEconomyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FalseEconomyDetector();
    }

    private TcoBreakdownDto createBreakdown(String name, BigDecimal purchaseCost, BigDecimal tco, int warrantyYears, BigDecimal failureRate) {
        return TcoBreakdownDto.builder()
                .offerId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .productName(name)
                .vendorName("Vendor " + name)
                .quantity(1)
                .horizonYears(3)
                .unitPurchaseCost(purchaseCost)
                .totalPurchaseCost(purchaseCost)
                .unitTco(tco)
                .totalTco(tco)
                .warrantyYears(warrantyYears)
                .failureRate(failureRate)
                .build();
    }

    @Test
    @DisplayName("Detects FALSE ECONOMY when cheaper upfront product has higher projected TCO")
    void testDirectFalseEconomyDetection() {
        // Offer A: ₹42,000 upfront, ₹52,000 TCO (due to high failures & 1-yr warranty)
        TcoBreakdownDto offerA = createBreakdown("Budget TV", new BigDecimal("42000.00"), new BigDecimal("52000.00"), 1, new BigDecimal("0.15"));

        // Offer B: ₹46,000 upfront, ₹47,000 TCO (due to 3-yr onsite warranty & high reliability)
        TcoBreakdownDto offerB = createBreakdown("Premium TV", new BigDecimal("46000.00"), new BigDecimal("47000.00"), 3, new BigDecimal("0.02"));

        List<FalseEconomyResult> results = detector.detectFalseEconomies(List.of(offerA, offerB), Collections.emptyList());

        assertThat(results).hasSize(1);
        FalseEconomyResult result = results.get(0);
        assertThat(result.isDetected()).isTrue();
        assertThat(result.getCheaperUpfrontOfferId()).isEqualTo(offerA.getOfferId());
        assertThat(result.getLowerTcoOfferId()).isEqualTo(offerB.getOfferId());
        assertThat(result.getUpfrontDifference()).isEqualByComparingTo("4000.00");
        assertThat(result.getTcoDifference()).isEqualByComparingTo("5000.00");
        assertThat(result.isExceptionOpportunity()).isFalse();
        assertThat(result.getExplanation()).contains("Budget TV").contains("Premium TV").contains("FALSE ECONOMY");
    }

    @Test
    @DisplayName("Does not flag false economy when cheaper product is also lower in TCO")
    void testCheaperAndLowerTcoNoFalseEconomy() {
        TcoBreakdownDto offerA = createBreakdown("Good Budget Laptop", new BigDecimal("40000.00"), new BigDecimal("43000.00"), 2, new BigDecimal("0.03"));
        TcoBreakdownDto offerB = createBreakdown("Expensive Laptop", new BigDecimal("60000.00"), new BigDecimal("64000.00"), 3, new BigDecimal("0.02"));

        List<FalseEconomyResult> results = detector.detectFalseEconomies(List.of(offerA, offerB), Collections.emptyList());

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Detects EXCEPTION OPPORTUNITY when over-budget candidate provides superior TCO")
    void testExceptionOpportunityDetection() {
        // Eligible: ₹42,000 upfront, ₹52,000 TCO
        TcoBreakdownDto compliantOffer = createBreakdown("Compliant Chair", new BigDecimal("42000.00"), new BigDecimal("52000.00"), 1, new BigDecimal("0.12"));

        // Exception (Over-budget): ₹46,000 upfront, ₹47,000 TCO
        TcoBreakdownDto exceptionOffer = createBreakdown("Ergonomic Exception Chair", new BigDecimal("46000.00"), new BigDecimal("47000.00"), 5, new BigDecimal("0.01"));

        List<FalseEconomyResult> results = detector.detectFalseEconomies(List.of(compliantOffer), List.of(exceptionOffer));

        assertThat(results).hasSize(1);
        FalseEconomyResult result = results.get(0);
        assertThat(result.isDetected()).isTrue();
        assertThat(result.isExceptionOpportunity()).isTrue();
        assertThat(result.getExplanation()).contains("EXCEPTION OPPORTUNITY").contains("Human budget override recommended");
    }
}
