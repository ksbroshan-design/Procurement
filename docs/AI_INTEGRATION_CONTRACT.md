# AI Integration Contract (Person A $\leftrightarrow$ Person B Interface Specification)

## 1. Architectural Responsibility Boundary

```
PERSON B (AI / LangChain / Frontend):
   Natural Language Buying Brief
                 │
                 ▼
          AI Intent Parser
                 │
                 ▼
    Structured Procurement Intent (UNTRUSTED INPUT)
                 │
                 ▼ (REST API)
─────────────────────────────────────────────────────────────
PERSON A (Authoritative Spring Boot Backend):
                 │
                 ▼
        ProcurementOrchestrator
                 │
        ┌────────┴────────┐
        ▼                 ▼
Multi-Vendor SPI     Constraint Engine
        │                 │
        ▼                 ▼
   TCO Engine       Ranking Engine
        │                 │
        ▼                 ▼
 Recommendation    Authorization Engine
        │                 │
        ▼                 ▼
 HITL Approvals    Revalidation Engine
        │                 │
        ▼                 ▼
 Mock Purchase       Audit Trail
```

### Security & Authority Principle
- **The AI is NEVER trusted to enforce business rules.**
- The AI cannot modify authorization limits, alter product prices, bypass hard constraints, skip pre-purchase revalidation, or arbitrarily select vendor offers.
- All values submitted by Person B are **untrusted inputs** validated authoritatively by the Spring Boot backend.

---

## 2. API Endpoints for Person B

### A. Create Procurement Request
- **Endpoint**: `POST /api/procurements`
- **Request Headers**: `Content-Type: application/json`
- **Payload Schema**:
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
      "value": "85000",
      "mandatory": true
    },
    {
      "attribute": "deliveryDays",
      "operator": "<=",
      "value": "7",
      "mandatory": true
    },
    {
      "attribute": "weightKg",
      "operator": "<=",
      "value": "1.8",
      "mandatory": false
    }
  ]
}
```

- **Supported Constraint Operators**:
  - `>=`, `>`, `<=`, `<`, `==`, `!=`, `CONTAINS`, `IN`
- **Supported Categories**: `Laptop`, `Tablet`, `TV`, `Monitor`, `Office chair`, `Keyboard`

---

### B. Execute Deterministic Procurement Workflow
- **Endpoint**: `POST /api/procurements/{id}/execute`
- **Response**:
```json
{
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

---

### C. Retrieve Procurement Status & Summary
- **Endpoint**: `GET /api/procurements/{id}`
- **Response**:
```json
{
  "success": true,
  "data": {
    "id": "c39b3a0e-49b0-466d-9be2-4467c6999b80",
    "category": "Laptop",
    "quantity": 5,
    "authorizationLimit": 450000.00,
    "status": "COMPLETED",
    "selectedOfferId": "f90c379a-...",
    "selectedProductName": "Dell Latitude 5540 Business Laptop",
    "selectedVendorName": "TechDirect Enterprises",
    "constraintCount": 5
  }
}
```

---

### D. Human-in-the-Loop (HITL) Manager Approval
If `status` is `WAITING_APPROVAL`:
- **Query Pending Approval**: `GET /api/procurements/{id}/approval`
- **Approve**: `POST /api/procurements/{id}/approval/approve`
  ```json
  {
    "comments": "Approved by Procurement Director"
  }
  ```
- **Reject**: `POST /api/procurements/{id}/approval/reject`
  ```json
  {
    "comments": "Budget override denied"
  }
  ```

---

### E. Pre-Purchase Revalidation & Purchase Execution
- **Revalidate**: `POST /api/procurements/{id}/revalidate`
- **Purchase**: `POST /api/procurements/{id}/purchase`
- **Retrieve Purchase Order**: `GET /api/procurements/{id}/purchase-order`

---

### F. Query Audit Trail
- **Endpoint**: `GET /api/procurements/{id}/audit`
- **Response**: Chronological timeline of events with structured metadata.
