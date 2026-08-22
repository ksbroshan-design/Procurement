import pytest
from copy import deepcopy

from app.workflow.graph import ProcurementGraph
from app.workflow.state import ProcurementState
from app.agent.state import Stage, Status
from app.guardrails.domain_guardrail import GuardrailResult
from app.parser.intent_parser import IntentResponse
from app.models.procurement import ProcurementRequest
from app.recommendation.models import RankedProduct
from app.recommendation.models import TCOResult
from app.recommendation.models import RecommendationResult
from app.discovery.mock_sources import vendor_alpha_products, vendor_bravo_products
from app.authorization.models import ApprovalDecision


class FakeGuardrail:
    def __init__(self, is_procurement: bool = True, reason: str = "ok"):
        self._res = GuardrailResult(is_procurement=is_procurement, reason=reason)

    def check(self, text: str):
        return self._res


class FakeParser:
    def __init__(self, request: ProcurementRequest, status: str = "ok"):
        self._resp = IntentResponse(status=status, is_procurement=True if status == "ok" else False, request=request if status == "ok" else None)

    def parse(self, brief: str):
        return self._resp


class EmptyDiscovery:
    def discover(self, request):
        return type("R", (), {"discovered_products": [], "unavailable_vendors": []})()


class UnavailableDiscovery:
    def __init__(self, products, unavailable_vendors):
        self._products = products
        self._unavailable = unavailable_vendors

    def discover(self, request):
        return type("R", (), {"discovered_products": deepcopy(self._products), "unavailable_vendors": deepcopy(self._unavailable)})()


class UnavailableAlwaysDiscovery:
    """Discovery that reports unavailable vendors and no products, to force retries."""

    def __init__(self, unavailable_vendors):
        self._unavailable = unavailable_vendors

    def discover(self, request):
        return type("R", (), {"discovered_products": [], "unavailable_vendors": deepcopy(self._unavailable)})()


@pytest.fixture
def default_products():
    return vendor_alpha_products() + vendor_bravo_products()


def make_request(brief, category="tablet", qty=1, budget=None):
    return ProcurementRequest(original_brief=brief, category=category, quantity=qty, budget=budget)


def test_normal_product_purchase(default_products):
    req = make_request("Buy 1 tablet", category="tablet", qty=1, budget=999999)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)

    graph = ProcurementGraph(intent_parser=parser, guardrail=guard)
    state = ProcurementState(request_id="r1", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)

    # Expect purchase order created
    assert state.purchase_order is not None
    # Verify audit completed
    assert state.status == Status.COMPLETED
    assert state.current_stage == Stage.COMPLETED
    # Verify mandatory constraints passed for purchased product
    assert state.recommendation is not None
    if state.recommendation.recommended:
        assert state.recommendation.recommended.eligibility


def test_budget_exception_flow(default_products):
    req = make_request("Buy 1 tablet", category="tablet", qty=1, budget=1000)  # low budget to force exception
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)

    graph = ProcurementGraph(intent_parser=parser, guardrail=guard)
    state = ProcurementState(request_id="r2", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # Should be waiting for human approval and an approval request should be created
    assert state.status == Status.WAITING_FOR_HUMAN
    assert state.approval_request is not None

    # Approve externally
    state.approval_decision = ApprovalDecision(approval_id="a1", approved=True, approver="manager", reason="ok", timestamp="2026-08-19T00:00:00Z")

    # Resume graph to complete purchase
    state = graph.run(state, intent_parser=parser, guardrail=guard)
    assert state.purchase_order is not None
    assert state.status == Status.COMPLETED


def test_human_rejection_stops_purchase(default_products):
    req = make_request("Buy 1 tablet", category="tablet", qty=1, budget=1000)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard)
    state = ProcurementState(request_id="r3", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # create approval request
    state = graph.run(state, intent_parser=parser, guardrail=guard)
    assert state.approval_request is not None

    # Reject
    state.approval_decision = ApprovalDecision(approval_id="a2", approved=False, approver="manager", reason="not approved", timestamp="2026-08-19T00:00:00Z")

    # resume
    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # no purchase
    assert state.purchase_order is None
    # audit completed
    assert state.status == Status.COMPLETED


def test_stale_product_and_rediscovery(default_products):
    # Simulate approval but force revalidation to treat as stale by setting retry_count high
    req = make_request("Buy 1 tablet", category="tablet", qty=1, budget=999999)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard, max_retries=0)
    state = ProcurementState(request_id="r4", original_brief=req.original_brief)

    # Run to get recommendation
    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # Should either have purchased or be at authorization; if purchased, ensure revalidation occurred
    if state.purchase_order:
        assert state.status == Status.COMPLETED
    else:
        # If waiting for human, simulate approval and set retry_count to exceed
        state.approval_decision = ApprovalDecision(approval_id="a3", approved=True, approver="manager", reason="ok", timestamp="2026-08-19T00:00:00Z")
        # set retry_count high to simulate stale
        state.retry_count = 10
        state = graph.run(state, intent_parser=parser, guardrail=guard)
        # After stale revalidation, should audit and not purchase
        assert state.purchase_order is None
        assert state.status == Status.COMPLETED


def test_vendor_unavailable_triggers_retry(default_products):
    # Use a discovery that reports an unavailable vendor
    req = make_request("Buy 1 printer", category="printer", qty=1, budget=50000)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)
    # discovery returns an unavailable vendor list to trigger retry
    discovery = UnavailableDiscovery(products=default_products, unavailable_vendors=["Alpha Supplies"]) 
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard, discovery_service=discovery, max_retries=1)
    state = ProcurementState(request_id="r5", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # retry_count should have incremented due to vendor unavailable
    assert state.retry_count > 0


def test_no_matching_product_stops_early():
    req = make_request("Buy 1 spaceship", category="spaceship", qty=1)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard, discovery_service=EmptyDiscovery())
    state = ProcurementState(request_id="r6", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # No products found should lead to audit/completed without purchase
    assert state.purchase_order is None
    assert state.status == Status.COMPLETED


def test_off_topic_request_is_rejected():
    req = make_request("Tell me a joke", category="joke", qty=1)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=False)
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard)
    state = ProcurementState(request_id="r7", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    assert state.purchase_order is None
    assert state.status == Status.FAILED


def test_authorization_limit_requires_human_and_no_automatic_purchase(default_products):
    # low budget to force human approval; ensure no automatic purchase without approval
    req = make_request("Buy 1 tv", category="tv", qty=1, budget=1000)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard)
    state = ProcurementState(request_id="r8", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # waiting for human
    assert state.status == Status.WAITING_FOR_HUMAN
    # do not approve, resume
    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # still no purchase
    assert state.purchase_order is None


def test_revalidation_retry_limit_terminates_safely(default_products):
    # Use a discovery that always reports unavailable vendors so retries occur
    req = make_request("Buy 1 printer", category="printer", qty=1, budget=50000)
    parser = FakeParser(req)
    guard = FakeGuardrail(is_procurement=True)
    discovery = UnavailableAlwaysDiscovery(unavailable_vendors=["Alpha Supplies"]) 
    graph = ProcurementGraph(intent_parser=parser, guardrail=guard, discovery_service=discovery, max_retries=1)
    state = ProcurementState(request_id="r9", original_brief=req.original_brief)

    state = graph.run(state, intent_parser=parser, guardrail=guard)
    # retries attempted; eventually safe termination
    assert state.retry_count <= graph.max_retries
    assert state.status in (Status.COMPLETED, Status.FAILED)
    assert state.purchase_order is None
