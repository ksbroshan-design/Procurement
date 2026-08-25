from __future__ import annotations

from typing import Optional

from app.workflow.state import ProcurementState
from app.workflow.nodes import (
    parse_intent_node,
    guardrail_node,
    discovery_node,
    recommendation_node,
    authorization_node,
    human_approval_node,
    revalidation_node,
    purchase_node,
    audit_node,
)
from app.guardrails.domain_guardrail import DomainGuardrail
from app.parser.intent_parser import IntentParser
from app.discovery.discovery_service import DiscoveryService
from app.recommendation.recommendation_service import RecommendationService
from app.agent.state import Stage


class ProcurementGraph:
    """Lightweight state graph that sequences node calls according to the
    procurement workflow. This is a simple, deterministic runner intended to
    be LangGraph-compatible in shape (nodes + conditional edges) but does not
    depend on an external LangGraph runtime so unit tests remain fast and
    deterministic.

    The graph enforces safe transitions and a revalidation retry limit to
    avoid infinite rediscovery loops.
    """

    def __init__(
        self,
        intent_parser: Optional[IntentParser] = None,
        guardrail: Optional[DomainGuardrail] = None,
        discovery_service: Optional[DiscoveryService] = None,
        recommendation_service: Optional[RecommendationService] = None,
        max_retries: int = 2,
    ) -> None:
        self.intent_parser = intent_parser or IntentParser  # type: ignore
        self.guardrail = guardrail or DomainGuardrail  # type: ignore
        self.discovery_service = discovery_service or DiscoveryService()
        self.recommendation_service = recommendation_service or RecommendationService()
        self.max_retries = max_retries

    def run(
        self,
        state: ProcurementState,
        intent_parser: Optional[IntentParser] = None,
        guardrail: Optional[DomainGuardrail] = None,
    ) -> ProcurementState:
        """Execute the graph from START to a stopping point. The method
        accepts optional overrides for the parser and guardrail instances.

        The runner is deterministic and stops when the state becomes FAILED,
        COMPLETED, or WAITING_FOR_HUMAN.
        """
        # allow per-run overrides
        parser = intent_parser or self.intent_parser
        guard = guardrail or self.guardrail

        # If we were waiting for human approval and a decision exists, handle it:
        if state.status == state.status.WAITING_FOR_HUMAN and state.approval_decision is not None:
            if getattr(state.approval_decision, "approved", False):
                # resume after approval
                state.status = state.status.RUNNING
                state.add_event(state.current_stage, "Resuming run after human approval")
            else:
                # human rejected -> finalize via audit (no purchase)
                state.add_event(state.current_stage, "Human rejected approval; finalizing")
                state = audit_node(state)
                return state

        # parse intent
        # parser might be a class or an instance that provides .parse
        parser_instance = parser if hasattr(parser, "parse") else parser(self._fake_llm())
        state = parse_intent_node(state, parser_instance)
        # if parse resulted in waiting for human or failed, stop
        if state.status in (state.status.FAILED, state.status.WAITING_FOR_HUMAN):
            return state

        # guardrail (run a domain check as well to be safe)
        guard_instance = guard if hasattr(guard, "check") else guard(self._fake_llm())
        state = guardrail_node(state, guard_instance)
        if state.status in (state.status.FAILED, state.status.WAITING_FOR_HUMAN):
            return state

        # discovery
        state = discovery_node(state, self.discovery_service)
        if state.status == state.status.FAILED:
            return state

        # If no products found, audit and stop
        if not state.discovery_result or not state.discovery_result.discovered_products:
            state.add_event(state.current_stage, "No products found; ending")
            state = audit_node(state)
            return state

        # recommendation
        state = recommendation_node(state, self.recommendation_service)
        if state.status == state.status.FAILED:
            return state

        # authorization
        state = authorization_node(state)
        # If recommendation had budget exceptions but no eligible recommended product,
        # surface as a human approval checkpoint so a user can review budget-exception candidates.
        if state.recommendation and state.recommendation.recommended is None:
            # If an approval decision already exists and is approved, skip creating another approval request
            if state.approval_decision is not None and getattr(state.approval_decision, "approved", False):
                state.add_event(state.current_stage, "Approval already granted previously; continuing")
            else:
                bc = state.recommendation.metadata.get("budget_exceptions_count") if state.recommendation.metadata else None
                if bc and bc > 0:
                    state.add_event(state.current_stage, "Budget exceptions found; requesting human review", {"count": bc})
                    # Create an approval request for human review of budget-exception candidates.
                    # Use the first alternative as a representative candidate if available.
                    try:
                        from app.authorization.models import ApprovalRequest
                    except Exception:
                        ApprovalRequest = None
                    candidate = None
                    if state.recommendation.alternatives:
                        candidate = state.recommendation.alternatives[0]
                    if ApprovalRequest is not None and candidate is not None:
                        ar = ApprovalRequest(
                            request_id=state.request_id,
                            recommended_product=candidate,
                            requested_amount=candidate.product.price,
                            reason=state.recommendation.reason or "Budget exception candidates",
                            details=state.recommendation.budget_exception if state.recommendation else None,
                            tco_comparison=state.recommendation.tco_comparison if state.recommendation else None,
                        )
                        state.approval_request = ar
                        state.status = state.status.WAITING_FOR_HUMAN
                        state.current_stage = Stage.HUMAN_APPROVAL
                        state.add_event(Stage.HUMAN_APPROVAL, "Approval request created for budget exceptions", {"requested_amount": ar.requested_amount})
                        return state
                    # fallback: call human_approval_node to let node decide
                    state = human_approval_node(state)
                    return state

        if state.status == state.status.WAITING_FOR_HUMAN:
            # create approval request
            state = human_approval_node(state)
            return state
        if state.status == state.status.FAILED:
            return state

        # if authorization allows, proceed to revalidation
        # enforce retry loop for vendor unavailability: if discovery reported unavailable vendors
        if state.discovery_result and getattr(state.discovery_result, "unavailable_vendors", None):
            if state.retry_count < self.max_retries:
                state.retry_count += 1
                state.add_event(state.current_stage, "Vendor unavailable; retrying discovery", {"retry_count": state.retry_count})
                state = discovery_node(state, self.discovery_service)
                state = recommendation_node(state, self.recommendation_service)
                state = authorization_node(state)
                if state.status == state.status.WAITING_FOR_HUMAN:
                    state = human_approval_node(state)
                    return state
                if state.status == state.status.FAILED:
                    return state

        # If human approval was already granted for a budget-exception candidate,
        # promote the approved candidate to be the recommended product so the
        # purchase path can proceed.
        if state.approval_decision is not None and getattr(state.approval_decision, "approved", False):
            if state.recommendation and state.recommendation.recommended is None and state.approval_request is not None:
                state.recommendation.recommended = state.approval_request.recommended_product
                state.add_event(state.current_stage, "Promoted approved candidate to recommended product", {"product": state.recommendation.recommended.product.product_name})
            # ensure authorization reflects the human approval so purchase can proceed
            if state.authorization_decision is None or not getattr(state.authorization_decision, "allowed", False):
                try:
                    from app.authorization.models import AuthorizationDecision
                except Exception:
                    AuthorizationDecision = None
                if AuthorizationDecision is not None:
                    auth_dec = AuthorizationDecision(
                        allowed=True,
                        requires_human_approval=False,
                        reason="Approved by human",
                        requested_amount=state.approval_request.requested_amount if state.approval_request else None,
                        authorization_limit=None,
                        budget_limit=state.recommendation.request.budget if state.recommendation and state.recommendation.request else None,
                        exceeded_by=None,
                    )
                    state.authorization_decision = auth_dec
                    state.add_event(state.current_stage, "Authorization updated from human approval", {"allowed": True})

        # revalidation
        state = revalidation_node(state)
        # Simple policy: if retry_count exceeded, treat as stale and abort to audit
        if state.retry_count > self.max_retries:
            state.add_event(state.current_stage, "Revalidation stale; aborting to discovery limit")
            state = audit_node(state)
            return state

        # Purchase: only proceed if authorization allowed and (if required) approval_decision approved
        auth = state.authorization_decision
        if auth and auth.allowed:
            # if human approval was required earlier, ensure approval_decision is present and approved
            if auth.requires_human_approval:
                if state.approval_decision is None or not getattr(state.approval_decision, "approved", False):
                    state.add_event(state.current_stage, "Approval not granted; skipping purchase")
                    state = audit_node(state)
                    return state
            # create purchase order
            state = purchase_node(state)
            state = audit_node(state)
            return state
        else:
            # Not allowed: go to audit
            state.add_event(state.current_stage, "Authorization did not permit purchase; auditing")
            state = audit_node(state)
            return state

    def _fake_llm(self):
        # The graph runner can construct lightweight parser/guardrail wrappers if
        # the caller passed class objects instead of instances. Tests normally
        # pass real instances or fakes; this is a fallback no-op provider.
        class _Fake:
            def parse(self, brief: str):
                raise RuntimeError("No IntentParser provided")

            def check(self, brief: str):
                raise RuntimeError("No Guardrail provided")

        return _Fake()
