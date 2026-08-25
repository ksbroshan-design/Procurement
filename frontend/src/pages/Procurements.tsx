import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { listProcurements } from '../api/procurement';
import { ProcurementSummary } from '../types';
import { formatCurrency, formatDate, getStateBadgeClasses } from '../utils/format';
import {
  Layers,
  Search,
  RefreshCw,
  ArrowRight,
  Sparkles,
} from 'lucide-react';

export const Procurements: React.FC = () => {
  const [procurements, setProcurements] = useState<ProcurementSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  const fetchProcurements = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const list = await listProcurements();
      if (Array.isArray(list)) {
        setProcurements(list);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load procurement records.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchProcurements();
  }, []);

  const filteredProcurements = procurements.filter((p) => {
    const matchesSearch =
      p.category?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.id?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.selectedProductName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.selectedVendorName?.toLowerCase().includes(searchQuery.toLowerCase());

    if (!matchesSearch) return false;

    if (statusFilter === 'ALL') return true;
    if (statusFilter === 'COMPLETED') return p.status === 'COMPLETED';
    if (statusFilter === 'WAITING_APPROVAL') return p.status === 'WAITING_APPROVAL';
    if (statusFilter === 'ACTIVE') return !['COMPLETED', 'REJECTED', 'FAILED'].includes(p.status);
    if (statusFilter === 'TERMINATED') return ['REJECTED', 'FAILED'].includes(p.status);

    return true;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">Procurements</h1>
          <p className="text-xs text-slate-400">All registered corporate procurement requests and live lifecycles</p>
        </div>

        <div className="flex items-center space-x-3">
          <button
            onClick={fetchProcurements}
            disabled={isLoading}
            className="p-2.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition"
            title="Refresh list"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
          <Link
            to="/procure"
            className="px-4 py-2 bg-brand-600 hover:bg-brand-500 text-white rounded-lg text-xs font-bold transition shadow-lg shadow-brand-500/25 flex items-center space-x-2"
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>New AI Request</span>
          </Link>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="glass-panel p-4 rounded-xl border border-slate-800 flex flex-col md:flex-row gap-4 items-center justify-between">
        {/* Search */}
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by ID, Category, Product..."
            className="w-full pl-9 pr-4 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-brand-500 transition"
          />
        </div>

        {/* Status Filter Tabs */}
        <div className="flex flex-wrap gap-1.5 w-full md:w-auto">
          {[
            { id: 'ALL', label: 'All Requests' },
            { id: 'ACTIVE', label: 'Active In-Flight' },
            { id: 'WAITING_APPROVAL', label: 'Waiting Approval' },
            { id: 'COMPLETED', label: 'Completed POs' },
            { id: 'TERMINATED', label: 'Terminated' },
          ].map((tab) => (
            <button
              key={tab.id}
              onClick={() => setStatusFilter(tab.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                statusFilter === tab.id
                  ? 'bg-brand-600 text-white shadow-md shadow-brand-500/20'
                  : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-800'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Table Card */}
      <div className="glass-panel rounded-xl border border-slate-800 overflow-hidden">
        {isLoading ? (
          <div className="p-12 text-center text-xs text-slate-400 space-y-2">
            <div className="w-6 h-6 border-2 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
            <p>Loading authoritative procurement records...</p>
          </div>
        ) : filteredProcurements.length === 0 ? (
          <div className="p-12 text-center text-xs text-slate-400 space-y-2">
            <Layers className="w-8 h-8 text-slate-600 mx-auto" />
            <p>No procurement requests matching criteria.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-900/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
                <tr>
                  <th className="px-5 py-3">Procurement ID</th>
                  <th className="px-5 py-3">Category</th>
                  <th className="px-5 py-3">Quantity</th>
                  <th className="px-5 py-3">Selected Product / Vendor</th>
                  <th className="px-5 py-3">Spend Limit</th>
                  <th className="px-5 py-3 text-center">Lifecycle Status</th>
                  <th className="px-5 py-3 text-right">Created Date</th>
                  <th className="px-5 py-3 text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {filteredProcurements.map((proc) => (
                  <tr key={proc.id} className="hover:bg-slate-900/60 transition group">
                    <td className="px-5 py-3.5 font-mono text-[11px] text-slate-400 font-semibold">
                      {proc.id ? `${proc.id.substring(0, 8)}...` : 'N/A'}
                    </td>
                    <td className="px-5 py-3.5 font-bold text-slate-100">{proc.category}</td>
                    <td className="px-5 py-3.5 font-medium">{proc.quantity} units</td>
                    <td className="px-5 py-3.5">
                      {proc.selectedProductName ? (
                        <div>
                          <div className="font-semibold text-slate-200">{proc.selectedProductName}</div>
                          <div className="text-[11px] text-slate-400">{proc.selectedVendorName}</div>
                        </div>
                      ) : (
                        <span className="text-slate-500 italic">Processing discovery</span>
                      )}
                    </td>
                    <td className="px-5 py-3.5 font-mono text-slate-200">
                      {formatCurrency(proc.authorizationLimit)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <span
                        className={`inline-block px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${getStateBadgeClasses(
                          proc.status
                        )}`}
                      >
                        {proc.status}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right text-slate-400">
                      {formatDate(proc.createdAt)}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <Link
                        to={`/procurements/${proc.id}`}
                        className="inline-flex items-center space-x-1 px-3 py-1.5 rounded-md bg-slate-900 group-hover:bg-brand-600 group-hover:text-white text-slate-300 border border-slate-800 text-[11px] font-medium transition"
                      >
                        <span>View</span>
                        <ArrowRight className="w-3 h-3" />
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
