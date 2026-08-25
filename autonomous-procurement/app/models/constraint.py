from typing import Literal, Union

from pydantic import BaseModel, Field, field_validator

ConstraintOperator = Literal["<=", ">=", "=", "<", ">", "contains"]
ConstraintValue = Union[int, float, str]


class Constraint(BaseModel):
    """A mandatory (or optional) procurement requirement.

    Category-agnostic: any product attribute can be expressed as
    attribute + operator + value, e.g. price <= 45000 INR.
    """

    attribute: str = Field(..., min_length=1)
    operator: ConstraintOperator
    value: ConstraintValue
    unit: str | None = Field(default=None, min_length=1)
    mandatory: bool = True

    @field_validator("attribute", "unit", mode="before")
    @classmethod
    def strip_optional_text(cls, value: object) -> object:
        if isinstance(value, str):
            stripped = value.strip()
            if stripped == "":
                return None
            return stripped
        return value
