from __future__ import annotations

import os
from typing import Any, Dict, Optional
from fastapi import FastAPI, Header, HTTPException, status
from pydantic import BaseModel, ConfigDict, Field

from app.service import (
    ApprovalDecisionResult,
    ProcessBriefResult,
    PurchaseExecutionResult,
    RevalidationDecisionResult,
    approve_procurement,
    process_brief,
    purchase_procurement,
    reject_procurement,
    revalidate_procurement,
)

from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(
    title="Autonomous Procurement AI Service",
    description="Intelligence and intent processing service for Autonomous Procurement Engine",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)



def _extract_token(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    if authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return authorization.strip()


class ProcessBriefRequestBody(BaseModel):
    model_config = ConfigDict(extra="ignore")
    brief: str
    execute: bool = True


class ApprovalRequestBody(BaseModel):
    model_config = ConfigDict(extra="ignore")
    procurement_id: str
    comments: Optional[str] = None
    approved_offer_id: Optional[str] = None
    resume_execution: bool = True


class RejectionRequestBody(BaseModel):
    model_config = ConfigDict(extra="ignore")
    procurement_id: str
    comments: Optional[str] = None


class RevalidateRequestBody(BaseModel):
    model_config = ConfigDict(extra="ignore")
    procurement_id: str


class PurchaseRequestBody(BaseModel):
    model_config = ConfigDict(extra="ignore")
    procurement_id: str


@app.get("/health")
def health_check() -> Dict[str, str]:
    """Health check endpoint for container health probes."""
    return {"status": "UP", "service": "ai-service"}


@app.post("/api/ai/process", response_model=ProcessBriefResult)
def api_process_brief(
    body: ProcessBriefRequestBody,
    authorization: Optional[str] = Header(None),
) -> ProcessBriefResult:
    """Process a natural language procurement brief."""
    token = _extract_token(authorization)
    result = process_brief(brief=body.brief, token=token, execute=body.execute)
    return result


@app.post("/api/ai/approve", response_model=ApprovalDecisionResult)
def api_approve_procurement(
    body: ApprovalRequestBody,
    authorization: Optional[str] = Header(None),
) -> ApprovalDecisionResult:
    """Forward a human approval decision to the Spring Boot backend."""
    token = _extract_token(authorization)
    result = approve_procurement(
        procurement_id=body.procurement_id,
        comments=body.comments,
        approved_offer_id=body.approved_offer_id,
        token=token,
        resume_execution=body.resume_execution,
    )
    return result


@app.post("/api/ai/reject", response_model=ApprovalDecisionResult)
def api_reject_procurement(
    body: RejectionRequestBody,
    authorization: Optional[str] = Header(None),
) -> ApprovalDecisionResult:
    """Forward a human rejection decision to the Spring Boot backend."""
    token = _extract_token(authorization)
    result = reject_procurement(
        procurement_id=body.procurement_id,
        comments=body.comments,
        token=token,
    )
    return result


@app.post("/api/ai/revalidate", response_model=RevalidationDecisionResult)
def api_revalidate_procurement(
    body: RevalidateRequestBody,
    authorization: Optional[str] = Header(None),
) -> RevalidationDecisionResult:
    """Execute pre-purchase revalidation on the Spring Boot backend."""
    token = _extract_token(authorization)
    result = revalidate_procurement(
        procurement_id=body.procurement_id,
        token=token,
    )
    return result


@app.post("/api/ai/purchase", response_model=PurchaseExecutionResult)
def api_purchase_procurement(
    body: PurchaseRequestBody,
    authorization: Optional[str] = Header(None),
) -> PurchaseExecutionResult:
    """Execute mock purchase on the Spring Boot backend."""
    token = _extract_token(authorization)
    result = purchase_procurement(
        procurement_id=body.procurement_id,
        token=token,
    )
    return result


if __name__ == "__main__":
    import uvicorn

    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("app.api:app", host="0.0.0.0", port=port, reload=False)
