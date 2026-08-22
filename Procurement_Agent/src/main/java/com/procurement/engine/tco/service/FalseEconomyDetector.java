package com.procurement.engine.tco.service;

import com.procurement.engine.tco.model.FalseEconomyResult;
import com.procurement.engine.tco.model.TcoBreakdownDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic False Economy Detector.
 * <p>
 * Identifies instances where an upfront cheaper product generates higher total cost of ownership.
 */
@Component
public class FalseEconomyDetector {

    /**
     * Analyzes all evaluated candidate TCO breakdowns and detects false economy scenarios.
     *
     * @param eligibleTcoList   TCO breakdowns of hard-constraint-compliant eligible offers
     * @param exceptionTcoList  TCO breakdowns of hard-constraint-violating exception offers
     * @return List of detected false economy instances
     */
    public List<FalseEconomyResult> detectFalseEconomies(List<TcoBreakdownDto> eligibleTcoList,
                                                         List<TcoBreakdownDto> exceptionTcoList) {
        List<FalseEconomyResult> results = new ArrayList<>();

        if (eligibleTcoList == null || eligibleTcoList.isEmpty()) {
            return results;
        }

        // 1. Check pairs within eligible offers
        for (int i = 0; i < eligibleTcoList.size(); i++) {
            TcoBreakdownDto offerA = eligibleTcoList.get(i);
            for (int j = 0; j < eligibleTcoList.size(); j++) {
                if (i == j) continue;
                TcoBreakdownDto offerB = eligibleTcoList.get(j);

                // A is cheaper upfront, but B has lower total TCO
                if (offerA.getTotalPurchaseCost().compareTo(offerB.getTotalPurchaseCost()) < 0
                        && offerA.getTotalTco().compareTo(offerB.getTotalTco()) > 0) {

                    BigDecimal upfrontDiff = offerB.getTotalPurchaseCost().subtract(offerA.getTotalPurchaseCost());
                    BigDecimal tcoDiff = offerA.getTotalTco().subtract(offerB.getTotalTco());

                    String explanation = String.format(
                            "Offer '%s' (₹%s upfront) is a FALSE ECONOMY compared to '%s' (₹%s upfront). " +
                                    "Although it saves ₹%s initially, it incurs ₹%s higher projected %d-year ownership cost " +
                                    "due to higher failure rate (%.1f%% vs %.1f%%) and warranty differences (%d yrs vs %d yrs).",
                            offerA.getProductName(), offerA.getTotalPurchaseCost(),
                            offerB.getProductName(), offerB.getTotalPurchaseCost(),
                            upfrontDiff, tcoDiff, offerA.getHorizonYears(),
                            offerA.getFailureRate().multiply(BigDecimal.valueOf(100)),
                            offerB.getFailureRate().multiply(BigDecimal.valueOf(100)),
                            offerA.getWarrantyYears(), offerB.getWarrantyYears()
                    );

                    results.add(new FalseEconomyResult(
                            true,
                            offerA.getOfferId(),
                            offerA.getProductName(),
                            offerA.getTotalPurchaseCost(),
                            offerA.getTotalTco(),
                            offerB.getOfferId(),
                            offerB.getProductName(),
                            offerB.getTotalPurchaseCost(),
                            offerB.getTotalTco(),
                            upfrontDiff,
                            tcoDiff,
                            false,
                            explanation
                    ));
                }
            }
        }

        // 2. Check eligible offers against over-budget exception offers
        if (exceptionTcoList != null) {
            for (TcoBreakdownDto eligible : eligibleTcoList) {
                for (TcoBreakdownDto exception : exceptionTcoList) {
                    // Exception is more expensive upfront, but has lower TCO than the eligible offer
                    if (eligible.getTotalPurchaseCost().compareTo(exception.getTotalPurchaseCost()) < 0
                            && eligible.getTotalTco().compareTo(exception.getTotalTco()) > 0) {

                        BigDecimal upfrontDiff = exception.getTotalPurchaseCost().subtract(eligible.getTotalPurchaseCost());
                        BigDecimal tcoDiff = eligible.getTotalTco().subtract(exception.getTotalTco());

                        String explanation = String.format(
                                "EXCEPTION OPPORTUNITY: Non-compliant offer '%s' (₹%s upfront) exceeds constraints by ₹%s upfront, " +
                                        "but offers ₹%s lower %d-year projected TCO than best compliant option '%s' (TCO: ₹%s vs ₹%s). " +
                                        "Human budget override recommended.",
                                exception.getProductName(), exception.getTotalPurchaseCost(),
                                upfrontDiff, tcoDiff, eligible.getHorizonYears(),
                                eligible.getProductName(), exception.getTotalTco(), eligible.getTotalTco()
                        );

                        results.add(new FalseEconomyResult(
                                true,
                                eligible.getOfferId(),
                                eligible.getProductName(),
                                eligible.getTotalPurchaseCost(),
                                eligible.getTotalTco(),
                                exception.getOfferId(),
                                exception.getProductName(),
                                exception.getTotalPurchaseCost(),
                                exception.getTotalTco(),
                                upfrontDiff,
                                tcoDiff,
                                true,
                                explanation
                        ));
                    }
                }
            }
        }

        return results;
    }
}
