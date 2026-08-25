import pytest

from app.llm.base import LLMClient, LLMError
from app.parser import IntentParseError, IntentParser, handle_brief


class FakeLLM(LLMClient):
    def __init__(self, payload: dict) -> None:
        self.payload = payload

    def generate_json(self, system_prompt, user_prompt, json_schema) -> dict:
        return self.payload


class ScriptedLLM(LLMClient):
    def __init__(self, payloads: list[dict]) -> None:
        self.payloads = list(payloads)

    def generate_json(self, system_prompt, user_prompt, json_schema) -> dict:
        return self.payloads.pop(0)


class FailingLLM(LLMClient):
    def __init__(self, error: Exception) -> None:
        self.error = error

    def generate_json(self, system_prompt, user_prompt, json_schema) -> dict:
        raise self.error


TABLET_BRIEF = (
    "Buy 10 tablets under 30000 INR each, with at least 8GB RAM and "
    "256GB storage, delivery within 7 days. Prefer longer warranty."
)

TABLET_PAYLOAD = {
    "category": "tablet",
    "quantity": 10,
    "constraints": [
        {"attribute": "price", "operator": "<=", "value": 30000, "unit": "₹"},
        {"attribute": "RAM", "operator": ">=", "value": 8, "unit": "GB"},
        {"attribute": "storage", "operator": ">=", "value": 256, "unit": "GB"},
    ],
    "preferences": [
        {"attribute": "warranty", "direction": "maximize", "weight": 0.5},
    ],
    "budget": 30000,
    "max_delivery_days": 7,
    "authorization_limit": None,
}


def test_parse_maps_llm_json_to_procurement_request():
    result = IntentParser(FakeLLM(TABLET_PAYLOAD)).parse(TABLET_BRIEF)

    assert result.status == "ok"
    request = result.request
    assert request is not None
    assert request.original_brief == TABLET_BRIEF
    assert request.category == "tablet"
    assert request.quantity == 10
    assert request.budget == 30000
    assert request.max_delivery_days == 7
    assert [item.attribute for item in request.constraints] == [
        "price",
        "RAM",
        "storage",
    ]
    assert request.preferences[0].attribute == "warranty"
    assert request.preferences[0].direction == "maximize"


def test_original_brief_is_taken_from_user_not_llm():
    payload = dict(TABLET_PAYLOAD)
    payload["original_brief"] = "rewritten by the model"
    result = IntentParser(FakeLLM(payload)).parse(TABLET_BRIEF)

    assert result.request is not None
    assert result.request.original_brief == TABLET_BRIEF


def test_rupee_symbol_is_normalized_to_inr():
    result = IntentParser(FakeLLM(TABLET_PAYLOAD)).parse(TABLET_BRIEF)

    assert result.request is not None
    assert result.request.constraints[0].unit == "INR"


def test_unmentioned_specs_are_not_invented():
    payload = {
        "category": "printer",
        "quantity": 2,
        "constraints": [
            {"attribute": "RAM", "operator": ">=", "value": 16, "unit": "GB"},
        ],
        "preferences": [],
        "budget": 99999,
        "max_delivery_days": 3,
        "authorization_limit": 50000,
    }
    result = IntentParser(FakeLLM(payload)).parse("Buy 2 printers")

    assert result.status == "ok"
    request = result.request
    assert request is not None
    assert request.constraints == []
    assert request.budget is None
    assert request.max_delivery_days is None
    assert request.authorization_limit is None


def test_incomplete_request_asks_for_clarification():
    payload = {
        "category": "things",
        "quantity": 1,
        "constraints": [],
        "preferences": [],
        "budget": None,
        "max_delivery_days": None,
        "authorization_limit": None,
    }
    result = IntentParser(FakeLLM(payload)).parse("Buy some good things")

    assert result.status == "needs_clarification"
    assert result.request is None
    assert "category" in result.missing_information
    assert "quantity" in result.missing_information
    assert result.clarification_question


def test_missing_quantity_asks_for_clarification():
    payload = {
        "category": "TV",
        "quantity": None,
        "constraints": [],
        "preferences": [],
        "budget": None,
        "max_delivery_days": None,
        "authorization_limit": None,
    }
    result = IntentParser(FakeLLM(payload)).parse("Find me a good TV")

    assert result.status == "needs_clarification"
    assert result.missing_information == ["quantity"]
    assert result.request is None


def test_empty_brief_is_rejected():
    with pytest.raises(IntentParseError):
        IntentParser(FakeLLM(TABLET_PAYLOAD)).parse("   ")


def test_handle_brief_allows_complete_purchase():
    llm = ScriptedLLM(
        [
            {"is_procurement": True, "reason": "User wants to buy tablets."},
            TABLET_PAYLOAD,
        ]
    )
    result = handle_brief(TABLET_BRIEF, llm)

    assert result.status == "ok"
    assert result.is_procurement is True
    assert result.request is not None
    assert result.request.quantity == 10


# ==============================================================================
# Regression Tests for Heuristic Extraction Fallback
# ==============================================================================

def test_heuristic_fallback_for_laptops_brief():
    """Verifies laptop brief extraction when LLM fails."""
    llm = FailingLLM(LLMError("LLM 401 Unauthorized"))
    brief = "Buy 5 laptops under ₹85,000 each with at least 16GB RAM and delivery within 7 days."
    result = IntentParser(llm).parse(brief)

    assert result.status == "ok"
    assert result.is_procurement is True
    req = result.request
    assert req is not None
    assert req.category == "Laptop"
    assert req.quantity == 5
    assert req.budget == 85000.0
    assert req.max_delivery_days == 7
    attrs = {c.attribute: c for c in req.constraints}
    assert "price" in attrs
    assert "RAM" in attrs
    assert attrs["RAM"].value == "16"


def test_heuristic_fallback_for_tv_brief():
    """Verifies TV brief extraction when LLM fails."""
    llm = FailingLLM(LLMError("LLM 503 Service Unavailable"))
    brief = "Purchase 2 TVs under ₹200,000 with at least 55 inch screen and delivery within 7 days."
    result = IntentParser(llm).parse(brief)

    assert result.status == "ok"
    assert result.is_procurement is True
    req = result.request
    assert req is not None
    assert req.category == "TV"
    assert req.quantity == 2
    assert req.budget == 200000.0
    assert req.max_delivery_days == 7
    attrs = {c.attribute: c for c in req.constraints}
    assert "price" in attrs
    assert "screen_size" in attrs
    assert attrs["screen_size"].value == "55"
