from app.workflow.state import ProcurementState, WorkflowState
from app.models.procurement import ProcurementRequest
from app.guardrails.domain_guardrail import GuardrailResult
from app.discovery.mock_sources import vendor_alpha_products


def test_workflow_state_basic_fields():
    req = ProcurementRequest(original_brief="Buy 1 chair", category="chair", quantity=1)
    st = ProcurementState(request_id="r1", original_brief=req.original_brief, procurement_request=req)
    assert st.request_id == "r1"
    assert st.procurement_request.category == "chair"
    # add event
    st.add_event(st.current_stage, "started")
    assert len(st.events) == 1


def test_workflow_state_hold_discovery_and_recommendation():
    st = ProcurementState(request_id="r2", original_brief="Buy monitors")
    # attach discovery
    from app.discovery.mock_sources import vendor_alpha_products
    st.discovery_result = type("D", (), {"discovered_products": vendor_alpha_products()})()
    st.normalized_products = vendor_alpha_products()
    assert st.discovery_result is not None
    assert len(st.normalized_products) > 0
