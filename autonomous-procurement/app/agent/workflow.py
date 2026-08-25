from __future__ import annotations

from typing import Any

from app.agent.state import ProcurementState, Stage, Status

import uuid


class ProcurementWorkflow:
    """Simple orchestration skeleton for procurement workflows.

    This workflow requires explicit injection of the guardrail and parser
    components. They must implement the minimal interfaces used here:
      - guardrail.check(text) -> object with attributes is_procurement (bool) and reason (str)
      - parser.parse(brief) -> object with attributes: status ("ok"|"needs_clarification"|...),
        missing_information (list), clarification_question (str), request (ProcurementRequest | None)

    Requiring these components at construction time makes the workflow easy to
    test with fakes and ensures it does not instantiate or depend on any real
    LLM or external API directly.
    """

    def __init__(self, guardrail: Any, parser: Any) -> None:
        if guardrail is None or parser is None:
            raise ValueError("ProcurementWorkflow requires guardrail and parser instances to be passed in")
        self.guardrail = guardrail
        self.parser = parser

    def start(self, brief: str) -> ProcurementState:
        """Start a new workflow run and return the final state snapshot.

        The method runs only the existing components (domain guardrail and
        intent parser) and stops. It records audit events for observability.
        """
        request_id = str(uuid.uuid4())
        state = ProcurementState(request_id=request_id, original_brief=brief)
        state.add_event(Stage.START, "Workflow started")

        # DOMAIN CHECK
        state.stage = Stage.DOMAIN_CHECK
        state.add_event(state.stage, "Running domain guardrail check")
        verdict = self.guardrail.check(brief)
        state.add_event(state.stage, "Domain check result", details={"is_procurement": verdict.is_procurement, "reason": verdict.reason})
        if not verdict.is_procurement:
            state.status = Status.FAILED
            state.stage = Stage.FAILED
            state.error = f"Domain guardrail rejected the brief: {verdict.reason}"
            state.add_event(state.stage, "Stopped: not procurement", details={"reason": verdict.reason})
            return state

        # INTENT PARSING
        state.stage = Stage.INTENT_PARSING
        state.add_event(state.stage, "Parsing intent with intent parser")
        response: IntentResponse = self.parser.parse(brief)

        # If parser needs clarification, stop and surface the question
        if response.status == "needs_clarification":
            state.status = Status.WAITING_FOR_HUMAN
            state.stage = Stage.VALIDATION
            state.add_event(state.stage, "Needs clarification", details={"missing_information": response.missing_information, "clarification_question": response.clarification_question})
            return state

        if response.status != "ok" or response.request is None:
            # treat other non-ok responses conservatively as failure
            state.status = Status.FAILED
            state.stage = Stage.FAILED
            state.error = "Intent parser failed to produce a valid request"
            state.add_event(state.stage, "Parsing failed", details={"response_status": response.status})
            return state

        # Store parsed ProcurementRequest and move to VALIDATION stage
        state.current_request = response.request
        state.stage = Stage.VALIDATION
        state.add_event(state.stage, "Parsed request stored", details={"category": response.request.category, "quantity": response.request.quantity})

        # For this skeleton we stop after successful validation step.
        state.status = Status.COMPLETED
        state.stage = Stage.COMPLETED
        state.add_event(state.stage, "Workflow completed (skeleton)")
        return state
