import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { listProcurements } from '../api/procurement';
import { getPendingApprovals } from '../api/approval';
import { ProcurementSummary, ApprovalResponse } from '../types';
import { formatCurrency, formatDate, getStateBadgeClasses } from '../utils/format';
import {
  Sparkles,
  Layers,
  CheckCircle2,
  AlertTriangle,
  Clock,
  ArrowRight,
  RefreshCw,
} from 'lucide-react';

export const Dashboard: React.FC = () => {
  const { user, isManager } = useAuth();
  const [procurements, setProcurements] = useState<ProcurementSummary[]>([]);
  const [pendingApprovals, setPendingApprovals] = useState<ApprovalResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const procList = await listProcurements();
      if (Array.isArray(procList)) {
        setProcurements(procList);
      }
      if (isManager) {
        const approvals = await getPendingApprovals();
        if (Array.isArray(approvals)) {
          setPendingApprovals(approvals);
        }
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch dashboard data from Spring Boot backend.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [isManager]);

  const totalCount = procurements.length;
  const completedCount = procurements.filter((p) => p.status === 'COMPLETED').length;
  const waitingApprovalCount = procurements.filter((p) => p.status === 'WAITING_APPROVAL').length;
  const activeCount = procurements.filter(
    (p) => !['COMPLETED', 'REJECTED', 'FAILED'].includes(p.status)
  ).length;

  return (
    <div className="space-y-8">
      {/* Welcome Banner */}
      <div className="glass-panel p-6 sm:p-8 rounded-2xl border border-slate-800 relative overflow-hidden bg-gradient-to-r from-brand-950/30 via-slate-900/50 to-slate-950/70">
        <div className="max-w-2xl space-y-3">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full text-xs font-semibold bg-brand-500/10 text-brand-400 border border-brand-500/20">
            <Sparkles className="w-3.5 h-3.5" />
            <span>Autonomous Intelligence & Authoritative Governance</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-100 tracking-tight">
            Welcome back, {user?.name}
          </h1>
          <p className="text-sm text-slate-400 leading-relaxed">
            Submit natural language procurement requests, inspect multi-dimensional supplier evaluations, and manage authoritative corporate spend workflows.
          </p>
          <div className="pt-2 flex flex-wrap gap-3">
            <Link
              to="/procure"
              className="px-5 py-2.5 bg-brand-600 hover:bg-brand-500 text-white rounded-lg text-xs font-bold transition shadow-lg shadow-brand-500/25 flex items-center space-x-2"
            >
              <Sparkles className="w-4 h-4" />
              <span>New AI Procurement</span>
            </Link>
            <button
              onClick={fetchData}
              disabled={isLoading}
              className="px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 rounded-lg text-xs font-semibold transition flex items-center space-x-2"
            >
              <RefreshCw className={"w-3.5 h-3.5 " + (isLoading ? "animate-spin" : "")} />
              <span>Refresh Metrics</span>
            </button>
          </div>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-medium flex items-center space-x-2.5">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="glass-card p-5 rounded-xl border border-slate-800 space-y-2">
          <div className="flex justify-between items-center text-slate-400">
            <span className="text-xs font-semibold uppercase tracking-wider">Total Orders</span>
            <Layers className="w-4 h-4 text-brand-400" />
          </div>
          <p className="text-2xl font-black text-slate-100">{isLoading ? '-' : totalCount}</p>
          <p className="text-[11px] text-slate-400">All registered procurement requests</p>
        </div>

        <div className="glass-card p-5 rounded-xl border border-slate-800 space-y-2">
          <div className="flex justify-between items-center text-slate-400">
            <span className="text-xs font-semibold uppercase tracking-wider">Pending Approval</span>
            <AlertTriangle className="w-4 h-4 text-amber-400" />
          </div>
          <p className="text-2xl font-black text-amber-400">{isLoading ? '-' : waitingApprovalCount}</p>
          <p className="text-[11px] text-slate-400">Requires manager review</p>
        </div>

        <div className="glass-card p-5 rounded-xl border border-slate-800 space-y-2">
          <div className="flex justify-between items-center text-slate-400">
            <span className="text-xs font-semibold uppercase tracking-wider">Confirmed & Done</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <p className="text-2xl font-black text-emerald-400">{isLoading ? '-' : completedCount}</p>
          <p className="text-[11px] text-slate-400">Purchase orders created</p>
        </div>

        <div className="glass-card p-5 rounded-xl border border-slate-800 space-y-2">
          <div className="flex justify-between items-center text-slate-400">
            <span className="text-xs font-semibold uppercase tracking-wider">In-Flight</span>
            <Clock className="w-4 h-4 text-sky-400" />
          </div>
          <p className="text-2xl font-black text-sky-400">{isLoading ? '-' : activeCount}</p>
          <p className="text-[11px] text-slate-400">Actively processing</p>
        </div>
      </div>

      {/* Manager Approval Banner */}
      {isManager && pendingApprovals.length > 0 && (
        <div className="glass-panel p-5 rounded-xl border border-amber-500/40 bg-amber-950/15 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-100">
                {pendingApprovals.length} Approval Requests Require Manager Decision
              </h3>
              <p className="text-xs text-amber-300/80">
                Procurements currently halted at state <span className="font-mono">WAITING_APPROVAL</span>.
              </p>
            </div>
          </div>
          <Link
            to="/approvals"
            className="px-4 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-lg text-xs transition flex items-center space-x-1.5"
          >
            <span>Review Approvals</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      )}

      {/* Recent Procurements Table */}
      <div className="glass-panel rounded-xl border border-slate-800 overflow-hidden space-y-0">
        <div className="p-5 border-b border-slate-800 flex justify-between items-center">
          <div>
            <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
              Recent Procurements
            </h3>
            <p className="text-xs text-slate-400">Live operational records from Spring Boot core</p>
          </div>
          <Link
            to="/procurements"
            className="text-xs text-brand-400 hover:text-brand-300 font-semibold flex items-center space-x-1 transition"
          >
            <span>View All</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        {isLoading ? (
          <div className="p-8 text-center text-xs text-slate-400 space-y-2">
            <div className="w-6 h-6 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
            <p>Fetching procurement stream...</p>
          </div>
        ) : procurements.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-400 space-y-2">
            <Layers className="w-8 h-8 text-slate-600 mx-auto" />
            <p>No procurement requests created yet.</p>
            <Link
              to="/procure"
              className="inline-block mt-2 text-brand-400 hover:text-brand-300 font-semibold"
            >
              Start your first AI procurement &rarr;
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-900/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                <tr>
                  <th className="px-5 py-3">Category</th>
                  <th className="px-5 py-3">Units</th>
                  <th className="px-5 py-3">Selected Product / Vendor</th>
                  <th className="px-5 py-3">Spend Limit</th>
                  <th className="px-5 py-3 text-center">Lifecycle State</th>
                  <th className="px-5 py-3 text-right">Created</th>
                  <th className="px-5 py-3 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {procurements.slice(0, 5).map((proc) => (
                  <tr key={proc.id} className="hover:bg-slate-900/60 transition">
                    <td className="px-5 py-3.5 font-bold text-slate-100">{proc.category}</td>
                    <td className="px-5 py-3.5 font-medium">{proc.quantity}</td>
                    <td className="px-5 py-3.5">
                      {proc.selectedProductName ? (
                        <div>
                          <div className="font-semibold text-slate-200">{proc.selectedProductName}</div>
                          <div className="text-[11px] text-slate-400">{proc.selectedVendorName}</div>
                        </div>
                      ) : (
                        <span className="text-slate-500 italic">Discovery in progress</span>
                      )}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-slate-200">
                      {formatCurrency(proc.authorizationLimit)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <span
                        className={"inline-block px-2.5 py-0.5 rounded-full text-[10px] font-bold border " + getStateBadgeClasses(proc.status)}
                      >
                        {proc.status}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right text-slate-400">
                      {formatDate(proc.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <Link
                        to={"/procurements/" + proc.id}
                        className="px-3 py-1.5 rounded-md bg-slate-900 hover:bg-brand-600 hover:text-white text-slate-300 border border-slate-800 text-[11px] font-medium transition"
                      >
                        Details
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
