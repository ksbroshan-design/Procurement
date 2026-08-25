import pytest
from pydantic import ValidationError

from app.models.preference import Preference


@pytest.mark.parametrize(
    ("attribute", "direction", "weight"),
    [
        ("warranty", "maximize", 0.8),
        ("reliability", "maximize", 0.9),
        ("delivery_time", "minimize", 0.7),
    ],
)
def test_valid_preferences(attribute, direction, weight):
    preference = Preference(
        attribute=attribute,
        direction=direction,
        weight=weight,
    )

    assert preference.attribute == attribute
    assert preference.direction == direction
    assert preference.weight == weight


def test_prefer_direction_is_valid():
    preference = Preference(attribute="brand", direction="prefer", weight=0.5)

    assert preference.direction == "prefer"


def test_weight_boundary_values_are_valid():
    lowest = Preference(attribute="noise", direction="minimize", weight=0)
    highest = Preference(attribute="warranty", direction="maximize", weight=1)

    assert lowest.weight == 0
    assert highest.weight == 1


def test_weight_below_zero_is_invalid():
    with pytest.raises(ValidationError):
        Preference(attribute="warranty", direction="maximize", weight=-0.1)


def test_weight_above_one_is_invalid():
    with pytest.raises(ValidationError):
        Preference(attribute="warranty", direction="maximize", weight=1.1)


def test_missing_weight_is_invalid():
    with pytest.raises(ValidationError):
        Preference(attribute="warranty", direction="maximize")


def test_missing_attribute_is_invalid():
    with pytest.raises(ValidationError):
        Preference(direction="maximize", weight=0.8)


def test_empty_attribute_is_invalid():
    with pytest.raises(ValidationError):
        Preference(attribute="", direction="maximize", weight=0.8)


def test_invalid_direction_is_rejected():
    with pytest.raises(ValidationError):
        Preference(attribute="warranty", direction="highest", weight=0.8)
