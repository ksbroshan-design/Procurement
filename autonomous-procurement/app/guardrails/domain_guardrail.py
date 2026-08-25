import logging
import re
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.llm.base import LLMClient, LLMError

logger = logging.getLogger("app.guardrails.domain_guardrail")

SYSTEM_PROMPT = """
You classify whether a user message is about online purchasing or procurement.

ALLOW when the user wants to buy, purchase, order, procure, shop for,
or find a product or goods to acquire. Any product category is allowed.

REJECT when the message is not a buying request. Examples of reject:
coding help, jokes, homework, general chat, or unrelated questions.

Do not name vendors. Do not recommend products. Do not write the
answer to a rejected request. Only classify.

Return JSON with:
- is_procurement: true or false
- reason: a short explanation of the classification
""".strip()


class GuardrailResult(BaseModel):
    """Whether a message is an online procurement request."""

    model_config = ConfigDict(extra="ignore")

    is_procurement: bool
    reason: str = Field(..., min_length=1)


# Explicit non-procurement indicators (programming, algorithms, chat, jokes, math, essay)
NON_PROCUREMENT_PATTERNS = [
    re.compile(r"\b(write|create|generate|show|debug|refactor)\s+(a\s+)?(python|java|c\+\+|js|javascript|sql|code|script|program|function|class|binary tree|algorithm)\b", re.IGNORECASE),
    re.compile(r"\b(reverse|invert|traverse|sort|search)\s+(a\s+)?(binary tree|linked list|array|string|graph|tree|node)\b", re.IGNORECASE),
    re.compile(r"\b(tell|give)\s+(me\s+)?(a\s+)?(joke|riddle|story|poem|essay|song)\b", re.IGNORECASE),
    re.compile(r"\b(how to|explain|what is|why is|translate|summarize)\b", re.IGNORECASE),
    re.compile(r"^(hello|hi|hey|good morning|good evening|how are you)[.!?]?$", re.IGNORECASE),
]

# Explicit purchasing / procurement verbs
PROCUREMENT_VERB_PATTERNS = [
    re.compile(r"\b(buy|purchase|procure|order|acquire|source|get|need|want|shop for)\b", re.IGNORECASE),
]

# Commercial & procurement signals (currency, constraints, specs)
PROCUREMENT_COMMERCE_PATTERNS = [
    re.compile(r"₹|rs\.?|inr|usd|eur|gbp|\$|€|£", re.IGNORECASE),
    re.compile(r"\b(under|below|budget|cost|price|delivery|shipping|warranty|specs?|ram|ssd|inches?)\b", re.IGNORECASE),
]


def classify_procurement_heuristically(text: str) -> GuardrailResult:
    """Deterministic fallback classification when LLM is unavailable or unconfigured."""
    cleaned = text.strip()
    if not cleaned:
        return GuardrailResult(
            is_procurement=False,
            reason="The message is empty.",
        )

    # 1. Check explicit non-procurement indicators
    for pat in NON_PROCUREMENT_PATTERNS:
        if pat.search(cleaned):
            # If no procurement verb exists, reject immediately
            if not any(v.search(cleaned) for v in PROCUREMENT_VERB_PATTERNS):
                return GuardrailResult(
                    is_procurement=False,
                    reason="The message is a programming or general conversation request, not a procurement request.",
                )

    # 2. Check for procurement verbs and commercial signals
    has_procurement_verb = any(v.search(cleaned) for v in PROCUREMENT_VERB_PATTERNS)
    has_commerce_signals = any(c.search(cleaned) for c in PROCUREMENT_COMMERCE_PATTERNS)

    if has_procurement_verb or has_commerce_signals:
        return GuardrailResult(
            is_procurement=True,
            reason="The message contains explicit purchasing or procurement intent.",
        )

    return GuardrailResult(
        is_procurement=False,
        reason="The message does not contain identifiable procurement or purchasing intent.",
    )


class DomainGuardrail:
    """LLM-based domain check with deterministic fallback. Does not answer the user's request."""

    def __init__(self, llm: LLMClient) -> None:
        self.llm = llm

    def check(self, text: str) -> GuardrailResult:
        cleaned = text.strip()
        if not cleaned:
            logger.info("DomainGuardrail: empty message received -> is_procurement=False")
            return GuardrailResult(
                is_procurement=False,
                reason="The message is empty.",
            )

        model_name = getattr(self.llm, "model", type(self.llm).__name__)
        logger.info(f"DomainGuardrail: received brief: '{cleaned}'")
        logger.info(f"DomainGuardrail: execution started using model: {model_name}")

        try:
            logger.info("DomainGuardrail: LLM call started")
            raw = self.llm.generate_json(
                system_prompt=SYSTEM_PROMPT,
                user_prompt=f"Classify this message:\n\n{cleaned}",
                json_schema=GuardrailResult.model_json_schema(),
            )
            result = GuardrailResult.model_validate(raw)
            logger.info(f"DomainGuardrail: LLM call succeeded, raw result: {raw}")
            logger.info(f"DomainGuardrail: final classification: is_procurement={result.is_procurement}, reason='{result.reason}'")
            return result
        except (LLMError, ValidationError, KeyError, TypeError, Exception) as error:
            error_type = type(error).__name__
            logger.warning(f"DomainGuardrail: LLM call failed ({error_type}: {error}). Falling back to heuristic classifier.")

            fallback_res = classify_procurement_heuristically(cleaned)
            logger.info(f"DomainGuardrail: fallback classification: is_procurement={fallback_res.is_procurement}, reason='{fallback_res.reason}'")
            return fallback_res
