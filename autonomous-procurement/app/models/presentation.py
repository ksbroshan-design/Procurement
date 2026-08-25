from __future__ import annotations

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, ConfigDict, Field


class RecommendationPresentation(BaseModel):
    """Presentation model for Spring Boot's two-tier explainable recommendation."""

    model_config = ConfigDict(extra="ignore")

    procurement_id: Optional[str] = None
    category: Optional[str] = None
    recommendation_type: Optional[str] = None
    best_eligible_option: Optional[Dict[str, Any]] = None
    best_exception_option: Optional[Dict[str, Any]] = None
    proposed_exception_offer: Optional[Dict[str, Any]] = None
    selected_offer_id: Optional[str] = None
    selected_product_id: Optional[str] = None
    explanation: Optional[str] = None
    trade_offs: List[str] = Field(default_factory=list)
    ranked_alternatives: List[Dict[str, Any]] = Field(default_factory=list)
    false_economy_report: List[Dict[str, Any]] = Field(default_factory=list)


class TcoPresentation(BaseModel):
    """Presentation model for Spring Boot's authoritative TCO calculation."""

    model_config = ConfigDict(extra="ignore")

    offer_id: Optional[str] = None
    product_id: Optional[str] = None
    product_name: Optional[str] = None
    vendor_name: Optional[str] = None
    quantity: Optional[int] = None
    horizon_years: Optional[int] = None
    unit_purchase_cost: Optional[float] = None
    unit_maintenance_cost: Optional[float] = None
    unit_expected_repair_cost: Optional[float] = None
    unit_expected_downtime_cost: Optional[float] = None
    unit_replacement_cost: Optional[float] = None
    unit_warranty_benefit: Optional[float] = None
    unit_tco: Optional[float] = None
    total_purchase_cost: Optional[float] = None
    total_maintenance_cost: Optional[float] = None
    total_expected_repair_cost: Optional[float] = None
    total_expected_downtime_cost: Optional[float] = None
    total_replacement_cost: Optional[float] = None
    total_warranty_benefit: Optional[float] = None
    total_tco: Optional[float] = None
    failure_rate: Optional[float] = None
    warranty_years: Optional[int] = None
    warranty_type: Optional[str] = None
    data_grounded: Optional[bool] = None
    assumptions: List[str] = Field(default_factory=list)


class RevalidationCheckPresentation(BaseModel):
    """Presentation model for an individual pre-purchase revalidation check."""

    model_config = ConfigDict(extra="ignore")

    check_type: Optional[str] = None
    expected_value: Optional[str] = None
    actual_value: Optional[str] = None
    passed: bool = True
    message: Optional[str] = None


class RevalidationPresentation(BaseModel):
    """Presentation model for Spring Boot's authoritative pre-purchase revalidation result."""

    model_config = ConfigDict(extra="ignore")

    procurement_id: Optional[str] = None
    offer_id: Optional[str] = None
    product_name: Optional[str] = None
    vendor_name: Optional[str] = None
    status: Optional[str] = None
    valid: bool = True
    revalidation_attempts: int = 0
    max_retry_attempts: int = 3
    checks: List[Dict[str, Any]] = Field(default_factory=list)
    message: Optional[str] = None
    next_state: Optional[str] = None


class PurchaseOrderPresentation(BaseModel):
    """Presentation model for Spring Boot's confirmed Purchase Order."""

    model_config = ConfigDict(extra="ignore")

    id: Optional[str] = None
    procurement_id: Optional[str] = None
    vendor_id: Optional[str] = None
    vendor_name: Optional[str] = None
    product_id: Optional[str] = None
    product_name: Optional[str] = None
    quantity: Optional[int] = None
    unit_price: Optional[float] = None
    total_amount: Optional[float] = None
    status: Optional[str] = None
    created_at: Optional[str] = None
    confirmed_at: Optional[str] = None

