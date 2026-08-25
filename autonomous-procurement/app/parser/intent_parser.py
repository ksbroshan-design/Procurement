import logging
import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, PositiveInt, ValidationError, field_validator

from app.guardrails.domain_guardrail import DomainGuardrail
from app.llm.base import LLMClient, LLMError
from app.models.constraint import Constraint
from app.models.preference import Preference
from app.models.procurement import ProcurementRequest

logger = logging.getLogger("app.parser.intent_parser")

SYSTEM_PROMPT = """
You extract structured procurement intent from a user's message.

Rules:
- Extract only facts the user stated. Do not invent requirements.
- If a field was not stated, use null or an empty list.
- Do not invent quantity, budget, specifications, or delivery deadlines.
- Do not guess quantity as 1 unless the user gave a number.
- category is the product type in the user's own words. Any product is allowed.
  If they did not name a specific product type, set category to null.
- quantity is how many units they want. If they did not give a number, set it to null.
- Hard limits are constraints: attribute, operator, value, optional unit.
  Operators must be one of: <=, >=, =, <, >, contains.
  Only create a constraint when the user stated that limit.
- Soft wishes are preferences: attribute, direction, weight.
  direction must be one of: minimize, maximize, prefer.
  If they prefer something but give no weight, use 0.5.
- Price caps such as "under 30000" mean a price constraint with operator <= .
- Delivery phrases such as "within 7 days" set max_delivery_days, not a constraint.
- If they give a price cap, also set budget to that numeric amount.
- Set authorization_limit only if they mention an approval or spend limit.
- Normalize obvious currency symbols in unit: ₹ or Rs or rupees → INR.
- Do not choose vendors, rank products, calculate TCO, or decide a purchase.
""".strip()

UNIT_ALIASES = {
    "₹": "INR",
    "rs": "INR",
    "rs.": "INR",
    "rupee": "INR",
    "rupees": "INR",
    "inr": "INR",
    "$": "USD",
    "usd": "USD",
    "€": "EUR",
    "eur": "EUR",
    "£": "GBP",
    "gbp": "GBP",
    "gb": "GB",
    "tb": "TB",
    "mb": "MB",
    "inch": "inch",
    "inches": "inch",
    "\"": "inch",
}

VAGUE_CATEGORIES = {
    "thing",
    "things",
    "stuff",
    "item",
    "items",
    "good",
    "goods",
    "product",
    "products",
    "something",
    "anything",
}

NUMBER_WORDS = {
    "one": 1,
    "two": 2,
    "three": 3,
    "four": 4,
    "five": 5,
    "six": 6,
    "seven": 7,
    "eight": 8,
    "nine": 9,
    "ten": 10,
    "eleven": 11,
    "twelve": 12,
    "dozen": 12,
    "twenty": 20,
    "thirty": 30,
    "forty": 40,
    "fifty": 50,
    "hundred": 100,
}

PRICE_HINT = re.compile(
    r"₹|rs\.?|inr|usd|eur|gbp|\$|€|£|budget|under |below |"
    r"at most|no more than|price|cost|rupee",
    re.IGNORECASE,
)
DELIVERY_HINT = re.compile(
    r"\b(day|days|week|weeks|deliver|delivery|arrive|shipping)\b",
    re.IGNORECASE,
)
AUTHORIZATION_HINT = re.compile(
    r"\b(authori[sz]e|authorization|approval|spend limit|approval limit)\b",
    re.IGNORECASE,
)


class ExtractedIntent(BaseModel):
    """LLM JSON shape. Optional fields stay empty when the user omitted them."""

    model_config = ConfigDict(extra="ignore")

    category: str | None = Field(default=None, min_length=1)
    quantity: PositiveInt | None = None
    constraints: list[Constraint] = Field(default_factory=list)
    preferences: list[Preference] = Field(default_factory=list)
    budget: float | None = Field(default=None, ge=0)
    max_delivery_days: PositiveInt | None = None
    authorization_limit: float | None = Field(default=None, ge=0)

    @field_validator("category", mode="before")
    @classmethod
    def blank_category_to_none(cls, value: object) -> object:
        if isinstance(value, str) and value.strip() == "":
            return None
        return value


class ClarificationResult(BaseModel):
    """Returned when the brief is procurement-related but incomplete."""

    status: Literal["needs_clarification"] = "needs_clarification"
    missing_information: list[str]
    clarification_question: str


class IntentResponse(BaseModel):
    """Unified result of guardrail + parse. request is set only when status is ok."""

    model_config = ConfigDict(extra="ignore")

    status: Literal["ok", "rejected", "needs_clarification"]
    is_procurement: bool | None = None
    reason: str | None = None
    missing_information: list[str] = Field(default_factory=list)
    clarification_question: str | None = None
    request: ProcurementRequest | None = None


class IntentParseError(Exception):
    """Raised when extraction fails for a technical reason."""


def _extract_intent_heuristically(brief: str) -> ExtractedIntent:
    """Deterministic intent extractor when LLM is unavailable or fails."""
    # 1. Category extraction
    category_patterns = [
        (r"\b(laptops?|notebooks?|macbooks?)\b", "Laptop"),
        (r"\b(tvs?|televisions?|smart tvs?)\b", "TV"),
        (r"\b(monitors?|displays?|screens?)\b", "Monitor"),
        (r"\b(servers?|blade servers?)\b", "Server"),
        (r"\b(printers?|scanners?)\b", "Printer"),
        (r"\b(desks?|standing desks?|workstations?)\b", "Desk"),
        (r"\b(chairs?|office chairs?|ergonomic chairs?)\b", "Chair"),
        (r"\b(keyboards?)\b", "Keyboard"),
        (r"\b(mice|mouse)\b", "Mouse"),
        (r"\b(headphones?|headsets?)\b", "Headphone"),
        (r"\b(tablets?|ipads?)\b", "Tablet"),
        (r"\b(phones?|smartphones?)\b", "Phone"),
    ]

    extracted_category = None
    for pat, cat_name in category_patterns:
        if re.search(pat, brief, re.IGNORECASE):
            extracted_category = cat_name
            break

    # 2. Quantity extraction
    extracted_quantity = None
    # Look for digits before product or words
    digits = re.findall(r"\b\d+\b", brief)
    for d_str in digits:
        val = int(d_str)
        # Avoid matching large numbers (prices), memory specs (16, 512), or delivery days
        if val > 500:
            continue
        if re.search(rf"\b{val}\s*(?:laptops?|tvs?|monitors?|tablets?|printers?|servers?|desks?|chairs?|units?|pieces?|nos?\.?)\b", brief, re.IGNORECASE):
            extracted_quantity = val
            break
        elif re.search(rf"\b(?:buy|purchase|procure|order|get)\s+{val}\b", brief, re.IGNORECASE):
            extracted_quantity = val
            break

    if extracted_quantity is None:
        for w, num_val in NUMBER_WORDS.items():
            if re.search(rf"\b{w}\s*(?:laptops?|tvs?|monitors?|tablets?|printers?|servers?|desks?|chairs?|units?|pieces?)\b", brief, re.IGNORECASE):
                extracted_quantity = num_val
                break
            elif re.search(rf"\b(?:buy|purchase|procure|order|get)\s+{w}\b", brief, re.IGNORECASE):
                extracted_quantity = num_val
                break

    # 3. Budget / Price cap extraction
    extracted_budget = None
    b_match = re.search(r"(?:under|below|budget of|budget|at most|less than|up to|max price of|max price|price under)\s*(?:₹|Rs\.?|INR|USD|EUR|\$)?\s*([\d,]+)", brief, re.IGNORECASE)
    if b_match:
        num_str = b_match.group(1).replace(",", "")
        try:
            extracted_budget = float(num_str)
        except ValueError:
            pass
    elif re.search(r"(?:₹|Rs\.?|INR|\$)\s*([\d,]+)", brief):
        c_match = re.search(r"(?:₹|Rs\.?|INR|\$)\s*([\d,]+)", brief)
        if c_match:
            num_str = c_match.group(1).replace(",", "")
            try:
                extracted_budget = float(num_str)
            except ValueError:
                pass

    # 4. Delivery Days extraction
    extracted_delivery_days = None
    d_match = re.search(r"(?:delivery|shipping|within|arrive in|deliver in)\s*(?:within)?\s*(\d+)\s*(?:days?|weeks?)", brief, re.IGNORECASE)
    if d_match:
        days_val = int(d_match.group(1))
        if "week" in d_match.group(0).lower():
            days_val *= 7
        extracted_delivery_days = days_val

    # 5. Constraints extraction
    constraints = []
    if extracted_budget is not None:
        constraints.append(Constraint(
            attribute="price",
            operator="<=",
            value=str(int(extracted_budget) if extracted_budget.is_integer() else extracted_budget),
            unit="INR",
            mandatory=True,
        ))

    # RAM constraint: "at least 16GB RAM" or "16 GB RAM"
    ram_match = re.search(r"(?:at least|minimum|min|>=)?\s*(\d+)\s*(?:GB|gb)\s*RAM", brief, re.IGNORECASE)
    if ram_match:
        constraints.append(Constraint(
            attribute="RAM",
            operator=">=",
            value=ram_match.group(1),
            unit="GB",
            mandatory=True,
        ))

    # Screen Size constraint: "at least 55 inch screen" or "55 inch"
    screen_match = re.search(r"(?:at least|minimum|min|>=)?\s*(\d+)\s*(?:inch|inches|\")\s*(?:screen|display|tv)?", brief, re.IGNORECASE)
    if screen_match:
        constraints.append(Constraint(
            attribute="screen_size",
            operator=">=",
            value=screen_match.group(1),
            unit="inch",
            mandatory=True,
        ))

    # Storage constraint: "at least 256GB storage" or "512GB SSD"
    storage_match = re.search(r"(?:at least|minimum|min|>=)?\s*(\d+)\s*(?:GB|TB|gb|tb)\s*(?:storage|ssd|hdd|drive|rom)", brief, re.IGNORECASE)
    if storage_match:
        constraints.append(Constraint(
            attribute="storage",
            operator=">=",
            value=storage_match.group(1),
            unit="GB",
            mandatory=True,
        ))

    # Preferences extraction
    preferences = []
    if re.search(r"\b(prefer|longer|better)\s*(warranty)\b", brief, re.IGNORECASE):
        preferences.append(Preference(attribute="warranty", direction="maximize", weight=0.5))
    if re.search(r"\b(prefer|cheaper|lowest)\s*(price|cost)\b", brief, re.IGNORECASE):
        preferences.append(Preference(attribute="price", direction="minimize", weight=0.5))
    if re.search(r"\b(prefer|fastest|faster|quick)\s*(delivery|shipping)\b", brief, re.IGNORECASE):
        preferences.append(Preference(attribute="delivery", direction="minimize", weight=0.5))

    return ExtractedIntent(
        category=extracted_category,
        quantity=extracted_quantity,
        budget=extracted_budget,
        max_delivery_days=extracted_delivery_days,
        constraints=constraints,
        preferences=preferences,
    )


class IntentParser:
    """Turns a plain-language procurement brief into a structured result."""

    def __init__(self, llm: LLMClient) -> None:
        self.llm = llm

    def parse(self, brief: str) -> IntentResponse:
        cleaned_brief = brief.strip()
        if not cleaned_brief:
            raise IntentParseError("Procurement brief is empty")

        try:
            logger.info("IntentParser: LLM call started")
            raw = self.llm.generate_json(
                system_prompt=SYSTEM_PROMPT,
                user_prompt=_user_prompt(cleaned_brief),
                json_schema=ExtractedIntent.model_json_schema(),
            )
            extracted = ExtractedIntent.model_validate(raw)
            logger.info(f"IntentParser: LLM parsing succeeded: {raw}")
        except (LLMError, ValidationError, Exception) as error:
            logger.warning(
                f"IntentParser: LLM call failed ({type(error).__name__}: {error}). Falling back to heuristic extractor."
            )
            extracted = _extract_intent_heuristically(cleaned_brief)

        extracted = _ground_in_brief(_normalize_units(extracted), cleaned_brief)
        missing = _missing_information(extracted)
        if missing:
            clarification = _clarification_result(missing)
            return IntentResponse(
                status=clarification.status,
                is_procurement=True,
                missing_information=clarification.missing_information,
                clarification_question=clarification.clarification_question,
            )

        # Enforce final grounding when creating the ProcurementRequest: do not accept invented budget
        final_budget = extracted.budget if _brief_has_budget(cleaned_brief) else None
        request = ProcurementRequest(
            original_brief=cleaned_brief,
            category=extracted.category,
            quantity=extracted.quantity,
            constraints=extracted.constraints,
            preferences=extracted.preferences,
            budget=final_budget,
            max_delivery_days=extracted.max_delivery_days,
            authorization_limit=extracted.authorization_limit,
        )
        return IntentResponse(
            status="ok",
            is_procurement=True,
            request=request,
        )


def handle_brief(brief: str, llm: LLMClient) -> IntentResponse:
    """Run domain guardrail, then parse. Rejected messages are not parsed."""
    verdict = DomainGuardrail(llm).check(brief)
    if not verdict.is_procurement:
        return IntentResponse(
            status="rejected",
            is_procurement=False,
            reason=verdict.reason,
        )
    return IntentParser(llm).parse(brief)


def _user_prompt(brief: str) -> str:
    return f"Extract procurement intent from this request.\n\n{brief}"


def _normalize_unit(unit: str | None) -> str | None:
    if unit is None:
        return None
    stripped = unit.strip()
    if stripped == "":
        return None
    return UNIT_ALIASES.get(stripped) or UNIT_ALIASES.get(stripped.lower()) or stripped


def _normalize_units(extracted: ExtractedIntent) -> ExtractedIntent:
    constraints = [
        item.model_copy(update={"unit": _normalize_unit(item.unit)})
        for item in extracted.constraints
    ]
    return extracted.model_copy(update={"constraints": constraints})


def _brief_has_quantity(brief: str) -> bool:
    if re.search(r"\d+", brief):
        return True
    words = set(re.findall(r"[a-zA-Z]+", brief.lower()))
    return bool(words & set(NUMBER_WORDS.keys()))


def _brief_has_budget(brief: str) -> bool:
    if re.search(r"[₹$€£]", brief):
        return True
    if re.search(r"\b(?:inr|rs(?:\.)?|rupee(?:s)?|usd|eur|gbp)\b", brief, re.IGNORECASE):
        return True
    if re.search(r"\b(?:under|below|less than|budget|budget of)\b\s*[\d,]+", brief, re.IGNORECASE):
        return True
    if re.search(r"[\d,]+\s*(?:inr|rs(?:\.)?|usd|eur|gbp|\$)\b", brief, re.IGNORECASE):
        return True
    return False


def _attribute_mentioned(attribute: str, brief: str) -> bool:
    lowered = brief.lower()
    name = attribute.lower().replace("_", " ")
    if name in lowered or attribute.lower() in lowered:
        return True
    if name in {"price", "cost", "budget"} and PRICE_HINT.search(brief):
        return True
    if attribute.lower() in {"screen_size", "screensize", "screen"} and any(w in lowered for w in ("screen", "inch", "inches", "display")):
        return True
    if attribute.lower() in {"ram", "memory"} and any(w in lowered for w in ("ram", "memory")):
        return True
    if attribute.lower() in {"storage", "disk", "ssd", "hdd"} and any(w in lowered for w in ("storage", "ssd", "hdd", "disk", "drive")):
        return True
    return False


def _ground_in_brief(extracted: ExtractedIntent, brief: str) -> ExtractedIntent:
    category = extracted.category
    if category is not None and category.strip().lower() in VAGUE_CATEGORIES:
        category = None

    quantity = extracted.quantity
    if quantity is not None and not _brief_has_quantity(brief):
        quantity = None

    budget = extracted.budget
    if budget is not None and not _brief_has_budget(brief):
        budget = None

    max_delivery_days = extracted.max_delivery_days
    if max_delivery_days is not None and not DELIVERY_HINT.search(brief):
        max_delivery_days = None

    authorization_limit = extracted.authorization_limit
    if authorization_limit is not None and not AUTHORIZATION_HINT.search(brief):
        authorization_limit = None

    constraints = [
        item for item in extracted.constraints if _attribute_mentioned(item.attribute, brief)
    ]
    preferences = [
        item for item in extracted.preferences if _attribute_mentioned(item.attribute, brief)
    ]

    return extracted.model_copy(
        update={
            "category": category,
            "quantity": quantity,
            "budget": budget,
            "max_delivery_days": max_delivery_days,
            "authorization_limit": authorization_limit,
            "constraints": constraints,
            "preferences": preferences,
        }
    )


def _missing_information(extracted: ExtractedIntent) -> list[str]:
    missing: list[str] = []
    if not extracted.category:
        missing.append("category")
    if extracted.quantity is None:
        missing.append("quantity")
    return missing


def _clarification_result(missing: list[str]) -> ClarificationResult:
    if missing == ["category", "quantity"]:
        question = "What product do you want to buy, and how many units?"
    elif missing == ["category"]:
        question = "What product do you want to buy?"
    elif missing == ["quantity"]:
        question = "How many units do you want to buy?"
    else:
        question = "Please specify " + " and ".join(missing) + "."
    return ClarificationResult(
        missing_information=missing,
        clarification_question=question,
    )
