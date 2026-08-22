import os

SRC_DIR = r"C:\Users\ksbro\Downloads\Rockathon\frontend\src"

def write_file(rel_path, content):
    full_path = os.path.join(SRC_DIR, rel_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"Wrote {rel_path}")

API_CLIENT_TS = """export class ApiError extends Error {
  statusCode: number;
  details?: any;

  constructor(message: string, statusCode: number, details?: any) {
    super(message);
    this.name = 'ApiError';
    this.statusCode = statusCode;
    this.details = details;
  }
}

export class AuthenticationError extends ApiError {
  constructor(message = 'Session expired or unauthenticated. Please log in.', details?: any) {
    super(message, 401, details);
    this.name = 'AuthenticationError';
  }
}

export class AuthorizationError extends ApiError {
  constructor(message = 'You do not have permission to perform this action.', details?: any) {
    super(message, 403, details);
    this.name = 'AuthorizationError';
  }
}

export class StateConflictError extends ApiError {
  constructor(message = 'State conflict occurred on backend.', details?: any) {
    super(message, 409, details);
    this.name = 'StateConflictError';
  }
}

export const SPRING_API_URL = import.meta.env.VITE_SPRING_API_URL || 'http://localhost:8080';
export const PYTHON_API_URL = import.meta.env.VITE_PYTHON_API_URL || 'http://localhost:8000';

export function getStoredToken(): string | null {
  return localStorage.getItem('procurement_jwt');
}

export function setStoredToken(token: string): void {
  localStorage.setItem('procurement_jwt', token);
}

export function removeStoredToken(): void {
  localStorage.removeItem('procurement_jwt');
  localStorage.removeItem('procurement_user');
}

interface RequestOptions extends RequestInit {
  token?: string | null;
}

export async function request<T>(
  baseUrl: string,
  endpoint: string,
  options: RequestOptions = {}
): Promise<T> {
  const url = baseUrl + endpoint;
  const token = options.token !== undefined ? options.token : getStoredToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };

  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    let data: any = null;
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      try {
        data = await response.json();
      } catch (err) {
        data = null;
      }
    } else {
      data = await response.text();
    }

    if (response.ok) {
      if (data && typeof data === 'object' && 'data' in data && 'success' in data) {
        return data.data as T;
      }
      return data as T;
    }

    const errorMessage =
      (data && typeof data === 'object' && (data.message || data.error || data.detail)) ||
      'HTTP ' + response.status + ': Request failed';

    if (response.status === 401) {
      removeStoredToken();
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
      throw new AuthenticationError(errorMessage, data);
    }

    if (response.status === 403) {
      throw new AuthorizationError(errorMessage, data);
    }

    if (response.status === 409) {
      throw new StateConflictError(errorMessage, data);
    }

    throw new ApiError(errorMessage, response.status, data);
  } catch (error: any) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(
      error.message || 'Network connection to backend failed. Please check if services are running.',
      0,
      error
    );
  }
}

export const springClient = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, { ...options, method: 'GET' }),
  post: <T>(endpoint: string, body?: any, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),
  put: <T>(endpoint: string, body?: any, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, {
      ...options,
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(SPRING_API_URL, endpoint, { ...options, method: 'DELETE' }),
};

export const pythonClient = {
  get: <T>(endpoint: string, options?: RequestOptions) =>
    request<T>(PYTHON_API_URL, endpoint, { ...options, method: 'GET' }),
  post: <T>(endpoint: string, body?: any, options?: RequestOptions) =>
    request<T>(PYTHON_API_URL, endpoint, {
      ...options,
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    }),
};
"""

API_AUTH_TS = """import { springClient, setStoredToken, removeStoredToken } from './client';
import { AuthResponse, User } from '../types';

export interface LoginCredentials {
  email: string;
  password: string;
}

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const response = await springClient.post<AuthResponse>('/api/auth/login', credentials);
  if (response && response.token) {
    setStoredToken(response.token);
    localStorage.setItem('procurement_user', JSON.stringify(response.user));
  }
  return response;
}

export function logout(): void {
  removeStoredToken();
}

export function getStoredUser(): User | null {
  const userStr = localStorage.getItem('procurement_user');
  if (!userStr) return null;
  try {
    return JSON.parse(userStr) as User;
  } catch (err) {
    return null;
  }
}
"""

API_PROCUREMENT_TS = """import { springClient, pythonClient } from './client';
import {
  ProcurementSummary,
  ProcessBriefResult,
  RevalidationResult,
  PurchaseOrder,
} from '../types';

export async function listProcurements(): Promise<ProcurementSummary[]> {
  return springClient.get<ProcurementSummary[]>('/api/procurements');
}

export async function getProcurement(id: string): Promise<ProcurementSummary> {
  return springClient.get<ProcurementSummary>('/api/procurements/' + id);
}

export async function executeProcurement(id: string): Promise<any> {
  return springClient.post<any>('/api/procurements/' + id + '/execute');
}

export async function revalidateProcurement(id: string): Promise<RevalidationResult> {
  return springClient.post<RevalidationResult>('/api/procurements/' + id + '/revalidate');
}

export async function getRevalidation(id: string): Promise<RevalidationResult> {
  return springClient.get<RevalidationResult>('/api/procurements/' + id + '/revalidate');
}

export async function purchaseProcurement(id: string): Promise<any> {
  return springClient.post<any>('/api/procurements/' + id + '/purchase');
}

export async function getPurchaseOrder(id: string): Promise<PurchaseOrder> {
  return springClient.get<PurchaseOrder>('/api/procurements/' + id + '/purchase-order');
}

export async function processAiBrief(
  brief: string,
  execute = true
): Promise<ProcessBriefResult> {
  return pythonClient.post<ProcessBriefResult>('/api/ai/process', {
    brief,
    execute,
  });
}
"""

API_APPROVAL_TS = """import { springClient } from './client';
import { ApprovalResponse } from '../types';

export async function getApproval(procurementId: string): Promise<ApprovalResponse> {
  return springClient.get<ApprovalResponse>('/api/procurements/' + procurementId + '/approval');
}

export async function getPendingApprovals(): Promise<ApprovalResponse[]> {
  return springClient.get<ApprovalResponse[]>('/api/procurements/approvals/pending');
}

export async function approveProcurement(
  procurementId: string,
  comments?: string,
  approvedOfferId?: string
): Promise<ApprovalResponse> {
  return springClient.post<ApprovalResponse>(
    '/api/procurements/' + procurementId + '/approval/approve',
    {
      comments: comments || 'Approved by procurement manager',
      approvedOfferId: approvedOfferId || null,
    }
  );
}

export async function rejectProcurement(
  procurementId: string,
  comments?: string
): Promise<ApprovalResponse> {
  return springClient.post<ApprovalResponse>(
    '/api/procurements/' + procurementId + '/approval/reject',
    {
      comments: comments || 'Rejected by procurement manager',
    }
  );
}
"""

API_INTELLIGENCE_TS = """import { springClient } from './client';
import { RecommendationResponse, TcoBreakdown } from '../types';

export async function getRecommendation(procurementId: string): Promise<RecommendationResponse> {
  return springClient.get<RecommendationResponse>('/api/procurements/' + procurementId + '/recommendation');
}

export async function getTcoBreakdowns(procurementId: string): Promise<TcoBreakdown[]> {
  return springClient.get<TcoBreakdown[]>('/api/procurements/' + procurementId + '/tco');
}

export async function getRanking(procurementId: string): Promise<any> {
  return springClient.get<any>('/api/procurements/' + procurementId + '/ranking');
}
"""

API_AUDIT_TS = """import { springClient } from './client';
import { ProcurementAuditResponse } from '../types';

export async function getAuditTrail(procurementId: string): Promise<ProcurementAuditResponse> {
  return springClient.get<ProcurementAuditResponse>('/api/procurements/' + procurementId + '/audit');
}
"""

API_PURCHASE_ORDER_TS = """import { springClient } from './client';
import { PurchaseOrder } from '../types';

export async function listPurchaseOrders(): Promise<PurchaseOrder[]> {
  return springClient.get<PurchaseOrder[]>('/api/procurements/purchase-orders');
}

export async function getPurchaseOrder(procurementId: string): Promise<PurchaseOrder> {
  return springClient.get<PurchaseOrder>('/api/procurements/' + procurementId + '/purchase-order');
}
"""

APPROVALS_TSX = """import React, { useState, useEffect } from 'react';
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
"""

# Write API clients
write_file("api/client.ts", API_CLIENT_TS)
write_file("api/auth.ts", API_AUTH_TS)
write_file("api/procurement.ts", API_PROCUREMENT_TS)
write_file("api/approval.ts", API_APPROVAL_TS)
write_file("api/intelligence.ts", API_INTELLIGENCE_TS)
write_file("api/audit.ts", API_AUDIT_TS)
write_file("api/purchaseOrder.ts", API_PURCHASE_ORDER_TS)
write_file("pages/Approvals.tsx", APPROVALS_TSX)

print("API clients and Approvals.tsx written successfully!")
