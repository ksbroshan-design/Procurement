from __future__ import annotations

import os
from typing import Any, Dict, List, Optional, Union
import httpx

from app.client.dto_mapper import map_procurement_request
from app.models.procurement import ProcurementRequest


class SpringClientError(Exception):
    """Base exception for all Spring Boot client errors."""

    def __init__(self, message: str, status_code: Optional[int] = None, details: Optional[Dict[str, Any]] = None):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.details = details or {}


class BackendUnavailableError(SpringClientError):
    """Raised when Spring Boot backend is unreachable or timed out."""


class ValidationError(SpringClientError):
    """Raised on HTTP 400 Bad Request / constraint validation failure."""


class AuthenticationError(SpringClientError):
    """Raised on HTTP 401 Unauthorized (missing or invalid JWT token)."""


class AuthorizationError(SpringClientError):
    """Raised on HTTP 403 Forbidden (insufficient user role permissions)."""


class ResourceNotFoundError(SpringClientError):
    """Raised on HTTP 404 Not Found."""


class StateConflictError(SpringClientError):
    """Raised on HTTP 409 Conflict / invalid state machine transition."""


class BackendServerError(SpringClientError):
    """Raised on HTTP 500+ Internal Server Error."""


class SpringProcurementClient:
    """Typed REST client for communicating with the authoritative Spring Boot procurement backend."""

    def __init__(
        self,
        base_url: Optional[str] = None,
        token: Optional[str] = None,
        timeout: float = 10.0,
        transport: Optional[httpx.BaseTransport] = None,
        client: Optional[httpx.Client] = None,
    ) -> None:
        self.base_url = (base_url or os.getenv("SPRING_BASE_URL") or os.getenv("BACKEND_BASE_URL") or os.getenv("SPRING_BACKEND_URL") or "http://localhost:8080").rstrip("/")
        self.token = token
        self.timeout = timeout

        if client is not None:
            self._client = client
            self._owns_client = False
        else:
            self._client = httpx.Client(
                base_url=self.base_url,
                timeout=self.timeout,
                transport=transport,
            )
            self._owns_client = True

    def close(self) -> None:
        """Close the underlying HTTP client if owned."""
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> SpringProcurementClient:
        return self

    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        self.close()

    def _get_headers(self, token: Optional[str] = None) -> Dict[str, str]:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        effective_token = token or self.token
        if effective_token:
            headers["Authorization"] = f"Bearer {effective_token.strip()}"
        return headers

    def _handle_response(self, response: httpx.Response) -> Any:
        """Parse response and map error statuses to typed exceptions."""
        status_code = response.status_code

        # Try parsing JSON body if available
        body: Any = None
        try:
            body = response.json()
        except Exception:
            body = None

        if 200 <= status_code < 300:
            if isinstance(body, dict) and "data" in body and "success" in body:
                return body["data"]
            return body

        # Extract error message
        error_msg = f"HTTP {status_code} error from Spring backend"
        details: Dict[str, Any] = {}
        if isinstance(body, dict):
            details = body
            error_msg = body.get("message") or body.get("error") or error_msg

        if status_code == 400:
            raise ValidationError(error_msg, status_code=status_code, details=details)
        elif status_code == 401:
            raise AuthenticationError(error_msg, status_code=status_code, details=details)
        elif status_code == 403:
            raise AuthorizationError(error_msg, status_code=status_code, details=details)
        elif status_code == 404:
            raise ResourceNotFoundError(error_msg, status_code=status_code, details=details)
        elif status_code == 409:
            raise StateConflictError(error_msg, status_code=status_code, details=details)
        elif status_code >= 500:
            raise BackendServerError(error_msg, status_code=status_code, details=details)
        else:
            raise SpringClientError(error_msg, status_code=status_code, details=details)

    def _request(
        self,
        method: str,
        path: str,
        json_data: Optional[Any] = None,
        token: Optional[str] = None,
    ) -> Any:
        """Perform an HTTP request with error translation. Never blindly retries POST requests."""
        headers = self._get_headers(token=token)
        url = path if path.startswith("http") else f"{self.base_url}{path}"

        try:
            response = self._client.request(
                method=method,
                url=url,
                json=json_data,
                headers=headers,
            )
            return self._handle_response(response)
        except (httpx.ConnectError, httpx.ConnectTimeout, httpx.ReadTimeout, httpx.NetworkError) as e:
            raise BackendUnavailableError(f"Backend unavailable at {self.base_url}: {e}") from e

    # =========================================================================
    # Step 4 & 5 Authoritative REST Endpoints
    # =========================================================================

    def login(self, email: str, password: str) -> Dict[str, Any]:
        """POST /api/auth/login - Authenticates credentials and stores issued JWT token."""
        result = self._request("POST", "/api/auth/login", json_data={"email": email, "password": password})
        if isinstance(result, dict) and "token" in result:
            self.token = result["token"]
        return result

    def create_procurement(

        self,
        request: Union[ProcurementRequest, Dict[str, Any]],
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /api/procurements - Creates a new procurement request from structured intent."""
        if isinstance(request, ProcurementRequest):
            payload = map_procurement_request(request)
        else:
            payload = request

        return self._request("POST", "/api/procurements", json_data=payload, token=token)

    def execute_procurement(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /api/procurements/{id}/execute - Triggers deterministic end-to-end orchestration."""
        return self._request("POST", f"/api/procurements/{procurement_id}/execute", token=token)

    def get_procurement(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """GET /api/procurements/{id} - Retrieves status and summary of a procurement request."""
        return self._request("GET", f"/api/procurements/{procurement_id}", token=token)

    def get_recommendation(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """GET /api/procurements/{id}/recommendation - Retrieves two-tier explainable recommendation."""
        return self._request("GET", f"/api/procurements/{procurement_id}/recommendation", token=token)

    def get_tco(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> List[Dict[str, Any]]:
        """GET /api/procurements/{id}/tco - Retrieves detailed TCO breakdowns."""
        return self._request("GET", f"/api/procurements/{procurement_id}/tco", token=token)

    def get_approval(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """GET /api/procurements/{id}/approval - Retrieves pending human-in-the-loop approval record."""
        return self._request("GET", f"/api/procurements/{procurement_id}/approval", token=token)

    def approve_procurement(
        self,
        procurement_id: str,
        comments: Optional[str] = None,
        approved_offer_id: Optional[str] = None,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /api/procurements/{id}/approval/approve - Approves pending procurement decision."""
        payload: Dict[str, Any] = {}
        if comments is not None:
            payload["comments"] = comments
        if approved_offer_id is not None:
            payload["approvedOfferId"] = approved_offer_id

        return self._request("POST", f"/api/procurements/{procurement_id}/approval/approve", json_data=payload or None, token=token)

    def reject_procurement(
        self,
        procurement_id: str,
        comments: Optional[str] = None,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /api/procurements/{id}/approval/reject - Rejects pending procurement decision."""
        payload: Dict[str, Any] = {}
        if comments is not None:
            payload["comments"] = comments

        return self._request("POST", f"/api/procurements/{procurement_id}/approval/reject", json_data=payload or None, token=token)

    def revalidate(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /api/procurements/{id}/revalidate - Executes pre-purchase revalidation checks."""
        return self._request("POST", f"/api/procurements/{procurement_id}/revalidate", token=token)

    def revalidate_procurement(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Alias for revalidate()."""
        return self.revalidate(procurement_id, token=token)

    def get_revalidation(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """GET /api/procurements/{id}/revalidate - Retrieves the latest revalidation evaluation."""
        return self._request("GET", f"/api/procurements/{procurement_id}/revalidate", token=token)

    def purchase(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /api/procurements/{id}/purchase - Executes mock purchase and confirms PurchaseOrder."""
        return self._request("POST", f"/api/procurements/{procurement_id}/purchase", token=token)

    def purchase_procurement(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Alias for purchase()."""
        return self.purchase(procurement_id, token=token)

    def get_purchase_order(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """GET /api/procurements/{id}/purchase-order - Retrieves confirmed PurchaseOrder."""
        return self._request("GET", f"/api/procurements/{procurement_id}/purchase-order", token=token)


    def get_audit_trail(
        self,
        procurement_id: str,
        token: Optional[str] = None,
    ) -> Dict[str, Any]:
        """GET /api/procurements/{id}/audit - Retrieves chronological audit trail."""
        return self._request("GET", f"/api/procurements/{procurement_id}/audit", token=token)
