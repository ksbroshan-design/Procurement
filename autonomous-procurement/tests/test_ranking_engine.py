from app.discovery.mock_sources import vendor_alpha_products, vendor_bravo_products
from app.models.procurement import ProcurementRequest
from app.recommendation.constraint_engine import ConstraintEngine
from app.recommendation.tco_engine import TCOEngine
from app.recommendation.ranking_engine import RankingEngine


def test_ranking_basic_budget_exception():
    # Setup: request with budget that excludes Bravo Tablet but allows Alpha Tablet
    req = ProcurementRequest(original_brief="Buy 1 tablet", category="tablet", quantity=1, budget=29000)

    alpha = next(p for p in vendor_alpha_products() if p.product_name == "Alpha Tablet A10")
    bravo = next(p for p in vendor_bravo_products() if p.product_name == "Bravo Tablet S7")

    # No explicit constraints beyond budget
    ce = ConstraintEngine()
    ev_alpha = ce.evaluate(req, alpha)
    ev_bravo = ce.evaluate(req, bravo)

    # compute tco
    tco_engine = TCOEngine(analysis_period_years=3)
    tco_alpha = tco_engine.compute_tco(alpha)
    tco_bravo = tco_engine.compute_tco(bravo)

    tcos = {
        f"{alpha.vendor_name}::{alpha.product_name}": tco_alpha,
        f"{bravo.vendor_name}::{bravo.product_name}": tco_bravo,
    }

    ranking = RankingEngine()
    ranked = ranking.rank(req, [ev_alpha, ev_bravo], tcos=tcos)

    # Alpha should be eligible and ranked above Bravo
    assert ranked[0].product.product_name == "Alpha Tablet A10"
    # Bravo should appear but be ineligible (budget exception candidate)
    assert any(r.product.product_name == "Bravo Tablet S7" and not r.eligibility for r in ranked)


def test_ranking_ineligible_due_to_missing_attr():
    # Create a constraint that product cannot meet (e.g., require RAM >= 16)
    req = ProcurementRequest(original_brief="Buy 1 tablet with 16GB ram", category="tablet", quantity=1)
    req.constraints = []
    from app.models.constraint import Constraint

    req.constraints.append(Constraint(attribute="ram_gb", operator=">=", value=16))

    alpha = next(p for p in vendor_alpha_products() if p.product_name == "Alpha Tablet A10")
    ce = ConstraintEngine()
    ev = ce.evaluate(req, alpha)

    ranking = RankingEngine()
    ranked = ranking.rank(req, [ev], tcos={})

    # product should be present but ineligible
    assert len(ranked) == 1
    assert not ranked[0].eligibility
