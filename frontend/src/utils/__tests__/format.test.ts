import { describe, it, expect } from 'vitest';
import { formatCurrency, formatPercent, formatNumber, formatYears, formatDate, formatRole } from '../format';

describe('Format Utilities (format.ts)', () => {
  describe('formatCurrency', () => {
    it('formats normal positive currency amounts in INR', () => {
      expect(formatCurrency(58000)).toMatch(/₹\s*58,000(\.00)?/);
      expect(formatCurrency(59382.5)).toMatch(/₹\s*59,382\.50/);
    });

    it('formats zero correctly without displaying N/A or undefined', () => {
      expect(formatCurrency(0)).toMatch(/₹\s*0\.00/);
    });

    it('returns N/A on null, undefined, or NaN', () => {
      expect(formatCurrency(null)).toBe('N/A');
      expect(formatCurrency(undefined)).toBe('N/A');
      expect(formatCurrency(NaN)).toBe('N/A');
    });

    it('supports custom fallback string', () => {
      expect(formatCurrency(null, '—')).toBe('—');
      expect(formatCurrency(undefined, '₹0.00')).toBe('₹0.00');
    });
  });

  describe('formatPercent', () => {
    it('formats probability decimal fractions accurately as percentages', () => {
      expect(formatPercent(0.025)).toBe('2.5%');
      expect(formatPercent(0.03)).toBe('3.0%');
      expect(formatPercent(0.14)).toBe('14.0%');
      expect(formatPercent(0.018)).toBe('1.8%');
    });

    it('formats 0 rate as 0.0%', () => {
      expect(formatPercent(0)).toBe('0.0%');
    });

    it('returns N/A on null, undefined, or NaN without ever outputting NaN%', () => {
      expect(formatPercent(null)).toBe('N/A');
      expect(formatPercent(undefined)).toBe('N/A');
      expect(formatPercent(NaN)).toBe('N/A');
    });
  });

  describe('formatNumber', () => {
    it('formats integers and decimals cleanly', () => {
      expect(formatNumber(5)).toBe('5');
      expect(formatNumber(1000)).toBe('1,000');
      expect(formatNumber(0)).toBe('0');
    });

    it('handles null, undefined, NaN', () => {
      expect(formatNumber(null)).toBe('N/A');
      expect(formatNumber(undefined)).toBe('N/A');
      expect(formatNumber(NaN)).toBe('N/A');
    });
  });

  describe('formatYears', () => {
    it('formats 1 year as singular and >1 as plural', () => {
      expect(formatYears(1)).toBe('1 yr');
      expect(formatYears(3)).toBe('3 yrs');
    });

    it('handles null and undefined', () => {
      expect(formatYears(null)).toBe('N/A');
      expect(formatYears(undefined)).toBe('N/A');
    });
  });

  describe('formatDate & formatRole', () => {
    it('formats date and role gracefully', () => {
      expect(formatDate(null)).toBe('N/A');
      expect(formatRole('ROLE_PROCUREMENT_MANAGER')).toBe('Procurement Manager');
      expect(formatRole('PROCUREMENT_MANAGER')).toBe('Procurement Manager');
      expect(formatRole('ADMIN')).toBe('Administrator');
      expect(formatRole('USER')).toBe('Employee');
    });
  });
});
