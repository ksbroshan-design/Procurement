import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { processAiBrief } from '../api/procurement';
import { ProcessBriefResult } from '../types';
import { formatCurrency } from '../utils/format';
import {
  Sparkles,
  Send,
  HelpCircle,
  AlertCircle,
  CheckCircle2,
  ArrowRight,
  ShieldCheck,
} from 'lucide-react';

const SAMPLE_BRIEFS = [
  {
    title: 'Happy Path Laptops',
    brief: 'Buy 2 laptops under Rs 200000 with at least 16GB RAM and delivery within 7 days. Approval limit Rs 500000.',
    category: 'Laptop',
  },
  {
    title: 'Conference Room TV',
    brief: 'Buy 1 TV under Rs 60000 with at least 55 inches screen and delivery within 5 days. Approval limit Rs 500000.',
    category: 'TV',
  },
  {
    title: 'Incomplete Request (Clarification)',
    brief: 'Buy some laptops with 16GB RAM for the design team.',
    category: 'Clarification Demo',
  },
  {
    title: 'Off-Topic Non-Procurement',
    brief: 'Write Python code to reverse a binary tree.',
    category: 'Guardrail Demo',
  },
];

export const NewProcurement: React.FC = () => {
  const [brief, setBrief] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [processingStage, setProcessingStage] = useState<string>('');
  const [result, setResult] = useState<ProcessBriefResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [clarifiedQuantity, setClarifiedQuantity] = useState<string>('');
  const [clarifiedBudget, setClarifiedBudget] = useState<string>('');
  const [clarifiedDeliveryDays, setClarifiedDeliveryDays] = useState<string>('');

  const navigate = useNavigate();

  const handleProcess = async (textToProcess?: string) => {
    const targetBrief = textToProcess || brief;
    if (!targetBrief.trim()) return;

    setIsProcessing(true);
    setError(null);
    setResult(null);
    setProcessingStage('Consulting Domain Guardrail & Intent Parser (Python)...');

    try {
      const res = await processAiBrief(targetBrief, true);
      setResult(res);

      if (res.status === 'ok' || res.status === 'waiting_approval') {
        setProcessingStage('Complete! Synchronized with Spring Boot Engine.');
      }
    } catch (err: any) {
      setError(err.message || 'An error occurred while processing procurement brief.');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleClarificationSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const additions: string[] = [];
    if (clarifiedQuantity) additions.push(`Quantity: ${clarifiedQuantity}`);
    if (clarifiedBudget) additions.push(`Budget: Rs ${clarifiedBudget}`);
    if (clarifiedDeliveryDays) additions.push(`Delivery within ${clarifiedDeliveryDays} days`);

    const augmentedBrief = `${brief}. Specified dimensions: ${additions.join(', ')}.`;
    setBrief(augmentedBrief);
    handleProcess(augmentedBrief);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-8">
      {/* Header */}
      <div className="text-center space-y-2">
        <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full text-xs font-semibold bg-brand-500/10 text-brand-400 border border-brand-500/20">
          <Sparkles className="w-3.5 h-3.5" />
          <span>Natural Language AI Procurement Layer</span>
        </div>
        <h1 className="text-3xl font-extrabold text-slate-100 tracking-tight">
          What would you like me to procure?
        </h1>
        <p className="text-sm text-slate-400 max-w-xl mx-auto">
          Describe items, technical requirements, budget constraints, or delivery timelines. The AI intelligence service will ground your request and execute it via the authoritative core.
        </p>
      </div>

      {/* Input Card */}
      <div className="glass-panel p-6 sm:p-8 rounded-2xl border border-slate-800 shadow-2xl space-y-4 relative">
        <div className="space-y-2">
          <textarea
            value={brief}
            onChange={(e) => setBrief(e.target.value)}
            placeholder="e.g. Buy 2 laptops under Rs 2,00,000 with at least 16GB RAM and delivery within 7 days. Approval limit Rs 500,000."
            className="w-full h-32 px-4 py-3 bg-slate-900/90 border border-slate-800 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500 transition resize-none leading-relaxed"
            disabled={isProcessing}
          />
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pt-2">
          <div className="text-xs text-slate-400 flex items-center space-x-2">
            <ShieldCheck className="w-4 h-4 text-brand-400" />
            <span>Strict server-side validation & audit governance</span>
          </div>

          <button
            onClick={() => handleProcess()}
            disabled={isProcessing || !brief.trim()}
            className="px-6 py-2.5 bg-brand-600 hover:bg-brand-500 text-white font-bold rounded-xl text-xs transition duration-150 shadow-lg shadow-brand-500/25 flex items-center justify-center space-x-2 disabled:opacity-40"
          >
            {isProcessing ? (
              <>
                <div className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin"></div>
                <span>Executing Pipeline...</span>
              </>
            ) : (
              <>
                <span>Submit to Engine</span>
                <Send className="w-3.5 h-3.5" />
              </>
            )}
          </button>
        </div>

        {/* Quick Sample Prompts */}
        <div className="pt-4 border-t border-slate-800/80">
          <p className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2.5">
            Quick Test Prompts
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {SAMPLE_BRIEFS.map((item, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => {
                  setBrief(item.brief);
                }}
                className="p-3 rounded-lg bg-slate-900/60 hover:bg-slate-800/80 border border-slate-800 text-left transition space-y-1 group"
              >
                <div className="flex items-center justify-between text-xs font-semibold text-slate-200 group-hover:text-brand-400">
                  <span>{item.title}</span>
                  <span className="text-[10px] text-slate-400">{item.category}</span>
                </div>
                <p className="text-[11px] text-slate-400 line-clamp-1">{item.brief}</p>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Live Processing Indicator */}
      {isProcessing && (
        <div className="glass-panel p-6 rounded-xl border border-brand-500/30 text-center space-y-3 animate-in fade-in duration-200">
          <div className="w-8 h-8 border-3 border-brand-500/20 border-t-brand-500 rounded-full animate-spin mx-auto"></div>
          <div>
            <h4 className="text-sm font-bold text-slate-100">Processing Procurement Pipeline</h4>
            <p className="text-xs text-brand-300 font-mono mt-1">{processingStage}</p>
          </div>
        </div>
      )}

      {/* Error Display */}
      {error && (
        <div className="glass-panel p-5 rounded-xl border border-rose-500/40 bg-rose-950/20 flex items-start space-x-3 text-rose-300 text-xs">
          <AlertCircle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
          <div className="space-y-1">
            <p className="font-bold text-slate-100">Processing Error</p>
            <p className="text-slate-300">{error}</p>
          </div>
        </div>
      )}

      {/* Result Display: Clarification Required */}
      {result && result.status === 'needs_clarification' && (
        <div className="glass-panel p-6 rounded-2xl border-2 border-amber-500/40 bg-gradient-to-br from-amber-950/20 via-slate-900/80 to-slate-950/90 space-y-5">
          <div className="flex items-center space-x-3 text-amber-400">
            <div className="w-10 h-10 rounded-lg bg-amber-500/20 flex items-center justify-center font-bold">
              <HelpCircle className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-100">Clarification Needed</h3>
              <p className="text-xs text-amber-300/80">
                The Python AI Intent Parser detected missing mandatory procurement dimensions
              </p>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-950/70 border border-slate-800 text-xs text-slate-300 space-y-2">
            <p className="font-semibold text-slate-200">
              {result.clarification_needed || 'Please provide the missing requirements to continue:'}
            </p>
            {result.missing_fields && result.missing_fields.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {result.missing_fields.map((f, i) => (
                  <span
                    key={i}
                    className="px-2 py-0.5 rounded bg-amber-500/10 text-amber-300 border border-amber-500/30 text-[10px] font-mono font-bold uppercase"
                  >
                    Missing: {f}
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Interactive Clarification Form */}
          <form onSubmit={handleClarificationSubmit} className="space-y-4 pt-2">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Required Quantity
                </label>
                <input
                  type="number"
                  min="1"
                  value={clarifiedQuantity}
                  onChange={(e) => setClarifiedQuantity(e.target.value)}
                  placeholder="e.g. 5"
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Budget (INR)
                </label>
                <input
                  type="number"
                  value={clarifiedBudget}
                  onChange={(e) => setClarifiedBudget(e.target.value)}
                  placeholder="e.g. 200000"
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Max Delivery Days
                </label>
                <input
                  type="number"
                  min="1"
                  value={clarifiedDeliveryDays}
                  onChange={(e) => setClarifiedDeliveryDays(e.target.value)}
                  placeholder="e.g. 7"
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-100 focus:border-brand-500 focus:outline-none"
                />
              </div>
            </div>

            <div className="flex justify-end">
              <button
                type="submit"
                className="px-5 py-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-lg text-xs transition flex items-center space-x-1.5"
              >
                <span>Continue With Clarified Brief</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Result Display: Out of Domain Rejection */}
      {result && result.status === 'rejected' && (
        <div className="glass-panel p-6 rounded-2xl border-2 border-rose-500/40 bg-gradient-to-br from-rose-950/20 via-slate-900/80 to-slate-950/90 space-y-4">
          <div className="flex items-center space-x-3 text-rose-400">
            <div className="w-10 h-10 rounded-lg bg-rose-500/20 flex items-center justify-center font-bold">
              <AlertCircle className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-100">Domain Guardrail Rejection</h3>
              <p className="text-xs text-rose-300/80">
                Non-procurement request safely blocked before reaching the authoritative backend
              </p>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-950/70 border border-slate-800 text-xs text-slate-300 space-y-1">
            <p className="font-semibold text-slate-200">Rejection Rationale:</p>
            <p className="text-slate-400 leading-relaxed">
              {result.rejection_reason || 'This brief was classified as out-of-domain for enterprise IT/office procurement.'}
            </p>
          </div>
        </div>
      )}

      {/* Result Display: Successful Execution */}
      {result && (result.status === 'ok' || result.status === 'waiting_approval') && result.procurement_id && (
        <div className="glass-panel p-6 sm:p-8 rounded-2xl border-2 border-emerald-500/40 bg-gradient-to-br from-emerald-950/20 via-slate-900/80 to-slate-950/90 space-y-6">
          <div className="flex items-center justify-between flex-wrap gap-4">
            <div className="flex items-center space-x-3">
              <div className="w-11 h-11 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-lg font-extrabold text-slate-100">
                  Procurement Request Processed Successfully
                </h3>
                <p className="text-xs text-emerald-300/90 flex items-center space-x-2">
                  <span>Authoritative Backend Status:</span>
                  <span className="font-mono font-bold">{result.backend_status}</span>
                </p>
              </div>
            </div>

            <button
              onClick={() => navigate(`/procurements/${result.procurement_id}`)}
              className="px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-bold transition shadow-lg shadow-emerald-600/20 flex items-center space-x-2"
            >
              <span>Inspect Full Lifecycle</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>

          {/* Quick Summary Highlights */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
            <div className="p-4 rounded-xl bg-slate-950/70 border border-slate-800 space-y-1">
              <span className="text-slate-400 uppercase text-[10px]">Procurement ID</span>
              <p className="font-mono font-bold text-slate-200 truncate">{result.procurement_id}</p>
            </div>
            <div className="p-4 rounded-xl bg-slate-950/70 border border-slate-800 space-y-1">
              <span className="text-slate-400 uppercase text-[10px]">Selected Candidate</span>
              <p className="font-bold text-slate-200">
                {result.recommendation?.bestEligibleOption?.productName || 'Offer Evaluated'}
              </p>
            </div>
            <div className="p-4 rounded-xl bg-slate-950/70 border border-slate-800 space-y-1">
              <span className="text-slate-400 uppercase text-[10px]">Projected 3-Yr TCO</span>
              <p className="font-bold text-emerald-400">
                {formatCurrency(result.recommendation?.bestEligibleOption?.tco)}
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
