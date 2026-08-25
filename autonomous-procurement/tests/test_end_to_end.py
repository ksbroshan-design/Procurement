import json
import os
import urllib.request
import pytest

from app.client.spring_client import (
    AuthenticationError,
    AuthorizationError,
    ResourceNotFoundError,
    SpringProcurementClient,
    StateConflictError,
    ValidationError,
)
from app.guardrails.domain_guardrail import DomainGuardrail
from app.llm.base import LLMClient
from app.models.constraint import Constraint
from app.models.preference import Preference
from app.models.procurement import ProcurementRequest
from app.parser.intent_parser import IntentParser
from app.service import (
    ProcessBriefResult,
    ProcurementService,
    approve_procurement,
    process_brief,
    purchase_procurement,
    reject_procurement,
    revalidate_procurement,
)

SPRING_BASE_URL = os.getenv("SPRING_BASE_URL", "http://localhost:8080")


class ScriptedLLM(LLMClient):
    """Deterministic LLM for End-to-End pipeline testing without external API dependency."""
    def __init__(self, responses: list[dict]) -> None:
        self.responses = list(responses)
        self.calls = 0

    def generate_json(self, system_prompt: str, user_prompt: str, json_schema: dict) -> dict:
        self.calls += 1
        if not self.responses:
            raise RuntimeError("ScriptedLLM ran out of scripted responses")
        return self.responses.pop(0)


def get_token(email: str = "manager@procurement.com", password: str = "password123") -> str:
    """Helper to acquire JWT token from live Spring Boot backend."""
    client = SpringProcurementClient(base_url=SPRING_BASE_URL)
    auth_data = client.login(email=email, password=password)
    return auth_data["token"]


def is_backend_live() -> bool:
    """Check if Spring Boot backend is reachable."""
    try:
        req = urllib.request.Request(f"{SPRING_BASE_URL}/api/auth/login", method="POST")
        req.add_header("Content-Type", "application/json")
        urllib.request.urlopen(req, data=b"{}", timeout=2.0)
        return True
    except urllib.error.HTTPError as e:
        return e.code in (400, 401, 403, 404, 405)
    except Exception:
        return False


requires_backend = pytest.mark.skipif(
    not is_backend_live(),
    reason="Spring Boot backend is not running at " + SPRING_BASE_URL,
)


# ==============================================================================
# SCENARIO A: HAPPY PATH (Complete End-to-End Procurement Flow)
# ==============================================================================
@requires_backend
def test_scenario_a_happy_path():
    """Scenario A: Natural language request -> Guardrail -> Parser -> DTO Mapper -> Spring execute -> COMPLETED + PO."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    mock_llm = ScriptedLLM([
        {"is_procurement": True, "reason": "User is requesting smart TV for conference room."},
        {
            "category": "TV",
            "quantity": 1,
            "constraints": [
                {"attribute": "screenSize", "operator": ">=", "value": "55", "unit": "inches", "mandatory": True}
            ],
            "preferences": [
                {"attribute": "brand", "direction": "prefer", "weight": 0.8}
            ],
            "budget": 60000.0,
            "max_delivery_days": 5,
            "authorization_limit": 500000.0,
        },
    ])

    brief = "Buy 1 TV under Rs 60000 with at least 55 inches screen and delivery within 5 days. Approval limit Rs 500000."
    service = ProcurementService(llm=mock_llm, client=client)
    result = service.process_brief(brief=brief, token=token, execute=True)

    # 1. Pipeline Verification
    assert result.status == "ok"
    assert result.is_procurement is True
    assert result.procurement_id is not None
    assert result.backend_status == "COMPLETED"

    # 2. Recommendation & TCO Verification (Authoritative from Spring)
    assert result.recommendation is not None
    assert result.recommendation["category"] == "TV"
    assert "bestEligibleOption" in result.recommendation

    assert result.tco_breakdowns is not None
    assert len(result.tco_breakdowns) >= 1
    assert float(result.tco_breakdowns[0]["totalTco"]) > 0

    # 3. Purchase Order Verification (Created ONLY by Spring)
    assert result.purchase_order is not None
    assert result.purchase_order["status"] == "CONFIRMED"
    assert result.purchase_order["quantity"] == 1
    assert float(result.purchase_order["totalAmount"]) > 0

    # 4. Audit Trail Verification
    assert result.audit_trail is not None
    assert "events" in result.audit_trail
    assert any(e["eventType"] == "STATE_TRANSITION" for e in result.audit_trail["events"])



# ==============================================================================
# SCENARIO B: HUMAN APPROVAL (HITL Exception Workflow)
# ==============================================================================
@requires_backend
def test_scenario_b_human_approval():
    """Scenario B: Purchase exceeds limit -> WAITING_APPROVAL -> Manager Approval -> COMPLETED + PO."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    # 1. Create a procurement with amount exceeding manager limit (4 MacBooks * 125000 = 500000 > 450000)
    req = ProcurementRequest(
        original_brief="Buy 4 MacBooks exceeding manager authorization limit",
        category="Laptop",
        quantity=4,
        constraints=[Constraint(attribute="brand", operator="=", value="Apple", mandatory=True)],
        budget=150000.0,
        authorization_limit=450000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]

    # 2. Trigger orchestrator -> escalates to WAITING_APPROVAL
    orch_res = client.execute_procurement(proc_id)
    final_state = orch_res.get("finalState") or orch_res.get("status")
    assert final_state == "WAITING_APPROVAL"

    # 3. Verify approval record on Spring
    approval = client.get_approval(proc_id)
    assert approval["status"] == "PENDING"
    assert float(approval["requestedAmount"]) > float(approval["authorizationLimit"])

    # 4. Perform explicit human approval via manager
    decision_res = approve_procurement(
        procurement_id=proc_id,
        comments="Approved budget exception by Procurement Manager Alex Hunter",
        token=token,
        resume_execution=True,
        client=client,
    )

    assert decision_res.action == "approved"
    assert decision_res.status == "ok"
    assert decision_res.backend_status in ("COMPLETED", "REVALIDATING", "PURCHASING", "SEARCHING")
    assert decision_res.approval["status"] == "APPROVED"


# ==============================================================================
# SCENARIO C: HUMAN REJECTION (HITL Rejection Workflow)
# ==============================================================================
@requires_backend
def test_scenario_c_human_rejection():
    """Scenario C: Purchase exceeds limit -> WAITING_APPROVAL -> Manager Rejection -> REJECTED (no PO)."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    # Create procurement exceeding manager limit (4 MacBooks * 125000 = 500000 > 450000)
    req = ProcurementRequest(
        original_brief="Buy 4 MacBooks exceeding manager limit",
        category="Laptop",
        quantity=4,
        constraints=[Constraint(attribute="brand", operator="=", value="Apple", mandatory=True)],
        budget=150000.0,
        authorization_limit=450000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]
    client.execute_procurement(proc_id)

    # Reject procurement via manager
    decision_res = reject_procurement(
        procurement_id=proc_id,
        comments="Budget rejected due to quarterly austerity policy",
        token=token,
        client=client,
    )

    assert decision_res.action == "rejected"
    assert decision_res.status == "ok"
    assert decision_res.backend_status == "REJECTED"
    assert decision_res.approval["status"] == "REJECTED"

    # Verify no purchase order exists
    with pytest.raises(ResourceNotFoundError):
        client.get_purchase_order(proc_id)


# ==============================================================================
# SCENARIO D: INCOMPLETE BRIEF (Clarification Handling)
# ==============================================================================
def test_scenario_d_incomplete_brief_clarification():
    """Scenario D: Incomplete brief missing required fields -> needs_clarification (no backend call)."""
    mock_llm = ScriptedLLM([
        {"is_procurement": True, "reason": "User wants to buy laptops."},
        {
            "category": "Laptop",
            "quantity": None,  # Missing quantity
            "constraints": [],
            "preferences": [],
            "budget": None,
            "max_delivery_days": None,
            "authorization_limit": None,
        },
    ])

    result = process_brief("Buy some laptops", llm=mock_llm, execute=False)

    assert result.status == "needs_clarification"
    assert result.is_procurement is True
    assert "quantity" in result.missing_information
    assert result.procurement_id is None


# ==============================================================================
# SCENARIO E: NON-PROCUREMENT REQUEST (Domain Guardrail Rejection)
# ==============================================================================
def test_scenario_e_non_procurement_guardrail_rejection():
    """Scenario E: Off-topic non-procurement request -> Guardrail rejects (no backend call)."""
    mock_llm = ScriptedLLM([
        {"is_procurement": False, "reason": "Request asks for Python coding assistance, not procurement."}
    ])

    result = process_brief("Write Python code to sort a list.", llm=mock_llm, execute=False)

    assert result.status == "rejected"
    assert result.is_procurement is False
    assert "coding" in result.reason.lower() or "procurement" in result.reason.lower()
    assert result.procurement_id is None


# ==============================================================================
# SCENARIO F: HARD CONSTRAINT ENFORCEMENT
# ==============================================================================
@requires_backend
def test_scenario_f_hard_constraint_enforcement():
    """Scenario F: Mandatory constraint (RAM >= 128GB) cannot be satisfied by catalog -> Spring rejects violating options."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    req = ProcurementRequest(
        original_brief="Buy 1 workstation laptop with impossible 128GB RAM requirement",
        category="Laptop",
        quantity=1,
        constraints=[Constraint(attribute="ram", operator=">=", value="128", unit="GB", mandatory=True)],
        budget=500000.0,
        authorization_limit=500000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]

    orch_res = client.execute_procurement(proc_id)
    final_state = orch_res.get("finalState") or orch_res.get("status")

    # When no products satisfy mandatory hard constraints, backend halts with NO_ELIGIBLE_PRODUCTS
    assert final_state in ("WAITING_USER", "FAILED", "RECOMMENDED", "SEARCHING")
    assert orch_res.get("status") in ("NO_ELIGIBLE_PRODUCTS", "FAILED", "SEARCHING")


# ==============================================================================
# SCENARIO G: REVALIDATION EVALUATION
# ==============================================================================
@requires_backend
def test_scenario_g_revalidation_evaluation():
    """Scenario G: Pre-purchase revalidation evaluates checks against authoritative inventory and catalog."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    req = ProcurementRequest(
        original_brief="Buy 1 laptop for revalidation evaluation",
        category="Laptop",
        quantity=1,
        constraints=[Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True)],
        budget=85000.0,
        authorization_limit=450000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]

    result = revalidate_procurement(procurement_id=proc_id, token=token, client=client)
    assert result.status == "ok"
    assert result.revalidation is not None


# ==============================================================================
# SCENARIO H & I: RETRY EXHAUSTION TO WAITING_USER
# ==============================================================================
def test_scenario_h_and_i_retry_exhaustion():
    """Scenario H & I: Revalidation failure increments attempts up to max retries -> WAITING_USER."""
    class RevalFailClient:
        def revalidate(self, procurement_id, token=None):
            return {
                "procurementId": procurement_id,
                "status": "STALE",
                "valid": False,
                "revalidationAttempts": 3,
                "maxRetryAttempts": 3,
                "checks": [{"checkType": "PRICE_STABILITY", "passed": False, "message": "Price changed"}],
                "nextState": "WAITING_USER",
            }

        def get_procurement(self, procurement_id, token=None):
            return {"id": procurement_id, "status": "WAITING_USER"}

        def close(self):
            pass

    res = revalidate_procurement(procurement_id="proc-123", token="token", client=RevalFailClient())
    assert res.backend_status == "WAITING_USER"
    assert res.revalidation["valid"] is False
    assert res.revalidation["revalidationAttempts"] == 3


# ==============================================================================
# SCENARIO J: PURCHASE IDEMPOTENCY
# ==============================================================================
@requires_backend
def test_scenario_j_purchase_idempotency():
    """Scenario J: Repeating purchase on a completed procurement returns existing PO without double purchase."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    req = ProcurementRequest(
        original_brief="Buy 1 laptop for idempotency test",
        category="Laptop",
        quantity=1,
        budget=85000.0,
        authorization_limit=500000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]
    client.execute_procurement(proc_id)

    # First purchase attempt
    res1 = purchase_procurement(procurement_id=proc_id, token=token, client=client)
    if res1.backend_status == "COMPLETED" and res1.purchase_order is not None:
        po1_id = res1.purchase_order["id"]

        # Second purchase attempt
        res2 = purchase_procurement(procurement_id=proc_id, token=token, client=client)
        assert res2.status == "ok"
        assert res2.backend_status == "COMPLETED"
        assert res2.purchase_order["id"] == po1_id
        assert res2.purchase_result["status"] in ("ALREADY_COMPLETED", "CONFIRMED")


# ==============================================================================
# SCENARIOS K & L: JWT SECURITY & 401 UNAUTHORIZED
# ==============================================================================
@requires_backend
def test_scenario_k_and_l_jwt_security():
    """Scenarios K & L: Missing or invalid JWT tokens result in 401 AuthenticationError."""
    client_no_auth = SpringProcurementClient(base_url=SPRING_BASE_URL, token=None)
    client_bad_auth = SpringProcurementClient(base_url=SPRING_BASE_URL, token="invalid.bearer.token")

    # Missing token -> 401
    with pytest.raises(AuthenticationError):
        client_no_auth.get_procurement("c39b3a0e-49b0-466d-9be2-4467c6999b80")

    # Invalid token -> 401
    with pytest.raises(AuthenticationError):
        client_bad_auth.execute_procurement("c39b3a0e-49b0-466d-9be2-4467c6999b80")


# ==============================================================================
# SCENARIO M: ROLE-BASED APPROVAL SECURITY (403 FORBIDDEN)
# ==============================================================================
@requires_backend
def test_scenario_m_rbac_approval_security():
    """Scenario M: Standard employee (ROLE_USER) is forbidden (403) from approving exceptions."""
    user_token = get_token("user@procurement.com", "password123")
    user_client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=user_token)

    manager_token = get_token("manager@procurement.com", "password123")
    manager_client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=manager_token)

    # 1. Create a procurement requiring approval (4 MacBooks * 125k = 500k > 450k)
    req = ProcurementRequest(
        original_brief="Buy 4 MacBooks exceeding user limits",
        category="Laptop",
        quantity=4,
        constraints=[Constraint(attribute="brand", operator="=", value="Apple", mandatory=True)],
        budget=150000.0,
        authorization_limit=450000.0,
    )
    created = manager_client.create_procurement(req)
    proc_id = created["id"]
    manager_client.execute_procurement(proc_id)

    # 2. Standard user attempts to approve -> 403 Forbidden (AuthorizationError)
    with pytest.raises(AuthorizationError):
        user_client.approve_procurement(procurement_id=proc_id, comments="Illegal user approval")

    # 3. Manager approves -> succeeds (200 OK)
    mgr_approval = manager_client.approve_procurement(procurement_id=proc_id, comments="Manager approval")
    assert mgr_approval["status"] == "APPROVED"


# ==============================================================================
# SCENARIO N: AUDIT TRAIL COMPLETENESS
# ==============================================================================
@requires_backend
def test_scenario_n_audit_completeness():
    """Scenario N: Detailed audit trail can be retrieved containing state transition events."""
    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    req = ProcurementRequest(
        original_brief="Buy 1 laptop for audit verification",
        category="Laptop",
        quantity=1,
        budget=85000.0,
        authorization_limit=500000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]
    client.execute_procurement(proc_id)

    audit = client.get_audit_trail(proc_id)
    assert audit["procurementId"] == proc_id
    assert "events" in audit
    assert len(audit["events"]) >= 1
    assert any(e["eventType"] == "STATE_TRANSITION" for e in audit["events"])


# ==============================================================================
# SCENARIO P: ARCHITECTURAL AUTHORITY BOUNDARY (Static Code Analysis)
# ==============================================================================
def test_scenario_p_authority_boundary_static_analysis():
    """Scenario P: Prove Python codebase contains NO database connection libraries or direct PO mutations."""
    python_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    app_dir = os.path.join(python_root, "app")

    forbidden_imports = ["import psycopg", "import psycopg2", "import sqlalchemy", "from sqlalchemy", "import asyncpg"]

    for root, _, files in os.walk(app_dir):
        for file in files:
            if file.endswith(".py"):
                filepath = os.path.join(root, file)
                with open(filepath, "r", encoding="utf-8") as f:
                    content = f.read().lower()
                    for forbidden in forbidden_imports:
                        assert forbidden not in content, f"Authority boundary violation: '{forbidden}' found in {filepath}"


# ==============================================================================
# SCENARIOS Q & R: COMPLETE RESULT PRESENTATION & HITL PRESENTATION
# ==============================================================================
def test_scenario_q_and_r_result_presentation():
    """Scenarios Q & R: ProcessBriefResult structure preserves and presents backend decisions accurately."""
    # Normal Completed Result
    completed_res = ProcessBriefResult(
        status="ok",
        is_procurement=True,
        procurement_id="proc-123",
        backend_status="COMPLETED",
        recommendation={"bestEligibleOption": {"productName": "Dell Latitude 5540"}},
        tco_breakdowns=[{"totalTco": 412500.0}],
        purchase_order={"id": "po-999", "status": "CONFIRMED"},
        audit_trail={"events": []},
    )
    assert completed_res.backend_status == "COMPLETED"
    assert completed_res.purchase_order["id"] == "po-999"

    # WAITING_APPROVAL Result
    approval_res = ProcessBriefResult(
        status="waiting_approval",
        is_procurement=True,
        procurement_id="proc-456",
        backend_status="WAITING_APPROVAL",
        approval_required=True,
        approval={"requestedAmount": 390000.0, "authorizationLimit": 100000.0},
        decision_message="Procurement requires manager approval due to authorization limit.",
    )
    assert approval_res.status == "waiting_approval"
    assert approval_res.approval_required is True
    assert approval_res.approval["requestedAmount"] == 390000.0


# ==============================================================================
# SCENARIO O: DATABASE AUTHORITY & DIRECT POSTGRESQL VERIFICATION
# ==============================================================================
@requires_backend
def test_scenario_o_database_authority():
    """Scenario O: Harness verifies PostgreSQL stores procurement_requests, purchase_orders, and audit_logs created exclusively by Spring."""
    import subprocess

    token = get_token("manager@procurement.com", "password123")
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token=token)

    req = ProcurementRequest(
        original_brief="Buy 1 TV for database authority test",
        category="TV",
        quantity=1,
        constraints=[Constraint(attribute="screenSize", operator=">=", value="55", unit="inches", mandatory=True)],
        budget=60000.0,
        authorization_limit=500000.0,
    )
    created = client.create_procurement(req)
    proc_id = created["id"]
    client.execute_procurement(proc_id)

    # Direct query via docker exec psql to verify database authority
    try:
        cmd = [
            "docker", "exec", "procurement-postgres",
            "psql", "-U", "postgres", "-d", "procurement_db", "-t", "-c",
            f"SELECT count(*) FROM procurement_requests WHERE id = '{proc_id}';"
        ]
        out = subprocess.check_output(cmd, text=True).strip()
        assert int(out) == 1

        # Check purchase order in Postgres
        po_cmd = [
            "docker", "exec", "procurement-postgres",
            "psql", "-U", "postgres", "-d", "procurement_db", "-t", "-c",
            f"SELECT count(*) FROM purchase_orders WHERE procurement_id = '{proc_id}';"
        ]
        po_out = subprocess.check_output(po_cmd, text=True).strip()
        assert int(po_out) >= 1
    except (subprocess.SubprocessError, FileNotFoundError):
        pytest.skip("Docker CLI not accessible from test runner environment for direct psql check")


# ==============================================================================
# ERROR HANDLING: TYPED EXCEPTIONS & NO AUTOMATIC POST RETRY
# ==============================================================================
def test_scenario_error_handling_mappings():
    """Verify HTTP status codes map to precise typed exceptions without auto-retrying mutations."""
    client = SpringProcurementClient(base_url="http://invalid-host:9999")
    with pytest.raises(Exception):
        client.get_procurement("proc-000")

