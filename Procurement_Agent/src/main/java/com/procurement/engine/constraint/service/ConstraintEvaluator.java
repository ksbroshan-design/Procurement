package com.procurement.engine.constraint.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.procurement.engine.common.util.JsonUtils;
import com.procurement.engine.constraint.entity.ConstraintOperator;
import com.procurement.engine.constraint.entity.ProcurementConstraint;
import com.procurement.engine.constraint.model.ConstraintStatus;
import com.procurement.engine.constraint.model.ProductConstraintEvaluation;
import com.procurement.engine.constraint.model.SingleConstraintResult;
import com.procurement.engine.constraint.resolver.ProductAttributeResolver;
import com.procurement.engine.product.entity.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Product-agnostic Constraint Evaluation Engine.
 * <p>
 * Evaluates arbitrary product attributes against hard constraints and soft preferences.
 * Distinguishes PASS, FAIL, and UNKNOWN deterministically.
 */
@Component
public class ConstraintEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConstraintEvaluator.class);
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[+-]?(\\d+(\\.\\d+)?|\\.\\d+)$");
    private static final Pattern LEADING_NUMBER_PATTERN = Pattern.compile("^([+-]?\\d+(\\.\\d+)?|\\.\\d+)");

    private final ProductAttributeResolver attributeResolver;

    public ConstraintEvaluator(ProductAttributeResolver attributeResolver) {
        this.attributeResolver = attributeResolver;
    }

    /**
     * Evaluates a list of constraints against a product.
     */
    public ProductConstraintEvaluation evaluate(Product product, List<ProcurementConstraint> constraints) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }

        if (constraints == null || constraints.isEmpty()) {
            return ProductConstraintEvaluation.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .category(product.getCategory())
                    .eligible(true)
                    .totalConstraints(0)
                    .passedCount(0)
                    .hardFailureCount(0)
                    .softFailureCount(0)
                    .totalPenalty(BigDecimal.ZERO)
                    .summary("No constraints specified. Product is eligible.")
                    .build();
        }

        List<SingleConstraintResult> results = new ArrayList<>();
        List<String> failedMandatoryAttrs = new ArrayList<>();
        int passedCount = 0;
        int hardFailureCount = 0;
        int softFailureCount = 0;
        BigDecimal totalPenalty = BigDecimal.ZERO;
        boolean eligible = true;

        for (ProcurementConstraint constraint : constraints) {
            SingleConstraintResult result = evaluateSingleConstraint(product, constraint);
            results.add(result);

            if (result.isPassed()) {
                passedCount++;
            } else {
                if (constraint.isMandatory()) {
                    hardFailureCount++;
                    eligible = false;
                    failedMandatoryAttrs.add(constraint.getAttribute());
                } else {
                    softFailureCount++;
                    totalPenalty = totalPenalty.add(result.getPenaltyScore());
                }
            }
        }

        String summary = String.format(
                "Evaluated %d constraints: %d passed, %d hard failures, %d soft penalties. Product is %s.",
                constraints.size(), passedCount, hardFailureCount, softFailureCount,
                eligible ? "ELIGIBLE" : "INELIGIBLE"
        );

        return ProductConstraintEvaluation.builder()
                .productId(product.getId())
                .productName(product.getName())
                .category(product.getCategory())
                .eligible(eligible)
                .totalConstraints(constraints.size())
                .passedCount(passedCount)
                .hardFailureCount(hardFailureCount)
                .softFailureCount(softFailureCount)
                .totalPenalty(totalPenalty)
                .failedMandatoryAttributes(failedMandatoryAttrs)
                .constraintResults(results)
                .summary(summary)
                .build();
    }

    /**
     * Evaluates an individual constraint against a product.
     */
    public SingleConstraintResult evaluateSingleConstraint(Product product, ProcurementConstraint constraint) {
        String attribute = constraint.getAttribute();
        ConstraintOperator operator = constraint.getOperator();
        String expectedValue = constraint.getValue();
        String unit = constraint.getUnit();
        boolean mandatory = constraint.isMandatory();
        BigDecimal weight = constraint.getWeight() != null ? constraint.getWeight() : BigDecimal.ONE;

        Optional<Object> actualOpt = attributeResolver.resolve(product, attribute);

        // 1. Missing Attribute Handling
        if (actualOpt.isEmpty()) {
            if (mandatory) {
                return SingleConstraintResult.builder()
                        .attribute(attribute)
                        .operator(operator)
                        .expectedValue(expectedValue)
                        .actualValue(null)
                        .unit(unit)
                        .mandatory(true)
                        .weight(weight)
                        .status(ConstraintStatus.FAIL)
                        .passed(false)
                        .reason("Mandatory attribute '" + attribute + "' is missing in product specifications")
                        .penaltyScore(weight)
                        .build();
            } else {
                return SingleConstraintResult.builder()
                        .attribute(attribute)
                        .operator(operator)
                        .expectedValue(expectedValue)
                        .actualValue(null)
                        .unit(unit)
                        .mandatory(false)
                        .weight(weight)
                        .status(ConstraintStatus.UNKNOWN)
                        .passed(false)
                        .reason("Optional attribute '" + attribute + "' is missing; treated as soft preference failure")
                        .penaltyScore(weight)
                        .build();
            }
        }

        Object actualValue = actualOpt.get();

        // 2. Perform Operator Evaluation
        try {
            return evaluateOperator(attribute, operator, expectedValue, actualValue, unit, mandatory, weight);
        } catch (Exception ex) {
            log.warn("Unexpected error during constraint evaluation for attribute {}: {}", attribute, ex.getMessage());
            ConstraintStatus status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
            return SingleConstraintResult.builder()
                    .attribute(attribute)
                    .operator(operator)
                    .expectedValue(expectedValue)
                    .actualValue(actualValue)
                    .unit(unit)
                    .mandatory(mandatory)
                    .weight(weight)
                    .status(status)
                    .passed(false)
                    .reason("Evaluation error on attribute '" + attribute + "': " + ex.getMessage())
                    .penaltyScore(weight)
                    .build();
        }
    }

    private SingleConstraintResult evaluateOperator(String attribute,
                                                    ConstraintOperator operator,
                                                    String expectedStr,
                                                    Object actualValue,
                                                    String unit,
                                                    boolean mandatory,
                                                    BigDecimal weight) {
        if (operator == null) {
            operator = ConstraintOperator.EQUALS;
        }

        BigDecimal actualNum = tryParseBigDecimal(actualValue);
        BigDecimal expectedNum = tryParseBigDecimal(expectedStr);

        Boolean actualBool = tryParseBoolean(actualValue);
        Boolean expectedBool = tryParseBoolean(expectedStr);

        boolean passed = false;
        ConstraintStatus status = ConstraintStatus.PASS;
        String reason;

        switch (operator) {
            case EQUALS: {
                if (actualNum != null && expectedNum != null) {
                    passed = actualNum.compareTo(expectedNum) == 0;
                } else if (actualBool != null && expectedBool != null) {
                    passed = actualBool.equals(expectedBool);
                } else if (actualValue instanceof Collection<?> actualColl) {
                    passed = checkCollectionEquals(actualColl, expectedStr);
                } else {
                    passed = actualValue.toString().trim().equalsIgnoreCase(expectedStr != null ? expectedStr.trim() : "");
                }
                status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                reason = passed
                        ? String.format("'%s' satisfies equality constraint (%s == %s)", attribute, actualValue, expectedStr)
                        : String.format("'%s' failed equality constraint: expected '%s', actual was '%s'", attribute, expectedStr, actualValue);
                break;
            }

            case NOT_EQUALS: {
                if (actualNum != null && expectedNum != null) {
                    passed = actualNum.compareTo(expectedNum) != 0;
                } else if (actualBool != null && expectedBool != null) {
                    passed = !actualBool.equals(expectedBool);
                } else {
                    passed = !actualValue.toString().trim().equalsIgnoreCase(expectedStr != null ? expectedStr.trim() : "");
                }
                status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                reason = passed
                        ? String.format("'%s' satisfies inequality constraint (%s != %s)", attribute, actualValue, expectedStr)
                        : String.format("'%s' failed inequality constraint: value '%s' matches excluded '%s'", attribute, actualValue, expectedStr);
                break;
            }

            case GREATER_THAN: {
                if (actualNum == null || expectedNum == null) {
                    status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
                    passed = false;
                    reason = String.format("Type mismatch for numeric comparison '>' on '%s': actual='%s', expected='%s'", attribute, actualValue, expectedStr);
                } else {
                    passed = actualNum.compareTo(expectedNum) > 0;
                    status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                    reason = passed
                            ? String.format("'%s' satisfies '>' constraint (%s > %s)", attribute, actualNum, expectedNum)
                            : String.format("'%s' failed '>' constraint: expected > %s, actual was %s", attribute, expectedNum, actualNum);
                }
                break;
            }

            case GREATER_THAN_OR_EQUAL: {
                if (actualNum == null || expectedNum == null) {
                    status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
                    passed = false;
                    reason = String.format("Type mismatch for numeric comparison '>=' on '%s': actual='%s', expected='%s'", attribute, actualValue, expectedStr);
                } else {
                    passed = actualNum.compareTo(expectedNum) >= 0;
                    status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                    reason = passed
                            ? String.format("'%s' satisfies '>=' constraint (%s >= %s)", attribute, actualNum, expectedNum)
                            : String.format("'%s' failed '>=' constraint: expected >= %s, actual was %s", attribute, expectedNum, actualNum);
                }
                break;
            }

            case LESS_THAN: {
                if (actualNum == null || expectedNum == null) {
                    status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
                    passed = false;
                    reason = String.format("Type mismatch for numeric comparison '<' on '%s': actual='%s', expected='%s'", attribute, actualValue, expectedStr);
                } else {
                    passed = actualNum.compareTo(expectedNum) < 0;
                    status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                    reason = passed
                            ? String.format("'%s' satisfies '<' constraint (%s < %s)", attribute, actualNum, expectedNum)
                            : String.format("'%s' failed '<' constraint: expected < %s, actual was %s", attribute, expectedNum, actualNum);
                }
                break;
            }

            case LESS_THAN_OR_EQUAL: {
                if (actualNum == null || expectedNum == null) {
                    status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
                    passed = false;
                    reason = String.format("Type mismatch for numeric comparison '<=' on '%s': actual='%s', expected='%s'", attribute, actualValue, expectedStr);
                } else {
                    passed = actualNum.compareTo(expectedNum) <= 0;
                    status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                    reason = passed
                            ? String.format("'%s' satisfies '<=' constraint (%s <= %s)", attribute, actualNum, expectedNum)
                            : String.format("'%s' failed '<=' constraint: expected <= %s, actual was %s", attribute, expectedNum, actualNum);
                }
                break;
            }

            case IN: {
                List<String> expectedItems = parseItems(expectedStr);
                if (expectedItems.isEmpty()) {
                    status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
                    passed = false;
                    reason = String.format("IN constraint on '%s' has empty expected list", attribute);
                } else {
                    passed = checkIn(actualValue, actualNum, expectedItems);
                    status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                    reason = passed
                            ? String.format("'%s' with value '%s' is present in %s", attribute, actualValue, expectedItems)
                            : String.format("'%s' with value '%s' is not present in expected %s", attribute, actualValue, expectedItems);
                }
                break;
            }

            case CONTAINS: {
                if (expectedStr == null || expectedStr.isBlank()) {
                    passed = true;
                    status = ConstraintStatus.PASS;
                    reason = String.format("CONTAINS constraint on '%s' passed with empty expected string", attribute);
                } else {
                    passed = checkContains(actualValue, expectedStr);
                    status = passed ? ConstraintStatus.PASS : ConstraintStatus.FAIL;
                    reason = passed
                            ? String.format("'%s' (%s) contains '%s'", attribute, actualValue, expectedStr)
                            : String.format("'%s' (%s) does not contain '%s'", attribute, actualValue, expectedStr);
                }
                break;
            }

            default:
                status = mandatory ? ConstraintStatus.FAIL : ConstraintStatus.UNKNOWN;
                passed = false;
                reason = "Unsupported constraint operator: " + operator;
        }

        BigDecimal penalty = passed ? BigDecimal.ZERO : weight;

        return SingleConstraintResult.builder()
                .attribute(attribute)
                .operator(operator)
                .expectedValue(expectedStr)
                .actualValue(actualValue)
                .unit(unit)
                .mandatory(mandatory)
                .weight(weight)
                .status(status)
                .passed(passed)
                .reason(reason)
                .penaltyScore(penalty)
                .build();
    }

    private boolean checkIn(Object actualValue, BigDecimal actualNum, List<String> expectedItems) {
        if (actualValue == null) {
            return false;
        }

        for (String item : expectedItems) {
            if (actualNum != null) {
                BigDecimal itemNum = tryParseBigDecimal(item);
                if (itemNum != null && actualNum.compareTo(itemNum) == 0) {
                    return true;
                }
            }
            if (actualValue.toString().trim().equalsIgnoreCase(item.trim())) {
                return true;
            }
        }

        if (actualValue instanceof Collection<?> coll) {
            for (Object elem : coll) {
                if (elem != null) {
                    for (String item : expectedItems) {
                        if (elem.toString().trim().equalsIgnoreCase(item.trim())) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean checkContains(Object actualValue, String expectedStr) {
        if (actualValue == null || expectedStr == null) {
            return false;
        }

        String search = expectedStr.trim().toLowerCase();

        if (actualValue instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (item != null && item.toString().trim().equalsIgnoreCase(expectedStr.trim())) {
                    return true;
                }
                if (item != null && item.toString().toLowerCase().contains(search)) {
                    return true;
                }
            }
            return false;
        }

        if (actualValue.getClass().isArray()) {
            Object[] arr = (Object[]) actualValue;
            for (Object item : arr) {
                if (item != null && item.toString().trim().equalsIgnoreCase(expectedStr.trim())) {
                    return true;
                }
                if (item != null && item.toString().toLowerCase().contains(search)) {
                    return true;
                }
            }
            return false;
        }

        return actualValue.toString().toLowerCase().contains(search);
    }

    private boolean checkCollectionEquals(Collection<?> coll, String expectedStr) {
        if (coll == null || expectedStr == null) {
            return coll == null && expectedStr == null;
        }
        List<String> items = parseItems(expectedStr);
        if (coll.size() != items.size()) {
            return false;
        }
        for (String item : items) {
            boolean found = false;
            for (Object actual : coll) {
                if (actual != null && actual.toString().trim().equalsIgnoreCase(item.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private List<String> parseItems(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();

        // Handle JSON array e.g. ["a", "b"]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                List<Object> parsed = JsonUtils.fromJson(trimmed, new TypeReference<List<Object>>() {});
                if (parsed != null) {
                    List<String> result = new ArrayList<>();
                    for (Object obj : parsed) {
                        if (obj != null) {
                            result.add(obj.toString().trim());
                        }
                    }
                    return result;
                }
            } catch (Exception ignored) {}
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        String[] parts = trimmed.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String clean = p.trim();
            if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
                clean = clean.substring(1, clean.length() - 1).trim();
            } else if (clean.startsWith("'") && clean.endsWith("'") && clean.length() >= 2) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
            if (!clean.isBlank()) {
                list.add(clean);
            }
        }
        return list;
    }

    private BigDecimal tryParseBigDecimal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal bd) {
            return bd;
        }
        if (obj instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        String str = obj.toString().trim();
        if (str.isEmpty()) {
            return null;
        }

        // Clean commas (e.g. "60,000" -> "60000")
        str = str.replace(",", "");

        if (NUMERIC_PATTERN.matcher(str).matches()) {
            try {
                return new BigDecimal(str);
            } catch (Exception ignored) {}
        }

        // If string starts with a number followed by units e.g. "55 inch", "16GB", "512 GB"
        Matcher matcher = LEADING_NUMBER_PATTERN.matcher(str);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1));
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Boolean tryParseBoolean(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Boolean b) {
            return b;
        }
        String str = obj.toString().trim().toLowerCase();
        switch (str) {
            case "true":
            case "yes":
            case "1":
            case "t":
            case "y":
                return Boolean.TRUE;
            case "false":
            case "no":
            case "0":
            case "f":
            case "n":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
}
