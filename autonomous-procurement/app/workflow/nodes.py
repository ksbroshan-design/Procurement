from __future__ import annotations

from typing import Optional
from datetime import datetime

from app.workflow.state import ProcurementState
from app.guardrails.domain_guardrail import DomainGuardrail, GuardrailResult
from app.parser.intent_parser import IntentParser, IntentResponse
from app.discovery.discovery_service import DiscoveryService
from app.recommendation.recommendation_service import RecommendationService
from app.authorization.models import AuthorizationDecision, ApprovalRequest, ApprovalDecision
from app.agent.state import Stage, Status


def guardrail_node(state: ProcurementState, guardrail: DomainGuardrail) -> ProcurementState:
    """Run the domain guardrail and update state.guardrail_result.

    If the message is not procurement-related, mark the state as FAILED.
    """
    state.add_event(Stage.DOMAIN_CHECK, "Running domain guardrail")
    result: GuardrailResult = guardrail.check(state.original_brief)
    state.guardrail_result = result
    state.add_event(Stage.DOMAIN_CHECK, "Domain guardrail result", {"is_procurement": result.is_procurement, "reason": result.reason})
    if not result.is_procurement:
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.error = "Rejected by domain guardrail"
    else:
        state.current_stage = Stage.INTENT_PARSING
    return state


def parse_intent_node(state: ProcurementState, intent_parser: IntentParser) -> ProcurementState:
    """Parse the user's brief into a ProcurementRequest using the provided parser.

    On parse errors or clarification required, update the state accordingly.
    """
    state.add_event(Stage.INTENT_PARSING, "Parsing intent")
    try:
        resp: IntentResponse = intent_parser.parse(state.original_brief)
    except Exception as e:
        state.error = f"Intent parsing failed: {e}"
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.add_event(Stage.INTENT_PARSING, "Parsing error", {"error": str(e)})
        return state

    if resp.status == "rejected":
        state.guardrail_result = state.guardrail_result or None
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.error = f"Rejected by guardrail during parse: {resp.reason}"
        state.add_event(Stage.INTENT_PARSING, "Rejected by parse/guardrail", {"reason": resp.reason})
        return state

    if resp.status == "needs_clarification":
        # leave state.procurement_request as None and mark WAITING_FOR_HUMAN
        state.status = Status.WAITING_FOR_HUMAN
        state.current_stage = Stage.VALIDATION
        state.add_event(Stage.INTENT_PARSING, "Clarification required", {"missing": resp.missing_information, "question": resp.clarification_question})
        return state

    # status ok
    state.procurement_request = resp.request
    state.add_event(Stage.INTENT_PARSING, "Intent parsed", {"category": resp.request.category if resp.request else None})
    state.current_stage = Stage.DISCOVERY
    return state


def discovery_node(state: ProcurementState, discovery_service: DiscoveryService) -> ProcurementState:
    """Run discovery on the parsed ProcurementRequest and attach results.

    Assumes state.procurement_request is present.
    """
    state.add_event(Stage.DISCOVERY, "Starting discovery")
    if state.procurement_request is None:
        state.error = "Discovery requested but procurement_request is missing"
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.add_event(Stage.DISCOVERY, "Failed: no procurement_request")
        return state

    result = discovery_service.discover(state.procurement_request)
    state.discovery_result = result
    # normalized_products are the discovered products for now
    state.normalized_products = result.discovered_products
    state.add_event(Stage.DISCOVERY, "Discovery completed", {"found": len(result.discovered_products)})
    state.current_stage = Stage.COMPARISON
    return state


def recommendation_node(state: ProcurementState, recommendation_service: RecommendationService) -> ProcurementState:
    """Run recommendation over the discovery result and attach RecommendationResult."""
    state.add_event(Stage.COMPARISON, "Running recommendation")
    if state.procurement_request is None or state.discovery_result is None:
        state.error = "Recommendation requested but prerequisites missing"
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.add_event(Stage.COMPARISON, "Failed: prerequisites missing")
        return state

    rec = recommendation_service.recommend(state.procurement_request, state.discovery_result)
    state.recommendation = rec
    state.add_event(Stage.COMPARISON, "Recommendation completed", {"recommended": rec.recommended.product.product_name if rec.recommended else None, "human_approval_required": rec.human_approval_required})
    # advance to authorization
    state.current_stage = Stage.AUTHORIZATION
    return state


def authorization_node(state: ProcurementState) -> ProcurementState:
    """Produce an AuthorizationDecision based on recommendation result.

    This is a lightweight bridge: recommendation service already signals when
    human approval is required. The authorization node formalizes that into
    an AuthorizationDecision object placed on the state.
    """
    state.add_event(Stage.AUTHORIZATION, "Running authorization checks")
    if state.recommendation is None:
        state.error = "Authorization requested but recommendation missing"
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.add_event(Stage.AUTHORIZATION, "Failed: recommendation missing")
        return state

    rec = state.recommendation
    recommended = rec.recommended

    if recommended is None:
        # nothing to authorize
        decision = AuthorizationDecision(allowed=False, requires_human_approval=False, reason="No recommended product", requested_amount=None, authorization_limit=None, budget_limit=rec.request.budget if rec.request else None, exceeded_by=None)
        state.authorization_decision = decision
        state.add_event(Stage.AUTHORIZATION, "No recommended product to authorize")
        state.current_stage = Stage.AUDIT
        return state

    price = recommended.product.price
    budget_limit = rec.request.budget if rec.request else None
    requires_human = rec.human_approval_required
    allowed = not requires_human and (budget_limit is None or price <= budget_limit)
    exceeded = None
    if budget_limit is not None and price > budget_limit:
        exceeded = price - budget_limit

    decision = AuthorizationDecision(
        allowed=allowed,
        requires_human_approval=requires_human,
        reason="Auto-approval allowed" if allowed else "Human approval required" if requires_human else "Not allowed",
        requested_amount=price,
        authorization_limit=None,
        budget_limit=budget_limit,
        exceeded_by=exceeded,
    )
    # If human approval already exists and is approved, elevate to allowed
    if decision.requires_human_approval and state.approval_decision is not None and getattr(state.approval_decision, "approved", False):
        decision.allowed = True
        # keep a record that human approval was applied
        decision.requires_human_approval = False
        state.add_event(Stage.AUTHORIZATION, "Human approval already present; authorization elevated", {"approved_by": state.approval_decision.approver})

    state.authorization_decision = decision
    state.add_event(Stage.AUTHORIZATION, "Authorization decision made", {"allowed": decision.allowed, "requires_human_approval": decision.requires_human_approval})

    if decision.requires_human_approval:
        state.status = Status.WAITING_FOR_HUMAN
        state.current_stage = Stage.HUMAN_APPROVAL
    else:
        state.current_stage = Stage.REVALIDATION
    return state


def human_approval_node(state: ProcurementState) -> ProcurementState:
    """Create an ApprovalRequest when human approval is required.

    This node does not wait for human input; it only creates the request payload.
    """
    state.add_event(Stage.HUMAN_APPROVAL, "Preparing approval request")
    if state.authorization_decision is None or not state.authorization_decision.requires_human_approval:
        state.add_event(Stage.HUMAN_APPROVAL, "No human approval required")
        state.current_stage = Stage.REVALIDATION
        return state

    # build approval request
    recommended = state.recommendation.recommended if state.recommendation else None
    if recommended is None:
        state.error = "Human approval requested but no recommended product present"
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
        state.add_event(Stage.HUMAN_APPROVAL, "Failed: no recommended product for approval")
        return state

    approval = ApprovalRequest(
        request_id=state.request_id,
        recommended_product=recommended,
        requested_amount=recommended.product.price,
        reason=state.recommendation.reason if state.recommendation else "Budget exception",
        details=state.recommendation.budget_exception if state.recommendation else None,
        tco_comparison=state.recommendation.tco_comparison if state.recommendation else None,
    )
    state.approval_request = approval
    state.add_event(Stage.HUMAN_APPROVAL, "Approval request created", {"requested_amount": approval.requested_amount})
    # remain in WAITING_FOR_HUMAN until an approval_decision is set externally
    state.status = Status.WAITING_FOR_HUMAN
    state.current_stage = Stage.HUMAN_APPROVAL
    return state


def revalidation_node(state: ProcurementState) -> ProcurementState:
    """Placeholder revalidation node: mark revalidation as attempted.

    In a full pipeline this would re-run constraint checks after approvals or
    updates. Here, set a simple marker.
    """
    state.add_event(Stage.REVALIDATION, "Revalidation step (noop)")
    state.revalidation_result = {"revalidated_at": datetime.utcnow().isoformat() + "Z"}
    state.current_stage = Stage.PURCHASE
    return state


def purchase_node(state: ProcurementState) -> ProcurementState:
    """Placeholder purchase node. Creates a purchase_order when authorization
    allows it. If human approval was required, an ApprovalDecision must be present
    and approved. This node does NOT execute any external purchase; it only
    records an intent."""
    state.add_event(Stage.PURCHASE, "Purchase node invoked")

    auth = state.authorization_decision
    # If authorization required human approval, ensure a decision exists
    if auth and auth.requires_human_approval:
        if state.approval_decision is None:
            state.add_event(Stage.PURCHASE, "Approval decision missing; skipping purchase")
            return state
        if not state.approval_decision.approved:
            state.add_event(Stage.PURCHASE, "Approval rejected; not purchasing")
            state.current_stage = Stage.AUDIT
            return state

    # If authorization disallows purchase, skip
    if auth and not auth.allowed:
        state.add_event(Stage.PURCHASE, "Authorization disallows purchase; skipping")
        state.current_stage = Stage.AUDIT
        return state

    # Proceed to create purchase order when recommendation present
    recommended = state.recommendation.recommended if state.recommendation else None
    if recommended:
        state.purchase_order = {
            "order_id": f"PO-{state.request_id}-{int(datetime.utcnow().timestamp())}",
            "product": {"vendor": recommended.product.vendor_name, "product_name": recommended.product.product_name, "price": recommended.product.price},
            "status": "created",
        }
        state.add_event(Stage.PURCHASE, "Purchase order created", {"order_id": state.purchase_order["order_id"]})
        state.current_stage = Stage.AUDIT
    else:
        state.add_event(Stage.PURCHASE, "No recommended product; not purchasing")
    return state


def audit_node(state: ProcurementState) -> ProcurementState:
    """Finalize auditing and mark workflow completed if there are no errors."""
    state.add_event(Stage.AUDIT, "Audit step completed")
    if state.error:
        state.status = Status.FAILED
        state.current_stage = Stage.FAILED
    else:
        state.status = Status.COMPLETED
        state.current_stage = Stage.COMPLETED
    return state
