import React from 'react';
import { RecommendationResponse } from '../../types';
import { formatCurrency } from '../../utils/format';
import {
  Trophy,
  CheckCircle,
  AlertTriangle,
  Shield,
  Star,
  Clock,
  Sparkles,
  Info,
} from 'lucide-react';

interface RecommendationCardProps {
  recommendation: RecommendationResponse;
}

export const RecommendationCard: React.FC<RecommendationCardProps> = ({ recommendation }) => {
  const best = recommendation.bestEligibleOption;
  const exception = recommendation.topExceptionOption || recommendation.proposedExceptionOffer;
  const falseEconomies = recommendation.falseEconomies || [];

  if (!best && !exception) {
    return (
      <div className="glass-panel p-6 rounded-xl border border-slate-800 text-center">
        <Info className="w-8 h-8 text-slate-500 mx-auto mb-2" />
        <h4 className="text-sm font-semibold text-slate-300">No Authoritative Recommendation</h4>
        <p className="text-xs text-slate-400 mt-1">{recommendation.explanation || 'No compliant candidates met hard constraints.'}</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Primary Recommended Offer Card */}
      {best && (
        <div className="glass-panel p-6 rounded-xl border-2 border-brand-500/40 relative overflow-hidden bg-gradient-to-br from-brand-950/40 via-slate-900/60 to-slate-950/80">
          <div className="absolute top-0 right-0 px-4 py-1.5 bg-brand-500 text-white font-bold text-xs uppercase tracking-wider rounded-bl-xl shadow-lg flex items-center space-x-1.5">
            <Trophy className="w-3.5 h-3.5" />
            <span>Authoritative Top Recommendation</span>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 pt-2">
            {/* Main Product Info */}
            <div className="lg:col-span-2 space-y-3">
              <div>
                <span className="text-xs font-semibold text-brand-400 tracking-wider uppercase">
                  {best.vendorName}
                </span>
                <h3 className="text-xl font-bold text-slate-100 mt-0.5">{best.productName}</h3>
              </div>

              {/* Badges / Metrics */}
              <div className="flex flex-wrap gap-2 pt-1">
                <span className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-md text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  <CheckCircle className="w-3.5 h-3.5" />
                  <span>Eligible Compliant Offer</span>
                </span>
                <span className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-md text-xs font-medium bg-brand-500/10 text-brand-400 border border-brand-500/20">
                  <Star className="w-3.5 h-3.5 fill-brand-400" />
                  <span>Rating: {best.sellerRating ? best.sellerRating.toFixed(1) : 'N/A'}</span>
                </span>
                <span className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-md text-xs font-medium bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  <Shield className="w-3.5 h-3.5" />
                  <span>{best.warrantyYears || 1} Year Warranty</span>
                </span>
                <span className="inline-flex items-center space-x-1 px-2.5 py-1 rounded-md text-xs font-medium bg-purple-500/10 text-purple-400 border border-purple-500/20">
                  <Clock className="w-3.5 h-3.5" />
                  <span>Delivery: {best.deliveryDays} Days</span>
                </span>
              </div>

              {/* Justification Text */}
              <div className="p-3.5 rounded-lg bg-slate-900/80 border border-slate-800 text-xs text-slate-300 leading-relaxed">
                <div className="flex items-center space-x-1.5 font-semibold text-brand-300 mb-1">
                  <Sparkles className="w-3.5 h-3.5 text-brand-400" />
                  <span>Why This Was Selected (Spring Decision Engine)</span>
                </div>
                <p>{recommendation.explanation}</p>
              </div>
            </div>

            {/* Financial Summary */}
            <div className="p-4 rounded-xl bg-slate-950/80 border border-slate-800 flex flex-col justify-between space-y-4">
              <div>
                <p className="text-xs font-medium text-slate-400 uppercase tracking-wider">Financial Economics</p>
                <div className="mt-2 space-y-2">
                  <div>
                    <span className="text-xs text-slate-400">Upfront Price:</span>
                    <p className="text-xl font-bold text-slate-100">{formatCurrency(best.price)}</p>
                  </div>
                  <div>
                    <span className="text-xs text-slate-400">3-Year Projected TCO:</span>
                    <p className="text-lg font-bold text-emerald-400">{formatCurrency(best.tco)}</p>
                  </div>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-800 flex justify-between items-center text-xs">
                <span className="text-slate-400">Multi-Dim Score:</span>
                <span className="text-sm font-bold text-brand-400">{best.totalScore ? best.totalScore.toFixed(2) : 'N/A'} / 100</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Superior Exception Option Warning */}
      {exception && recommendation.recommendationType === 'BUDGET_OVERRIDE_RECOMMENDED' && (
        <div className="glass-panel p-5 rounded-xl border border-amber-500/40 bg-amber-950/20 space-y-2">
          <div className="flex items-center space-x-2 text-amber-300 text-sm font-bold">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <span>High-Value Exception Option Identified</span>
          </div>
          <p className="text-xs text-slate-300 leading-relaxed">
            Exception candidate <span className="font-semibold text-amber-200">{exception.productName}</span> from {exception.vendorName} ({formatCurrency(exception.price)}) offers superior long-term TCO ({formatCurrency(exception.tco)}) with extended {exception.warrantyYears}-year warranty.
          </p>
        </div>
      )}

      {/* False Economy Reports */}
      {falseEconomies.length > 0 && (
        <div className="glass-panel p-5 rounded-xl border border-rose-500/30 bg-rose-950/10 space-y-3">
          <div className="flex items-center space-x-2 text-rose-400 text-xs font-bold uppercase tracking-wider">
            <AlertTriangle className="w-4 h-4" />
            <span>False Economy Diagnostic Alerts</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {falseEconomies.map((fe, idx) => (
              <div key={idx} className="p-3 rounded-lg bg-slate-900/90 border border-rose-500/20 text-xs space-y-1">
                <div className="flex justify-between items-center font-bold text-slate-200">
                  <span>{fe.productName}</span>
                  <span className="text-rose-400">+{formatCurrency(fe.additionalCostVsTopRanked)} TCO Risk</span>
                </div>
                <p className="text-slate-400 text-[11px]">{fe.riskSummary}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
