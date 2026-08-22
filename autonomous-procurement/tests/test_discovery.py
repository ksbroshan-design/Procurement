import pytest

from app.discovery.discovery_service import DiscoveryService
from app.discovery.mock_sources import vendor_alpha_products, vendor_bravo_products
from app.discovery.normalizer import normalize_product
from app.discovery.models import VendorProduct
from app.models.procurement import ProcurementRequest


def test_discovery_returns_products_from_two_vendors():
    svc = DiscoveryService()
    req = ProcurementRequest(original_brief="Buy tablets", category="tablet", quantity=2)
    res = svc.discover(req)
    vendors = {p.vendor_name for p in res.discovered_products}
    assert "Alpha Supplies" in vendors
    assert "Bravo Bazaar" in vendors


def test_discovery_works_for_tablets():
    svc = DiscoveryService()
    req = ProcurementRequest(original_brief="Buy tablets", category="tablet", quantity=2)
    res = svc.discover(req)
    tablets = [p for p in res.discovered_products if p.category and p.category.lower() == "tablet"]
    assert tablets, "Expected at least one tablet in discovered products"


def test_discovery_works_for_tvs():
    svc = DiscoveryService()
    req = ProcurementRequest(original_brief="Buy a TV", category="tv", quantity=1)
    res = svc.discover(req)
    tvs = [p for p in res.discovered_products if p.category and p.category.lower() == "tv"]
    assert tvs, "Expected at least one TV in discovered products"


def test_discovery_works_for_office_chairs():
    svc = DiscoveryService()
    req = ProcurementRequest(original_brief="Buy office chairs", category="office chair", quantity=5)
    res = svc.discover(req)
    chairs = [p for p in res.discovered_products if p.category and "chair" in p.category.lower()]
    assert chairs, "Expected at least one office chair in discovered products"


def test_vendor_unavailable_representation():
    # Create a fake source where all products are unavailable for a vendor
    def dead_vendor():
        return [
            VendorProduct(vendor_name="DeadVendor", product_name="DV1", category="widget", price=10.0, specifications={}, available=False),
            VendorProduct(vendor_name="DeadVendor", product_name="DV2", category="widget", price=20.0, specifications={}, available=False),
        ]

    svc = DiscoveryService(sources=[dead_vendor, vendor_bravo_products])
    req = ProcurementRequest(original_brief="Buy widgets", category="widget", quantity=1)
    res = svc.discover(req)
    assert "DeadVendor" in res.unavailable_vendors
    # Bravo should not be listed as unavailable (has available products)
    assert "Bravo Bazaar" not in res.unavailable_vendors


def test_normalization_converts_different_vendor_formats_consistently():
    # Vendor A format
    a = VendorProduct(vendor_name="A", product_name="Tablet A", category="tablet", price=100.0, specifications={"RAM": "8 GB", "storage": "8192 MB"})
    # Vendor B format
    b = VendorProduct(vendor_name="B", product_name="Tablet B", category="tablet", price=110.0, specifications={"memory": "8192 MB", "SSD": "0.5 TB"})

    na = normalize_product(a)
    nb = normalize_product(b)

    assert na.specifications.get("ram_gb") == nb.specifications.get("ram_gb") == 8
    # storage normalization: 8192 MB -> 8 GB, 0.5 TB -> 512 GB
    assert na.specifications.get("storage_gb") == 8
    assert nb.specifications.get("storage_gb") == 512


def test_unknown_specifications_preserved():
    p = VendorProduct(vendor_name="X", product_name="Lumens Lamp", category="lighting", price=50.0, specifications={"lumens": 3500, "material": "plastic"})
    np = normalize_product(p)
    assert np.specifications.get("lumens") == 3500
    assert np.specifications.get("material") == "plastic"


def test_discovery_category_agnostic():
    svc = DiscoveryService()
    # Use an arbitrary category not present in mock sources; should not error
    req = ProcurementRequest(original_brief="Buy projectors", category="projector", quantity=1)
    res = svc.discover(req)
    assert hasattr(res, "discovered_products")
    assert isinstance(res.discovered_products, list)
