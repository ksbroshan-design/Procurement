package com.procurement.engine.constraint.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of evaluating a batch of products against procurement constraints.
 */
public class BatchConstraintEvaluationResult {

    private final int totalProductsEvaluated;
    private final int eligibleProductsCount;
    private final int ineligibleProductsCount;
    private final List<ProductConstraintEvaluation> allEvaluations;
    private final List<ProductConstraintEvaluation> eligibleEvaluations;
    private final List<ProductConstraintEvaluation> ineligibleEvaluations;
    private final boolean hasMatches;

    public BatchConstraintEvaluationResult(List<ProductConstraintEvaluation> evaluations) {
        if (evaluations == null) {
            evaluations = Collections.emptyList();
        }
        this.allEvaluations = List.copyOf(evaluations);
        this.totalProductsEvaluated = allEvaluations.size();

        List<ProductConstraintEvaluation> eligibleList = new ArrayList<>();
        List<ProductConstraintEvaluation> ineligibleList = new ArrayList<>();

        for (ProductConstraintEvaluation eval : allEvaluations) {
            if (eval.isEligible()) {
                eligibleList.add(eval);
            } else {
                ineligibleList.add(eval);
            }
        }

        this.eligibleEvaluations = Collections.unmodifiableList(eligibleList);
        this.ineligibleEvaluations = Collections.unmodifiableList(ineligibleList);
        this.eligibleProductsCount = eligibleList.size();
        this.ineligibleProductsCount = ineligibleList.size();
        this.hasMatches = !eligibleList.isEmpty();
    }

    public int getTotalProductsEvaluated() { return totalProductsEvaluated; }
    public int getEligibleProductsCount() { return eligibleProductsCount; }
    public int getIneligibleProductsCount() { return ineligibleProductsCount; }
    public List<ProductConstraintEvaluation> getAllEvaluations() { return allEvaluations; }
    public List<ProductConstraintEvaluation> getEligibleEvaluations() { return eligibleEvaluations; }
    public List<ProductConstraintEvaluation> getIneligibleEvaluations() { return ineligibleEvaluations; }
    public boolean hasMatches() { return hasMatches; }
}
