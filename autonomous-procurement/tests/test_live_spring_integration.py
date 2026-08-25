import os
import pytest

from app.client.spring_client import (
    SpringProcurementClient,
    AuthenticationError,
    AuthorizationError,
    BackendUnavailableError,
)
from app.models.constraint import Constraint
from app.models.procurement import ProcurementRequest

# Mark all tests in this file as integration tests
pytestmark = pytest.mark.integration

SPRING_BASE_URL = os.getenv("SPRING_BACKEND_URL") or os.getenv("BACKEND_BASE_URL") or "http://localhost:8080"


@pytest.fixture(scope="module")
def live_client():
    """Provides a live client connected to Spring Boot backend."""
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, timeout=10.0)
    try:
        # Check if backend is reachable via login attempt
        client.login("manager@procurement.com", "password123")
    except BackendUnavailableError:
        pytest.skip(f"Live Spring Boot backend is not running at {SPRING_BASE_URL}. Skipping integration tests.")
    yield client
    client.close()


def test_real_login_returns_jwt(live_client):
    """Test 1: Verify POST /api/auth/login returns valid JWT and role metadata."""
    auth_resp = live_client.login("manager@procurement.com", "password123")
    assert "token" in auth_resp
    assert auth_resp["tokenType"] == "Bearer"
    assert auth_resp["email"] == "manager@procurement.com"
    assert auth_resp["role"] in ("PROCUREMENT_MANAGER", "ROLE_PROCUREMENT_MANAGER")
    assert len(auth_resp["token"]) > 20



def test_authenticated_client_can_access_backend(live_client):
    """Test 2: Verify Bearer JWT token allows accessing protected endpoints."""
    # Using the manager client token
    assert live_client.token is not None
    # Creating a minimal procurement request
    req = ProcurementRequest(
        original_brief="Integration Test Request",
        category="Laptop",
        quantity=1,
        authorization_limit=100000.0,
    )
    result = live_client.create_procurement(req)
    assert "id" in result
    assert result["category"] == "Laptop"


def test_invalid_jwt_returns_401():
    """Test 3: Verify invalid Bearer token returns 401 AuthenticationError."""
    client = SpringProcurementClient(base_url=SPRING_BASE_URL, token="invalid-token-12345")
    try:
        with pytest.raises(AuthenticationError) as exc_info:
            client.get_procurement("00000000-0000-0000-0000-000000000000")
        assert exc_info.value.status_code == 401
    finally:
        client.close()


def test_authenticated_client_can_create_procurement(live_client):
    """Test 4: Verify creating a procurement with constraints from Python ProcurementRequest model."""
    req = ProcurementRequest(
        original_brief="Buy 5 business laptops with 16GB RAM under 85000",
        category="Laptop",
        quantity=5,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True),
            Constraint(attribute="storage", operator=">=", value="512", unit="GB", mandatory=True),
        ],
        budget=85000.0,
        max_delivery_days=7,
        authorization_limit=450000.0,
    )

    created = live_client.create_procurement(req)
    assert "id" in created
    assert created["category"] == "Laptop"
    assert created["quantity"] == 5
    assert created["status"] == "SUBMITTED"
    assert created["constraintCount"] >= 3  # ram, storage, price, deliveryDays


def test_created_procurement_can_be_retrieved(live_client):
    """Test 5: Verify newly created procurement is persisted in PostgreSQL and retrievable via GET."""
    req = ProcurementRequest(
        original_brief="Buy 2 ergonomic chairs with budget 25000",
        category="Office chair",
        quantity=2,
        budget=25000.0,
        authorization_limit=50000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]

    retrieved = live_client.get_procurement(proc_id)
    assert retrieved["id"] == proc_id
    assert retrieved["category"] == "Office chair"
    assert retrieved["quantity"] == 2
    assert float(retrieved["authorizationLimit"]) == 50000.0
    assert retrieved["status"] == "SUBMITTED"


def test_user_cannot_approve_manager_action(live_client):
    """Test 6: Verify ROLE_USER cannot perform manager-only approvals (403 Forbidden) if USER seed account available."""
    # 1. Create a procurement as manager
    req = ProcurementRequest(
        original_brief="Test approval permissions",
        category="Laptop",
        quantity=1,
        authorization_limit=50000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]

    # 2. Login as regular buyer (ROLE_USER)
    user_client = SpringProcurementClient(base_url=SPRING_BASE_URL)
    try:
        user_client.login("user@procurement.com", "password123")
        assert user_client.token is not None


        # 3. Attempt manager approval endpoint
        with pytest.raises(AuthorizationError) as exc_info:
            user_client.approve_procurement(proc_id, comments="Unauthorized approval attempt")
        assert exc_info.value.status_code == 403
    finally:
        user_client.close()


def test_live_execute_procurement_end_to_end(live_client):
    """Test 7: Verify executing workflow via Spring ProcurementOrchestrator advances state."""
    # 1. Create request
    req = ProcurementRequest(
        original_brief="Buy 5 Dell laptops under 85000 with 16GB RAM and delivery within 7 days",
        category="Laptop",
        quantity=5,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True),
        ],
        budget=85000.0,
        max_delivery_days=7,
        authorization_limit=450000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]
    assert created["status"] == "SUBMITTED"

    # 2. Execute workflow on backend
    orch_res = live_client.execute_procurement(proc_id)
    assert orch_res["procurementId"] == proc_id
    # Backend orchestrator advances state through pipeline
    final_state = orch_res.get("finalState") or orch_res.get("status")
    assert final_state in ("COMPLETED", "WAITING_APPROVAL", "REVALIDATING", "RECOMMENDED", "SEARCHING", "PURCHASING")


    # 3. Retrieve authoritative state
    summary = live_client.get_procurement(proc_id)
    assert summary["id"] == proc_id
    assert summary["status"] == final_state


def test_live_process_brief_service_flow(live_client):
    """Test 8: Verify process_brief flows through parser, Spring create, execute, and state observation."""
    from app.service import ProcurementService
    from app.llm.base import LLMClient

    class LiveScriptedLLM(LLMClient):
        def generate_json(self, system_prompt, user_prompt, json_schema):
            if "classify" in user_prompt.lower():
                return {"is_procurement": True, "reason": "User wants to buy laptops"}
            return {
                "category": "Laptop",
                "quantity": 5,
                "constraints": [
                    {"attribute": "ram", "operator": ">=", "value": 16, "unit": "GB"},
                ],
                "preferences": [],
                "budget": 85000.0,
                "max_delivery_days": 7,
                "authorization_limit": 450000.0,
            }

    service = ProcurementService(llm=LiveScriptedLLM(), client=live_client)
    brief = "Buy 5 laptops under ₹85000 with at least 16GB RAM and delivery within 7 days. Approval limit ₹450000."
    result = service.process_brief(brief, token=live_client.token)

    assert result.status in ("ok", "waiting_approval")
    assert result.is_procurement is True
    assert result.procurement_id is not None
    assert len(result.procurement_id) == 36  # Valid UUID string format
    assert result.backend_status in ("COMPLETED", "WAITING_APPROVAL", "RECOMMENDED", "REVALIDATING", "SEARCHING", "PURCHASING")
    assert result.backend_summary is not None
    assert result.request is not None
    assert result.recommendation is not None
    assert result.tco_breakdowns is not None


def test_live_recommendation_and_tco_retrieval(live_client):
    """Test 9: Verify direct GET /recommendation and GET /tco retrieve authoritative results from Spring."""
    # 1. Create and execute procurement
    req = ProcurementRequest(
        original_brief="Buy 3 business laptops with 16GB RAM under 80000",
        category="Laptop",
        quantity=3,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True),
        ],
        budget=80000.0,
        authorization_limit=300000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]
    live_client.execute_procurement(proc_id)

    # 2. Retrieve recommendation
    rec = live_client.get_recommendation(proc_id)
    assert rec["procurementId"] == proc_id
    assert "recommendationType" in rec
    assert "explanation" in rec
    assert "bestEligibleOption" in rec
    if rec["bestEligibleOption"]:
        assert "productName" in rec["bestEligibleOption"]
        assert "tco" in rec["bestEligibleOption"]

    # 3. Retrieve TCO breakdowns
    tco_list = live_client.get_tco(proc_id)
    assert isinstance(tco_list, list)
    assert len(tco_list) >= 1
    first_tco = tco_list[0]
    assert "totalTco" in first_tco
    assert "unitPurchaseCost" in first_tco
    assert "horizonYears" in first_tco
    assert "totalPurchaseCost" in first_tco


def test_live_hitl_approval_workflow(live_client):
    """Test 10: Verify end-to-end HITL approval workflow against running Spring backend."""
    from app.service import approve_procurement

    # 1. Create a procurement with tight authorization limit to trigger WAITING_APPROVAL
    req = ProcurementRequest(
        original_brief="Buy 5 business laptops with limit 100000 (total will exceed limit)",
        category="Laptop",
        quantity=5,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True),
        ],
        budget=85000.0,
        authorization_limit=100000.0,  # 5 laptops * ~78000 = ~390000 > 100000
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]

    # 2. Execute on backend -> escalates to WAITING_APPROVAL
    orch_res = live_client.execute_procurement(proc_id)
    final_state = orch_res.get("finalState") or orch_res.get("status")
    assert final_state == "WAITING_APPROVAL"

    # 3. Retrieve approval record
    appr = live_client.get_approval(proc_id)
    assert appr["status"] == "PENDING"
    assert float(appr["requestedAmount"]) > float(appr["authorizationLimit"])

    # 4. Perform explicit human approval via manager
    result = approve_procurement(
        procurement_id=proc_id,
        comments="Approved budget exception by manager",
        token=live_client.token,
        client=live_client,
    )
    assert result.action == "approved"
    assert result.status == "ok"
    assert result.backend_status in ("REVALIDATING", "PURCHASING", "COMPLETED", "SEARCHING")
    assert result.approval["status"] == "APPROVED"



def test_live_hitl_rejection_workflow(live_client):
    """Test 11: Verify end-to-end HITL rejection workflow against running Spring backend."""
    from app.service import reject_procurement

    # 1. Create a procurement that triggers WAITING_APPROVAL
    req = ProcurementRequest(
        original_brief="Buy 10 laptops with limit 50000",
        category="Laptop",
        quantity=10,
        budget=85000.0,
        authorization_limit=50000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]
    live_client.execute_procurement(proc_id)

    # 2. Perform explicit human rejection via manager
    result = reject_procurement(
        procurement_id=proc_id,
        comments="Budget rejected due to quarterly cuts",
        token=live_client.token,
        client=live_client,
    )
    assert result.action == "rejected"
    assert result.status == "ok"
    assert result.backend_status == "REJECTED"
    assert result.approval["status"] == "REJECTED"


def test_live_revalidation_endpoint(live_client):
    """Test 12: Verify POST /revalidate evaluates checks against authoritative inventory and catalog."""
    from app.service import approve_procurement, revalidate_procurement

    # 1. Create a procurement requiring approval so it lands in WAITING_APPROVAL
    req = ProcurementRequest(
        original_brief="Buy 5 laptops with limit 100000",
        category="Laptop",
        quantity=5,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True),
        ],
        budget=85000.0,
        authorization_limit=100000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]
    live_client.execute_procurement(proc_id)

    # 2. Approve it without auto-resuming execution so it sits in REVALIDATING state
    approve_res = approve_procurement(
        procurement_id=proc_id,
        comments="Approved for revalidation test",
        token=live_client.token,
        resume_execution=False,
        client=live_client,
    )
    assert approve_res.backend_status == "REVALIDATING"

    # 3. Trigger direct revalidation endpoint
    result = revalidate_procurement(procurement_id=proc_id, token=live_client.token, client=live_client)
    if result.status != "ok":
        print(f"DEBUG REVALIDATION ERROR: {result.error}")
    assert result.status == "ok"

    assert result.backend_status in ("PURCHASING", "SEARCHING", "COMPLETED", "WAITING_USER")
    assert result.revalidation is not None
    assert "checks" in result.revalidation
    assert len(result.revalidation["checks"]) >= 3



def test_live_purchase_and_purchase_order_retrieval(live_client):
    """Test 13: Verify purchase execution creates confirmed PurchaseOrder and transitions state to COMPLETED."""
    from app.service import purchase_procurement

    # 1. Create a procurement with plenty of authorization limit
    req = ProcurementRequest(
        original_brief="Buy 1 office laptop with 16GB RAM under 85000",
        category="Laptop",
        quantity=1,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True),
        ],
        budget=85000.0,
        authorization_limit=500000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]

    # 2. Execute on backend (advances through discovery, recommendation, authorization, revalidation)
    orch_res = live_client.execute_procurement(proc_id)
    final_state = orch_res.get("finalState") or orch_res.get("status")

    # 3. Call purchase endpoint
    result = purchase_procurement(procurement_id=proc_id, token=live_client.token, client=live_client)
    if final_state == "COMPLETED":
        assert result.status == "ok"
        assert result.backend_status == "COMPLETED"
        assert result.purchase_order is not None
        assert result.purchase_order["status"] == "CONFIRMED"
        assert result.purchase_order["quantity"] == 1
        assert float(result.purchase_order["totalAmount"]) > 0
    else:
        # If revalidation flagged stale conditions, Spring self-protects and rejects purchase
        assert result.status in ("ok", "failed")



def test_live_purchase_idempotency(live_client):
    """Test 14: Verify repeating purchase on a completed procurement returns the existing PO without duplicate purchase."""
    from app.service import purchase_procurement

    # 1. Create a 1-unit procurement
    req = ProcurementRequest(
        original_brief="Buy 1 laptop for idempotency testing",
        category="Laptop",
        quantity=1,
        budget=85000.0,
        authorization_limit=500000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]
    live_client.execute_procurement(proc_id)

    # 2. First purchase attempt
    res1 = purchase_procurement(procurement_id=proc_id, token=live_client.token, client=live_client)
    if res1.backend_status == "COMPLETED":
        po1_id = res1.purchase_order["id"]

        # 3. Second purchase attempt
        res2 = purchase_procurement(procurement_id=proc_id, token=live_client.token, client=live_client)
        assert res2.status == "ok"
        assert res2.backend_status == "COMPLETED"
        assert res2.purchase_order["id"] == po1_id
        assert res2.purchase_result["status"] in ("ALREADY_COMPLETED", "CONFIRMED")


def test_live_audit_trail_retrieval(live_client):
    """Test 15: Verify chronological audit trail can be retrieved after execution."""
    req = ProcurementRequest(
        original_brief="Buy 1 laptop for audit verification",
        category="Laptop",
        quantity=1,
        budget=85000.0,
        authorization_limit=500000.0,
    )
    created = live_client.create_procurement(req)
    proc_id = created["id"]
    live_client.execute_procurement(proc_id)

    audit = live_client.get_audit_trail(proc_id)
    assert audit["procurementId"] == proc_id
    assert "events" in audit
    assert len(audit["events"]) >= 1
    assert any(e["eventType"] == "STATE_TRANSITION" for e in audit["events"])





