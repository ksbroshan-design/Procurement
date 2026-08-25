from app.discovery.mock_sources import vendor_alpha_products, vendor_bravo_products
from app.recommendation.tco_engine import TCOEngine
from app.recommendation.mock_tco_data import MOCK_TCO


def test_tco_with_mock_data():
    # pick a product with mock data
    products = vendor_alpha_products()
    prod = next(p for p in products if p.product_name == "Alpha Tablet A10")
    engine = TCOEngine(analysis_period_years=3)
    tco = engine.compute_tco(prod)

    assert tco.purchase_price == prod.price
    assert tco.expected_maintenance_cost is not None
    assert "No historical" not in " ".join(tco.assumptions)
    assert tco.estimated_total_cost >= tco.purchase_price


def test_tco_without_mock_data():
    # pick a product without mock data (monitor in bravo)
    products = vendor_bravo_products()
    prod = next(p for p in products if p.product_name == "Bravo Monitor 24")
    engine = TCOEngine(analysis_period_years=3)
    tco = engine.compute_tco(prod)

    assert tco.purchase_price == prod.price
    assert tco.expected_maintenance_cost is None
    assert any("No historical" in a for a in tco.assumptions)


def test_tco_comparison_savings():
    # compare alpha tablet vs bravo tv
    alpha = next(p for p in vendor_alpha_products() if p.product_name == "Alpha Tablet A10")
    bravo = next(p for p in vendor_bravo_products() if p.product_name == "Bravo 55-inch TV X1")
    engine = TCOEngine(analysis_period_years=3)
    tco_alpha = engine.compute_tco(alpha, comparison=bravo)

    # savings should be set (could be positive or negative depending on mock data)
    assert tco_alpha.savings_vs_baseline is not None
    assert isinstance(tco_alpha.savings_vs_baseline, float)
