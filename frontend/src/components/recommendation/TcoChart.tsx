import React, { useState, useEffect } from 'react';
import { TcoBreakdown } from '../../types';
import { formatCurrency, formatPercent } from '../../utils/format';
import { Calculator, ShieldAlert, Wrench, ShieldCheck, DollarSign, RefreshCw, AlertCircle } from 'lucide-react';

interface TcoChartProps {
  tcoBreakdowns?: TcoBreakdown[] | null;
  selectedOfferId?: string | null;
}

export const TcoChart: React.FC<TcoChartProps> = ({ tcoBreakdowns, selectedOfferId }) => {
  const [activeOfferId, setActiveOfferId] = useState<string | null>(selectedOfferId || null);

  useEffect(() => {
    if (selectedOfferId) {
      setActiveOfferId(selectedOfferId);
    }
  }, [selectedOfferId]);

  if (!tcoBreakdowns || tcoBreakdowns.length === 0) {
    return (
      <div className="glass-panel p-6 rounded-xl border border-slate-800 text-center text-xs text-slate-400">
        No TCO breakdown data available.
      </div>
    );
  }

  // Resolve matching offer breakdown or fallback to the first element
  const primary = (activeOfferId ? tcoBreakdowns.find((t) => t.offerId === activeOfferId) : null) || tcoBreakdowns[0];

  if (!primary) {
    return (
      <div className="glass-panel p-6 rounded-xl border border-slate-800 text-center text-xs text-slate-400">
        No TCO breakdown data available.
      </div>
    );
  }

  const purchaseAmount = primary.totalPurchaseCost ?? primary.purchaseCost ?? 0;
  const maintenanceAmount = primary.totalMaintenanceCost ?? primary.maintenanceCost ?? 0;
  const repairAmount = primary.totalExpectedRepairCost ?? primary.expectedRepairCost ?? 0;
  const downtimeAmount = primary.totalExpectedDowntimeCost ?? primary.downtimeRiskCost ?? 0;
  const replacementAmount = primary.totalReplacementCost ?? primary.replacementRiskCost ?? 0;
  const warrantyBenefit = primary.totalWarrantyBenefit ?? primary.unitWarrantyBenefit ?? primary.warrantyBenefit ?? 0;
  const totalTco = primary.totalTco ?? 0;

  const horizonYears = primary.horizonYears ?? primary.durationYears ?? 3;
  const quantity = primary.quantity ?? 1;
  const failureRate = primary.failureRate ?? primary.annualFailureRate;
  const avgRepairCost = primary.averageRepairCost;
  const avgDowntimeCost = primary.averageDowntimeCost ?? primary.downtimeCostPerHour;
  const unitTco = primary.unitTco ?? 0;
  const unitPurchaseCost = primary.unitPurchaseCost;
  const warrantyYears = primary.warrantyYears ?? primary.effectiveWarrantyYears ?? 1;
  const warrantyType = primary.warrantyType || 'STANDARD';

  const maxCost = Math.max(
    totalTco,
    purchaseAmount + maintenanceAmount + repairAmount + downtimeAmount + replacementAmount,
    1
  );

  const getWidthPercent = (value: number) => {
    if (!maxCost || maxCost <= 0 || !value || value <= 0) return 0;
    return Math.min(100, Math.max(4, (value / maxCost) * 100));
  };

  const components = [
    {
      label: 'Initial Purchase Cost',
      amount: purchaseAmount,
      color: 'bg-brand-500',
      icon: DollarSign,
      desc: `${quantity} ${quantity === 1 ? 'unit' : 'units'} @ ${formatCurrency(unitPurchaseCost)}`,
    },
    {
      label: 'Routine Maintenance',
      amount: maintenanceAmount,
      color: 'bg-sky-500',
      icon: Wrench,
      desc: `${horizonYears}-year service & scheduled upkeep (${formatCurrency(primary.unitMaintenanceCost)}/unit)`,
    },
    {
      label: 'Expected Failure Repairs',
      amount: repairAmount,
      color: 'bg-amber-500',
      icon: ShieldAlert,
      desc: `Risk-adjusted by failure rate (${formatPercent(failureRate)}/yr)`,
    },
    {
      label: 'Operational Downtime Risk',
      amount: downtimeAmount,
      color: 'bg-purple-500',
      icon: Calculator,
      desc: `Impact of service interruptions (${formatCurrency(avgDowntimeCost)}/incident)`,
    },
    ...(replacementAmount > 0
      ? [
          {
            label: 'Post-Warranty Replacement Risk',
            amount: replacementAmount,
            color: 'bg-rose-500',
            icon: RefreshCw,
            desc: `High failure probability replacement exposure (${formatCurrency(primary.unitReplacementCost)}/unit)`,
          },
        ]
      : []),
  ];

  return (
    <div className="glass-panel p-6 rounded-xl border border-slate-800 space-y-6">
      {/* Header & Product Switcher */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
              Deterministic TCO Breakdown
            </h3>
            {tcoBreakdowns.length > 1 && (
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 font-semibold">
                {tcoBreakdowns.length} Candidate Models
              </span>
            )}
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            {horizonYears}-Year lifecycle analysis computed strictly by Spring Boot Financial Engine
          </p>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center gap-4">
          {/* Candidate selector if multiple offers exist */}
          {tcoBreakdowns.length > 1 && (
            <div className="flex items-center space-x-2">
              <span className="text-xs text-slate-400 whitespace-nowrap">Viewing:</span>
              <select
                value={primary.offerId}
                onChange={(e) => setActiveOfferId(e.target.value)}
                className="bg-slate-900 border border-slate-700 text-slate-200 text-xs rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-brand-500 max-w-[240px] truncate"
              >
                {tcoBreakdowns.map((t) => (
                  <option key={t.offerId} value={t.offerId}>
                    {t.productName} ({t.vendorName})
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="text-left sm:text-right">
            <span className="text-xs text-slate-400">Total Lifecycle TCO:</span>
            <p className="text-xl font-extrabold text-emerald-400">{formatCurrency(totalTco)}</p>
          </div>
        </div>
      </div>

      {/* Selected Product Banner */}
      <div className="p-3 rounded-lg bg-slate-900/60 border border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between text-xs gap-2">
        <div>
          <span className="text-slate-400">Model: </span>
          <span className="font-semibold text-slate-200">{primary.productName}</span>
          <span className="text-slate-500 mx-1.5">•</span>
          <span className="text-slate-400">Vendor: </span>
          <span className="font-medium text-slate-300">{primary.vendorName}</span>
        </div>
        <div className="text-slate-400">
          <span>Order Scope: </span>
          <span className="font-semibold text-slate-200">
            {quantity} {quantity === 1 ? 'unit' : 'units'} over {horizonYears} years
          </span>
        </div>
      </div>

      {/* Component Horizontal Bars */}
      <div className="space-y-4">
        {components.map((item, idx) => (
          <div key={idx} className="space-y-1.5">
            <div className="flex justify-between text-xs">
              <div className="flex items-center space-x-2">
                <span className={`w-2.5 h-2.5 rounded-sm ${item.color}`}></span>
                <span className="font-semibold text-slate-200">{item.label}</span>
                <span className="text-slate-400 text-[11px] hidden md:inline">({item.desc})</span>
              </div>
              <span className="font-bold text-slate-100">{formatCurrency(item.amount)}</span>
            </div>
            <div className="w-full h-3 bg-slate-900 rounded-full overflow-hidden border border-slate-800">
              <div
                className={`h-full ${item.color} rounded-full transition-all duration-500`}
                style={{ width: `${getWidthPercent(item.amount)}%` }}
              ></div>
            </div>
          </div>
        ))}

        {/* Warranty Coverage Offset */}
        {warrantyBenefit > 0 && (
          <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex justify-between items-center text-xs">
            <div className="flex items-center space-x-2 text-emerald-300">
              <ShieldCheck className="w-4 h-4" />
              <span>
                Warranty Coverage Offset ({warrantyYears} yrs {warrantyType} coverage)
              </span>
            </div>
            <span className="font-bold text-emerald-400">-{formatCurrency(warrantyBenefit)}</span>
          </div>
        )}
      </div>

      {/* Additional Risk Metas */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 text-center text-xs">
        <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800">
          <span className="text-slate-400 text-[10px] uppercase tracking-wider">Annual Failure Rate</span>
          <p className="font-bold text-slate-200 mt-0.5">{formatPercent(failureRate)}</p>
        </div>
        <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800">
          <span className="text-slate-400 text-[10px] uppercase tracking-wider">Avg Repair Cost</span>
          <p className="font-bold text-slate-200 mt-0.5">{formatCurrency(avgRepairCost)}</p>
        </div>
        <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800">
          <span className="text-slate-400 text-[10px] uppercase tracking-wider">Avg Downtime Cost</span>
          <p className="font-bold text-slate-200 mt-0.5">{formatCurrency(avgDowntimeCost)}</p>
        </div>
        <div className="p-2.5 rounded-lg bg-slate-900/60 border border-slate-800">
          <span className="text-slate-400 text-[10px] uppercase tracking-wider">Unit TCO</span>
          <p className="font-bold text-emerald-400 mt-0.5">{formatCurrency(unitTco)}</p>
        </div>
      </div>

      {/* Authoritative Calculation Assumptions */}
      {primary.assumptions && primary.assumptions.length > 0 && (
        <div className="p-3.5 rounded-lg bg-slate-900/40 border border-slate-800/60 space-y-1.5 text-xs">
          <div className="flex items-center space-x-1.5 text-slate-300 font-semibold text-[11px]">
            <AlertCircle className="w-3.5 h-3.5 text-brand-400" />
            <span>Authoritative Engine Assumptions</span>
          </div>
          <ul className="list-disc list-inside space-y-0.5 text-slate-400 text-[11px]">
            {primary.assumptions.map((assumption, aIdx) => (
              <li key={aIdx}>{assumption}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};

