import json
import pytest
import httpx

from app.client.dto_mapper import (
    map_constraint,
    map_procurement_request,
    DtoMappingError,
)
from app.client.spring_client import (
    SpringProcurementClient,
    ValidationError,
    AuthenticationError,
    AuthorizationError,
    ResourceNotFoundError,
    StateConflictError,
    BackendServerError,
    BackendUnavailableError,
)
from app.models.constraint import Constraint
from app.models.procurement import ProcurementRequest


# =============================================================================
# DTO Mapper Tests (Tests 22-29)
# =============================================================================

def test_dto_mapper_normal_procurement():
    """Test 22: normal procurement mapping."""
    req = ProcurementRequest(
        original_brief="Buy 5 laptops under 85000",
        category="Laptop",
        quantity=5,
        constraints=[
            Constraint(attribute="ram", operator=">=", value="16", unit="GB", mandatory=True)
        ],
        budget=85000.0,
        max_delivery_days=7,
        authorization_limit=450000.0,
    )

    dto = map_procurement_request(req)
    assert dto["category"] == "Laptop"
    assert dto["quantity"] == 5
    assert dto["authorizationLimit"] == 450000.0
    assert len(dto["constraints"]) == 3  # ram, price, deliveryDays

    ram_c = next(c for c in dto["constraints"] if c["attribute"] == "ram")
    assert ram_c["operator"] == ">="
    assert ram_c["value"] == "16"
    assert ram_c["mandatory"] is True


def test_dto_mapper_authorization_limit_fallback():
    """Test 23: authorization limit mapping and fallback to budget * quantity."""
    req = ProcurementRequest(
        original_brief="Buy 3 laptops with budget 50000",
        category="Laptop",
        quantity=3,
        budget=50000.0,
        authorization_limit=None,
    )
    dto = map_procurement_request(req)
    assert dto["authorizationLimit"] == 150000.0


def test_dto_mapper_budget_mapping():
    """Test 24: budget -> price <= budget mapping."""
    req = ProcurementRequest(
        original_brief="Buy 1 monitor with budget 30000",
        category="Monitor",
        quantity=1,
        budget=30000.0,
    )
    dto = map_procurement_request(req)
    price_c = next(c for c in dto["constraints"] if c["attribute"] == "price")
    assert price_c["operator"] == "<="
    assert price_c["value"] == "30000.0"
    assert price_c["mandatory"] is True


def test_dto_mapper_delivery_days_mapping():
    """Test 25: delivery -> deliveryDays <= max_delivery_days mapping."""
    req = ProcurementRequest(
        original_brief="Buy 2 tablets deliver within 5 days",
        category="Tablet",
        quantity=2,
        max_delivery_days=5,
    )
    dto = map_procurement_request(req)
    deliv_c = next(c for c in dto["constraints"] if c["attribute"] == "deliveryDays")
    assert deliv_c["operator"] == "<="
    assert deliv_c["value"] == "5"
    assert deliv_c["mandatory"] is True


def test_dto_mapper_equality_operator_mapping():
    """Test 26: equality operator mapping ('=' and '==' -> '==')."""
    c1 = Constraint(attribute="resolution", operator="=", value="4K")
    mapped1 = map_constraint(c1)
    assert mapped1["operator"] == "=="

    c2 = Constraint.model_construct(attribute="brand", operator="==", value="Dell", mandatory=True)
    mapped2 = map_constraint(c2)
    assert mapped2["operator"] == "=="



def test_dto_mapper_numeric_and_string_values():
    """Test 27: numeric and string constraint values conversion."""
    c_num = Constraint(attribute="storage", operator=">=", value=512)
    mapped_num = map_constraint(c_num)
    assert mapped_num["value"] == "512"

    c_str = Constraint(attribute="panelType", operator="contains", value="OLED")
    mapped_str = map_constraint(c_str)
    assert mapped_str["value"] == "OLED"
    assert mapped_str["operator"] == "CONTAINS"


def test_dto_mapper_unsupported_operator_rejection():
    """Test 28: unsupported operator rejection."""
    c_bad = Constraint.model_construct(attribute="ram", operator="~=", value="16")
    with pytest.raises(DtoMappingError) as exc_info:
        map_constraint(c_bad)
    assert "Unsupported constraint operator" in str(exc_info.value)


def test_dto_mapper_missing_optional_fields():
    """Test 29: missing optional fields handling."""
    req = ProcurementRequest(
        original_brief="Buy 1 chair",
        category="Office chair",
        quantity=1,
    )
    dto = map_procurement_request(req)
    assert dto["category"] == "Office chair"
    assert dto["quantity"] == 1
    assert dto["authorizationLimit"] == 0.0
    assert dto["constraints"] == []


def test_dto_mapper_empty_category_or_invalid_quantity():
    with pytest.raises(DtoMappingError):
        map_procurement_request(ProcurementRequest.model_construct(original_brief="test", category="", quantity=1))

    with pytest.raises(DtoMappingError):
        map_procurement_request(ProcurementRequest.model_construct(original_brief="test", category="Laptop", quantity=0))


# =============================================================================
# SpringProcurementClient HTTP Tests (Tests 1-21)
# =============================================================================

def test_create_procurement_sends_correct_json_and_jwt():
    """Tests 1 & 2: create_procurement sends correct JSON and forwards JWT Bearer token."""
    captured_request = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured_request["method"] = request.method
        captured_request["url"] = str(request.url)
        captured_request["headers"] = dict(request.headers)
        captured_request["body"] = json.loads(request.read())

        response_body = {
            "timestamp": "2026-08-20T12:00:00Z",
            "success": True,
            "message": "Created",
            "data": {
                "id": "11111111-2222-3333-4444-555555555555",
                "category": "Laptop",
                "quantity": 5,
                "status": "SUBMITTED",
            }
        }
        return httpx.Response(200, json=response_body)

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", token="test-jwt-token", transport=transport)

    req = ProcurementRequest(
        original_brief="Buy 5 laptops under 85000",
        category="Laptop",
        quantity=5,
        budget=85000.0,
        authorization_limit=450000.0,
    )

    result = client.create_procurement(req)

    assert captured_request["method"] == "POST"
    assert captured_request["url"] == "http://mock-spring:8080/api/procurements"
    assert captured_request["headers"]["authorization"] == "Bearer test-jwt-token"
    assert captured_request["body"]["category"] == "Laptop"
    assert captured_request["body"]["quantity"] == 5
    assert result["id"] == "11111111-2222-3333-4444-555555555555"


def test_execute_procurement_endpoint():
    """Test 3: execute_procurement sends correct endpoint."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/execute"
        return httpx.Response(200, json={
            "success": True,
            "data": {
                "procurementId": proc_id,
                "status": "COMPLETED",
                "purchaseOrderId": "po-123",
            }
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.execute_procurement(proc_id)
    assert result["status"] == "COMPLETED"
    assert result["purchaseOrderId"] == "po-123"


def test_get_procurement_parses_response():
    """Test 4: get_procurement parses response."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "GET"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}"
        return httpx.Response(200, json={
            "success": True,
            "data": {
                "id": proc_id,
                "category": "Laptop",
                "status": "WAITING_APPROVAL",
            }
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.get_procurement(proc_id)
    assert result["id"] == proc_id
    assert result["status"] == "WAITING_APPROVAL"


def test_get_recommendation_parsing():
    """Test 5: recommendation response parsing."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/recommendation"
        return httpx.Response(200, json={
            "success": True,
            "data": {
                "procurementId": proc_id,
                "recommendationType": "STANDARD_RECOMMENDED",
                "bestEligibleOption": {
                    "productName": "Dell Latitude 5540",
                    "price": 390000.0,
                }
            }
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.get_recommendation(proc_id)
    assert result["recommendationType"] == "STANDARD_RECOMMENDED"
    assert result["bestEligibleOption"]["productName"] == "Dell Latitude 5540"


def test_get_tco_parsing():
    """Test 6: TCO response parsing."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/tco"
        return httpx.Response(200, json={
            "success": True,
            "data": [
                {
                    "productName": "Dell Latitude 5540",
                    "totalTco": 412500.0,
                    "horizonYears": 3,
                }
            ]
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.get_tco(proc_id)
    assert len(result) == 1
    assert result[0]["totalTco"] == 412500.0


def test_get_approval_retrieval():
    """Test 7: approval retrieval."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/approval"
        return httpx.Response(200, json={
            "success": True,
            "data": {
                "approvalId": "appr-1",
                "status": "PENDING",
                "requestedAmount": 500000.0,
            }
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.get_approval(proc_id)
    assert result["approvalId"] == "appr-1"
    assert result["status"] == "PENDING"


def test_approve_procurement_request():
    """Test 8: approval request."""
    proc_id = "11111111-2222-3333-4444-555555555555"
    captured_body = {}

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/approval/approve"
        captured_body.update(json.loads(request.read()))
        return httpx.Response(200, json={
            "success": True,
            "data": {"status": "APPROVED"}
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.approve_procurement(proc_id, comments="Manager approved", approved_offer_id="off-1")
    assert captured_body["comments"] == "Manager approved"
    assert captured_body["approvedOfferId"] == "off-1"
    assert result["status"] == "APPROVED"


def test_reject_procurement_request():
    """Test 9: rejection request."""
    proc_id = "11111111-2222-3333-4444-555555555555"
    captured_body = {}

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/approval/reject"
        captured_body.update(json.loads(request.read()))
        return httpx.Response(200, json={
            "success": True,
            "data": {"status": "REJECTED"}
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.reject_procurement(proc_id, comments="Over budget")
    assert captured_body["comments"] == "Over budget"
    assert result["status"] == "REJECTED"


def test_revalidate_request():
    """Test 10: revalidation request."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/revalidate"
        return httpx.Response(200, json={
            "success": True,
            "data": {"status": "VALID", "valid": True}
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.revalidate(proc_id)
    assert result["valid"] is True
    assert result["status"] == "VALID"


def test_purchase_request():
    """Test 11: purchase request."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/purchase"
        return httpx.Response(200, json={
            "success": True,
            "data": {"purchaseOrderId": "po-999", "status": "CONFIRMED"}
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.purchase(proc_id)
    assert result["purchaseOrderId"] == "po-999"


def test_get_purchase_order_retrieval():
    """Test 12: purchase-order retrieval."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "GET"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/purchase-order"
        return httpx.Response(200, json={
            "success": True,
            "data": {
                "id": "po-999",
                "productName": "Dell Latitude 5540",
                "status": "CONFIRMED",
            }
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.get_purchase_order(proc_id)
    assert result["id"] == "po-999"
    assert result["status"] == "CONFIRMED"


def test_get_audit_trail_retrieval():
    """Test 13: audit retrieval."""
    proc_id = "11111111-2222-3333-4444-555555555555"

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "GET"
        assert str(request.url) == f"http://mock-spring:8080/api/procurements/{proc_id}/audit"
        return httpx.Response(200, json={
            "success": True,
            "data": {
                "procurementId": proc_id,
                "events": [
                    {"eventType": "STATE_TRANSITION", "state": "SUBMITTED"}
                ]
            }
        })

    transport = httpx.MockTransport(handler)
    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=transport)
    result = client.get_audit_trail(proc_id)
    assert result["procurementId"] == proc_id
    assert len(result["events"]) == 1


# =============================================================================
# Error Handling Tests (Tests 14-21)
# =============================================================================

def test_400_validation_error_handling():
    """Test 14: 400 handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"error": "Bad Request", "message": "Invalid constraints"})

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(ValidationError) as exc:
        client.get_procurement("id-123")
    assert exc.value.status_code == 400
    assert "Invalid constraints" in str(exc.value)


def test_401_authentication_error_handling():
    """Test 15: 401 handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(401, json={"error": "Unauthorized", "message": "JWT token expired"})

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(AuthenticationError) as exc:
        client.get_procurement("id-123")
    assert exc.value.status_code == 401
    assert "JWT token expired" in str(exc.value)


def test_403_authorization_error_handling():
    """Test 16: 403 handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, json={"error": "Forbidden", "message": "Requires PROCUREMENT_MANAGER role"})

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(AuthorizationError) as exc:
        client.approve_procurement("id-123")
    assert exc.value.status_code == 403
    assert "Requires PROCUREMENT_MANAGER role" in str(exc.value)


def test_404_resource_not_found_handling():
    """Test 17: 404 handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(404, json={"error": "Not Found", "message": "Procurement not found"})

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(ResourceNotFoundError) as exc:
        client.get_procurement("missing-id")
    assert exc.value.status_code == 404


def test_409_state_conflict_handling():
    """Test 18: 409 handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(409, json={"error": "Conflict", "message": "Cannot transition from COMPLETED to SUBMITTED"})

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(StateConflictError) as exc:
        client.execute_procurement("id-123")
    assert exc.value.status_code == 409


def test_500_backend_server_error_handling():
    """Test 19: 500 handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(500, json={"error": "Internal Server Error", "message": "Database connection pool exhausted"})

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(BackendServerError) as exc:
        client.get_procurement("id-123")
    assert exc.value.status_code == 500


def test_timeout_and_network_failure_handling():
    """Test 20: timeout/network failure handling."""
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("Connection timed out")

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(BackendUnavailableError) as exc:
        client.get_procurement("id-123")
    assert "Backend unavailable" in str(exc.value)


def test_post_requests_are_not_automatically_retried():
    """Test 21: POST requests are NOT automatically retried."""
    call_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal call_count
        call_count += 1
        raise httpx.ConnectTimeout("Network dropped")

    client = SpringProcurementClient(base_url="http://mock-spring:8080", transport=httpx.MockTransport(handler))
    with pytest.raises(BackendUnavailableError):
        client.purchase("id-123")

    # Verify handler was called exactly once, meaning no auto-retry on POST
    assert call_count == 1
