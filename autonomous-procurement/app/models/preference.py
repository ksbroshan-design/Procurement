from typing import Literal

from pydantic import BaseModel, Field, field_validator

PreferenceDirection = Literal["minimize", "maximize", "prefer"]


class Preference(BaseModel):
    """A soft (weighted) procurement preference.

    Category-agnostic: any attribute can be optimized or preferred,
    e.g. warranty maximize with weight 0.8.
    """

    attribute: str = Field(..., min_length=1)
    direction: PreferenceDirection
    weight: float = Field(..., ge=0, le=1)

    @field_validator("attribute", mode="before")
    @classmethod
    def strip_attribute(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value
