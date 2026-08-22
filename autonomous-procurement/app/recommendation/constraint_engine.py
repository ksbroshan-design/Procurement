from __future__ import annotations

from typing import Any, Optional, Tuple

from app.models.procurement import ProcurementRequest
from app.models.constraint import Constraint
from app.discovery.models import VendorProduct
from app.recommendation.models import ProductEvaluation, ConstraintEvaluation


def _normalize_key(k: str) -> str:
    return k.strip().lower().replace("-", "_").replace(" ", "_")


def _find_attribute_value(product: VendorProduct, attribute: str) -> Tuple[Optional[Any], Optional[str]]:
    """Locate attribute value on VendorProduct.

    Returns (value, source) where source is 'field' or 'spec' or None.
    """
    key = _normalize_key(attribute)
    # check well-known direct fields
    direct_map = {
        "price": "price",
        "delivery": "delivery_days",
        "delivery_days": "delivery_days",
        "warranty": "warranty_years",
        "warranty_years": "warranty_years",
        "available": "available",
        "vendor": "vendor_name",
        "vendor_name": "vendor_name",
        "product_name": "product_name",
        "category": "category",
    }
    if key in direct_map:
        attr = direct_map[key]
        return getattr(product, attr, None), "field"

    # check specifications dict by normalized keys
    specs = product.specifications or {}
    for spec_k, spec_v in specs.items():
        if _normalize_key(spec_k) == key:
            return spec_v, "spec"
    # also allow matching if attribute looks like 'ram_gb' and spec key 'ram'
    for spec_k, spec_v in specs.items():
        if _normalize_key(spec_k).startswith(key) or key.startswith(_normalize_key(spec_k)):
            return spec_v, "spec"

    return None, None


def _coerce_number(val: Any) -> Optional[float]:
    if val is None:
        return None
    if isinstance(val, (int, float)):
        return float(val)
    if isinstance(val, str):
        txt = val.replace(",", "").strip()
        # extract first number
        import re

        m = re.search(r"([-+]?[0-9]*\.?[0-9]+)", txt)
        if m:
            try:
                return float(m.group(1))
            except Exception:
                return None
    return None


def _evaluate_operator(actual: Any, operator: str, expected: Any) -> Tuple[bool, str]:
    """Evaluate operator between actual and expected, returning (passed, explanation)."""
    op = operator.strip()
    if op == "==":
        op = "="
    # attempt numeric comparison first
    actual_num = _coerce_number(actual)
    expected_num = _coerce_number(expected)

    if op in ("=", "=="):  # equality
        if actual is None:
            return False, "attribute not present on product"
        if actual_num is not None and expected_num is not None:
            passed = actual_num == expected_num
            expl = f"{actual_num} == {expected_num}: {'PASS' if passed else 'FAIL'}"
            return passed, expl
        # fallback string compare
        passed = str(actual).strip().lower() == str(expected).strip().lower()
        expl = f"'{actual}' == '{expected}': {'PASS' if passed else 'FAIL'}"
        return passed, expl

    if op == "!=":
        if actual is None:
            return False, "attribute not present on product"
        if actual_num is not None and expected_num is not None:
            passed = actual_num != expected_num
            expl = f"{actual_num} != {expected_num}: {'PASS' if passed else 'FAIL'}"
            return passed, expl
        passed = str(actual).strip().lower() != str(expected).strip().lower()
        expl = f"'{actual}' != '{expected}': {'PASS' if passed else 'FAIL'}"
        return passed, expl

    # relational operators require numbers
    if op in (">", ">=", "<", "<="):
        if actual_num is None or expected_num is None:
            return False, "non-numeric values cannot be compared with relational operator"
        if op == ">":
            passed = actual_num > expected_num
        elif op == ">=":
            passed = actual_num >= expected_num
        elif op == "<":
            passed = actual_num < expected_num
        else:  # <=
            passed = actual_num <= expected_num
        expl = f"{actual_num} {op} {expected_num}: {'PASS' if passed else 'FAIL'}"
        return passed, expl

    # unsupported operator
    return False, f"unsupported operator '{operator}'"


class ConstraintEngine:
    """Deterministic engine to evaluate mandatory constraints against a product."""

    def evaluate(self, request: ProcurementRequest, product: VendorProduct) -> ProductEvaluation:
        evaluations: list[ConstraintEvaluation] = []

        # Evaluate explicit constraints from request
        for c in request.constraints:
            expr_val, src = _find_attribute_value(product, c.attribute)
            expected = c.value
            passed = False
            explanation = ""
            if expr_val is None:
                explanation = f"Attribute '{c.attribute}' not found on product"
                passed = False
            else:
                passed, explanation = _evaluate_operator(expr_val, c.operator, expected)
            ce = ConstraintEvaluation(
                constraint=c,
                passed=passed,
                actual_value=expr_val,
                expected_value=expected,
                explanation=explanation,
            )
            evaluations.append(ce)

        # Budget constraint (hard) if request.budget present
        budget_violation = False
        if request.budget is not None:
            # treat as price <= budget
            price_val, _ = _find_attribute_value(product, "price")
            if price_val is None:
                passed = False
                explanation = "price not available on product"
            else:
                passed, explanation = _evaluate_operator(price_val, "<=", request.budget)
            ce = ConstraintEvaluation(
                constraint=Constraint(attribute="price", operator="<=", value=request.budget, unit=None),
                passed=passed,
                actual_value=price_val,
                expected_value=request.budget,
                explanation=f"Budget check: {explanation}",
            )
            evaluations.append(ce)
            if not passed:
                budget_violation = True

        # Delivery constraint (hard) if request.max_delivery_days present
        delivery_violation = False
        if request.max_delivery_days is not None:
            del_val, _ = _find_attribute_value(product, "delivery_days")
            if del_val is None:
                passed = False
                explanation = "delivery_days not available on product"
            else:
                passed, explanation = _evaluate_operator(del_val, "<=", request.max_delivery_days)
            ce = ConstraintEvaluation(
                constraint=Constraint(attribute="delivery_days", operator="<=", value=request.max_delivery_days, unit="days"),
                passed=passed,
                actual_value=del_val,
                expected_value=request.max_delivery_days,
                explanation=f"Delivery check: {explanation}",
            )
            evaluations.append(ce)
            if not passed:
                delivery_violation = True

        all_constraints_satisfied = all(ev.passed for ev in evaluations if ev.constraint.mandatory)

        overall_eligibility = all_constraints_satisfied and bool(product.available)

        result = ProductEvaluation(
            product=product,
            constraint_evaluations=evaluations,
            all_constraints_satisfied=all_constraints_satisfied,
            budget_violation=budget_violation,
            delivery_violation=delivery_violation,
            overall_eligibility=overall_eligibility,
        )

        return result
