from __future__ import annotations

from typing import Any, Dict, List
from app.models.constraint import Constraint
from app.models.procurement import ProcurementRequest


class DtoMappingError(Exception):
    """Raised when mapping a Python model to a Spring DTO fails."""


# Recognized and supported constraint operators in Spring Boot
OPERATOR_MAP: Dict[str, str] = {
    ">=": ">=",
    "GREATER_THAN_OR_EQUAL": ">=",
    "GTE": ">=",
    "<=": "<=",
    "LESS_THAN_OR_EQUAL": "<=",
    "LTE": "<=",
    "=": "==",
    "==": "==",
    "EQUALS": "==",
    "EQ": "==",
    ">": ">",
    "GREATER_THAN": ">",
    "GT": ">",
    "<": "<",
    "LESS_THAN": "<",
    "LT": "<",
    "!=": "!=",
    "NOT_EQUALS": "!=",
    "NEQ": "!=",
    "CONTAINS": "CONTAINS",
    "contains": "CONTAINS",
    "IN": "IN",
    "in": "IN",
}


def map_constraint(constraint: Constraint) -> Dict[str, Any]:
    """Convert a Python Constraint model into a Spring ConstraintInputDto payload."""
    if not constraint.attribute or not constraint.attribute.strip():
        raise DtoMappingError("Constraint attribute cannot be empty")

    raw_op = constraint.operator.strip() if isinstance(constraint.operator, str) else str(constraint.operator)
    if raw_op not in OPERATOR_MAP:
        raise DtoMappingError(f"Unsupported constraint operator: '{constraint.operator}'")

    normalized_op = OPERATOR_MAP[raw_op]

    # Convert value to clean string representation as expected by Spring
    if constraint.value is None:
        raise DtoMappingError(f"Constraint value for attribute '{constraint.attribute}' cannot be None")
    
    val_str = str(constraint.value).strip()
    if not val_str:
        raise DtoMappingError(f"Constraint value for attribute '{constraint.attribute}' cannot be empty")

    return {
        "attribute": constraint.attribute.strip(),
        "operator": normalized_op,
        "value": val_str,
        "mandatory": bool(constraint.mandatory),
    }


def map_procurement_request(request: ProcurementRequest) -> Dict[str, Any]:
    """Convert a Python ProcurementRequest model into a Spring CreateProcurementRequestDto payload."""
    if not request.category or not request.category.strip():
        raise DtoMappingError("Procurement category is required and cannot be empty")

    if request.quantity is None or request.quantity <= 0:
        raise DtoMappingError(f"Procurement quantity must be positive, got: {request.quantity}")

    # Determine effective authorization limit (default to 0.0 if not specified)
    auth_limit: float = 0.0
    if request.authorization_limit is not None and request.authorization_limit > 0:
        auth_limit = float(request.authorization_limit)
    elif request.budget is not None and request.budget > 0:
        # Fallback to total budget (unit budget * quantity) if explicit authorization_limit not provided
        auth_limit = float(request.budget * request.quantity)

    mapped_constraints: List[Dict[str, Any]] = []
    has_price_constraint = False
    has_delivery_constraint = False

    # Map existing constraints
    for c in request.constraints:
        mapped_c = map_constraint(c)
        attr_lower = mapped_c["attribute"].lower()
        if attr_lower in ("price", "cost", "budget", "unitprice", "unit_price"):
            has_price_constraint = True
        if attr_lower in ("delivery", "deliverydays", "delivery_days", "shippingdays", "shipping_days"):
            has_delivery_constraint = True
        mapped_constraints.append(mapped_c)

    # Budget grounding: if budget is provided and no price constraint exists, add deterministic price constraint
    if request.budget is not None and request.budget > 0 and not has_price_constraint:
        mapped_constraints.append({
            "attribute": "price",
            "operator": "<=",
            "value": str(request.budget),
            "mandatory": True,
        })

    # Delivery deadline grounding: if max_delivery_days is provided and no delivery constraint exists, add deterministic delivery constraint
    if request.max_delivery_days is not None and request.max_delivery_days > 0 and not has_delivery_constraint:
        mapped_constraints.append({
            "attribute": "deliveryDays",
            "operator": "<=",
            "value": str(request.max_delivery_days),
            "mandatory": True,
        })

    return {
        "category": request.category.strip(),
        "quantity": int(request.quantity),
        "authorizationLimit": round(auth_limit, 2),
        "constraints": mapped_constraints,
    }
