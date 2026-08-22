from app.authorization.models import AuthorizationDecision, ApprovalRequest, ApprovalDecision, ApprovalStatus
from app.discovery.mock_sources import vendor_alpha_products
from app.recommendation.models import RankedProduct


def test_authorization_models_basic():
    prod = vendor_alpha_products()[0]
    rp = RankedProduct(product=prod, eligibility=True)

    ar = ApprovalRequest(request_id="r1", recommended_product=rp, requested_amount=prod.price, reason="Over budget")
    assert ar.status == ApprovalStatus.pending

    ad = ApprovalDecision(approval_id="a1", approved=True, approver="manager", reason="OK", timestamp="2026-08-19T00:00:00Z")
    assert ad.approved

    auth = AuthorizationDecision(allowed=False, requires_human_approval=True, reason="Over budget", requested_amount=prod.price, budget_limit=10000, exceeded_by=prod.price - 10000)
    assert auth.requires_human_approval
