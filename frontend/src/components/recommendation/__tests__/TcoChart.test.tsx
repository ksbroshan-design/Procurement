import { describe, it, expect } from 'vitest';
import React from 'react';
import { render, screen } from '@testing-library/react';
import { TcoChart } from '../TcoChart';
import { TcoBreakdown } from '../../../types';

describe('TcoChart Component', () => {
  const mockTcoBreakdownLG: TcoBreakdown = {
    offerId: '87965ffc-208f-4854-8756-d8502f09f394',
    productId: '79b85ea3-4f42-4f99-a8c4-584a363fb2e4',
    productName: 'LG C3 55-Inch 4K OLED evo Smart TV',
    vendorName: 'TechDirect Enterprises',
    quantity: 1,
    horizonYears: 3,
    unitPurchaseCost: 56000.0,
    unitMaintenanceCost: 3360.0,
    unitExpectedRepairCost: 90.0,
    unitExpectedDowntimeCost: 30.0,
    unitReplacementCost: 0,
    unitWarrantyBenefit: 97.5,
    unitTco: 59382.5,
    totalPurchaseCost: 56000.0,
    totalMaintenanceCost: 3360.0,
    totalExpectedRepairCost: 90.0,
    totalExpectedDowntimeCost: 30.0,
    totalReplacementCost: 0.0,
    totalWarrantyBenefit: 97.5,
    totalTco: 59382.5,
    failureRate: 0.025,
    averageRepairCost: 1200.0,
    averageDowntimeCost: 400.0,
    warrantyYears: 3,
    warrantyType: 'ONSITE',
    dataGrounded: true,
    assumptions: [
      'TCO evaluated over a 3-year ownership horizon',
      'Historical reliability data applied: annual failure rate = 2.50%, avg repair cost = ₹1200.00, avg downtime cost = ₹400.00 (sample size: 850)',
      'Annual baseline maintenance estimated at 2.0% of unit price per year',
      'Warranty (3-year ONSITE) offsets ₹97.50 in repair/downtime costs during covered period',
    ],
  };

  const mockTcoBreakdownSamsung: TcoBreakdown = {
    offerId: '7a2a414f-9f4b-452d-b9ee-9b1492a180c6',
    productId: '2d3c873f-3357-4fd1-a72e-6fbda3024a9d',
    productName: 'Samsung 55-Inch Neo QLED 4K Smart TV',
    vendorName: 'GlobalEquip Solutions',
    quantity: 1,
    horizonYears: 3,
    unitPurchaseCost: 58000.0,
    unitMaintenanceCost: 3480.0,
    unitExpectedRepairCost: 135.0,
    unitExpectedDowntimeCost: 54.0,
    unitReplacementCost: 0,
    unitWarrantyBenefit: 149.85,
    unitTco: 61519.15,
    totalPurchaseCost: 58000.0,
    totalMaintenanceCost: 3480.0,
    totalExpectedRepairCost: 135.0,
    totalExpectedDowntimeCost: 54.0,
    totalReplacementCost: 0.0,
    totalWarrantyBenefit: 149.85,
    totalTco: 61519.15,
    failureRate: 0.03,
    averageRepairCost: 1500.0,
    averageDowntimeCost: 600.0,
    warrantyYears: 3,
    warrantyType: 'ONSITE',
    dataGrounded: true,
    assumptions: [],
  };

  it('renders normal populated TCO response accurately without NaN or undefined', () => {
    const { container } = render(<TcoChart tcoBreakdowns={[mockTcoBreakdownLG]} />);

    const html = container.innerHTML;
    expect(html).not.toContain('NaN');
    expect(html).not.toContain('undefined');

    // Asserts expected text and values
    expect(screen.getByText('LG C3 55-Inch 4K OLED evo Smart TV')).toBeDefined();
    expect(screen.getByText('TechDirect Enterprises')).toBeDefined();
    expect(screen.getByText('2.5%')).toBeDefined(); // failureRate = 0.025 -> 2.5%
    expect(html).toContain('3-Year lifecycle analysis');
    expect(html).toContain('1 unit over 3 years');
  });

  it('matches selectedOfferId when provided instead of blindly picking index 0', () => {
    const { container } = render(
      <TcoChart
        tcoBreakdowns={[mockTcoBreakdownSamsung, mockTcoBreakdownLG]}
        selectedOfferId="87965ffc-208f-4854-8756-d8502f09f394"
      />
    );

    // Selected offer is LG (56,000 / 59,382.50), not Samsung (58,000)
    expect(screen.getByText('LG C3 55-Inch 4K OLED evo Smart TV')).toBeDefined();
    const html = container.innerHTML;
    expect(html).not.toContain('NaN');
    expect(html).not.toContain('undefined');
  });

  it('handles zero-valued costs and zero failure rates gracefully without NaN', () => {
    const zeroCostTco: TcoBreakdown = {
      ...mockTcoBreakdownLG,
      totalPurchaseCost: 0,
      totalMaintenanceCost: 0,
      totalExpectedRepairCost: 0,
      totalExpectedDowntimeCost: 0,
      totalReplacementCost: 0,
      totalWarrantyBenefit: 0,
      totalTco: 0,
      failureRate: 0,
      unitTco: 0,
    };

    const { container } = render(<TcoChart tcoBreakdowns={[zeroCostTco]} />);
    const html = container.innerHTML;

    expect(html).not.toContain('NaN');
    expect(html).not.toContain('undefined');
    expect(screen.getByText('0.0%')).toBeDefined();
  });

  it('handles missing/null fields gracefully displaying N/A without NaN or undefined', () => {
    const sparseTco: any = {
      offerId: 'sparse-1',
      productName: 'Generic Office Desk',
      vendorName: 'MegaRetail',
      quantity: 2,
      horizonYears: null,
      totalPurchaseCost: null,
      totalTco: null,
      failureRate: null,
      averageRepairCost: null,
      averageDowntimeCost: null,
    };

    const { container } = render(<TcoChart tcoBreakdowns={[sparseTco]} />);
    const html = container.innerHTML;

    expect(html).not.toContain('NaN');
    expect(html).not.toContain('undefined');
    expect(screen.getByText('Generic Office Desk')).toBeDefined();
  });

  it('renders fallback message when tcoBreakdowns is null or empty', () => {
    render(<TcoChart tcoBreakdowns={[]} />);
    expect(screen.getByText('No TCO breakdown data available.')).toBeDefined();

    render(<TcoChart tcoBreakdowns={null} />);
    expect(screen.getAllByText('No TCO breakdown data available.')).toBeDefined();
  });
});
