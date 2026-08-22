from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field

from app.discovery.models import VendorProduct
from app.models.procurement import ProcurementRequest
from app.models.constraint import Constraint


class ConstraintEvaluation(BaseModel):
    """Result of evaluating one Constraint against a product.

    - constraint: the original Constraint object
    - passed: whether the product satisfied the constraint
    - actual_value: the observed value on the product (if available)
    - expected_value: the constraint's value for clarity
    - explanation: short human-readable explanation
    """

    constraint: Constraint
    passed: bool
    actual_value: Optional[Any] = None
    expected_value: Optional[Any] = None
    explanation: Optional[str] = None


class ProductEvaluation(BaseModel):
    """Evaluation of a single VendorProduct against a ProcurementRequest.

    Kept generic and conservative: no new calculations are performed here by
    default — this is a typed container for later evaluation logic.
    """

    product: VendorProduct
    constraint_evaluations: List[ConstraintEvaluation] = Field(default_factory=list)
    all_constraints_satisfied: bool = False

    # Violations are reported as booleans and may be accompanied by details
    budget_violation: bool = False
    delivery_violation: bool = False

    # Overall eligibility is a conservative boolean summarizing whether the
    # product is suitable for recommendation (derived later by the service).
    overall_eligibility: bool = False


class TCOResult(BaseModel):
    """Placeholder container for Total Cost of Ownership analysis.

    Rules enforced by design:
    - Do NOT invent maintenance/repair costs. If such data is unavailable,
      those fields remain None and assumptions must be recorded.
    - Analysis must remain explicit about assumptions.
    """

    purchase_price: float
    warranty_cost: Optional[float] = None
    expected_maintenance_cost: Optional[float] = None
    expected_additional_costs: Optional[float] = None

    analysis_period_years: int = 1
    estimated_total_cost: Optional[float] = None

    # Optional savings compared with another option (e.g., baseline). When
    # present, should be positive when this option is cheaper.
    savings_vs_baseline: Optional[float] = None

    # Assumptions used for the calculation (strings for transparency)
    assumptions: List[str] = Field(default_factory=list)


class RankedProduct(BaseModel):
    """A ranked product entry with TCO and scoring metadata.

    Ranking and scoring are not performed here — fields are placeholders for
    later pipeline stages. Keep category-agnostic and simple.
    """

    product: VendorProduct
    eligibility: bool
    tco: Optional[TCOResult] = None

    # score and rank are optional until a ranking implementation is provided
    ranking_score: Optional[float] = None
    rank: Optional[int] = None

    # scoring_breakdown is a dictionary of named score components
    scoring_breakdown: Dict[str, float] = Field(default_factory=dict)
    explanation: Optional[str] = None


class RecommendationResult(BaseModel):
    """High-level recommendation output.

    - recommended: the top-ranked product (may be None if no eligible product)
    - alternatives: other ranked products
    - reason: short explanation for why this recommendation was selected
    - human_approval_required: whether the pipeline signals that human
      approval is needed (e.g., budget exception)
    - budget_exception: structured info when recommendation exceeds budget
    - tco_comparison: optional summary of TCOs for recommended vs alternatives
    """

    request: Optional[ProcurementRequest] = None
    recommended: Optional[RankedProduct] = None
    alternatives: List[RankedProduct] = Field(default_factory=list)
    reason: Optional[str] = None

    human_approval_required: bool = False

    # budget exception detail (if recommendation violates budget)
    budget_exception: Optional[Dict[str, Any]] = None

    # mapping product_id/vendor -> TCOResult or a short summary
    tco_comparison: Optional[Dict[str, TCOResult]] = None

    # Additional metadata for audit and explainability
    metadata: Dict[str, Any] = Field(default_factory=dict)
