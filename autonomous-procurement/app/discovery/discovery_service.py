from __future__ import annotations

from typing import List
from datetime import datetime

from app.discovery.models import VendorProduct, DiscoveryResult, DiscoveryMetadata
from app.discovery.mock_sources import vendor_alpha_products, vendor_bravo_products
from app.discovery.normalizer import normalize_category
from app.models.procurement import ProcurementRequest


class DiscoveryService:
    """Aggregates product listings from multiple mock vendor sources.

    Behavior:
    - Accepts a ProcurementRequest and queries two mock vendors.
    - Returns a DiscoveryResult containing all products (no ranking or filtering).
    - Reports vendors that have no available products as unavailable_vendors.
    """

    def __init__(self, sources: List[callable] | None = None) -> None:
        # sources are callables returning List[VendorProduct]
        self.sources = sources or [vendor_alpha_products, vendor_bravo_products]

    def discover(self, request: ProcurementRequest) -> DiscoveryResult:
        cat = normalize_category(request.category)
        quantity = request.quantity

        all_products: List[VendorProduct] = []
        unavailable_vendors: List[str] = []

        for src in self.sources:
            try:
                products = src()
            except Exception:
                # source failure: mark vendor as unavailable by name if possible
                # best-effort: skip this source
                continue
            all_products.extend(products)
            # mark vendor unavailable if none of its products are available
            vendor_names = {p.vendor_name for p in products}
            for vn in vendor_names:
                has_available = any(p.available for p in products if p.vendor_name == vn)
                if not has_available:
                    unavailable_vendors.append(vn)

        metadata = DiscoveryMetadata(timestamp=datetime.utcnow().isoformat() + "Z", sources_queried=len(self.sources), total_products_found=len(all_products))

        result = DiscoveryResult(
            procurement_category=cat or "",
            requested_quantity=quantity,
            discovered_products=all_products,
            unavailable_vendors=unavailable_vendors,
            metadata=metadata,
        )
        return result
