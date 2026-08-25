import pytest

from app.agent.state import ProcurementState, Stage, Status, AuditEvent
from app.models.procurement import ProcurementRequest


def test_procurement_state_basic_fields():
    st = ProcurementState(request_id="r1", original_brief="Buy 2 monitors")
    assert st.stage == Stage.START
    assert st.status == Status.RUNNING

    # human approval fields
    assert hasattr(st, "human_approval_required")
    assert hasattr(st, "human_approval_result")

    # can store a ProcurementRequest
    req = ProcurementRequest(original_brief="Buy 2 monitors", category="monitor", quantity=2)
    st.current_request = req
    assert isinstance(st.current_request, ProcurementRequest)

    # audit events
    st.add_event(Stage.DOMAIN_CHECK, "checked")
    assert isinstance(st.events[-1], AuditEvent)
    assert st.events[-1].stage == Stage.DOMAIN_CHECK

    # statuses and stages are valid enums
    assert Stage.COMPLETED in Stage
    assert Status.FAILED in Status


def test_audit_history_serializable():
    st = ProcurementState(request_id="r2", original_brief="Need chairs")
    st.add_event(Stage.START, "started")
    st.add_event(Stage.DOMAIN_CHECK, "domain ok")
    # ensure events have timestamps
    for ev in st.events:
        assert ev.timestamp.endswith("Z")
