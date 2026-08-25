from app.guardrails import DomainGuardrail, GuardrailResult
from app.llm.base import LLMClient, LLMError
from app.parser import handle_brief


class FakeLLM(LLMClient):
    def __init__(self, payload: dict) -> None:
        self.payload = payload
        self.calls = 0

    def generate_json(self, system_prompt, user_prompt, json_schema) -> dict:
        self.calls += 1
        return self.payload


class ScriptedLLM(LLMClient):
    def __init__(self, payloads: list[dict]) -> None:
        self.payloads = list(payloads)
        self.calls = 0

    def generate_json(self, system_prompt, user_prompt, json_schema) -> dict:
        self.calls += 1
        return self.payloads.pop(0)


class FailingLLM(LLMClient):
    def __init__(self, error: Exception) -> None:
        self.error = error
        self.calls = 0

    def generate_json(self, system_prompt, user_prompt, json_schema) -> dict:
        self.calls += 1
        raise self.error


def test_guardrail_allows_purchase_request():
    llm = FakeLLM(
        {
            "is_procurement": True,
            "reason": "The user wants to buy monitors.",
        }
    )
    result = DomainGuardrail(llm).check("Buy 5 monitors under 40000")

    assert isinstance(result, GuardrailResult)
    assert result.is_procurement is True


def test_guardrail_rejects_non_purchase_request():
    llm = FakeLLM(
        {
            "is_procurement": False,
            "reason": "The user asked for a joke, not a purchase.",
        }
    )
    result = DomainGuardrail(llm).check("Tell me a joke")

    assert result.is_procurement is False
    assert "joke" in result.reason.lower()


def test_rejected_request_is_not_parsed():
    llm = ScriptedLLM(
        [
            {
                "is_procurement": False,
                "reason": "The user asked for programming help.",
            }
        ]
    )
    result = handle_brief("Write a Python program", llm)

    assert result.status == "rejected"
    assert result.is_procurement is False
    assert result.request is None
    assert llm.calls == 1
    assert llm.payloads == []


# ==============================================================================
# Regression Tests for Heuristic Fallback & LLM Failure Behaviors
# ==============================================================================

def test_valid_laptop_procurement_when_llm_fails():
    """Valid laptop procurement is allowed even when LLM call throws an HTTP / auth error."""
    llm = FailingLLM(LLMError("LLM HTTP 401: Invalid API Key"))
    brief = "Buy 5 laptops under ₹85,000 each with at least 16GB RAM and delivery within 7 days."
    result = DomainGuardrail(llm).check(brief)

    assert result.is_procurement is True
    assert "purchasing" in result.reason.lower() or "procurement" in result.reason.lower()


def test_valid_tv_procurement_when_llm_fails():
    """Valid TV procurement is allowed even when LLM call throws an error."""
    llm = FailingLLM(LLMError("LLM HTTP 401: Invalid API Key"))
    brief = "Purchase 2 TVs under ₹200,000 with at least 55 inch screen and delivery within 7 days."
    result = DomainGuardrail(llm).check(brief)

    assert result.is_procurement is True
    assert "purchasing" in result.reason.lower() or "procurement" in result.reason.lower()


def test_programming_request_rejected_when_llm_fails():
    """Coding request is rejected even when LLM call fails."""
    llm = FailingLLM(LLMError("LLM HTTP 500: Server Error"))
    brief = "Write Python code to reverse a binary tree."
    result = DomainGuardrail(llm).check(brief)

    assert result.is_procurement is False
    assert "programming" in result.reason.lower() or "not a procurement" in result.reason.lower()


def test_llm_failure_behavior_with_network_exception():
    """Tests general network exception triggering heuristic classification."""
    llm = FailingLLM(ConnectionError("Connection timed out"))
    brief = "Order 10 ergonomic chairs for the new office."
    result = DomainGuardrail(llm).check(brief)

    assert result.is_procurement is True


def test_malformed_llm_response_behavior():
    """Tests malformed LLM response payload safely falling back to heuristic."""
    llm = FakeLLM({"invalid_key": 123, "not_a_guardrail": True})
    brief = "Procure 3 servers under $5000."
    result = DomainGuardrail(llm).check(brief)

    assert result.is_procurement is True
