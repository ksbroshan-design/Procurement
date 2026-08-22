from __future__ import annotations

from typing import Optional

from app.discovery.models import VendorProduct
from app.recommendation.models import TCOResult
from app.recommendation.mock_tco_data import get_mock_tco_data


class TCOEngine:
    """Deterministic Total Cost of Ownership engine.

    - Uses structured vendor/product data and optional mock historical data.
    - Does NOT invent maintenance or additional costs when data is absent.
    - Records assumptions explicitly in the TCOResult.assumptions list.
    """

    def __init__(self, analysis_period_years: int = 3) -> None:
        self.analysis_period_years = analysis_period_years

    def compute_tco(self, product: VendorProduct, comparison: VendorProduct | None = None) -> TCOResult:
        assumptions = []

        purchase_price = float(product.price)

        mock = get_mock_tco_data(product.vendor_name, product.product_name)

        expected_maintenance_cost: Optional[float] = None
        expected_additional_costs: Optional[float] = None
        warranty_value: Optional[float] = None

        # Use mock data if available
        if mock is not None:
            eam = mock.get("expected_annual_maintenance")
            if eam is not None:
                # base maintenance over analysis period
                expected_maintenance_cost = float(eam) * self.analysis_period_years
                # allow reliability influence if provided and product has reliability_score
                rel_factor = mock.get("reliability_influence")
                if rel_factor is not None and product.reliability_score is not None:
                    # scale maintenance by (1 + (1 - reliability_score) * rel_factor)
                    adj = 1.0 + (1.0 - float(product.reliability_score)) * float(rel_factor)
                    expected_maintenance_cost = expected_maintenance_cost * adj
                    assumptions.append(
                        "Maintenance adjusted by product.reliability_score using mock.reliability_influence"
                    )
                else:
                    if rel_factor is not None:
                        assumptions.append("Mock defines reliability_influence but product.reliability_score missing; no adjustment applied")
            add = mock.get("additional_costs")
            if add is not None:
                expected_additional_costs = float(add) * 1.0  # treat as one-time
        else:
            assumptions.append("No historical maintenance data available for this product; maintenance costs unknown")

        # warranty value: if both warranty_years and expected_maintenance_cost available,
        # assume warranty covers maintenance for warranty_years (reduces maintenance cost accordingly)
        if product.warranty_years is not None and expected_maintenance_cost is not None:
            covered_years = min(product.warranty_years, self.analysis_period_years)
            # expected maintenance per year
            per_year = expected_maintenance_cost / float(self.analysis_period_years)
            warranty_value = per_year * covered_years
            assumptions.append(f"Warranty covers {covered_years} of {self.analysis_period_years} years; warranty reduces maintenance by estimated {warranty_value}")
        else:
            if product.warranty_years is not None:
                assumptions.append("Warranty present but maintenance estimates missing; warranty value not quantified")

        # Compose estimated_total_cost conservatively: sum known components, treat unknowns as missing but still compute partial TCO
        est_total = purchase_price
        if expected_maintenance_cost is not None:
            est_total += expected_maintenance_cost
        if expected_additional_costs is not None:
            est_total += expected_additional_costs
        if warranty_value is not None:
            est_total -= warranty_value

        estimated_total_cost = float(est_total)

        tco = TCOResult(
            purchase_price=purchase_price,
            warranty_cost=None,  # placeholder: we don't charge warranty cost here
            expected_maintenance_cost=expected_maintenance_cost,
            expected_additional_costs=expected_additional_costs,
            analysis_period_years=self.analysis_period_years,
            estimated_total_cost=estimated_total_cost,
            savings_vs_baseline=None,
            assumptions=assumptions,
        )

        # If comparison product provided, compute savings (if both have estimated_total_cost)
        if comparison is not None:
            comp_tco = self.compute_tco(comparison)  # recursive call with same analysis period
            if comp_tco.estimated_total_cost is not None:
                tco.savings_vs_baseline = comp_tco.estimated_total_cost - tco.estimated_total_cost
                if comp_tco.estimated_total_cost > 0:
                    tco_ass_pct = (tco.savings_vs_baseline / comp_tco.estimated_total_cost) * 100.0
                    # attach as an assumption/explanatory note
                    tco.assumptions.append(f"Compared to baseline estimated_total_cost {comp_tco.estimated_total_cost}, savings {tco.savings_vs_baseline} ({tco_ass_pct:.1f}%)")
        return tco
