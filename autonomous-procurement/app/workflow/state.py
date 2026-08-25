from __future__ import annotations

from typing import Any, Dict, List, Optional
from datetime import datetime

from pydantic import BaseModel, Field

from app.models.procurement import ProcurementRequest
from app.guardrails.domain_guardrail import GuardrailResult
from app.discovery.models import DiscoveryResult, VendorProduct
from app.recommendation.models import ProductEvaluation, TCOResult, RecommendationResult
from app.authorization.models import AuthorizationDecision, ApprovalRequest, ApprovalDecision
from app.agent.state import Stage, Status, AuditEvent


class ProcurementState(BaseModel):
    """Aggregated workflow state suitable for LangGraph-style orchestration.

    This is a serializable snapshot of the end-to-end procurement workflow.
    It intentionally reuses existing Pydantic models from other modules so
    components remain loosely coupled.
    """

    request_id: str
    original_brief: str

    # Parsed request
    procurement_request: Optional[ProcurementRequest] = None

    # Guardrail result (procurement domain check)
    guardrail_result: Optional[GuardrailResult] = None

    # Discovery and normalization
    discovery_result: Optional[DiscoveryResult] = None
    normalized_products: List[VendorProduct] = Field(default_factory=list)

    # Evaluations and TCO
    evaluations: List[ProductEvaluation] = Field(default_factory=list)
    tco_results: Dict[str, TCOResult] = Field(default_factory=dict)

    # Recommendation and authorization artifacts
    recommendation: Optional[RecommendationResult] = None
    authorization_decision: Optional[AuthorizationDecision] = None
    approval_request: Optional[ApprovalRequest] = None
    approval_decision: Optional[ApprovalDecision] = None

    # Revalidation, purchase order, and other downstream artifacts (kept generic)
    revalidation_result: Optional[Dict[str, Any]] = None
    purchase_order: Optional[Dict[str, Any]] = None

    # Audit events and workflow control
    events: List[AuditEvent] = Field(default_factory=list)
    current_stage: Stage = Stage.START
    status: Status = Status.RUNNING

    # Error and retry counters
    error: Optional[str] = None
    retry_count: int = 0

    # Utility helpers
    def add_event(self, stage: Stage, message: str, details: Optional[Dict[str, Any]] = None) -> None:
        self.events.append(AuditEvent(timestamp=datetime.utcnow().isoformat() + "Z", stage=stage, message=message, details=details))


# Backwards-compat alias for convenience
WorkflowState = ProcurementState
