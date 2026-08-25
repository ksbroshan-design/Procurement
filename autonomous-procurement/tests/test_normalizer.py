from app.discovery.normalizer import normalize_product
from app.discovery.models import VendorProduct


def test_do_not_infer_from_product_name():
    p = VendorProduct(vendor_name="X", product_name='Samsung 55" UHD Smart TV', category="tv", price=1000.0, specifications={})
    norm = normalize_product(p)
    # no specs were provided, so normalizer should not invent screen_size or resolution
    assert norm.specifications == {}


def test_normalize_structured_screen_and_resolution():
    p = VendorProduct(vendor_name="Y", product_name="55-inch TV", category="tv", price=900.0, specifications={"screen": "55 inch", "resolution": "UHD"})
    norm = normalize_product(p)
    assert norm.specifications.get("screen_size_inches") == 55
    assert norm.specifications.get("resolution") == "4K"


def test_normalize_ram_and_storage_units():
    p = VendorProduct(vendor_name="Z", product_name="Tablet", category="tablet", price=200.0, specifications={"RAM": "8192 MB", "storage": "0.5 TB"})
    norm = normalize_product(p)
    assert norm.specifications.get("ram_gb") == 8
    assert norm.specifications.get("storage_gb") == 512


def test_preserve_unknown_specs():
    specs = {"material": "mesh", "weight_capacity": "120kg", "lumens": 3500}
    p = VendorProduct(vendor_name="A", product_name="Chair", category="office chair", price=100.0, specifications=specs)
    norm = normalize_product(p)
    # unknown fields preserved
    assert norm.specifications.get("material") == "mesh"
    assert norm.specifications.get("weight_capacity") == "120kg"
    assert norm.specifications.get("lumens") == 3500


def test_warranty_not_duplicated_when_present_field():
    # product has warranty_years set; normalizer should not create duplicate warranty_years in specs
    p = VendorProduct(vendor_name="B", product_name="Printer", category="printer", price=150.0, specifications={"warranty": "3 years"}, warranty_years=3)
    norm = normalize_product(p)
    assert "warranty_years" not in norm.specifications
    # original warranty string preserved
    assert norm.specifications.get("warranty") == "3 years"
