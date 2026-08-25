import pytest
from pydantic import ValidationError

from app.models.constraint import Constraint


@pytest.mark.parametrize(
    ("attribute", "operator", "value", "unit"),
    [
        ("price", "<=", 45000, "INR"),
        ("RAM", ">=", 16, "GB"),
        ("storage", ">=", 512, "GB"),
        ("display_type", "=", "OLED", None),
        ("warranty", ">=", 3, "years"),
    ],
)
def test_valid_constraints(attribute, operator, value, unit):
    constraint = Constraint(
        attribute=attribute,
        operator=operator,
        value=value,
        unit=unit,
    )

    assert constraint.attribute == attribute
    assert constraint.operator == operator
    assert constraint.value == value
    assert constraint.unit == unit
    assert constraint.mandatory is True


def test_string_and_numeric_values_are_accepted():
    numeric = Constraint(attribute="price", operator="<=", value=45000, unit="INR")
    textual = Constraint(attribute="display_type", operator="=", value="OLED")

    assert isinstance(numeric.value, int)
    assert isinstance(textual.value, str)


def test_contains_operator_is_valid():
    constraint = Constraint(
        attribute="description",
        operator="contains",
        value="waterproof",
    )

    assert constraint.operator == "contains"


def test_mandatory_can_be_set_to_false():
    constraint = Constraint(
        attribute="color",
        operator="=",
        value="black",
        mandatory=False,
    )

    assert constraint.mandatory is False


def test_missing_attribute_is_invalid():
    with pytest.raises(ValidationError):
        Constraint(operator="<=", value=45000, unit="INR")


def test_empty_attribute_is_invalid():
    with pytest.raises(ValidationError):
        Constraint(attribute="", operator="<=", value=45000, unit="INR")


def test_whitespace_attribute_is_invalid():
    with pytest.raises(ValidationError):
        Constraint(attribute="   ", operator="<=", value=45000, unit="INR")


def test_missing_operator_is_invalid():
    with pytest.raises(ValidationError):
        Constraint(attribute="price", value=45000, unit="INR")


def test_invalid_operator_is_rejected():
    with pytest.raises(ValidationError):
        Constraint(attribute="price", operator="!=", value=45000, unit="INR")


def test_missing_value_is_invalid():
    with pytest.raises(ValidationError):
        Constraint(attribute="price", operator="<=", unit="INR")


def test_blank_unit_becomes_none():
    constraint = Constraint(attribute="display_type", operator="=", value="OLED", unit="")

    assert constraint.unit is None
