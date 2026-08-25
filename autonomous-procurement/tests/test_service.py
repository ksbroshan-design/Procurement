import pytest

from app.client.spring_client import (
    AuthenticationError,
    AuthorizationError,
    BackendUnavailableError,
    ResourceNotFoundError,
    StateConflictError,
    ValidationError,
)
from app.llm.base import LLMClient
from app.service import (
    ApprovalDecisionResult,
    ProcurementService,
    PurchaseExecutionResult,
    RevalidationDecisionResult,
    approve_procurement,
    process_brief,
    purchase_procurement,
    reject_procurement,
    revalidate_procurement,
)


class ScriptedLLM(LLMClient):
    """Mock LLM returning sequential payloads for testing."""
    def __init__(self, payloads: list[dict]) -> None:
        self.payloads = list(payloads)
        self.calls = 0

    def generate_json(self, system_prompt: str, user_prompt: str, json_schema: dict) -> dict:
        self.calls += 1
        if not self.payloads:
            raise RuntimeError("ScriptedLLM ran out of payloads")
        return self.payloads.pop(0)


class FakeSpringClient:
    """Mock SpringProcurementClient recording all method calls."""
    def __init__(
        self,
        create_response: dict | None = None,
        execute_response: dict | None = None,
        get_response: dict | None = None,
        recommendation_response: dict | None = None,
        tco_response: list[dict] | None = None,
        approval_response: dict | None = None,
        approve_response: dict | None = None,
        reject_response: dict | None = None,
        revalidate_response: dict | None = None,
        purchase_response: dict | None = None,
        po_response: dict | None = None,
        audit_response: dict | None = None,
        raise_on_create: Exception | None = None,
        raise_on_execute: Exception | None = None,
        raise_on_get: Exception | None = None,
        raise_on_rec: Exception | None = None,
        raise_on_tco: Exception | None = None,
        raise_on_approve: Exception | None = None,
        raise_on_reject: Exception | None = None,
        raise_on_revalidate: Exception | None = None,
        raise_on_purchase: Exception | None = None,
    ) -> None:
        self.create_response = create_response or {
            "id": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "category": "Laptop",
            "quantity": 5,
            "status": "SUBMITTED",
            "constraintCount": 3,
        }
        self.execute_response = execute_response or {
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "initialState": "SUBMITTED",
            "finalState": "COMPLETED",
            "status": "COMPLETED",
            "decisionMessage": "Purchase order created",
            "purchaseOrderId": "po-123",
            "totalAmount": 390000.0,
        }
        self.get_response = get_response or {
            "id": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "category": "Laptop",
            "quantity": 5,
            "status": "COMPLETED",
        }
        self.recommendation_response = recommendation_response or {
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "category": "Laptop",
            "recommendationType": "STANDARD_RECOMMENDED",
            "bestEligibleOption": {
                "productName": "Dell Latitude 5540 Business Laptop",
                "vendorName": "TechDirect Enterprises",
                "price": 390000.0,
                "tco": 412500.0,
                "totalScore": 92.45,
            },
            "bestExceptionOption": None,
            "explanation": "Top-ranked eligible offer provides optimal TCO and 3-year warranty.",
            "tradeOffs": [],
        }
        self.tco_response = tco_response if tco_response is not None else [
            {
                "productName": "Dell Latitude 5540 Business Laptop",
                "vendorName": "TechDirect Enterprises",
                "totalPurchaseCost": 390000.0,
                "totalMaintenanceCost": 15000.0,
                "totalExpectedRepairCost": 7500.0,
                "totalTco": 412500.0,
                "horizonYears": 3,
                "assumptions": ["3-year horizon", "Standard enterprise failure rate 2.5%"],
            }
        ]
        self.approval_response = approval_response or {
            "approvalId": "appr-001",
            "status": "PENDING",
            "requestedAmount": 500000.0,
            "authorizationLimit": 450000.0,
            "difference": 50000.0,
            "reason": "Limit exceeded",
        }
        self.approve_response = approve_response or {
            "approvalId": "appr-001",
            "status": "APPROVED",
            "comments": "Approved by manager",
            "decidedByName": "Manager User",
        }
        self.reject_response = reject_response or {
            "approvalId": "appr-001",
            "status": "REJECTED",
            "comments": "Budget constraints exceeded",
            "decidedByName": "Manager User",
        }
        self.revalidate_response = revalidate_response or {
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "status": "VALID",
            "valid": True,
            "revalidationAttempts": 0,
            "maxRetryAttempts": 3,
            "checks": [
                {"checkType": "VENDOR_STATUS", "passed": True, "message": "Vendor active"},
                {"checkType": "INVENTORY", "passed": True, "message": "Stock available"},
                {"checkType": "PRICE_STABILITY", "passed": True, "message": "Price verified"},
            ],
            "nextState": "PURCHASING",
        }
        self.purchase_response = purchase_response or {
            "purchaseOrderId": "po-123",
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "vendorName": "TechDirect Enterprises",
            "productName": "Dell Latitude 5540",
            "quantity": 5,
            "unitPrice": 78000.0,
            "totalAmount": 390000.0,
            "status": "CONFIRMED",
            "message": "Purchase order successfully placed and confirmed with vendor.",
            "procurementStatus": "COMPLETED",
        }
        self.po_response = po_response or {
            "id": "po-123",
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "vendorName": "TechDirect Enterprises",
            "productName": "Dell Latitude 5540",
            "totalAmount": 390000.0,
            "status": "CONFIRMED",
        }
        self.audit_response = audit_response or {
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "events": [{"eventType": "STATE_TRANSITION", "state": "COMPLETED"}],
        }

        self.raise_on_create = raise_on_create
        self.raise_on_execute = raise_on_execute
        self.raise_on_get = raise_on_get
        self.raise_on_rec = raise_on_rec
        self.raise_on_tco = raise_on_tco
        self.raise_on_approve = raise_on_approve
        self.raise_on_reject = raise_on_reject
        self.raise_on_revalidate = raise_on_revalidate
        self.raise_on_purchase = raise_on_purchase

        self.calls = []

    def create_procurement(self, request, token=None):
        self.calls.append({"method": "create_procurement", "request": request, "token": token})
        if self.raise_on_create:
            raise self.raise_on_create
        return self.create_response

    def execute_procurement(self, procurement_id, token=None):
        self.calls.append({"method": "execute_procurement", "procurement_id": procurement_id, "token": token})
        if self.raise_on_execute:
            raise self.raise_on_execute
        return self.execute_response

    def get_procurement(self, procurement_id, token=None):
        self.calls.append({"method": "get_procurement", "procurement_id": procurement_id, "token": token})
        if self.raise_on_get:
            raise self.raise_on_get
        return self.get_response

    def get_recommendation(self, procurement_id, token=None):
        self.calls.append({"method": "get_recommendation", "procurement_id": procurement_id, "token": token})
        if self.raise_on_rec:
            raise self.raise_on_rec
        return self.recommendation_response

    def get_tco(self, procurement_id, token=None):
        self.calls.append({"method": "get_tco", "procurement_id": procurement_id, "token": token})
        if self.raise_on_tco:
            raise self.raise_on_tco
        return self.tco_response

    def get_approval(self, procurement_id, token=None):
        self.calls.append({"method": "get_approval", "procurement_id": procurement_id, "token": token})
        return self.approval_response

    def approve_procurement(self, procurement_id, comments=None, approved_offer_id=None, token=None):
        self.calls.append({
            "method": "approve_procurement",
            "procurement_id": procurement_id,
            "comments": comments,
            "approved_offer_id": approved_offer_id,
            "token": token,
        })
        if self.raise_on_approve:
            raise self.raise_on_approve
        return self.approve_response

    def reject_procurement(self, procurement_id, comments=None, token=None):
        self.calls.append({
            "method": "reject_procurement",
            "procurement_id": procurement_id,
            "comments": comments,
            "token": token,
        })
        if self.raise_on_reject:
            raise self.raise_on_reject
        return self.reject_response

    def revalidate(self, procurement_id, token=None):
        self.calls.append({"method": "revalidate", "procurement_id": procurement_id, "token": token})
        if self.raise_on_revalidate:
            raise self.raise_on_revalidate
        return self.revalidate_response

    def get_revalidation(self, procurement_id, token=None):
        self.calls.append({"method": "get_revalidation", "procurement_id": procurement_id, "token": token})
        return self.revalidate_response

    def purchase(self, procurement_id, token=None):
        self.calls.append({"method": "purchase", "procurement_id": procurement_id, "token": token})
        if self.raise_on_purchase:
            raise self.raise_on_purchase
        return self.purchase_response

    def get_purchase_order(self, procurement_id, token=None):
        self.calls.append({"method": "get_purchase_order", "procurement_id": procurement_id, "token": token})
        return self.po_response

    def get_audit_trail(self, procurement_id, token=None):
        self.calls.append({"method": "get_audit_trail", "procurement_id": procurement_id, "token": token})
        return self.audit_response

    def close(self):
        pass


def test_process_brief_guardrail_rejection():
    mock_llm = ScriptedLLM([
        {"is_procurement": False, "reason": "The user requested Python coding help, not procurement."}
    ])
    spring_client = FakeSpringClient()

    result = process_brief("Write Python code to sort a list", token="jwt-123", llm=mock_llm, client=spring_client)

    assert result.status == "rejected"
    assert result.is_procurement is False
    assert len(spring_client.calls) == 0


def test_process_brief_incomplete_brief():
    mock_llm = ScriptedLLM([
        {"is_procurement": True, "reason": "User wants to buy laptops."},
        {
            "category": "laptop",
            "quantity": None,
            "constraints": [],
            "preferences": [],
            "budget": None,
            "max_delivery_days": None,
            "authorization_limit": None,
        }
    ])
    spring_client = FakeSpringClient()

    result = process_brief("Buy some laptops", token="jwt-123", llm=mock_llm, client=spring_client)

    assert result.status == "needs_clarification"
    assert result.is_procurement is True
    assert "quantity" in result.missing_information
    assert len(spring_client.calls) == 0


def test_revalidation_request_forwards_jwt_and_preserves_valid_result():
    """Step 10 Tests 1, 2, 3: Revalidation request forwards JWT and preserves Spring result."""
    spring_client = FakeSpringClient(
        get_response={"id": "c39b3a0e-49b0-466d-9be2-4467c6999b80", "status": "PURCHASING"}
    )

    result = revalidate_procurement(
        procurement_id="c39b3a0e-49b0-466d-9be2-4467c6999b80",
        token="jwt-token-reval",
        client=spring_client,
    )

    assert result.status == "ok"
    assert result.backend_status == "PURCHASING"
    assert result.revalidation["valid"] is True
    assert result.revalidation["status"] == "VALID"
    assert len(result.revalidation["checks"]) == 3

    reval_calls = [c for c in spring_client.calls if c["method"] == "revalidate"]
    assert len(reval_calls) == 1
    assert reval_calls[0]["token"] == "jwt-token-reval"


def test_stale_revalidation_result_is_preserved():
    """Step 10 Test 4: Stale revalidation result is preserved with failure checks."""
    spring_client = FakeSpringClient(
        revalidate_response={
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "status": "STALE",
            "valid": False,
            "revalidationAttempts": 1,
            "maxRetryAttempts": 3,
            "checks": [
                {"checkType": "PRICE_STABILITY", "passed": False, "message": "Price increased"},
            ],
            "nextState": "SEARCHING",
        },
        get_response={"id": "c39b3a0e-49b0-466d-9be2-4467c6999b80", "status": "SEARCHING"},
    )

    result = revalidate_procurement(
        procurement_id="c39b3a0e-49b0-466d-9be2-4467c6999b80",
        token="jwt-token",
        client=spring_client,
    )

    assert result.status == "ok"
    assert result.backend_status == "SEARCHING"
    assert result.revalidation["valid"] is False
    assert result.revalidation["status"] == "STALE"


def test_waiting_user_surfaced_after_exhausted_retries():
    """Step 10 Test 5: WAITING_USER is surfaced when revalidation attempts are exhausted."""
    spring_client = FakeSpringClient(
        revalidate_response={
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "status": "STALE",
            "valid": False,
            "revalidationAttempts": 3,
            "maxRetryAttempts": 3,
            "checks": [{"checkType": "INVENTORY", "passed": False, "message": "Stock exhausted"}],
            "nextState": "WAITING_USER",
        },
        get_response={"id": "c39b3a0e-49b0-466d-9be2-4467c6999b80", "status": "WAITING_USER"},
    )

    result = revalidate_procurement(
        procurement_id="c39b3a0e-49b0-466d-9be2-4467c6999b80",
        token="jwt-token",
        client=spring_client,
    )

    assert result.backend_status == "WAITING_USER"
    assert result.revalidation["nextState"] == "WAITING_USER"


def test_revalidation_error_propagated():
    """Step 10 Test 6: Revalidation error is propagated cleanly."""
    spring_client = FakeSpringClient(
        raise_on_revalidate=ResourceNotFoundError("Procurement not found", status_code=404)
    )

    result = revalidate_procurement(
        procurement_id="nonexistent-id",
        token="jwt-token",
        client=spring_client,
    )

    assert result.status == "failed"
    assert "not found" in result.error.lower()


def test_purchase_procurement_forwards_jwt_and_retrieves_purchase_order():
    """Step 10 Tests 7, 8, 9, 12, 13: Purchase forwards JWT, retrieves confirmed PO, without Python stock deduction."""
    spring_client = FakeSpringClient()

    result = purchase_procurement(
        procurement_id="c39b3a0e-49b0-466d-9be2-4467c6999b80",
        token="jwt-token-purchase",
        client=spring_client,
    )

    assert result.status == "ok"
    assert result.backend_status == "COMPLETED"
    assert result.purchase_result["status"] == "CONFIRMED"
    assert result.purchase_order["id"] == "po-123"
    assert result.purchase_order["status"] == "CONFIRMED"
    assert result.purchase_order["totalAmount"] == 390000.0

    purchase_calls = [c for c in spring_client.calls if c["method"] == "purchase"]
    assert len(purchase_calls) == 1
    assert purchase_calls[0]["token"] == "jwt-token-purchase"


def test_purchase_cannot_bypass_revalidation():
    """Step 10 Test 10: Spring rejects unvalidated purchase with 400/409, Python propagates it cleanly."""
    spring_client = FakeSpringClient(
        raise_on_purchase=ValidationError("Cannot execute purchase. Pre-purchase revalidation failed: Stale offer", status_code=400)
    )

    result = purchase_procurement(
        procurement_id="c39b3a0e-49b0-466d-9be2-4467c6999b80",
        token="jwt-token",
        client=spring_client,
    )

    assert result.status == "failed"
    assert "revalidation failed" in result.error.lower()


def test_repeated_purchase_returns_existing_po_idempotently():
    """Step 10 Test 11: Repeated purchase returns existing PO idempotently."""
    spring_client = FakeSpringClient(
        purchase_response={
            "purchaseOrderId": "po-123",
            "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
            "vendorName": "TechDirect Enterprises",
            "productName": "Dell Latitude 5540",
            "quantity": 5,
            "unitPrice": 78000.0,
            "totalAmount": 390000.0,
            "status": "ALREADY_COMPLETED",
            "message": "Purchase order already confirmed for this procurement.",
            "procurementStatus": "COMPLETED",
        }
    )

    result = purchase_procurement(
        procurement_id="c39b3a0e-49b0-466d-9be2-4467c6999b80",
        token="jwt-token",
        client=spring_client,
    )

    assert result.status == "ok"
    assert result.purchase_result["status"] == "ALREADY_COMPLETED"
    assert result.purchase_order["id"] == "po-123"


def test_purchase_401_and_403_and_409_handling():
    """Step 10 Tests 14, 15, 16: Authentication (401), Authorization (403), and Conflict (409) errors are handled cleanly."""
    # 401
    client_401 = FakeSpringClient(raise_on_purchase=AuthenticationError("JWT expired", status_code=401))
    res_401 = purchase_procurement("id", token="expired", client=client_401)
    assert res_401.status == "failed"
    assert "JWT expired" in res_401.error

    # 403
    client_403 = FakeSpringClient(raise_on_purchase=AuthorizationError("Forbidden", status_code=403))
    res_403 = purchase_procurement("id", token="user-jwt", client=client_403)
    assert res_403.status == "failed"
    assert "Forbidden" in res_403.error

    # 409
    client_409 = FakeSpringClient(raise_on_purchase=StateConflictError("Invalid state transition", status_code=409))
    res_409 = purchase_procurement("id", token="mgr-jwt", client=client_409)
    assert res_409.status == "failed"
    assert "Invalid state transition" in res_409.error
