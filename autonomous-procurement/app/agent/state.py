from __future__ import annotations

from enum import Enum
from typing import Any, Dict, List, Optional
from datetime import datetime

from pydantic import BaseModel, Field

from app.models.procurement import ProcurementRequest


class Stage(str, Enum):
    START = "START"
    DOMAIN_CHECK = "DOMAIN_CHECK"
    INTENT_PARSING = "INTENT_PARSING"
    VALIDATION = "VALIDATION"
    DISCOVERY = "DISCOVERY"
    COMPARISON = "COMPARISON"
    TCO_ANALYSIS = "TCO_ANALYSIS"
    AUTHORIZATION = "AUTHORIZATION"
    HUMAN_APPROVAL = "HUMAN_APPROVAL"
    REVALIDATION = "REVALIDATION"
    PURCHASE = "PURCHASE"
    AUDIT = "AUDIT"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class Status(str, Enum):
    RUNNING = "RUNNING"
    WAITING_FOR_HUMAN = "WAITING_FOR_HUMAN"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class AuditEvent(BaseModel):
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")
    stage: Stage
    message: str
    details: Optional[Dict[str, Any]] = None


class ProcurementState(BaseModel):
    """Lightweight state model for a procurement workflow.

    Notes:
    - This is a simple, serializable snapshot of the workflow. Business logic
      should live in the workflow/orchestration code, not here.
    - Product/vendor fields are intentionally generic to support any category.
    """

    request_id: str
    original_brief: str

    # The parsed procurement request (when parsing succeeds)
    current_request: Optional[ProcurementRequest] = None

    # Stage and status
    stage: Stage = Stage.START
    status: Status = Status.RUNNING

    # Error information (kept simple)
    error: Optional[str] = None

    # Human approval flow
    human_approval_required: bool = False
    human_approval_result: Optional[bool] = None

    # Selected product/vendor info (generic structure)
    selected_product: Optional[Dict[str, Any]] = None
    selected_vendor: Optional[Dict[str, Any]] = None

    # Audit / event history
    events: List[AuditEvent] = Field(default_factory=list)

    def add_event(self, stage: Stage, message: str, details: Optional[Dict[str, Any]] = None) -> None:
        self.events.append(AuditEvent(stage=stage, message=message, details=details))

