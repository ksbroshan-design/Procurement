from __future__ import annotations

from typing import List

from .models import VendorProduct


def vendor_alpha_products() -> List[VendorProduct]:
    """Mock vendor 'Alpha Supplies' product set."""
    return [
        VendorProduct(
            vendor_name="Alpha Supplies",
            product_name="Alpha Tablet A10",
            category="tablet",
            price=27999.0,
            specifications={"RAM": "8GB", "storage": "128GB", "screen": "10.5 inch"},
            warranty_years=1,
            delivery_days=5,
            reliability_score=0.88,
            return_policy="30 days",
            available=True,
        ),
        VendorProduct(
            vendor_name="Alpha Supplies",
            product_name="Alpha Office Chair Ergonomic",
            category="office chair",
            price=7999.0,
            specifications={"type": "ergonomic", "material": "mesh"},
            warranty_years=2,
            delivery_days=7,
            reliability_score=0.82,
            return_policy="14 days",
            available=True,
        ),
        VendorProduct(
            vendor_name="Alpha Supplies",
            product_name="Alpha Printer Pro",
            category="printer",
            price=12000.0,
            specifications={"duplex": True, "wireless": True},
            warranty_years=1,
            delivery_days=3,
            reliability_score=0.79,
            return_policy="7 days",
            available=False,  # out of stock to exercise unavailable vendors
        ),
    ]


def vendor_bravo_products() -> List[VendorProduct]:
    """Mock vendor 'Bravo Bazaar' product set."""
    return [
        VendorProduct(
            vendor_name="Bravo Bazaar",
            product_name="Bravo 55-inch TV X1",
            category="tv",
            price=35999.0,
            specifications={"screen_size": "55 inch", "resolution": "4K"},
            warranty_years=2,
            delivery_days=6,
            reliability_score=0.91,
            return_policy="30 days",
            available=True,
        ),
        VendorProduct(
            vendor_name="Bravo Bazaar",
            product_name="Bravo Tablet S7",
            category="tablet",
            price=29999.0,
            specifications={"RAM": "8GB", "storage": "256GB"},
            warranty_years=1,
            delivery_days=4,
            reliability_score=0.85,
            return_policy="15 days",
            available=True,
        ),
        VendorProduct(
            vendor_name="Bravo Bazaar",
            product_name="Bravo Monitor 24",
            category="monitor",
            price=7999.0,
            specifications={"screen_size": "24 inch", "refresh_rate": "60Hz"},
            warranty_years=1,
            delivery_days=5,
            reliability_score=0.8,
            return_policy="14 days",
            available=True,
        ),
    ]
