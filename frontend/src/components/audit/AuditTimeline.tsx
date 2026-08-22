import React, { useState } from 'react';
import { ProcurementAuditResponse } from '../../types';
import { formatDate } from '../../utils/format';
import {
  History,
  ArrowRight,
  ChevronDown,
  ChevronRight,
  User,
  Clock,
} from 'lucide-react';

interface AuditTimelineProps {
  auditResponse?: ProcurementAuditResponse | null;
}

export const AuditTimeline: React.FC<AuditTimelineProps> = ({ auditResponse }) => {
  const [expandedEvents, setExpandedEvents] = useState<Record<string, boolean>>({});

  if (!auditResponse || !auditResponse.events || auditResponse.events.length === 0) {
    return (
      <div className="glass-panel p-6 rounded-xl border border-slate-800 text-center text-xs text-slate-400">
        No audit trail records logged yet for this procurement.
      </div>
    );
  }

  const toggleExpand = (id: string) => {
    setExpandedEvents((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div className="glass-panel p-6 rounded-xl border border-slate-800 space-y-4">
      <div className="flex justify-between items-center border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <History className="w-4 h-4 text-brand-400" />
            <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
              Authoritative Audit Trail
            </h3>
          </div>
          <p className="text-xs text-slate-400 mt-0.5">
            Immutable, chronological record of all state transitions and system actions
          </p>
        </div>
        <span className="text-xs font-semibold px-2.5 py-1 rounded bg-slate-800 text-slate-300">
          {auditResponse.events.length} Logged Events
        </span>
      </div>

      <div className="relative pl-6 space-y-6 before:absolute before:left-2.5 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-800">
        {auditResponse.events.map((event, idx) => {
          const isExpanded = !!expandedEvents[event.id || idx.toString()];
          const hasDetails = event.details && Object.keys(event.details).length > 0;

          return (
            <div key={event.id || idx} className="relative group">
              {/* Dot on line */}
              <div className="absolute -left-[19px] top-1 w-3 h-3 rounded-full bg-slate-900 border-2 border-brand-500 group-hover:scale-110 transition"></div>

              <div className="p-3.5 rounded-lg bg-slate-900/60 border border-slate-800/80 hover:border-slate-700 transition space-y-2">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-bold text-slate-200">
                      {event.eventType}
                    </span>
                    {event.fromState && event.toState && (
                      <span className="inline-flex items-center space-x-1 text-[10px] font-mono px-2 py-0.5 rounded bg-slate-950 text-slate-400 border border-slate-800">
                        <span>{event.fromState}</span>
                        <ArrowRight className="w-2.5 h-2.5 text-brand-400" />
                        <span className="text-brand-300 font-semibold">{event.toState}</span>
                      </span>
                    )}
                  </div>
                  <div className="flex items-center space-x-2 text-[11px] text-slate-400">
                    <Clock className="w-3 h-3" />
                    <span>{formatDate(event.timestamp)}</span>
                  </div>
                </div>

                <div className="flex items-center justify-between text-xs text-slate-400">
                  <div className="flex items-center space-x-1.5">
                    <User className="w-3 h-3 text-slate-500" />
                    <span>Actor: <strong className="text-slate-300">{event.actor || 'SYSTEM'}</strong></span>
                  </div>

                  {hasDetails && (
                    <button
                      onClick={() => toggleExpand(event.id || idx.toString())}
                      className="text-[11px] text-brand-400 hover:text-brand-300 font-medium flex items-center space-x-1 transition"
                    >
                      <span>{isExpanded ? 'Hide Details' : 'View Details'}</span>
                      {isExpanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                    </button>
                  )}
                </div>

                {/* Expanded Details Map */}
                {isExpanded && hasDetails && (
                  <div className="mt-2 p-3 rounded bg-slate-950 border border-slate-800 text-[11px] font-mono space-y-1">
                    {Object.entries(event.details).map(([k, v]) => (
                      <div key={k} className="flex justify-between">
                        <span className="text-slate-400">{k}:</span>
                        <span className="text-slate-200 font-semibold text-right truncate max-w-xs">{v}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
