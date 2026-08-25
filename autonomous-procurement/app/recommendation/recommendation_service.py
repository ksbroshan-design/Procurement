from __future__ import annotations

from typing import Dict, List, Optional

from app.models.procurement import ProcurementRequest
from app.discovery.models import DiscoveryResult, VendorProduct
from app.recommendation.constraint_engine import ConstraintEngine
from app.recommendation.tco_engine import TCOEngine
from app.recommendation.ranking_engine import RankingEngine
from app.recommendation.models import RecommendationResult, RankedProduct, TCOResult


class RecommendationService:
    def __init__(
        self,
        constraint_engine: Optional[ConstraintEngine] = None,
        tco_engine: Optional[TCOEngine] = None,
        ranking_engine: Optional[RankingEngine] = None,
    ) -> None:
        self.constraint_engine = constraint_engine or ConstraintEngine()
        self.tco_engine = tco_engine or TCOEngine()
        self.ranking_engine = ranking_engine or RankingEngine()

    def recommend(self, request: ProcurementRequest, discovery: DiscoveryResult) -> RecommendationResult:
        # 1. Evaluate all discovered products
        evaluations = []
        for p in discovery.discovered_products:
            ev = self.constraint_engine.evaluate(request, p)
            evaluations.append(ev)

        # 2. Calculate TCOs where applicable and collect mapping by product key
        tco_map: Dict[str, TCOResult] = {}
        for ev in evaluations:
            key = f"{ev.product.vendor_name}::{ev.product.product_name}"
            try:
                tco = self.tco_engine.compute_tco(ev.product)
                tco_map[key] = tco
            except Exception:
                # don't fail the whole pipeline if TCO calculation errors; skip
                continue

        # 3. Rank eligible products
        ranked = self.ranking_engine.rank(request, evaluations, tcos=tco_map)

        # 4. Keep budget-exception candidates separately (already in ranked with eligibility False)
        budget_exceptions = [r for r in ranked if (not r.eligibility) and ("budget" in (r.explanation or "").lower())]

        # 5. Identify recommended product as top eligible ranked product
        recommended = next((r for r in ranked if r.eligibility), None)

        # 6. Construct deterministic explanation and human approval info
        human_approval_required = False
        budget_exception_info = None

        # budget handling: if recommended exists and exceeds budget, flag human approval
        if recommended and request.budget is not None:
            price = recommended.product.price
            if price > request.budget:
                human_approval_required = True
                over = price - request.budget
                budget_exception_info = {
                    "budget": request.budget,
                    "product_price": price,
                    "amount_over": over,
                }
                # include TCO comparison if available
                rec_key = f"{recommended.product.vendor_name}::{recommended.product.product_name}"
                rec_tco = tco_map.get(rec_key)
                # find best eligible tco among others
                other_tcos = {k: v for k, v in tco_map.items() if k != rec_key}
                # simple deterministic pick: pick min estimated_total_cost
                best_other = None
                best_cost = None
                for k, v in other_tcos.items():
                    if v.estimated_total_cost is not None:
                        if best_cost is None or v.estimated_total_cost < best_cost:
                            best_cost = v.estimated_total_cost
                            best_other = (k, v)
                tco_comparison = {}
                if rec_tco is not None:
                    tco_comparison[rec_key] = rec_tco
                if best_other is not None:
                    tco_comparison[best_other[0]] = best_other[1]
                # decide message
                reason = (
                    f"Recommended product '{recommended.product.product_name}' exceeds budget by {over:.2f}. "
                    "TCO comparison is provided for human review. Human approval required."
                )

                result = RecommendationResult(
                    request=request,
                    recommended=recommended,
                    alternatives=[r for r in ranked if r.product != recommended.product],
                    reason=reason,
                    human_approval_required=human_approval_required,
                    budget_exception=budget_exception_info,
                    tco_comparison=tco_comparison or None,
                    metadata={"budget_exceptions_count": len(budget_exceptions)},
                )
                return result

        # Normal path: return recommendation
        reason = None
        if recommended:
            reason = f"Top-ranked eligible product: {recommended.product.product_name} with score {recommended.ranking_score:.3f}"
        else:
            reason = "No eligible products found; see alternatives and budget exceptions."

        result = RecommendationResult(
            request=request,
            recommended=recommended,
            alternatives=[r for r in ranked if r.product != (recommended.product if recommended else None)],
            reason=reason,
            human_approval_required=human_approval_required,
            budget_exception=None,
            tco_comparison={k: v for k, v in tco_map.items()} if tco_map else None,
            metadata={"budget_exceptions_count": len(budget_exceptions)},
        )
        return result
