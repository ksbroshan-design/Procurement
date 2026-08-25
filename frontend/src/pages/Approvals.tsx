import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getPendingApprovals, approveProcurement, rejectProcurement } from '../api/approval';
import { ApprovalResponse } from '../types';
import { formatCurrency, formatDate } from '../utils/format';
import {
  AlertTriangle,
  CheckCircle2,
  XCircle,
  RefreshCw,
  ExternalLink,
  MessageSquare,
  ShieldAlert,
} from 'lucide-react';

export const Approvals: React.FC = () => {
  const [approvals, setApprovals] = useState<ApprovalResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  const [commentMap, setCommentMap] = useState<Record<string, string>>({});
  const [submittingMap, setSubmittingMap] = useState<Record<string, boolean>>({});

  const fetchApprovals = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getPendingApprovals();
      if (Array.isArray(data)) {
        setApprovals(data);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve pending approvals from backend.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchApprovals();
  }, []);

  const handleApprove = async (appr: ApprovalResponse) => {
    const id = appr.procurementId;
    setSubmittingMap((prev) => ({ ...prev, [id]: true }));
    setError(null);
    setActionSuccess(null);

    try {
      await approveProcurement(
        id,
        commentMap[id] || 'Approved by procurement manager',
        appr.proposedOfferId || undefined
      );
      setActionSuccess('Procurement #' + id.substring(0, 8) + ' approved successfully. Proceeding to revalidation.');
      fetchApprovals();
    } catch (err: any) {
      setError(err.message || 'Approval action failed.');
    } finally {
      setSubmittingMap((prev) => ({ ...prev, [id]: false }));
    }
  };

  const handleReject = async (appr: ApprovalResponse) => {
    const id = appr.procurementId;
    const comment = commentMap[id];
    if (!comment || !comment.trim()) {
      setError('Please provide a rejection reason in comments.');
      return;
    }

    setSubmittingMap((prev) => ({ ...prev, [id]: true }));
    setError(null);
    setActionSuccess(null);

    try {
      await rejectProcurement(id, comment);
      setActionSuccess('Procurement #' + id.substring(0, 8) + ' rejected.');
      fetchApprovals();
    } catch (err: any) {
      setError(err.message || 'Rejection action failed.');
    } finally {
      setSubmittingMap((prev) => ({ ...prev, [id]: false }));
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">Approval Dashboard</h1>
          <p className="text-xs text-slate-400">
            Manager-level authorization queue for spend limit exceptions and high-value override recommendations
          </p>
        </div>

        <button
          onClick={fetchApprovals}
          disabled={isLoading}
          className="p-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition self-start sm:self-auto"
          title="Refresh"
        >
          <RefreshCw className={"w-4 h-4 " + (isLoading ? "animate-spin" : "")} />
        </button>
      </div>

      {/* Success Notification */}
      {actionSuccess && (
        <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-semibold flex items-center space-x-2.5 animate-in fade-in duration-200">
          <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
          <span>{actionSuccess}</span>
        </div>
      )}

      {/* Error Alert */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-semibold flex items-center space-x-2.5">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Approvals List */}
      {isLoading ? (
        <div className="p-12 text-center text-xs text-slate-400 space-y-2">
          <div className="w-6 h-6 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
          <p>Loading pending approval records from Spring Boot core...</p>
        </div>
      ) : approvals.length === 0 ? (
        <div className="glass-panel p-12 rounded-2xl border border-slate-800 text-center space-y-3">
          <div className="w-12 h-12 rounded-full bg-emerald-500/10 text-emerald-400 flex items-center justify-center mx-auto border border-emerald-500/20">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <h3 className="text-base font-bold text-slate-200">All Approvals Clear</h3>
          <p className="text-xs text-slate-400 max-w-sm mx-auto">
            There are currently no procurements requiring manager spend authorization or budget overrides.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {approvals.map((appr) => {
            const id = appr.procurementId;
            const isSubmitting = !!submittingMap[id];

            return (
              <div
                key={appr.approvalId || id}
                className="glass-panel p-6 rounded-2xl border border-amber-500/30 bg-gradient-to-br from-amber-950/15 via-slate-900/80 to-slate-950/90 space-y-5"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-800 pb-4">
                  <div className="flex items-center space-x-3">
                    <div className="w-9 h-9 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold">
                      <ShieldAlert className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="flex items-center space-x-2">
                        <span className="text-xs font-mono font-bold text-amber-300">
                          #{id.substring(0, 8)}...
                        </span>
                        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">
                          {appr.exceptionType || 'BUDGET_OVERRIDE'}
                        </span>
                      </div>
                      <p className="text-xs text-slate-400 mt-0.5">
                        Requested: {formatDate(appr.requestedAt)}
                      </p>
                    </div>
                  </div>

                  <Link
                    to={"/procurements/" + id}
                    className="text-xs text-brand-400 hover:text-brand-300 font-semibold flex items-center space-x-1 transition self-start sm:self-auto"
                  >
                    <span>View Lifecycle Stepper</span>
                    <ExternalLink className="w-3.5 h-3.5" />
                  </Link>
                </div>

                {/* Candidate & Financial Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                  <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 space-y-1">
                    <span className="text-slate-400 uppercase text-[10px]">Proposed Product</span>
                    <p className="font-bold text-slate-100">{appr.proposedProductName || 'Candidate Product'}</p>
                    <p className="text-slate-400 text-[11px]">{appr.proposedVendorName}</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 space-y-1">
                    <span className="text-slate-400 uppercase text-[10px]">Requested Total</span>
                    <p className="font-bold text-slate-100 text-sm">{formatCurrency(appr.requestedAmount)}</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 space-y-1">
                    <span className="text-slate-400 uppercase text-[10px]">Authorized Limit</span>
                    <p className="font-bold text-slate-300 text-sm">{formatCurrency(appr.authorizationLimit)}</p>
                  </div>
                  <div className="p-3.5 rounded-xl bg-slate-950/60 border border-slate-800/80 space-y-1">
                    <span className="text-slate-400 uppercase text-[10px]">Overage Difference</span>
                    <p className="font-bold text-rose-400 text-sm">+{formatCurrency(appr.difference)}</p>
                  </div>
                </div>

                {/* Reason Explanation */}
                <div className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-800 text-xs text-slate-300 leading-relaxed">
                  <span className="font-semibold text-slate-200">System Justification:</span>{' '}
                  {appr.reason || 'Spend amount exceeds authorized requester limit.'}
                </div>

                {/* Decision Actions */}
                <div className="pt-2 space-y-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1 flex items-center space-x-1.5">
                      <MessageSquare className="w-3.5 h-3.5 text-slate-400" />
                      <span>Decision Comments (Required for rejection)</span>
                    </label>
                    <input
                      type="text"
                      value={commentMap[id] || ''}
                      onChange={(e) =>
                        setCommentMap((prev) => ({ ...prev, [id]: e.target.value }))
                      }
                      placeholder="e.g. Approved quarterly budget expansion / Rejected due to cap"
                      className="w-full px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-brand-500"
                      disabled={isSubmitting}
                    />
                  </div>

                  <div className="flex justify-end space-x-3">
                    <button
                      onClick={() => handleReject(appr)}
                      disabled={isSubmitting}
                      className="px-4 py-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 rounded-lg text-xs font-bold transition flex items-center space-x-1.5 disabled:opacity-50"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Reject</span>
                    </button>
                    <button
                      onClick={() => handleApprove(appr)}
                      disabled={isSubmitting}
                      className="px-5 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold transition flex items-center space-x-1.5 shadow-lg shadow-emerald-600/20 disabled:opacity-50"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Authorize & Proceed</span>
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
