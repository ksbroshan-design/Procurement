import pytest

from app.agent.workflow import ProcurementWorkflow
from app.agent.state import Stage, Status
from app.parser.intent_parser import IntentResponse
from app.models.procurement import ProcurementRequest
from app.guardrails.domain_guardrail import GuardrailResult


class FakeGuardrail:
    def __init__(self, is_procurement: bool = True, reason: str = "ok"):
        self._res = GuardrailResult(is_procurement=is_procurement, reason=reason)

    def check(self, text: str):
        return self._res


class FakeParser:
    def __init__(self, response: IntentResponse):
        self._response = response
        self.parse_calls = 0

    def parse(self, brief: str):
        self.parse_calls += 1
        return self._response


def test_valid_procurement_request_runs_happy_path():
    brief = "Buy 10 tablets under ₹30,000 each with at least 8GB RAM."
    # guardrail approves
    guard = FakeGuardrail(is_procurement=True)

    # parser returns a successful IntentResponse with a ProcurementRequest
    req = ProcurementRequest(original_brief=brief, category="tablet", quantity=10)
    parser_resp = IntentResponse(status="ok", is_procurement=True, request=req)
    parser = FakeParser(parser_resp)

    wf = ProcurementWorkflow(guardrail=guard, parser=parser)
    state = wf.start(brief)

    # workflow completed successfully and stored the request
    assert state.stage == Stage.COMPLETED
    assert state.status == Status.COMPLETED
    assert state.current_request is not None
    assert state.current_request.category == "tablet"
    # events should include domain check and intent parsing
    stages = [e.stage for e in state.events]
    assert Stage.DOMAIN_CHECK in stages
    assert Stage.INTENT_PARSING in stages
    assert Stage.VALIDATION in stages


def test_off_topic_request_stops_early():
    brief = "Write a Python program."
    guard = FakeGuardrail(is_procurement=False, reason="not procurement")
    # parser should not be called; provide a parser that would fail if called
    bad_parser = FakeParser(IntentResponse(status="ok"))

    wf = ProcurementWorkflow(guardrail=guard, parser=bad_parser)
    state = wf.start(brief)

    assert state.stage == Stage.FAILED
    assert state.status == Status.FAILED
    assert state.current_request is None
    # ensure parser.parse was never invoked
    assert bad_parser.parse_calls == 0


def test_incomplete_request_requests_clarification():
    brief = "Buy something good."
    guard = FakeGuardrail(is_procurement=True)
    # parser returns needs_clarification
    parser_resp = IntentResponse(status="needs_clarification", missing_information=["quantity"], clarification_question="How many?")
    parser = FakeParser(parser_resp)

    wf = ProcurementWorkflow(guardrail=guard, parser=parser)
    state = wf.start(brief)

    assert state.status == Status.WAITING_FOR_HUMAN
    # should not have a current_request
    assert state.current_request is None
    stages = [e.stage for e in state.events]
    assert Stage.INTENT_PARSING in stages
    assert Stage.VALIDATION in stages


def test_parser_failure_results_in_failed_state():
    brief = "Buy 2 printers"
    guard = FakeGuardrail(is_procurement=True)
    # parser returns an unusual non-ok status
    parser_resp = IntentResponse(status="rejected", request=None)
    parser = FakeParser(parser_resp)

    wf = ProcurementWorkflow(guardrail=guard, parser=parser)
    state = wf.start(brief)

    assert state.status == Status.FAILED
    assert state.stage == Stage.FAILED
    assert state.error is not None


def test_no_premature_execution_of_unimplemented_stages():
    brief = "Buy 5 chairs"
    guard = FakeGuardrail(is_procurement=True)
    req = ProcurementRequest(original_brief=brief, category="chair", quantity=5)
    parser_resp = IntentResponse(status="ok", request=req)
    parser = FakeParser(parser_resp)

    wf = ProcurementWorkflow(guardrail=guard, parser=parser)
    state = wf.start(brief)

    # Ensure not executed stages are not present in events
    forbidden = {Stage.DISCOVERY, Stage.COMPARISON, Stage.TCO_ANALYSIS, Stage.AUTHORIZATION, Stage.PURCHASE}
    event_stages = {e.stage for e in state.events}
    assert forbidden.isdisjoint(event_stages)
