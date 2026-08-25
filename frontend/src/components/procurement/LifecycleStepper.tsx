import React from 'react';
import { ProcurementState } from '../../types';
import {
  CheckCircle2,
  Clock,
  AlertTriangle,
  XCircle,
  ShieldCheck,
  Search,
  Cpu,
  ShoppingBag,
} from 'lucide-react';

interface LifecycleStepperProps {
  currentState: ProcurementState;
}

const PRIMARY_STEPS: { state: ProcurementState; label: string; icon: any }[] = [
  { state: 'SUBMITTED', label: 'Intent Submitted', icon: Clock },
  { state: 'VALIDATING', label: 'Brief Grounded', icon: Cpu },
  { state: 'SEARCHING', label: 'Vendor Discovery', icon: Search },
  { state: 'EVALUATING', label: 'Constraints Evaluated', icon: Cpu },
  { state: 'TCO_ANALYSIS', label: 'TCO Calculated', icon: Cpu },
  { state: 'RECOMMENDED', label: 'Offer Selected', icon: CheckCircle2 },
  { state: 'AUTHORIZATION_CHECK', label: 'Limit Evaluated', icon: ShieldCheck },
  { state: 'REVALIDATING', label: 'Pre-Purchase Checked', icon: ShieldCheck },
  { state: 'PURCHASING', label: 'Order Execution', icon: ShoppingBag },
  { state: 'COMPLETED', label: 'PO Confirmed', icon: CheckCircle2 },
];

export const LifecycleStepper: React.FC<LifecycleStepperProps> = ({ currentState }) => {
  const isTerminalFailure = currentState === 'REJECTED' || currentState === 'FAILED';
  const isWaitingApproval = currentState === 'WAITING_APPROVAL';
  const isWaitingUser = currentState === 'WAITING_USER';

  let currentIndex = PRIMARY_STEPS.findIndex((s) => s.state === currentState);
  if (currentIndex === -1) {
    if (currentState === 'WAITING_APPROVAL') {
      currentIndex = 6;
    } else if (currentState === 'NEGOTIATING') {
      currentIndex = 5;
    } else if (isTerminalFailure) {
      currentIndex = 6;
    } else {
      currentIndex = 0;
    }
  }

  return (
    <div className="glass-panel p-6 rounded-xl border border-slate-800 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
            Server-Authoritative Lifecycle
          </h3>
          <p className="text-xs text-slate-400">Strict Spring Boot State Machine Transition Pipeline</p>
        </div>
        <div className="flex items-center space-x-2">
          <span className="text-xs text-slate-400">Active State:</span>
          <span
            className={
              "text-xs font-bold px-2.5 py-1 rounded-full border " +
              (currentState === 'COMPLETED'
                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                : isWaitingApproval
                ? 'bg-amber-500/10 text-amber-400 border-amber-500/30 animate-pulse'
                : isTerminalFailure
                ? 'bg-rose-500/10 text-rose-400 border-rose-500/30'
                : isWaitingUser
                ? 'bg-orange-500/10 text-orange-400 border-orange-500/30'
                : 'bg-brand-500/10 text-brand-400 border-brand-500/30')
            }
          >
            {currentState}
          </span>
        </div>
      </div>

      {/* Exception Alerts */}
      {isWaitingApproval && (
        <div className="p-3.5 rounded-lg bg-amber-500/10 border border-amber-500/30 flex items-center space-x-3 text-amber-300 text-xs">
          <AlertTriangle className="w-5 h-5 flex-shrink-0 animate-pulse text-amber-400" />
          <div>
            <span className="font-bold">Human-in-the-Loop Required:</span> This procurement requires explicit manager authorization before advancing to revalidation.
          </div>
        </div>
      )}

      {isWaitingUser && (
        <div className="p-3.5 rounded-lg bg-orange-500/10 border border-orange-500/30 flex items-center space-x-3 text-orange-300 text-xs">
          <AlertTriangle className="w-5 h-5 flex-shrink-0 text-orange-400" />
          <div>
            <span className="font-bold">Awaiting Requester Input:</span> Revalidation retries exceeded or catalog conditions changed. User action needed.
          </div>
        </div>
      )}

      {isTerminalFailure && (
        <div className="p-3.5 rounded-lg bg-rose-500/10 border border-rose-500/30 flex items-center space-x-3 text-rose-300 text-xs">
          <XCircle className="w-5 h-5 flex-shrink-0 text-rose-400" />
          <div>
            <span className="font-bold">Procurement Terminated:</span> The procurement reached state <span className="font-mono font-semibold">{currentState}</span> and cannot proceed.
          </div>
        </div>
      )}

      {/* Stepper Steps */}
      <div className="grid grid-cols-2 sm:grid-cols-5 lg:grid-cols-10 gap-2 pt-2">
        {PRIMARY_STEPS.map((step, idx) => {
          const isPassed = idx < currentIndex || (idx === currentIndex && currentState === 'COMPLETED');
          const isCurrent = idx === currentIndex && currentState !== 'COMPLETED';

          return (
            <div
              key={step.state}
              className={
                "p-2.5 rounded-lg border flex flex-col items-center text-center transition-all " +
                (isPassed
                  ? "bg-slate-900/40 border-emerald-500/30 text-slate-300"
                  : isCurrent
                  ? isWaitingApproval
                    ? "bg-amber-500/10 border-amber-500/40 text-amber-300 shadow-lg shadow-amber-500/10"
                    : "bg-brand-500/10 border-brand-500/40 text-brand-300 shadow-lg shadow-brand-500/10"
                  : "bg-slate-900/20 border-slate-800/60 text-slate-400 opacity-60")
              }
            >
              <div
                className={
                  "w-6 h-6 rounded-full flex items-center justify-center mb-1 text-xs " +
                  (isPassed
                    ? "bg-emerald-500/20 text-emerald-400"
                    : isCurrent
                    ? isWaitingApproval
                      ? "bg-amber-500/20 text-amber-400 animate-pulse"
                      : "bg-brand-500/20 text-brand-400 animate-pulse"
                    : "bg-slate-800 text-slate-400")
                }
              >
                {isPassed ? <CheckCircle2 className="w-3.5 h-3.5" /> : <span>{idx + 1}</span>}
              </div>
              <span className="text-[10px] font-semibold tracking-tight leading-snug line-clamp-2">
                {step.label}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
};
