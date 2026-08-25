from pydantic import BaseModel, Field, PositiveInt, field_validator

from app.models.constraint import Constraint
from app.models.preference import Preference


class ProcurementRequest(BaseModel):
    """Structured procurement brief.

    Product details live in generic Constraint and Preference lists,
    not in category-specific request types.
    """

    original_brief: str = Field(..., min_length=1)
    category: str = Field(..., min_length=1)
    quantity: PositiveInt
    constraints: list[Constraint] = Field(default_factory=list)
    preferences: list[Preference] = Field(default_factory=list)
    budget: float | None = Field(default=None, ge=0)
    max_delivery_days: PositiveInt | None = None
    authorization_limit: float | None = Field(default=None, ge=0)

    @field_validator("original_brief", "category", mode="before")
    @classmethod
    def strip_text(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value
