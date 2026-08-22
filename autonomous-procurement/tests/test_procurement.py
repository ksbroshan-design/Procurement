import pytest
from pydantic import ValidationError

from app.models.constraint import Constraint
from app.models.preference import Preference
from app.models.procurement import ProcurementRequest


def test_laptop_procurement_request():
    request = ProcurementRequest(
        original_brief="Need 2 laptops under 45000 INR with 16GB RAM",
        category="laptop",
        quantity=2,
        constraints=[
            Constraint(attribute="price", operator="<=", value=45000, unit="INR"),
            Constraint(attribute="RAM", operator=">=", value=16, unit="GB"),
            Constraint(attribute="storage", operator=">=", value=512, unit="GB"),
            Constraint(attribute="display_type", operator="=", value="OLED"),
        ],
        preferences=[
            Preference(attribute="warranty", direction="maximize", weight=0.8),
            Preference(attribute="reliability", direction="maximize", weight=0.9),
        ],
        budget=45000,
        max_delivery_days=10,
    )

    assert request.category == "laptop"
    assert request.quantity == 2
    assert len(request.constraints) == 4
    assert len(request.preferences) == 2
    assert request.budget == 45000
    assert request.max_delivery_days == 10


def test_tv_procurement_request():
    request = ProcurementRequest(
        original_brief="Buy 1 55-inch TV under 40000 INR, prefer higher refresh rate",
        category="TV",
        quantity=1,
        constraints=[
            Constraint(attribute="price", operator="<=", value=40000, unit="INR"),
            Constraint(attribute="screen_size", operator=">=", value=55, unit="inch"),
        ],
        preferences=[
            Preference(attribute="refresh_rate", direction="maximize", weight=0.6),
        ],
        budget=40000,
        max_delivery_days=5,
    )

    assert request.category == "TV"
    assert request.quantity == 1
    assert request.constraints[0].attribute == "price"
    assert request.preferences[0].attribute == "refresh_rate"
    assert request.budget == 40000
    assert request.max_delivery_days == 5


def test_tablet_procurement_request():
    request = ProcurementRequest(
        original_brief=(
            "Buy 10 tablets under 30000 INR each, with at least 8GB RAM "
            "and 256GB storage, delivery within 7 days. Prefer longer warranty."
        ),
        category="tablet",
        quantity=10,
        constraints=[
            Constraint(attribute="price", operator="<=", value=30000, unit="INR"),
            Constraint(attribute="RAM", operator=">=", value=8, unit="GB"),
            Constraint(attribute="storage", operator=">=", value=256, unit="GB"),
        ],
        preferences=[
            Preference(attribute="warranty", direction="maximize", weight=0.8),
        ],
        budget=30000,
        max_delivery_days=7,
    )

    assert request.category == "tablet"
    assert request.quantity == 10
    assert [item.attribute for item in request.constraints] == ["price", "RAM", "storage"]
    assert request.preferences[0].direction == "maximize"
    assert request.budget == 30000
    assert request.max_delivery_days == 7


def test_office_chair_procurement_request():
    request = ProcurementRequest(
        original_brief="Procure 20 ergonomic office chairs under 8000 INR, prefer lumbar support",
        category="office chair",
        quantity=20,
        constraints=[
            Constraint(attribute="price", operator="<=", value=8000, unit="INR"),
            Constraint(attribute="type", operator="=", value="ergonomic"),
        ],
        preferences=[
            Preference(attribute="lumbar_support", direction="prefer", weight=0.75),
            Preference(attribute="delivery_time", direction="minimize", weight=0.7),
        ],
        budget=8000,
        max_delivery_days=14,
    )

    assert request.category == "office chair"
    assert request.quantity == 20
    assert len(request.constraints) == 2
    assert len(request.preferences) == 2
    assert request.budget == 8000
    assert request.max_delivery_days == 14


def test_quantity_zero_is_invalid():
    with pytest.raises(ValidationError):
        ProcurementRequest(
            original_brief="Need laptops",
            category="laptop",
            quantity=0,
        )


def test_negative_quantity_is_invalid():
    with pytest.raises(ValidationError):
        ProcurementRequest(
            original_brief="Need laptops",
            category="laptop",
            quantity=-3,
        )


def test_missing_required_fields_are_invalid():
    with pytest.raises(ValidationError):
        ProcurementRequest(quantity=1)


def test_empty_category_is_invalid():
    with pytest.raises(ValidationError):
        ProcurementRequest(
            original_brief="Need a TV",
            category="",
            quantity=1,
        )


def test_negative_budget_is_invalid():
    with pytest.raises(ValidationError):
        ProcurementRequest(
            original_brief="Need a tablet",
            category="tablet",
            quantity=1,
            budget=-1,
        )


def test_nested_invalid_constraint_makes_request_invalid():
    with pytest.raises(ValidationError):
        ProcurementRequest(
            original_brief="Need a laptop",
            category="laptop",
            quantity=1,
            constraints=[
                {"attribute": "price", "operator": "!=", "value": 1000},
            ],
        )


def test_procurement_request_nests_constraints_and_preferences():
    """Integration: ProcurementRequest contains Constraint[] and Preference[]."""
    request = ProcurementRequest(
        original_brief="Buy 4 laptops under 50000 INR, prefer longer warranty",
        category="laptop",
        quantity=4,
        constraints=[
            Constraint(attribute="price", operator="<=", value=50000, unit="INR"),
            Constraint(attribute="RAM", operator=">=", value=16, unit="GB"),
            Constraint(attribute="warranty", operator=">=", value=3, unit="years"),
        ],
        preferences=[
            Preference(attribute="warranty", direction="maximize", weight=0.8),
            Preference(attribute="delivery_time", direction="minimize", weight=0.7),
        ],
        budget=50000,
        max_delivery_days=7,
    )

    assert isinstance(request, ProcurementRequest)
    assert isinstance(request.constraints, list)
    assert isinstance(request.preferences, list)
    assert all(isinstance(item, Constraint) for item in request.constraints)
    assert all(isinstance(item, Preference) for item in request.preferences)

    price_rule = request.constraints[0]
    assert price_rule.attribute == "price"
    assert price_rule.operator == "<="
    assert price_rule.value == 50000
    assert price_rule.unit == "INR"

    warranty_pref = request.preferences[0]
    assert warranty_pref.attribute == "warranty"
    assert warranty_pref.direction == "maximize"
    assert warranty_pref.weight == 0.8
