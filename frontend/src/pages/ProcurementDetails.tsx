import React, { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { getProcurement } from '../api/procurement';
import { getRecommendation, getTcoBreakdowns, getRanking } from '../api/intelligence';
import { getApproval } from '../api/approval';
import { getAuditTrail } from '../api/audit';
import { getPurchaseOrder } from '../api/purchaseOrder';
import {
  ProcurementSummary,
  RecommendationResponse,
  TcoBreakdown,
  ApprovalResponse,
  RevalidationResult,
  ProcurementAuditResponse,
  PurchaseOrder,
} from '../types';
import { formatCurrency, formatDate, getStateBadgeClasses } from '../utils/format';
import { LifecycleStepper } from '../components/procurement/LifecycleStepper';
import { RecommendationCard } from '../components/recommendation/RecommendationCard';
import { VendorComparisonTable } from '../components/recommendation/VendorComparisonTable';
import { TcoChart } from '../components/recommendation/TcoChart';
import { RevalidationCard } from '../components/procurement/RevalidationCard';
import { ApprovalActionBox } from '../components/approval/ApprovalActionBox';
import { AuditTimeline } from '../components/audit/AuditTimeline';
import {
  Layers,
  Sparkles,
  ShoppingBag,
  History,
  ShieldCheck,
  Calculator,
  RefreshCw,
  Copy,
  Check,
  ArrowLeft,
  AlertTriangle,
} from 'lucide-react';

export const ProcurementDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();

  const [procurement, setProcurement] = useState<ProcurementSummary | null>(null);
  const [recommendation, setRecommendation] = useState<RecommendationResponse | null>(null);
  const [tcoBreakdowns, setTcoBreakdowns] = useState<TcoBreakdown[]>([]);
  const [ranking, setRanking] = useState<any>(null);
  const [revalidation, setRevalidation] = useState<RevalidationResult | null>(null);
  const [approval, setApproval] = useState<ApprovalResponse | null>(null);
  const [auditResponse, setAuditResponse] = useState<ProcurementAuditResponse | null>(null);
  const [purchaseOrder, setPurchaseOrder] = useState<PurchaseOrder | null>(null);

  const [activeTab, setActiveTab] = useState<'overview' | 'vendors' | 'tco' | 'revalidation' | 'audit' | 'po'>('overview');
  const [isLoading, setIsLoading] = useState(true);
  const [isPolling, setIsPolling] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const pollCountRef = useRef(0);
  const MAX_POLLS = 20;

  const fetchAllData = async (isBackgroundPoll = false) => {
    if (!id) return;
    if (!isBackgroundPoll) setIsLoading(true);
    setError(null);

    try {
      const summary = await getProcurement(id);
      setProcurement(summary);

      try {
        const rec = await getRecommendation(id);
        setRecommendation(rec);
      } catch (e) {}

      try {
        const tcos = await getTcoBreakdowns(id);
        if (Array.isArray(tcos)) setTcoBreakdowns(tcos);
      } catch (e) {}

      try {
        const rnk = await getRanking(id);
        setRanking(rnk);
      } catch (e) {}

      if (summary.status === 'WAITING_APPROVAL') {
        try {
          const app = await getApproval(id);
          setApproval(app);
        } catch (e) {}
      }

      try {
        const audit = await getAuditTrail(id);
        setAuditResponse(audit);
      } catch (e) {}

      if (summary.status === 'COMPLETED') {
        try {
          const po = await getPurchaseOrder(id);
          setPurchaseOrder(po);
        } catch (e) {}
      }

      const isTerminal = ['COMPLETED', 'REJECTED', 'FAILED', 'WAITING_APPROVAL', 'WAITING_USER'].includes(summary.status);
      if (!isTerminal && pollCountRef.current < MAX_POLLS) {
        setIsPolling(true);
        pollCountRef.current += 1;
        setTimeout(() => {
          fetchAllData(true);
        }, 2500);
      } else {
        setIsPolling(false);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve procurement details.');
      setIsPolling(false);
    } finally {
      if (!isBackgroundPoll) setIsLoading(false);
    }
  };

  useEffect(() => {
    pollCountRef.current = 0;
    fetchAllData();
  }, [id]);

  const copyId = () => {
    if (id) {
      navigator.clipboard.writeText(id);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  if (isLoading && !procurement) {
    return (
      <div className="p-12 text-center text-xs text-slate-400 space-y-3">
        <div className="w-8 h-8 border-3 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
        <p>Loading procurement lifecycle and authoritative records...</p>
      </div>
    );
  }

  if (!procurement && error) {
    return (
      <div className="glass-panel p-8 rounded-xl border border-rose-500/40 text-center max-w-lg mx-auto space-y-4">
        <AlertTriangle className="w-10 h-10 text-rose-400 mx-auto" />
        <h3 className="text-base font-bold text-slate-100">Unable to Load Procurement</h3>
        <p className="text-xs text-slate-400">{error}</p>
        <Link
          to="/procurements"
          className="inline-flex items-center space-x-2 px-4 py-2 rounded-lg bg-slate-900 text-slate-200 border border-slate-800 text-xs font-semibold"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to Procurements</span>
        </Link>
      </div>
    );
  }

  const allOffers = ranking?.rankedEligibleOffers || recommendation?.compliantAlternatives || [];
  if (recommendation?.bestEligibleOption && !allOffers.some((o: any) => o.offerId === recommendation.bestEligibleOption?.offerId)) {
    allOffers.unshift(recommendation.bestEligibleOption);
  }
  if (recommendation?.topExceptionOption && !allOffers.some((o: any) => o.offerId === recommendation.topExceptionOption?.offerId)) {
    allOffers.push(recommendation.topExceptionOption);
  }

  return (
    <div className="space-y-6">
      {/* Top Header Navigation */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center space-x-2">
            <Link
              to="/procurements"
              className="text-xs text-slate-400 hover:text-slate-200 flex items-center space-x-1"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Procurements</span>
            </Link>
            <span className="text-slate-600">/</span>
            <span className="text-xs font-mono text-slate-400 font-semibold">{id}</span>
            <button
              onClick={copyId}
              className="p-1 text-slate-500 hover:text-slate-300 transition"
              title="Copy ID"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>
          <h1 className="text-2xl font-bold text-slate-100 flex items-center space-x-3">
            <span>{procurement?.category} Procurement</span>
            <span
              className={`text-xs px-2.5 py-0.5 rounded-full font-bold border ${getStateBadgeClasses(
                procurement?.status
              )}`}
            >
              {procurement?.status}
            </span>
          </h1>
        </div>

        {/* Action Controls */}
        <div className="flex items-center space-x-3">
          {isPolling && (
            <div className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-sky-500/10 text-sky-400 border border-sky-500/20 text-xs animate-pulse">
              <RefreshCw className="w-3.5 h-3.5 animate-spin" />
              <span>Syncing State...</span>
            </div>
          )}
          <button
            onClick={() => fetchAllData()}
            disabled={isLoading}
            className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800 transition"
            title="Refresh"
          >
            <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
          {purchaseOrder && (
            <Link
              to={`/purchase-orders/${id}`}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold transition flex items-center space-x-1.5 shadow-lg shadow-emerald-600/20"
            >
              <ShoppingBag className="w-3.5 h-3.5" />
              <span>View Purchase Order</span>
            </Link>
          )}
        </div>
      </div>

      {/* Metadata Badges Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="glass-card p-3 rounded-xl border border-slate-800 text-xs">
          <span className="text-slate-400">Order Quantity:</span>
          <p className="font-bold text-slate-100 text-sm mt-0.5">{procurement?.quantity} Units</p>
        </div>
        <div className="glass-card p-3 rounded-xl border border-slate-800 text-xs">
          <span className="text-slate-400">Authorization Limit:</span>
          <p className="font-bold text-emerald-400 text-sm mt-0.5">
            {formatCurrency(procurement?.authorizationLimit)}
          </p>
        </div>
        <div className="glass-card p-3 rounded-xl border border-slate-800 text-xs">
          <span className="text-slate-400">Selected Product:</span>
          <p className="font-semibold text-slate-200 text-sm mt-0.5 truncate">
            {procurement?.selectedProductName || 'Discovery in progress'}
          </p>
        </div>
        <div className="glass-card p-3 rounded-xl border border-slate-800 text-xs">
          <span className="text-slate-400">Created Timestamp:</span>
          <p className="font-medium text-slate-300 text-xs mt-0.5">{formatDate(procurement?.createdAt)}</p>
        </div>
      </div>

      {/* Lifecycle Stepper */}
      {procurement && <LifecycleStepper currentState={procurement.status} />}

      {/* Approval Action Box if in WAITING_APPROVAL */}
      {procurement?.status === 'WAITING_APPROVAL' && (
        <ApprovalActionBox
          procurementId={id!}
          approval={approval}
          onDecisionComplete={() => fetchAllData()}
        />
      )}

      {/* Tabs Navigation */}
      <div className="flex border-b border-slate-800 space-x-2 overflow-x-auto">
        {[
          { id: 'overview', label: 'Recommendation & Overview', icon: Sparkles },
          { id: 'vendors', label: 'Vendor Matrix', count: allOffers.length, icon: Layers },
          { id: 'tco', label: 'TCO Breakdown', icon: Calculator },
          { id: 'revalidation', label: 'Pre-Purchase Checks', icon: ShieldCheck },
          { id: 'audit', label: 'Audit Trail', count: auditResponse?.events.length, icon: History },
          ...(purchaseOrder ? [{ id: 'po', label: 'Purchase Order', icon: ShoppingBag }] : []),
        ].map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center space-x-2 px-4 py-3 text-xs font-semibold border-b-2 transition whitespace-nowrap ${
                activeTab === tab.id
                  ? 'border-brand-500 text-brand-400 bg-brand-500/5'
                  : 'border-transparent text-slate-400 hover:text-slate-200 hover:border-slate-700'
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              <span>{tab.label}</span>
              {tab.count !== undefined && (
                <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-slate-800 text-slate-300">
                  {tab.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Tab Content Panels */}
      <div className="space-y-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {recommendation ? (
              <RecommendationCard recommendation={recommendation} />
            ) : (
              <div className="glass-panel p-8 rounded-xl border border-slate-800 text-center text-xs text-slate-400">
                Recommendation engine evaluating offers...
              </div>
            )}
            {tcoBreakdowns.length > 0 && (
              <TcoChart
                tcoBreakdowns={tcoBreakdowns}
                selectedOfferId={
                  procurement?.selectedOfferId ||
                  recommendation?.bestEligibleOption?.offerId ||
                  (recommendation as any)?.selectedOfferId
                }
              />
            )}
          </div>
        )}

        {activeTab === 'vendors' && (
          <VendorComparisonTable
            offers={allOffers}
            selectedOfferId={procurement?.selectedOfferId}
          />
        )}

        {activeTab === 'tco' && (
          <TcoChart
            tcoBreakdowns={tcoBreakdowns}
            selectedOfferId={
              procurement?.selectedOfferId ||
              recommendation?.bestEligibleOption?.offerId ||
              (recommendation as any)?.selectedOfferId
            }
          />
        )}

        {activeTab === 'revalidation' && <RevalidationCard revalidation={revalidation} />}

        {activeTab === 'audit' && <AuditTimeline auditResponse={auditResponse} />}

        {activeTab === 'po' && purchaseOrder && (
          <div className="glass-panel p-6 rounded-xl border border-emerald-500/30 bg-emerald-950/10 space-y-4">
            <div className="flex justify-between items-center">
              <div className="flex items-center space-x-3">
                <ShoppingBag className="w-6 h-6 text-emerald-400" />
                <div>
                  <h3 className="text-base font-bold text-slate-100">
                    Confirmed Purchase Order #{purchaseOrder.id ? purchaseOrder.id.substring(0, 8) : 'N/A'}
                  </h3>
                  <p className="text-xs text-emerald-300">
                    Generated and authorized strictly by Spring Boot Engine
                  </p>
                </div>
              </div>
              <Link
                to={`/purchase-orders/${id}`}
                className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold transition"
              >
                Open Full Document
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
