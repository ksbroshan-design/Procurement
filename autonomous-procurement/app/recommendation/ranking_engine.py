from __future__ import annotations

from typing import Dict, List, Optional, Tuple
import re

from app.models.procurement import ProcurementRequest
from app.recommendation.models import RankedProduct, ProductEvaluation, TCOResult
from app.discovery.models import VendorProduct


def _key_for_product(p: VendorProduct) -> str:
    return f"{p.vendor_name}::{p.product_name}"


def _extract_numeric_from_string(s: Optional[str]) -> Optional[float]:
    if s is None:
        return None
    m = re.search(r"(\d+(?:[\.,]\d+)?)", s)
    if not m:
        return None
    try:
        return float(m.group(1).replace(",", ""))
    except Exception:
        return None


class RankingEngine:
    """Configurable multi-criteria ranking engine.

    We produce RankedProduct list but do not perform final approvals.
    Mandatory constraints must be satisfied for a product to be eligible.
    Products failing only the budget constraint are marked as budget exception
    candidates (kept in the output but flagged as ineligible).
    """

    DEFAULT_WEIGHTS = {
        "price": 0.20,
        "spec_match": 0.20,
        "delivery": 0.10,
        "reliability": 0.15,
        "return_policy": 0.10,
        "warranty": 0.10,
        "tco": 0.15,
        "preference": 0.10,  # extra small slot; total may exceed 1.0 but will be normalized by caller
    }

    def __init__(self, weights: Optional[Dict[str, float]] = None) -> None:
        self.weights = dict(self.DEFAULT_WEIGHTS)
        if weights:
            self.weights.update(weights)

    def rank(
        self,
        request: ProcurementRequest,
        evaluations: List[ProductEvaluation],
        tcos: Optional[Dict[str, TCOResult]] = None,
    ) -> List[RankedProduct]:
        tcos = tcos or {}

        # Prepare candidates with keys
        candidates = []
        for ev in evaluations:
            key = _key_for_product(ev.product)
            tco = tcos.get(key)
            candidates.append((ev, tco))

        # First, determine eligibility and budget-exception flags
        processed = []  # tuples (ev,tco,eligible,is_budget_exception)
        for ev, tco in candidates:
            # eligible only if all_constraints_satisfied and product.available
            eligible = ev.all_constraints_satisfied and bool(ev.product.available)
            # budget exception: if product failed budget but passed other mandatory constraints
            budget_exception = False
            # detect budget failure in constraint_evaluations
            budget_fail = any(
                (ce.constraint.attribute == "price" or ce.constraint.attribute == "budget" or ce.constraint.attribute == "PRICE")
                and not ce.passed
                for ce in ev.constraint_evaluations
            )
            other_failures = any(ce for ce in ev.constraint_evaluations if not ce.passed and not ((ce.constraint.attribute == "price") or (ce.constraint.attribute == "budget")))
            if budget_fail and not other_failures:
                budget_exception = True
                eligible = False
            # ensure ineligible if any mandatory constraint failed
            if any(ce for ce in ev.constraint_evaluations if not ce.passed and ce.constraint.mandatory and ce.constraint.attribute not in ("price", "budget")):
                eligible = False
            processed.append((ev, tco, eligible, budget_exception))

        # For scoring, consider only those with eligible True
        eligibles = [p for p in processed if p[2]]

        # Prepare metric lists
        prices = [p[0].product.price for p in eligibles] if eligibles else []
        deliveries = [p[0].product.delivery_days for p in eligibles if p[0].product.delivery_days is not None]
        warranties = [p[0].product.warranty_years for p in eligibles if p[0].product.warranty_years is not None]
        reliabilities = [p[0].product.reliability_score for p in eligibles if p[0].product.reliability_score is not None]
        tco_values = [p[1].estimated_total_cost for p in eligibles if p[1] and p[1].estimated_total_cost is not None]

        # Helper normalizers
        def _norm_low_better(values: List[float], val: Optional[float]) -> float:
            if val is None:
                return 0.5
            if not values:
                return 0.5
            mn = min(values)
            mx = max(values)
            if mx == mn:
                return 1.0
            return 1.0 - ((val - mn) / (mx - mn))

        def _norm_high_better(values: List[float], val: Optional[float]) -> float:
            if val is None:
                return 0.5
            if not values:
                return 0.5
            mn = min(values)
            mx = max(values)
            if mx == mn:
                return 1.0
            return (val - mn) / (mx - mn)

        ranked: List[Tuple[RankedProduct, float]] = []

        # Score each processed candidate
        for ev, tco, eligible, budget_exception in processed:
            prod = ev.product
            score_breakdown: Dict[str, float] = {}

            if not eligible:
                # keep rank score but mark ineligible; score 0 for eligible metrics
                base_score = 0.0
                explanation = "Ineligible due to failed mandatory constraints" if not budget_exception else "Ineligible due to budget (budget exception candidate)"
                rp = RankedProduct(product=prod, eligibility=False, tco=tco, ranking_score=0.0, rank=None, scoring_breakdown={"reason": 0.0}, explanation=explanation)
                ranked.append((rp, 0.0))
                continue

            # price score: lower price better
            price_score = _norm_low_better(prices, prod.price)
            score_breakdown["price"] = price_score

            # spec match: proportion of non-budget/delivery constraints passed
            spec_constraints = [ce for ce in ev.constraint_evaluations if ce.constraint.attribute not in ("price", "budget", "delivery_days", "delivery")]
            if spec_constraints:
                passed = sum(1 for ce in spec_constraints if ce.passed)
                spec_score = passed / len(spec_constraints)
            else:
                spec_score = 0.5
            score_breakdown["spec_match"] = spec_score

            # delivery: lower delivery_days better
            delivery_score = _norm_low_better(deliveries, prod.delivery_days)
            score_breakdown["delivery"] = delivery_score

            # reliability
            reliability_score = _norm_high_better(reliabilities, prod.reliability_score)
            score_breakdown["reliability"] = reliability_score

            # return policy: try to derive days
            rp_days = _extract_numeric_from_string(prod.return_policy)
            # consider across eligibles
            return_days_values = [ _extract_numeric_from_string(p[0].product.return_policy) for p in eligibles ]
            # filter None
            return_days_values = [x for x in return_days_values if x is not None]
            return_score = _norm_high_better(return_days_values, rp_days)
            score_breakdown["return_policy"] = return_score

            # warranty
            warranty_score = _norm_high_better(warranties, prod.warranty_years)
            score_breakdown["warranty"] = warranty_score

            # tco: lower better
            tco_val_list = tco_values
            tco_score = 0.5
            if tco and tco.estimated_total_cost is not None:
                tco_score = _norm_low_better(tco_val_list, tco.estimated_total_cost)
            score_breakdown["tco"] = tco_score

            # preferences: rudimentary - if requester provided preferences, check presence
            pref_score = 0.5
            if request.preferences:
                # score preference by fraction of preferences that can be satisfied in some simple way
                matched = 0
                total = 0
                for pref in request.preferences:
                    total += 1
                    # check if product has this attribute in specs
                    v = None
                    # try product attribute first
                    try:
                        v = getattr(prod, pref.attribute)
                    except Exception:
                        v = None
                    if v is None:
                        v = prod.specifications.get(pref.attribute) if prod.specifications else None
                    if v is not None:
                        matched += 1
                if total > 0:
                    pref_score = matched / total
            score_breakdown["preference"] = pref_score

            # Combine scores using weights
            total_weight = sum(self.weights.values())
            weighted = 0.0
            for k, w in self.weights.items():
                comp = score_breakdown.get(k, 0.5)
                weighted += comp * w
            # normalize to 0..1
            final_score = weighted / total_weight if total_weight > 0 else weighted

            rp = RankedProduct(
                product=prod,
                eligibility=True,
                tco=tco,
                ranking_score=final_score,
                rank=None,
                scoring_breakdown=score_breakdown,
                explanation="Eligible and scored",
            )
            ranked.append((rp, final_score))

        # Sort: eligible by score desc, then budget_exception candidates, then other ineligible
        eligible_ranked = [r for r in ranked if r[0].eligibility]
        eligible_ranked.sort(key=lambda x: x[1], reverse=True)

        budget_exceptions = [r for r in ranked if (not r[0].eligibility) and ("budget" in (r[0].explanation or "").lower())]
        others_ineligible = [r for r in ranked if (not r[0].eligibility) and ("budget" not in (r[0].explanation or "").lower())]

        final_list: List[RankedProduct] = []
        rank_counter = 1
        for rp, sc in eligible_ranked:
            rp.rank = rank_counter
            final_list.append(rp)
            rank_counter += 1
        # include budget exceptions next (no rank assigned)
        for rp, sc in budget_exceptions:
            final_list.append(rp)
        # then other ineligible
        for rp, sc in others_ineligible:
            final_list.append(rp)

        return final_list
