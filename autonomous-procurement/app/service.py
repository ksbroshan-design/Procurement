from __future__ import annotations

import os
import time
from typing import Any, Dict, List, Literal, Optional
from pydantic import BaseModel, ConfigDict, Field

from app.client.dto_mapper import DtoMappingError, map_procurement_request
from app.client.spring_client import (
    ResourceNotFoundError,
    SpringClientError,
    SpringProcurementClient,
)
from app.guardrails.domain_guardrail import DomainGuardrail
from app.llm.base import LLMClient
from app.llm.openai_compatible import OpenAICompatibleLLM
from app.models.presentation import (
    PurchaseOrderPresentation,
    RecommendationPresentation,
    RevalidationPresentation,
    TcoPresentation,
)
from app.models.procurement import ProcurementRequest
from app.parser.intent_parser import IntentParseError, IntentParser

# Terminal or intervention states where workflow stops
INTERVENTION_OR_TERMINAL_STATES = {
    "COMPLETED",
    "WAITING_APPROVAL",
    "WAITING_USER",
    "REJECTED",
    "FAILED",
}


class ProcessBriefResult(BaseModel):
    """Structured AI-facing result returned by process_brief."""

    model_config = ConfigDict(extra="ignore")

    status: Literal["ok", "rejected", "needs_clarification", "failed", "waiting_approval", "waiting_user"]
    is_procurement: bool = True
    reason: Optional[str] = None
    missing_information: List[str] = Field(default_factory=list)
    clarification_question: Optional[str] = None
    request: Optional[ProcurementRequest] = None
    procurement_id: Optional[str] = None
    backend_status: Optional[str] = None
    backend_summary: Optional[Dict[str, Any]] = None
    orchestration_result: Optional[Dict[str, Any]] = None
    approval_required: bool = False
    approval: Optional[Dict[str, Any]] = None
    recommendation: Optional[Dict[str, Any]] = None
    tco_breakdowns: Optional[List[Dict[str, Any]]] = None
    revalidation: Optional[Dict[str, Any]] = None
    purchase_order: Optional[Dict[str, Any]] = None
    audit_trail: Optional[Dict[str, Any]] = None
    decision_message: Optional[str] = None
    error: Optional[str] = None


class ApprovalDecisionResult(BaseModel):
    """Result returned after forwarding a human approval or rejection decision to Spring Boot."""

    model_config = ConfigDict(extra="ignore")

    procurement_id: str
    action: Literal["approved", "rejected"]
    status: Literal["ok", "failed"] = "ok"
    backend_status: str
    approval: Optional[Dict[str, Any]] = None
    backend_summary: Optional[Dict[str, Any]] = None
    orchestration_result: Optional[Dict[str, Any]] = None
    recommendation: Optional[Dict[str, Any]] = None
    tco_breakdowns: Optional[List[Dict[str, Any]]] = None
    revalidation: Optional[Dict[str, Any]] = None
    purchase_order: Optional[Dict[str, Any]] = None
    audit_trail: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class RevalidationDecisionResult(BaseModel):
    """Result returned after executing pre-purchase revalidation on Spring Boot."""

    model_config = ConfigDict(extra="ignore")

    procurement_id: str
    status: Literal["ok", "failed"] = "ok"
    backend_status: str
    revalidation: Optional[Dict[str, Any]] = None
    backend_summary: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class PurchaseExecutionResult(BaseModel):
    """Result returned after executing mock purchase on Spring Boot."""

    model_config = ConfigDict(extra="ignore")

    procurement_id: str
    status: Literal["ok", "failed"] = "ok"
    backend_status: str
    purchase_result: Optional[Dict[str, Any]] = None
    purchase_order: Optional[Dict[str, Any]] = None
    backend_summary: Optional[Dict[str, Any]] = None
    audit_trail: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class ProcurementService:
    """High-level AI Service connecting intelligence layer to authoritative Spring backend."""

    def __init__(
        self,
        llm: Optional[LLMClient] = None,
        client: Optional[SpringProcurementClient] = None,
        max_polls: int = 5,
        poll_interval: float = 0.5,
    ) -> None:
        self.llm = llm
        self.client = client
        self.max_polls = max_polls
        self.poll_interval = poll_interval

    def get_llm(self) -> LLMClient:
        if self.llm is not None:
            return self.llm
        api_key = os.getenv("OPENAI_API_KEY", "mock-key")
        model = os.getenv("LLM_MODEL", "gpt-4o")
        base_url = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
        return OpenAICompatibleLLM(api_key=api_key, model=model, base_url=base_url)

    def process_brief(
        self,
        brief: str,
        token: Optional[str] = None,
        llm: Optional[LLMClient] = None,
        client: Optional[SpringProcurementClient] = None,
        execute: bool = True,
    ) -> ProcessBriefResult:
        """Process a natural-language brief through guardrail, parser, and Spring backend."""
        active_llm = llm or self.get_llm()
        active_client = client or self.client

        # Step A: Domain Guardrail
        guardrail = DomainGuardrail(active_llm)
        guardrail_res = guardrail.check(brief)
        if not guardrail_res.is_procurement:
            return ProcessBriefResult(
                status="rejected",
                is_procurement=False,
                reason=guardrail_res.reason,
            )

        # Step B: Intent Parsing & Grounding
        parser = IntentParser(active_llm)
        try:
            intent_res = parser.parse(brief)
        except IntentParseError as e:
            return ProcessBriefResult(
                status="failed",
                is_procurement=True,
                error=f"Failed to parse procurement brief: {e}",
            )

        if intent_res.status == "needs_clarification":
            return ProcessBriefResult(
                status="needs_clarification",
                is_procurement=True,
                missing_information=intent_res.missing_information,
                clarification_question=intent_res.clarification_question,
            )

        extracted_request = intent_res.request
        if extracted_request is None:
            return ProcessBriefResult(
                status="failed",
                is_procurement=True,
                error="Parser returned OK status but missing ProcurementRequest model",
            )

        # Step C: DTO Mapping
        try:
            payload = map_procurement_request(extracted_request)
        except DtoMappingError as e:
            return ProcessBriefResult(
                status="failed",
                is_procurement=True,
                request=extracted_request,
                error=f"DTO mapping error: {e}",
            )

        # Step D: Spring Boot Client Submission
        owns_client = False
        if active_client is None:
            active_client = SpringProcurementClient(token=token)
            owns_client = True

        try:
            # 1. Create procurement
            backend_res = active_client.create_procurement(payload, token=token)
            proc_id = backend_res.get("id") if isinstance(backend_res, dict) else None
            initial_status = backend_res.get("status", "SUBMITTED") if isinstance(backend_res, dict) else "SUBMITTED"

            if not proc_id:
                return ProcessBriefResult(
                    status="failed",
                    is_procurement=True,
                    request=extracted_request,
                    error="Spring backend did not return a valid procurement ID",
                )

            # If execute=False was explicitly requested, stop after creation
            if not execute:
                return ProcessBriefResult(
                    status="ok",
                    is_procurement=True,
                    request=extracted_request,
                    procurement_id=proc_id,
                    backend_status=initial_status,
                    backend_summary=backend_res,
                )

            # 2. Trigger Spring Procurement Orchestrator
            orch_res = active_client.execute_procurement(proc_id, token=token)
            decision_msg = orch_res.get("decisionMessage") if isinstance(orch_res, dict) else None
            final_state = orch_res.get("finalState") or orch_res.get("status") or initial_status

            # 3. Bounded State Polling if needed
            backend_summary: Dict[str, Any] = {}
            current_state = final_state
            if current_state not in INTERVENTION_OR_TERMINAL_STATES:
                for _ in range(self.max_polls):
                    time.sleep(self.poll_interval)
                    try:
                        poll_summary = active_client.get_procurement(proc_id, token=token)
                        if isinstance(poll_summary, dict):
                            backend_summary = poll_summary
                            current_state = poll_summary.get("status", current_state)
                            if current_state in INTERVENTION_OR_TERMINAL_STATES:
                                break
                    except SpringClientError:
                        break

            # If summary wasn't retrieved during polling, retrieve latest summary
            if not backend_summary:
                try:
                    summary_res = active_client.get_procurement(proc_id, token=token)
                    if isinstance(summary_res, dict):
                        backend_summary = summary_res
                        current_state = summary_res.get("status", current_state)
                except SpringClientError:
                    backend_summary = backend_res

            # Safe artifact retrieval helpers
            def safe_get_recommendation() -> Optional[Dict[str, Any]]:
                try:
                    return active_client.get_recommendation(proc_id, token=token)
                except SpringClientError:
                    return None

            def safe_get_tco() -> Optional[List[Dict[str, Any]]]:
                try:
                    return active_client.get_tco(proc_id, token=token)
                except SpringClientError:
                    return None

            def safe_get_revalidation() -> Optional[Dict[str, Any]]:
                if current_state != "REVALIDATING":
                    return None
                try:
                    return active_client.get_revalidation(proc_id, token=token)
                except SpringClientError:
                    return None


            # 4. Handle Specific Backend States
            # A. WAITING_APPROVAL -> STOP! Retrieve approval, recommendation, TCO.
            if current_state == "WAITING_APPROVAL":
                approval_info: Optional[Dict[str, Any]] = None
                try:
                    approval_info = active_client.get_approval(proc_id, token=token)
                except SpringClientError:
                    approval_info = None

                return ProcessBriefResult(
                    status="waiting_approval",
                    is_procurement=True,
                    procurement_id=proc_id,
                    backend_status="WAITING_APPROVAL",
                    backend_summary=backend_summary,
                    orchestration_result=orch_res,
                    approval_required=True,
                    approval=approval_info,
                    recommendation=safe_get_recommendation(),
                    tco_breakdowns=safe_get_tco(),
                    decision_message=decision_msg or "Procurement requires manager approval due to authorization limit.",
                    request=extracted_request,
                )

            # B. WAITING_USER -> STOP! Surface reason and revalidation context if available.
            if current_state == "WAITING_USER":
                return ProcessBriefResult(
                    status="waiting_user",
                    is_procurement=True,
                    procurement_id=proc_id,
                    backend_status="WAITING_USER",
                    backend_summary=backend_summary,
                    orchestration_result=orch_res,
                    recommendation=safe_get_recommendation(),
                    revalidation=safe_get_revalidation(),
                    decision_message=decision_msg or "Procurement is waiting for user input.",
                    request=extracted_request,
                )

            # C. FAILED or REJECTED -> Return structured failure
            if current_state in ("FAILED", "REJECTED"):
                return ProcessBriefResult(
                    status="failed",
                    is_procurement=True,
                    procurement_id=proc_id,
                    backend_status=current_state,
                    backend_summary=backend_summary,
                    orchestration_result=orch_res,
                    decision_message=decision_msg,
                    error=decision_msg or f"Procurement resulted in {current_state}",
                    request=extracted_request,
                )

            # D. COMPLETED -> Retrieve Recommendation, TCO, PurchaseOrder, and Audit Trail
            if current_state == "COMPLETED":
                po_info: Optional[Dict[str, Any]] = None
                try:
                    po_info = active_client.get_purchase_order(proc_id, token=token)
                except (ResourceNotFoundError, SpringClientError):
                    po_info = None

                audit_info: Optional[Dict[str, Any]] = None
                try:
                    audit_info = active_client.get_audit_trail(proc_id, token=token)
                except (ResourceNotFoundError, SpringClientError):
                    audit_info = None

                return ProcessBriefResult(
                    status="ok",
                    is_procurement=True,
                    procurement_id=proc_id,
                    backend_status="COMPLETED",
                    backend_summary=backend_summary,
                    orchestration_result=orch_res,
                    recommendation=safe_get_recommendation(),
                    tco_breakdowns=safe_get_tco(),
                    revalidation=safe_get_revalidation(),
                    purchase_order=po_info,
                    audit_trail=audit_info,
                    decision_message=decision_msg or "Procurement successfully executed and completed.",
                    request=extracted_request,
                )

            # E. Other non-terminal / intermediate states (e.g. SEARCHING, REVALIDATING, PURCHASING)
            return ProcessBriefResult(
                status="ok",
                is_procurement=True,
                procurement_id=proc_id,
                backend_status=current_state,
                backend_summary=backend_summary,
                orchestration_result=orch_res,
                recommendation=safe_get_recommendation(),
                tco_breakdowns=safe_get_tco(),
                revalidation=safe_get_revalidation(),
                decision_message=decision_msg,
                request=extracted_request,
            )

        except SpringClientError as e:
            return ProcessBriefResult(
                status="failed",
                is_procurement=True,
                request=extracted_request,
                error=f"Spring backend error: {e.message}",
            )
        finally:
            if owns_client:
                active_client.close()

    def approve_procurement(
        self,
        procurement_id: str,
        comments: Optional[str] = None,
        approved_offer_id: Optional[str] = None,
        token: Optional[str] = None,
        resume_execution: bool = True,
        client: Optional[SpringProcurementClient] = None,
    ) -> ApprovalDecisionResult:
        """Forwards an explicit human approval decision to the authoritative Spring Boot backend."""
        active_client = client or self.client
        owns_client = False
        if active_client is None:
            active_client = SpringProcurementClient(token=token)
            owns_client = True

        try:
            # 1. POST /api/procurements/{id}/approval/approve
            approval_res = active_client.approve_procurement(
                procurement_id=procurement_id,
                comments=comments,
                approved_offer_id=approved_offer_id,
                token=token,
            )

            # 2. If resume_execution is requested, trigger orchestrator to advance REVALIDATING -> PURCHASING -> COMPLETED
            orch_res: Optional[Dict[str, Any]] = None
            if resume_execution:
                try:
                    orch_res = active_client.execute_procurement(procurement_id, token=token)
                except SpringClientError:
                    orch_res = None

            # 3. Retrieve authoritative state and artifacts
            summary = active_client.get_procurement(procurement_id, token=token)
            current_state = summary.get("status", "REVALIDATING")

            rec_info: Optional[Dict[str, Any]] = None
            try:
                rec_info = active_client.get_recommendation(procurement_id, token=token)
            except SpringClientError:
                rec_info = None

            tco_info: Optional[List[Dict[str, Any]]] = None
            try:
                tco_info = active_client.get_tco(procurement_id, token=token)
            except SpringClientError:
                tco_info = None

            po_info: Optional[Dict[str, Any]] = None
            if current_state == "COMPLETED":
                try:
                    po_info = active_client.get_purchase_order(procurement_id, token=token)
                except SpringClientError:
                    po_info = None

            audit_info: Optional[Dict[str, Any]] = None
            try:
                audit_info = active_client.get_audit_trail(procurement_id, token=token)
            except SpringClientError:
                audit_info = None

            return ApprovalDecisionResult(
                procurement_id=procurement_id,
                action="approved",
                status="ok",
                backend_status=current_state,
                approval=approval_res,
                backend_summary=summary,
                orchestration_result=orch_res,
                recommendation=rec_info,
                tco_breakdowns=tco_info,
                purchase_order=po_info,
                audit_trail=audit_info,
            )

        except SpringClientError as e:
            return ApprovalDecisionResult(
                procurement_id=procurement_id,
                action="approved",
                status="failed",
                backend_status="UNKNOWN",
                error=f"Spring approval error: {e.message}",
            )
        finally:
            if owns_client:
                active_client.close()

    def reject_procurement(
        self,
        procurement_id: str,
        comments: Optional[str] = None,
        token: Optional[str] = None,
        client: Optional[SpringProcurementClient] = None,
    ) -> ApprovalDecisionResult:
        """Forwards an explicit human rejection decision to the authoritative Spring Boot backend."""
        active_client = client or self.client
        owns_client = False
        if active_client is None:
            active_client = SpringProcurementClient(token=token)
            owns_client = True

        try:
            # 1. POST /api/procurements/{id}/approval/reject
            approval_res = active_client.reject_procurement(
                procurement_id=procurement_id,
                comments=comments,
                token=token,
            )

            # 2. Retrieve authoritative state
            summary = active_client.get_procurement(procurement_id, token=token)
            current_state = summary.get("status", "REJECTED")

            audit_info: Optional[Dict[str, Any]] = None
            try:
                audit_info = active_client.get_audit_trail(procurement_id, token=token)
            except SpringClientError:
                audit_info = None

            return ApprovalDecisionResult(
                procurement_id=procurement_id,
                action="rejected",
                status="ok",
                backend_status=current_state,
                approval=approval_res,
                backend_summary=summary,
                audit_trail=audit_info,
            )
        except SpringClientError as e:
            return ApprovalDecisionResult(
                procurement_id=procurement_id,
                action="rejected",
                status="failed",
                backend_status="UNKNOWN",
                error=f"Spring rejection error: {e.message}",
            )
        finally:
            if owns_client:
                active_client.close()

    def revalidate_procurement(
        self,
        procurement_id: str,
        token: Optional[str] = None,
        client: Optional[SpringProcurementClient] = None,
    ) -> RevalidationDecisionResult:
        """Triggers pre-purchase revalidation on the authoritative Spring Boot backend."""
        active_client = client or self.client
        owns_client = False
        if active_client is None:
            active_client = SpringProcurementClient(token=token)
            owns_client = True

        try:
            reval_res = active_client.revalidate(procurement_id=procurement_id, token=token)
            summary = active_client.get_procurement(procurement_id, token=token)
            current_state = summary.get("status", reval_res.get("nextState", "UNKNOWN"))

            return RevalidationDecisionResult(
                procurement_id=procurement_id,
                status="ok",
                backend_status=current_state,
                revalidation=reval_res,
                backend_summary=summary,
            )
        except SpringClientError as e:
            return RevalidationDecisionResult(
                procurement_id=procurement_id,
                status="failed",
                backend_status="UNKNOWN",
                error=f"Spring revalidation error: {e.message}",
            )
        finally:
            if owns_client:
                active_client.close()

    def purchase_procurement(
        self,
        procurement_id: str,
        token: Optional[str] = None,
        client: Optional[SpringProcurementClient] = None,
    ) -> PurchaseExecutionResult:
        """Executes mock purchase and confirms PurchaseOrder on the authoritative Spring Boot backend."""
        active_client = client or self.client
        owns_client = False
        if active_client is None:
            active_client = SpringProcurementClient(token=token)
            owns_client = True

        try:
            purchase_res = active_client.purchase(procurement_id=procurement_id, token=token)
            summary = active_client.get_procurement(procurement_id, token=token)
            current_state = summary.get("status", "COMPLETED")

            po_info: Optional[Dict[str, Any]] = None
            try:
                po_info = active_client.get_purchase_order(procurement_id, token=token)
            except SpringClientError:
                po_info = None

            audit_info: Optional[Dict[str, Any]] = None
            try:
                audit_info = active_client.get_audit_trail(procurement_id, token=token)
            except SpringClientError:
                audit_info = None

            return PurchaseExecutionResult(
                procurement_id=procurement_id,
                status="ok",
                backend_status=current_state,
                purchase_result=purchase_res,
                purchase_order=po_info,
                backend_summary=summary,
                audit_trail=audit_info,
            )
        except SpringClientError as e:
            return PurchaseExecutionResult(
                procurement_id=procurement_id,
                status="failed",
                backend_status="UNKNOWN",
                error=f"Spring purchase execution error: {e.message}",
            )
        finally:
            if owns_client:
                active_client.close()


# Convenience functions
def process_brief(
    brief: str,
    token: Optional[str] = None,
    llm: Optional[LLMClient] = None,
    client: Optional[SpringProcurementClient] = None,
    execute: bool = True,
) -> ProcessBriefResult:
    """High-level function to process a procurement brief."""
    service = ProcurementService(llm=llm, client=client)
    return service.process_brief(brief=brief, token=token, execute=execute)


def approve_procurement(
    procurement_id: str,
    comments: Optional[str] = None,
    approved_offer_id: Optional[str] = None,
    token: Optional[str] = None,
    resume_execution: bool = True,
    client: Optional[SpringProcurementClient] = None,
) -> ApprovalDecisionResult:
    """High-level function to approve a pending procurement."""
    service = ProcurementService(client=client)
    return service.approve_procurement(
        procurement_id=procurement_id,
        comments=comments,
        approved_offer_id=approved_offer_id,
        token=token,
        resume_execution=resume_execution,
        client=client,
    )


def reject_procurement(
    procurement_id: str,
    comments: Optional[str] = None,
    token: Optional[str] = None,
    client: Optional[SpringProcurementClient] = None,
) -> ApprovalDecisionResult:
    """High-level function to reject a pending procurement."""
    service = ProcurementService(client=client)
    return service.reject_procurement(
        procurement_id=procurement_id,
        comments=comments,
        token=token,
        client=client,
    )


def revalidate_procurement(
    procurement_id: str,
    token: Optional[str] = None,
    client: Optional[SpringProcurementClient] = None,
) -> RevalidationDecisionResult:
    """High-level function to revalidate a procurement request."""
    service = ProcurementService(client=client)
    return service.revalidate_procurement(
        procurement_id=procurement_id,
        token=token,
        client=client,
    )


def purchase_procurement(
    procurement_id: str,
    token: Optional[str] = None,
    client: Optional[SpringProcurementClient] = None,
) -> PurchaseExecutionResult:
    """High-level function to execute mock purchase for a procurement request."""
    service = ProcurementService(client=client)
    return service.purchase_procurement(
        procurement_id=procurement_id,
        token=token,
        client=client,
    )
