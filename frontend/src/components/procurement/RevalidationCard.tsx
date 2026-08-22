import React from 'react';
import { RevalidationResult } from '../../types';
import { CheckCircle2, XCircle, ShieldCheck } from 'lucide-react';

interface RevalidationCardProps {
  revalidation?: RevalidationResult | null;
}

export const RevalidationCard: React.FC<RevalidationCardProps> = ({ revalidation }) => {
  if (!revalidation) {
    return (
      <div className="glass-panel p-6 rounded-xl border border-slate-800 text-center text-xs text-slate-400">
        Pre-purchase revalidation has not been triggered yet.
      </div>
    );
  }

  const isValid = revalidation.valid;

  return (
    <div className="glass-panel p-6 rounded-xl border border-slate-800 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <ShieldCheck className="w-4 h-4 text-brand-400" />
            <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
              Pre-Purchase Revalidation Engine
            </h3>
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            Real-time catalog, stock, and pricing freshness verification before purchase execution
          </p>
        </div>
        <div className="flex items-center space-x-2">
          <span
            className={
              "inline-flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-bold border " +
              (isValid
                ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                : "bg-rose-500/10 text-rose-400 border-rose-500/30")
            }
          >
            {isValid ? (
              <>
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>ALL CHECKS PASSED</span>
              </>
            ) : (
              <>
                <XCircle className="w-3.5 h-3.5" />
                <span>REVALIDATION BLOCKED</span>
              </>
            )}
          </span>
        </div>
      </div>

      {/* Backend Explanation */}
      <div
        className={
          "p-3.5 rounded-lg text-xs leading-relaxed " +
          (isValid
            ? "bg-emerald-500/5 border border-emerald-500/20 text-emerald-300"
            : "bg-rose-500/5 border border-rose-500/20 text-rose-300")
        }
      >
        <span className="font-semibold">{revalidation.message}</span>
        {revalidation.nextAction && (
          <p className="text-slate-400 text-[11px] mt-1">Next Action: {revalidation.nextAction}</p>
        )}
      </div>

      {/* 6-Point Verification Check Table */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1">
        {revalidation.checks?.map((check, idx) => (
          <div
            key={idx}
            className={
              "p-3.5 rounded-lg border flex items-start space-x-3 transition " +
              (check.passed
                ? "bg-slate-900/60 border-slate-800 text-slate-300"
                : "bg-rose-950/20 border-rose-500/30 text-rose-200")
            }
          >
            <div className="mt-0.5 flex-shrink-0">
              {check.passed ? (
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              ) : (
                <XCircle className="w-4 h-4 text-rose-400" />
              )}
            </div>
            <div className="text-xs space-y-0.5">
              <div className="flex items-center space-x-2 font-bold text-slate-100">
                <span>{check.name}</span>
                <span
                  className={
                    "text-[10px] font-semibold px-1.5 py-0.2 rounded " +
                    (check.passed
                      ? "bg-emerald-500/10 text-emerald-400"
                      : "bg-rose-500/10 text-rose-400")
                  }
                >
                  {check.passed ? 'PASS' : 'FAIL'}
                </span>
              </div>
              <p className="text-slate-400 text-[11px] leading-snug">{check.message}</p>
              {(check.expectedValue || check.actualValue) && (
                <div className="text-[10px] text-slate-400 pt-1 font-mono">
                  <span>Expected: {check.expectedValue}</span> • <span>Actual: {check.actualValue}</span>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
