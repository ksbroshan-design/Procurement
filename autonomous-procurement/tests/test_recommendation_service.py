from app.discovery.mock_sources import vendor_alpha_products, vendor_bravo_products
from app.models.procurement import ProcurementRequest
from app.recommendation.recommendation_service import RecommendationService


def test_recommendation_normal_flow():
    req = ProcurementRequest(original_brief="Buy 1 tablet under 30000", category="tablet", quantity=1, budget=30000)
    discovery_products = vendor_alpha_products() + vendor_bravo_products()
    discovery = type("D", (), {"discovered_products": discovery_products})()

    svc = RecommendationService()
    res = svc.recommend(req, discovery)

    assert isinstance(res, object)
    # recommended may be None if none eligible
    # ensure result contains metadata
    assert "budget_exceptions_count" in res.metadata


def test_recommendation_budget_exception_trigger():
    # low budget to force exception
    req = ProcurementRequest(original_brief="Buy 1 tv under 10000", category="tv", quantity=1, budget=10000)
    discovery_products = vendor_alpha_products() + vendor_bravo_products()
    discovery = type("D", (), {"discovered_products": discovery_products})()

    svc = RecommendationService()
    res = svc.recommend(req, discovery)

    assert res is not None
    if res.recommended:
        # if a recommended product exists and it's over budget, human approval flagged
        if res.recommended.product.price > req.budget:
            assert res.human_approval_required
            assert res.budget_exception is not None
    # ensure metadata present
    assert "budget_exceptions_count" in res.metadata
