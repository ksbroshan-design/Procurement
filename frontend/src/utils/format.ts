import { ProcurementState, Role } from '../types';

export function formatCurrency(amount?: number | null, fallback = 'N/A'): string {
  if (amount === null || amount === undefined || (typeof amount === 'number' && isNaN(amount))) {
    return fallback;
  }
  const num = Number(amount);
  if (isNaN(num)) return fallback;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
    minimumFractionDigits: 2,
  }).format(num);
}

export function formatPercent(rate?: number | null, fallback = 'N/A'): string {
  if (rate === null || rate === undefined || (typeof rate === 'number' && isNaN(rate))) {
    return fallback;
  }
  const num = Number(rate);
  if (isNaN(num)) return fallback;
  const pct = num <= 1 && num > 0 ? num * 100 : num;
  return pct.toFixed(1) + '%';
}

export function formatNumber(val?: number | null, fallback = 'N/A'): string {
  if (val === null || val === undefined || (typeof val === 'number' && isNaN(val))) {
    return fallback;
  }
  const num = Number(val);
  if (isNaN(num)) return fallback;
  return num.toLocaleString('en-US');
}

export function formatYears(years?: number | null, fallback = 'N/A'): string {
  if (years === null || years === undefined || (typeof years === 'number' && isNaN(years))) {
    return fallback;
  }
  const num = Number(years);
  if (isNaN(num)) return fallback;
  return num === 1 ? '1 yr' : `${num} yrs`;
}

export function formatDate(dateStr?: string | null): string {
  if (!dateStr) return 'N/A';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch (err) {
    return dateStr;
  }
}

export function formatRole(role?: Role | string | null): string {
  if (!role) return 'User';
  const norm = role.toUpperCase().replace('ROLE_', '');
  switch (norm) {
    case 'ADMIN':
      return 'Administrator';
    case 'PROCUREMENT_MANAGER':
      return 'Procurement Manager';
    case 'USER':
      return 'Employee';
    default:
      return role;
  }
}

export function getStateBadgeClasses(state?: ProcurementState | null): string {
  switch (state) {
    case 'COMPLETED':
      return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
    case 'WAITING_APPROVAL':
      return 'bg-amber-500/10 text-amber-400 border-amber-500/30 animate-pulse';
    case 'REVALIDATING':
      return 'bg-sky-500/10 text-sky-400 border-sky-500/30';
    case 'PURCHASING':
      return 'bg-cyan-500/10 text-cyan-400 border-cyan-500/30';
    case 'RECOMMENDED':
      return 'bg-purple-500/10 text-purple-400 border-purple-500/30';
    case 'SEARCHING':
    case 'EVALUATING':
    case 'TCO_ANALYSIS':
    case 'VALIDATING':
    case 'AUTHORIZATION_CHECK':
      return 'bg-blue-500/10 text-blue-400 border-blue-500/30';
    case 'WAITING_USER':
      return 'bg-orange-500/10 text-orange-400 border-orange-500/30';
    case 'REJECTED':
      return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
    case 'FAILED':
      return 'bg-red-500/10 text-red-400 border-red-500/30';
    case 'SUBMITTED':
    default:
      return 'bg-slate-700/30 text-slate-300 border-slate-600/40';
  }
}
