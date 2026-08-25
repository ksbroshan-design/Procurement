# AI Integration Contract: Person A (Authoritative Backend) $\leftrightarrow$ Person B (AI Service)

## 1. Architectural Authority Boundary

```
PERSON B (Python AI Layer):
   Natural Language Buying Brief
                 │
                 ▼
          DomainGuardrail (Procurement vs Non-Procurement Check)
                 │
                 ▼
          IntentParser (Conservative Extraction & Strict Grounding)
                 │
                 ├─► Missing Info? ──► Returns Clarification Dialogue
                 │
                 ▼
   Structured Procurement Intent (UNTRUSTED INPUT)
                 │
                 ▼ (REST API / Bearer JWT)
─────────────────────────────────────────────────────────────────
PERSON A (Authoritative Spring Boot Backend Core):
                 │
                 ▼
         POST /api/procurements
                 │
                 ▼
       ProcurementOrchestrator
                 │
   ┌─────────────┼─────────────┐
   ▼             ▼             ▼
Discovery   Normalization  Constraints
   │             │             │
   └─────────────┼─────────────┘
                 ▼
            TCO Engine (with False Economy Detection)
                 ▼
           Ranking Engine (Weighted Multi-Criteria)
                 ▼
       Recommendation Engine (Pool A Eligible vs Pool B Exception)
                 ▼
        Authorization Engine (Financial Limits & Exception Detection)
                 │
         ┌───────┴───────┐
         ▼               ▼
  [AUTO_AUTHORIZED]   [REQUIRES_APPROVAL]
         │               │
         │       WAITING_APPROVAL (Halts for Human Review)
         │               │
         │       Manager Approves: POST /approval/approve
         │               │
         └───────┬───────┘
                 ▼
       Revalidation Engine (Stock, Price Stability, Vendor Status, Constraints)
                 ▼
     PurchaseExecutionService (Atomic Stock Deduction & PO Generation)
                 ▼
          State: COMPLETED
                 ▼
         Audit Trail (PostgreSQL Log)
```

### Security & Authority Mandates
1. **The AI is NEVER trusted to enforce business rules or make procurement decisions.**
2. The AI cannot modify authorization limits, alter product prices, bypass hard constraints, approve purchases, skip revalidation, or arbitrarily select vendor offers.
3. All values submitted by Person B are **untrusted inputs** validated authoritatively by the Spring Boot backend.
4. Python forwards the authenticated caller's JWT: `Authorization: Bearer <JWT>`. Spring Security authenticates identity and enforces RBAC (`ROLE_PROCUREMENT_MANAGER` or `ROLE_ADMIN` for approvals). Python never trusts client-supplied approver names in request bodies.

---

## 2. API Endpoints & Contract Specifications

### 2.1 Authentication & Token Issuance
- **Endpoint**: `POST /api/auth/login`
- **Request Body**:
  ```json
  {
    "email": "manager@procurement.com",
    "password": "password123"
  }
  ```
- **Response Schema (`ApiResponse<AuthResponseDTO>`)**:
  ```json
  {
    "timestamp": "2026-08-20T13:30:00Z",
    "success": true,
    "message": "Authentication successful",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "tokenType": "Bearer",
      "email": "manager@procurement.com",
      "role": "ROLE_PROCUREMENT_MANAGER",
      "expiresInMs": 86400000
    }
  }
  ```

---

### 2.2 Create Procurement Request
- **Endpoint**: `POST /api/procurements`
- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <JWT>`
- **Request Schema (`CreateProcurementRequestDto`)**:
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
        "attribute": "storage",
        "operator": ">=",
        "value": "512",
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
- **Response Schema (`ApiResponse<ProcurementSummaryDto>`)**:
  ```json
  {
    "timestamp": "2026-08-20T13:30:01Z",
    "success": true,
    "message": "Procurement request created successfully",
    "data": {
      "id": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
      "category": "Laptop",
      "quantity": 5,
      "authorizationLimit": 450000.00,
      "status": "SUBMITTED",
      "selectedOfferId": null,
      "selectedProductName": null,
      "selectedVendorName": null,
      "constraintCount": 4,
      "createdAt": "2026-08-20T13:30:01Z",
      "updatedAt": "2026-08-20T13:30:01Z"
    }
  }
  ```

---

### 2.3 Execute Deterministic Backend Workflow
- **Endpoint**: `POST /api/procurements/{id}/execute`
- **Headers**: `Authorization: Bearer <JWT>`
- **Response Schema (`ApiResponse<OrchestrationResultDto>`)**:
  ```json
  {
    "timestamp": "2026-08-20T13:30:02Z",
    "success": true,
    "message": "Procurement workflow orchestrated",
    "data": {
      "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
      "initialState": "SUBMITTED",
      "finalState": "COMPLETED",
      "status": "COMPLETED",
      "decisionMessage": "Purchase order successfully placed and confirmed with vendor.",
      "recommendationType": "AUTONOMOUS_PURCHASE_READY",
      "purchaseOrderId": "a17f2231-c42e-4b68-8094-1a9860b0946d",
      "totalAmount": 390000.00
    }
  }
  ```
  *(Note: If authorization requires approval, `finalState` will be `WAITING_APPROVAL`, `status` will be `WAITING_APPROVAL`, and `purchaseOrderId` will be null).*

---

### 2.4 Query Procurement Summary & State
- **Endpoint**: `GET /api/procurements/{id}`
- **Headers**: `Authorization: Bearer <JWT>`
- **Response Schema (`ApiResponse<ProcurementSummaryDto>`)**:
  ```json
  {
    "timestamp": "2026-08-20T13:30:03Z",
    "success": true,
    "message": "Procurement request retrieved",
    "data": {
      "id": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
      "category": "Laptop",
      "quantity": 5,
      "authorizationLimit": 450000.00,
      "status": "COMPLETED",
      "selectedOfferId": "f90c379a-5b1e-4c7a-9c6e-8d2b7a1f5e3d",
      "selectedProductName": "Dell Latitude 5540 Business Laptop",
      "selectedVendorName": "TechDirect Enterprises",
      "constraintCount": 4,
      "createdAt": "2026-08-20T13:30:01Z",
      "updatedAt": "2026-08-20T13:30:02Z"
    }
  }
  ```

---

### 2.5 Query Recommendation & TCO Details
- **Endpoint**: `GET /api/procurements/{id}/recommendation`
- **Response Schema (`ApiResponse<RecommendationResponse>`)**:
  ```json
  {
    "timestamp": "2026-08-20T13:30:02Z",
    "success": true,
    "message": "Procurement recommendation generated",
    "data": {
      "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
      "category": "Laptop",
      "recommendationType": "STANDARD_RECOMMENDED",
      "bestEligibleOption": {
        "rank": 1,
        "offerId": "f90c379a-5b1e-4c7a-9c6e-8d2b7a1f5e3d",
        "productId": "e12c456a-1234-5678-9abc-def012345678",
        "productName": "Dell Latitude 5540 Business Laptop",
        "vendorName": "TechDirect Enterprises",
        "category": "Laptop",
        "price": 390000.00,
        "unitPrice": 78000.00,
        "tco": 412500.00,
        "unitTco": 82500.00,
        "totalScore": 92.45,
        "eligible": true,
        "budgetExceeded": false,
        "isExceptionOffer": false
      },
      "bestExceptionOption": null,
      "proposedExceptionOffer": null,
      "selectedOfferId": "f90c379a-5b1e-4c7a-9c6e-8d2b7a1f5e3d",
      "selectedProductId": "e12c456a-1234-5678-9abc-def012345678",
      "explanation": "Top-ranked eligible offer provides optimal TCO and 3-year warranty.",
      "tradeOffs": [],
      "rankedAlternatives": [],
      "falseEconomyReport": []
    }
  }
  ```

---

### 2.6 Human-in-the-Loop (HITL) Approval Endpoints
- **Query Pending Approval**: `GET /api/procurements/{id}/approval`
  - Returns `ApiResponse<ApprovalResponseDto>`.
- **Approve**: `POST /api/procurements/{id}/approval/approve`
  - **Required Role**: `ROLE_PROCUREMENT_MANAGER` or `ROLE_ADMIN`
  - **Payload**:
    ```json
    {
      "comments": "Approved budget exception due to superior 3-year TCO"
    }
    ```
  - **State Transition**: `WAITING_APPROVAL` $\to$ `REVALIDATING`
- **Reject**: `POST /api/procurements/{id}/approval/reject`
  - **Required Role**: `ROLE_PROCUREMENT_MANAGER` or `ROLE_ADMIN`
  - **Payload**:
    ```json
    {
      "comments": "Budget override denied"
    }
    ```
  - **State Transition**: `WAITING_APPROVAL` $\to$ `REJECTED`

---

### 2.7 Pre-Purchase Revalidation & Purchase Execution
- **Revalidate**: `POST /api/procurements/{id}/revalidate`
  - Returns `ApiResponse<RevalidationResultDto>` with live stock, price stability, vendor status, and constraint verification.
- **Purchase Execution**: `POST /api/procurements/{id}/purchase`
  - Returns `ApiResponse<PurchaseExecutionResultDto>` (Idempotent: if already completed, returns existing PO).
- **Retrieve Confirmed Purchase Order**: `GET /api/procurements/{id}/purchase-order`
  - Returns `ApiResponse<PurchaseOrderDto>`.

---

### 2.8 Audit Trail
- **Endpoint**: `GET /api/procurements/{id}/audit`
- **Response Schema (`ApiResponse<ProcurementAuditResponse>`)**:
  ```json
  {
    "timestamp": "2026-08-20T13:30:05Z",
    "success": true,
    "message": "Procurement audit trail retrieved",
    "data": {
      "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
      "events": [
        {
          "id": "11111111-2222-3333-4444-555555555555",
          "procurementId": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
          "timestamp": "2026-08-20T13:30:01Z",
          "eventType": "STATE_TRANSITION",
          "state": "VALIDATING",
          "actor": "AI_PARSER",
          "description": "Procurement created from AI parsed brief",
          "metadata": {
            "category": "Laptop",
            "quantity": 5
          }
        }
      ]
    }
  }
  ```

---

## 3. Supported Constraint Operators & Categories

### Supported Categories
- `Laptop`, `Tablet`, `TV`, `Monitor`, `Office chair`, `Keyboard`

### Supported Constraint Operators
| Python Operator | Normalized Token | Java `ConstraintOperator` | Behavior |
| :--- | :--- | :--- | :--- |
| `>=` | `GREATER_THAN_OR_EQUAL` | `GREATER_THAN_OR_EQUAL` | Numeric value $\ge$ constraint |
| `<=` | `LESS_THAN_OR_EQUAL` | `LESS_THAN_OR_EQUAL` | Numeric value $\le$ constraint |
| `=` / `==` | `EQUALS` | `EQUALS` | Exact equality (numeric or string) |
| `>` | `GREATER_THAN` | `GREATER_THAN` | Numeric value $>$ constraint |
| `<` | `LESS_THAN` | `LESS_THAN` | Numeric value $<$ constraint |
| `!=` | `NOT_EQUALS` | `NOT_EQUALS` | Inverted equality check |
| `contains` | `CONTAINS` | `CONTAINS` | Case-insensitive substring match |
| `in` | `IN` | `IN` | Comma-separated set membership |

---

## 4. Error Handling & Standard Error Response Schema

All errors from Spring Boot conform to `ErrorResponse`:
```json
{
  "timestamp": "2026-08-20T13:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Constraint validation failed: RAM must be a positive integer",
  "path": "/api/procurements",
  "details": {
    "field": "constraints[0].value"
  }
}
```

| HTTP Status | Error Type | Python Client Handling |
| :--- | :--- | :--- |
| **`400 Bad Request`** | Validation / Invalid Constraint | Extract message, return descriptive validation error |
| **`401 Unauthorized`** | Expired / Missing JWT | Discard token, re-authenticate or prompt for login |
| **`403 Forbidden`** | Insufficient Role (e.g. USER attempting approval) | Inform caller that Manager/Admin permissions are required |
| **`404 Not Found`** | Resource Missing | Return not found diagnostic |
| **`409 Conflict`** | Invalid State Transition | Synchronize state via `GET /api/procurements/{id}` |
| **`500 Internal Error`** | Server Error | Log server error, do not retry unsafe operations |
| **Network Timeout / Refusal** | Connection Error | Exponential backoff for idempotent GET queries only |
