from __future__ import annotations

from typing import Any, Dict, Optional
from enum import Enum

from pydantic import BaseModel, Field
from app.recommendation.models import RankedProduct


class ApprovalStatus(str, Enum):
    pending = "pending"
    approved = "approved"
    rejected = "rejected"


class AuthorizationDecision(BaseModel):
    """Represents an automated authorization decision.

    - allowed: whether the system can proceed without human approval
    - requires_human_approval: whether human approval is required
    - reason: textual explanation
    - requested_amount: the product price or requested budget override
    - authorization_limit: the system's configured automatic approval limit (if any)
    - budget_limit: the requestor's budget
    - exceeded_by: amount exceeded (if any)
    """

    allowed: bool
    requires_human_approval: bool
    reason: Optional[str] = None
    requested_amount: Optional[float] = None
    authorization_limit: Optional[float] = None
    budget_limit: Optional[float] = None
    exceeded_by: Optional[float] = None


class ApprovalRequest(BaseModel):
    """Pending human approval request payload.

    - request_id: unique identifier for tracking (string)
    - recommended_product: the product recommended (RankedProduct)
    - requested_amount: price requested for approval
    - reason: why approval is requested (budget exception, policy, etc.)
    - details: structured info such as constraint or budget exception details
    - tco_comparison: optional TCO information for explainability
    - status: current approval status
    """

    request_id: str
    recommended_product: RankedProduct
    requested_amount: float
    reason: str
    details: Optional[Dict[str, Any]] = None
    tco_comparison: Optional[Dict[str, Any]] = None
    status: ApprovalStatus = ApprovalStatus.pending


class ApprovalDecision(BaseModel):
    """Result of a human approval decision.

    - approval_id: unique id for the decision event
    - approved: whether the approval was granted
    - approver: optional identity or reference
    - reason: optional explanation
    - timestamp: optional timestamp string
    """

    approval_id: str
    approved: bool
    approver: Optional[str] = None
    reason: Optional[str] = None
    timestamp: Optional[str] = None
