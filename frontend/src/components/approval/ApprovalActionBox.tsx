import React, { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { ApprovalResponse } from '../../types';
import { approveProcurement, rejectProcurement } from '../../api/approval';
import { formatCurrency } from '../../utils/format';
import {
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Lock,
  MessageSquare,
} from 'lucide-react';

interface ApprovalActionBoxProps {
  procurementId: string;
  approval?: ApprovalResponse | null;
  onDecisionComplete: () => void;
}

export const ApprovalActionBox: React.FC<ApprovalActionBoxProps> = ({
  procurementId,
  approval,
  onDecisionComplete,
}) => {
  const { isManager, user } = useAuth();
  const [comments, setComments] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const handleApprove = async () => {
    setIsSubmitting(true);
    setActionError(null);
    try {
      await approveProcurement(
        procurementId,
        comments || 'Approved by procurement manager',
        approval?.proposedOfferId || undefined
      );
      onDecisionComplete();
    } catch (err: any) {
      setActionError(err.message || 'Failed to submit approval.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (!comments.trim()) {
      setActionError('Please provide a reason / comment when rejecting a procurement.');
      return;
    }
    setIsSubmitting(true);
    setActionError(null);
    try {
      await rejectProcurement(procurementId, comments);
      onDecisionComplete();
    } catch (err: any) {
      setActionError(err.message || 'Failed to submit rejection.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="glass-panel p-6 rounded-xl border-2 border-amber-500/40 bg-gradient-to-br from-amber-950/20 via-slate-900/60 to-slate-950/80 space-y-5">
      <div className="flex items-center space-x-3 text-amber-400">
        <div className="w-9 h-9 rounded-lg bg-amber-500/20 flex items-center justify-center border border-amber-500/30">
          <AlertTriangle className="w-5 h-5 text-amber-400" />
        </div>
        <div>
          <h3 className="text-base font-bold text-slate-100">
            Manager Authorization Escalation
          </h3>
          <p className="text-xs text-amber-300/80">
            Financial spend exceeds standard user limits or proposes budget override
          </p>
        </div>
      </div>

      {/* Financial Details Grid */}
      {approval && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs bg-slate-950/60 p-3.5 rounded-lg border border-slate-800">
          <div>
            <span className="text-slate-400">Requested Amount:</span>
            <p className="font-bold text-slate-100 mt-0.5">{formatCurrency(approval.requestedAmount)}</p>
          </div>
          <div>
            <span className="text-slate-400">Authorization Limit:</span>
            <p className="font-bold text-slate-100 mt-0.5">{formatCurrency(approval.authorizationLimit)}</p>
          </div>
          <div>
            <span className="text-slate-400">Over-Limit Difference:</span>
            <p className="font-bold text-rose-400 mt-0.5">+{formatCurrency(approval.difference)}</p>
          </div>
          <div>
            <span className="text-slate-400">Exception Classification:</span>
            <p className="font-semibold text-amber-300 mt-0.5">{approval.exceptionType || 'BUDGET_OVERRIDE'}</p>
          </div>
        </div>
      )}

      {/* Escalation Reason */}
      {approval?.reason && (
        <div className="text-xs text-slate-300 bg-slate-900/60 p-3 rounded-lg border border-slate-800/80">
          <span className="font-semibold text-slate-200">Escalation Reason:</span> {approval.reason}
        </div>
      )}

      {/* Error display */}
      {actionError && (
        <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-medium">
          {actionError}
        </div>
      )}

      {/* Decision Section */}
      {isManager ? (
        <div className="space-y-3 pt-2 border-t border-slate-800">
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1 flex items-center space-x-1.5">
              <MessageSquare className="w-3.5 h-3.5 text-slate-400" />
              <span>Decision Comments & Justification</span>
            </label>
            <textarea
              value={comments}
              onChange={(e) => setComments(e.target.value)}
              placeholder="Enter approval justification or rejection rationale for audit logging..."
              className="w-full h-20 px-3 py-2 bg-slate-950 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-brand-500 transition"
              disabled={isSubmitting}
            />
          </div>

          <div className="flex flex-wrap gap-3 justify-end pt-1">
            <button
              onClick={handleReject}
              disabled={isSubmitting}
              className="px-4 py-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 rounded-lg text-xs font-bold transition flex items-center space-x-1.5 disabled:opacity-50"
            >
              <XCircle className="w-4 h-4" />
              <span>Reject Procurement</span>
            </button>
            <button
              onClick={handleApprove}
              disabled={isSubmitting}
              className="px-5 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold shadow-lg shadow-emerald-600/20 transition flex items-center space-x-1.5 disabled:opacity-50"
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>Approve & Proceed to Revalidation</span>
            </button>
          </div>
        </div>
      ) : (
        <div className="p-3.5 rounded-lg bg-slate-900 border border-slate-800 text-xs text-slate-400 flex items-center space-x-2.5">
          <Lock className="w-4 h-4 text-slate-500 flex-shrink-0" />
          <span>
            Logged in as <strong className="text-slate-300">{user?.name} (ROLE_USER)</strong>. Manager-level authorization is required to approve or reject budget overrides.
          </span>
        </div>
      )}
    </div>
  );
};
