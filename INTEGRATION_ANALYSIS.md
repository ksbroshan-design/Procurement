# Autonomous Procurement Engine: Integration Analysis & Architecture Design

## 1. Executive Summary & Core Architectural Principle

This document defines the authoritative architectural integration design for unifying:
1. **Person A's Authoritative Backend Core (`Procurement_Agent/`)**: Java 21, Spring Boot 3.3.0, Spring Data JPA, Hibernate, PostgreSQL, Spring Security + JWT, Deterministic State Machine, Constraint Engine, TCO Engine, Multi-Vendor SPI, Authorization, Human Approval, Revalidation, and Purchase Execution.
2. **Person B's Intelligence / AI Layer (`autonomous-procurement/`)**: Python 3.14, Pydantic v2, Domain Guardrail, Conservative LLM Intent Parser, Grounding & Normalization, and LangGraph-compatible workflow.

### Core Architectural Principle
$$\mathbf{PERSON\ B = INTELLIGENCE} \quad \longleftrightarrow \quad \mathbf{PERSON\ A = AUTHORITY}$$

- **Person B (Python AI)**: Handles natural language understanding, domain classification, conservative intent parsing with strict grounding (preventing hallucination), clarification generation, and explainable result presentation.
- **Person A (Java / Spring Boot)**: The **sole, unchallengeable authority** for catalog discovery, normalization, hard constraint evaluation, TCO calculation, ranking, financial authorization limits, human-in-the-loop approvals, pre-purchase revalidation, inventory deductions, and purchase order creation.

---

## 2. Fundamental Architectural Guardrails

### 2.1 Spring Boot is the Single Procurement Orchestrator
Person A's existing `ProcurementOrchestrator` is the sole workflow coordinator. After Python runs domain guardrails and parses structured intent, it submits the request to Spring Boot. Spring Boot owns:
- Catalog discovery across multi-vendor sources
- Hard and soft constraint evaluation
- Attribute normalization
- Total Cost of Ownership (TCO) & False Economy Detection
- Multi-criteria weighted ranking
- Two-tier recommendation generation (Pool A Eligible vs Pool B Exception)
- Financial authorization evaluation
- Human-in-the-loop (HITL) approval gating
- Pre-purchase revalidation (stock, price stability, vendor status, constraints)
- Atomic stock deduction and PurchaseOrder creation
- Transactional audit log recording
- Authoritative state transitions

### 2.2 Python Must Not Reimplement the Decision Pipeline
Person B's local decision components (`ConstraintEngine`, `TCOEngine`, `RankingEngine`, `RecommendationService`, local authorization/revalidation/purchase logic) will **never** make independent decisions in production. Production workflow decisions are fetched directly from Spring Boot. Legacy Python engine classes are preserved solely to guarantee that Person B's existing 87 unit tests continue to pass in isolation.

### 2.3 Single Authoritative State Machine
Person A's 15-state `ProcurementStateMachine` is the single source of truth:
$$\text{SUBMITTED} \to \text{VALIDATING} \to \text{SEARCHING} \to \text{EVALUATING} \to \text{TCO\_ANALYSIS} \to \text{RECOMMENDED} \to \text{AUTHORIZATION\_CHECK} \to \begin{cases} \text{REVALIDATING} \to \text{PURCHASING} \to \text{COMPLETED} \\ \text{WAITING\_APPROVAL} \to \text{Manager Approval} \to \text{REVALIDATING} \to \text{PURCHASING} \to \text{COMPLETED} \end{cases}$$

Python's state models reflect Spring Boot's authoritative state (e.g. `backend_procurement_id`, `backend_status`, `selected_product`, `purchase_order`) rather than running a competing lifecycle.

### 2.4 JWT Authentication & Role Enforcement
Python forwards the authenticated caller's JWT Bearer token directly in the `Authorization: Bearer <JWT>` header on all calls to Spring Boot.
- Spring Security validates token signatures and extracts the authenticated principal.
- Python never trusts an `"approver"` field in the request body.
- Manager approvals strictly require `ROLE_PROCUREMENT_MANAGER` or `ROLE_ADMIN` enforced via Spring Security `@PreAuthorize`.

### 2.5 Normal Execution Path
$$\text{POST /api/procurements} \longrightarrow \text{POST /api/procurements/\{id\}/execute} \longrightarrow \text{Spring Orchestrator} \longrightarrow \text{Python queries state} \longrightarrow \text{Python presents result}$$
Individual stage endpoints (`/discover`, `/analyze-tco`, `/ranking`, `/revalidate`, `/purchase`) exist on Spring Boot for debugging, audit inspection, and targeted recovery, but the standard execution path leverages `ProcurementOrchestrator.orchestrate(id)`.

### 2.6 Purchase Safety & Idempotency
- Python never directly creates `PurchaseOrder` records and never directly connects to PostgreSQL.
- Purchase execution is strictly server-authoritative (`PurchaseExecutionService`), executed only after successful `RevalidationService` verification.
- Idempotency is enforced: network timeouts or retries check existing confirmed purchase orders via `GET /api/procurements/{id}/purchase-order` rather than blindly retrying POST `/purchase`.

### 2.7 Clean Codebase Separation
The directory structure remains clean and unpolluted:
```
parent/
├── Procurement_Agent/            # Person A: Authoritative Spring Boot backend
└── autonomous-procurement/       # Person B: Python AI service
```

---

## 3. Baseline Project Status

| Project | Technology | Baseline Test Results | Responsibilities |
| :--- | :--- | :--- | :--- |
| **`Procurement_Agent/`** | Java 21, Spring Boot 3.3.0, JPA, PostgreSQL, Spring Security | **136 tests passing (0 failures)** | Catalog discovery, normalization, constraints, TCO, ranking, authorization, HITL approvals, revalidation, purchase execution, audit trail. |
| **`autonomous-procurement/`** | Python 3.14, Pydantic v2, Pytest | **87 tests passing (0 failures)** | Domain guardrail, conservative intent parsing with grounding, clarification dialogue, AI explanation formatting, REST communication. |

---

## 4. Component Responsibility & Boundary Matrix

| Component Area | Python AI Role | Java Spring Boot Role | Authority Boundary |
| :--- | :--- | :--- | :--- |
| **Domain Guardrail** | `DomainGuardrail` classifies message via LLM | None | **Python AI Service** (Rejects non-procurement queries) |
| **Intent Parsing & Grounding** | `IntentParser` extracts structured JSON, grounds values against brief | None | **Python AI Service** (Zero hallucination) |
| **Clarification Dialogue** | Generates questions if category/quantity missing | None | **Python AI Service** |
| **Catalog Discovery** | None | `DiscoveryService` + `ProductDiscoverySource` SPI | **Spring Boot** |
| **Normalization** | None | `ProductNormalizationService` | **Spring Boot** |
| **Constraint Evaluation** | Submits proposed constraints | `ConstraintService` + `ConstraintEvaluator` | **Spring Boot** |
| **TCO Calculation** | None | `TcoService` + `TcoCalculator` + `FalseEconomyDetector` | **Spring Boot** |
| **Multi-Factor Ranking** | None | `RankingService` | **Spring Boot** |
| **Recommendation** | Formats explanation for user | `RecommendationService` (Pool A / Pool B) | **Spring Boot** |
| **Authorization Limit** | None | `AuthorizationService` | **Spring Boot** |
| **Human Approval (HITL)** | Halts & surfaces approval requirement | `ApprovalService` (RBAC, replay protection, offer binding) | **Spring Boot** |
| **Revalidation** | None | `RevalidationService` (stock, price, status, bounded retries) | **Spring Boot** |
| **Purchase Order** | Formats final PO for user | `PurchaseExecutionService` (stock deduction, PO persistence) | **Spring Boot** |
| **Audit Log** | Surfaces event history | `AuditService` + `AuditLog` in PostgreSQL | **Spring Boot** |
| **Security / RBAC** | Forwards `Authorization: Bearer <token>` | `JwtAuthenticationFilter` + Spring Security | **Spring Boot** |

---

## 5. REST Communication & DTO Mapping

### 5.1 Python Intent $\to$ Spring Boot DTO
When Python parses a brief:
> *"Buy 5 Dell laptops under ₹85,000 with at least 16GB RAM and delivery within 7 days. Approval limit is ₹450,000."*

Python's `SpringProcurementClient` constructs `CreateProcurementRequestDto`:
```json
{
  "category": "Laptop",
  "quantity": 5,
  "authorizationLimit": 450000.00,
  "constraints": [
    {
      "attribute": "ram",
      "operator": ">=",
      "value": "16",
      "mandatory": true
    },
    {
      "attribute": "price",
      "operator": "<=",
      "value": "85000.00",
      "mandatory": true
    },
    {
      "attribute": "deliveryDays",
      "operator": "<=",
      "value": "7",
      "mandatory": true
    }
  ]
}
```

### 5.2 Operator Mapping Matrix
| Python Operator | Normalized Token | Java `ConstraintOperator` |
| :--- | :--- | :--- |
| `>=` | `GREATER_THAN_OR_EQUAL` | `ConstraintOperator.GREATER_THAN_OR_EQUAL` |
| `<=` | `LESS_THAN_OR_EQUAL` | `ConstraintOperator.LESS_THAN_OR_EQUAL` |
| `=` / `==` | `EQUALS` | `ConstraintOperator.EQUALS` |
| `>` | `GREATER_THAN` | `ConstraintOperator.GREATER_THAN` |
| `<` | `LESS_THAN` | `ConstraintOperator.LESS_THAN` |
| `!=` | `NOT_EQUALS` | `ConstraintOperator.NOT_EQUALS` |
| `contains` | `CONTAINS` | `ConstraintOperator.CONTAINS` |
| `in` | `IN` | `ConstraintOperator.IN` |

---

## 6. End-to-End Orchestration & HITL Approval Flow

```
                      USER / CLIENT
                            │
                            ▼
              ┌───────────────────────────┐
              │     PYTHON AI LAYER       │
              │                           │
              │  1. Domain Guardrail      │
              │  2. Conservative Parser   │
              │  3. Clarification Check   │
              └─────────────┬─────────────┘
                            │ POST /api/procurements (Bearer JWT)
                            ▼
              ┌───────────────────────────┐
              │    SPRING BOOT BACKEND    │
              │                           │
              │ Creates ProcurementRequest│
              └─────────────┬─────────────┘
                            │ POST /api/procurements/{id}/execute
                            ▼
              ┌───────────────────────────┐
              │   ProcurementOrchestrator │
              │                           │
              │ 1. Discovery & Normalize  │
              │ 2. Evaluate Constraints   │
              │ 3. Calculate TCO          │
              │ 4. Rank Candidates        │
              │ 5. Generate Recommendation│
              │ 6. Authorization Check    │
              └─────────────┬─────────────┘
                            │
           ┌────────────────┴────────────────┐
           ▼                                 ▼
   [AUTO_AUTHORIZED]               [REQUIRES_APPROVAL]
           │                                 │
           │                     Transitions to WAITING_APPROVAL
           │                                 │
           │                     Python returns WAITING_APPROVAL
           │                     to User/Manager for Review
           │                                 │
           │                     Manager Approves via Spring:
           │                     POST /api/procurements/{id}/approval/approve
           │                                 │
           │                     Transitions to REVALIDATING
           │                                 │
           └────────────────┬────────────────┘
                            ▼
              ┌───────────────────────────┐
              │    RevalidationService    │
              │                           │
              │ 1. Vendor Status Check    │
              │ 2. Real-Time Stock Check  │
              │ 3. Price Stability Check  │
              │ 4. Constraint Check       │
              │ 5. Auth Limit Check       │
              └─────────────┬─────────────┘
                            │ (Valid)
                            ▼
              ┌───────────────────────────┐
              │ PurchaseExecutionService  │
              │                           │
              │ 1. Atomic Stock Deduction │
              │ 2. Confirmed PO Created   │
              │ 3. State -> COMPLETED     │
              │ 4. Audit Log Recorded     │
              └─────────────┬─────────────┘
                            │
                            ▼
              ┌───────────────────────────┐
              │      PYTHON AI LAYER      │
              │                           │
              │ Retrieves PO & Audit      │
              │ Formats summary for User  │
              └───────────────────────────┘
```

---

## 7. Error Handling & Timeout/Retry Strategy

| Failure Scenario | HTTP / Engine Outcome | Resilience Action |
| :--- | :--- | :--- |
| **Spring Boot Offline / Timeout** | Connection Error / 503 | Catch connection exception, report friendly status, allow retry on safe GET operations. |
| **Missing Mandatory Information** | `needs_clarification` from Parser | Return clear clarification prompt to user without calling backend. |
| **Non-Procurement Brief** | `is_procurement: false` from Guardrail | Reject early with reason; do not create database record. |
| **Invalid JWT / Token Expired** | `401 Unauthorized` | Clear cached token, prompt for login / refresh token. |
| **Unauthorized Action (Role Mismatch)** | `403 Forbidden` | Inform caller that manager/admin role is required for approval. |
| **No Eligible Products Found** | `NO_ELIGIBLE_PRODUCTS` | Spring completes orchestration cleanly; Python presents rejection diagnostics and candidate failures. |
| **Revalidation Failure (Stale Price / Out-of-Stock)** | `STALE` $\to$ Retry / `WAITING_USER` | Spring executes bounded rediscovery (up to 3 attempts); if exhausted, prompts user. |
| **Purchase Network Disruption** | Timeout during `/purchase` | **Never blindly retry.** Check `GET /api/procurements/{id}/purchase-order` first for confirmed PO before attempting re-call. |

---

## 8. Docker Architecture

Unified local orchestration via `docker-compose.yml`:
1. **`postgres`**: PostgreSQL 16 Alpine, port 5432, persistent volume, healthcheck via `pg_isready`.
2. **`backend`**: Spring Boot backend built from `Procurement_Agent/Dockerfile`, port 8080, depends on healthy postgres.
3. **`ai-service`**: Python AI service built from `autonomous-procurement/Dockerfile`, port 8000, depends on backend.
4. **Networking**: Service-to-service DNS resolution (`http://backend:8080`).

---

## 9. 12-Step Implementation Order & Zero-Regression Verification

1. **Step 1**: Inspect both repositories (Complete).
2. **Step 2**: Produce `INTEGRATION_ANALYSIS.md` (Complete).
3. **Step 3**: Define/finalize REST DTO contract (`docs/AI_INTEGRATION_CONTRACT.md`).
4. **Step 4**: Implement Python `SpringProcurementClient` (`app/client/spring_client.py`).
5. **Step 5**: Test Python $\to$ Spring authentication and procurement creation.
6. **Step 6**: Integrate the Python intent parser with Spring `ProcurementRequest` creation.
7. **Step 7**: Integrate backend execution and state polling (`POST /api/procurements/{id}/execute`).
8. **Step 8**: Integrate recommendation and TCO retrieval.
9. **Step 9**: Integrate HITL approval and rejection workflows.
10. **Step 10**: Integrate revalidation and purchase result retrieval.
11. **Step 11**: Create unified Docker Compose configuration.
12. **Step 12**: Add end-to-end integration tests (`tests/test_spring_integration.py`).

### Verification Mandate
After each step:
- Run Maven test suite: `mvn test` in `Procurement_Agent/` (Target: **136 passed, 0 failed**).
- Run Pytest test suite: `pytest` in `autonomous-procurement/` (Target: **87 passed, 0 failed**).
- Fix any regression immediately before progressing to the next step.
