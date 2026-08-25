import React from 'react';
import { RankedOffer } from '../../types';
import { formatCurrency } from '../../utils/format';
import { Check, X, Star } from 'lucide-react';

interface VendorComparisonTableProps {
  offers: RankedOffer[];
  selectedOfferId?: string | null;
}

export const VendorComparisonTable: React.FC<VendorComparisonTableProps> = ({
  offers,
  selectedOfferId,
}) => {
  if (!offers || offers.length === 0) {
    return (
      <div className="glass-panel p-6 rounded-xl border border-slate-800 text-center text-xs text-slate-400">
        No vendor offer evaluations available for this procurement.
      </div>
    );
  }

  return (
    <div className="glass-panel rounded-xl border border-slate-800 overflow-hidden">
      <div className="p-4 border-b border-slate-800 flex justify-between items-center">
        <div>
          <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
            Multi-Dimensional Vendor Matrix
          </h3>
          <p className="text-xs text-slate-400">Authoritative evaluation computed across all discovered vendor catalogs</p>
        </div>
        <span className="text-xs px-2.5 py-1 rounded bg-slate-800 text-slate-300 font-medium">
          {offers.length} Candidate Offers
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs text-slate-300">
          <thead className="bg-slate-900/80 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
            <tr>
              <th className="px-4 py-3">Rank</th>
              <th className="px-4 py-3">Vendor</th>
              <th className="px-4 py-3">Product Candidate</th>
              <th className="px-4 py-3 text-right">Unit Price</th>
              <th className="px-4 py-3 text-right">3-Yr Unit TCO</th>
              <th className="px-4 py-3 text-center">Score</th>
              <th className="px-4 py-3 text-center">Warranty</th>
              <th className="px-4 py-3 text-center">Delivery</th>
              <th className="px-4 py-3 text-center">Eligibility</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60">
            {offers.map((offer, idx) => {
              const isSelected = offer.offerId === selectedOfferId || idx === 0;
              return (
                <tr
                  key={offer.offerId || idx}
                  className={"hover:bg-slate-900/60 transition " + (isSelected ? "bg-brand-500/5" : "")}
                >
                  <td className="px-4 py-3.5 font-bold">
                    {idx === 0 ? (
                      <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30 text-xs font-bold">
                        1
                      </span>
                    ) : (
                      <span className="text-slate-400 pl-2">#{idx + 1}</span>
                    )}
                  </td>
                  <td className="px-4 py-3.5 font-semibold text-slate-100">
                    <div>{offer.vendorName}</div>
                    <div className="flex items-center space-x-1 text-[10px] text-slate-400 mt-0.5">
                      <Star className="w-3 h-3 fill-brand-400 text-brand-400" />
                      <span>{offer.sellerRating ? offer.sellerRating.toFixed(1) : 'N/A'}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 font-medium text-slate-200">
                    <div className="flex items-center space-x-2">
                      <span>{offer.productName}</span>
                      {isSelected && (
                        <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-brand-500/20 text-brand-400 border border-brand-500/30">
                          SELECTED
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-right font-semibold text-slate-100">
                    {formatCurrency(offer.unitPrice || offer.price)}
                  </td>
                  <td className="px-4 py-3.5 text-right font-semibold text-emerald-400">
                    {formatCurrency(offer.unitTco || offer.tco)}
                  </td>
                  <td className="px-4 py-3.5 text-center font-bold text-brand-400">
                    {offer.totalScore ? offer.totalScore.toFixed(1) : 'N/A'}
                  </td>
                  <td className="px-4 py-3.5 text-center text-slate-300">
                    {offer.warrantyYears ? offer.warrantyYears + " yrs" : "1 yr"}
                  </td>
                  <td className="px-4 py-3.5 text-center text-slate-300">
                    {offer.deliveryDays ? offer.deliveryDays + " days" : "3 days"}
                  </td>
                  <td className="px-4 py-3.5 text-center">
                    {offer.isEligible ? (
                      <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                        <Check className="w-3 h-3" />
                        <span>Compliant</span>
                      </span>
                    ) : (
                      <span className="inline-flex items-center space-x-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/30">
                        <X className="w-3 h-3" />
                        <span>Exception</span>
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
