from __future__ import annotations

from typing import Any, Dict, List, Optional
from datetime import datetime

from pydantic import BaseModel, Field


class VendorProduct(BaseModel):
    vendor_name: str
    product_name: str
    category: str
    price: float
    specifications: Dict[str, Any] = Field(default_factory=dict)
    warranty_years: Optional[int] = None
    delivery_days: Optional[int] = None
    reliability_score: Optional[float] = None  # 0-1 scale
    return_policy: Optional[str] = None
    available: bool = True


class DiscoveryMetadata(BaseModel):
    timestamp: str = Field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")
    sources_queried: int = 0
    total_products_found: int = 0


class DiscoveryResult(BaseModel):
    procurement_category: str
    requested_quantity: Optional[int] = None
    discovered_products: List[VendorProduct] = Field(default_factory=list)
    unavailable_vendors: List[str] = Field(default_factory=list)
    metadata: DiscoveryMetadata = Field(default_factory=DiscoveryMetadata)
