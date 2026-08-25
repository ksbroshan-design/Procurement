# Mock TCO data keyed by (vendor_name, product_name). This is intentionally small and
# used for deterministic unit tests. The engine treats this data as optional and
# will not invent values when an entry is missing.

MOCK_TCO = {
    ("Alpha Supplies", "Alpha Tablet A10"): {
        "expected_annual_maintenance": 500.0,
        "additional_costs": 100.0,
        "reliability_influence": 0.5,  # scales maintenance by (1 + (1 - reliability)*factor)
    },
    ("Bravo Bazaar", "Bravo 55-inch TV X1"): {
        "expected_annual_maintenance": 1200.0,
        "additional_costs": 50.0,
        "reliability_influence": 0.3,
    },
}


def get_mock_tco_data(vendor_name: str, product_name: str):
    return MOCK_TCO.get((vendor_name, product_name))
