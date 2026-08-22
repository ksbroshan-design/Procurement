import os

SRC_DIR = r"C:\Users\ksbro\Downloads\Rockathon\frontend\src"

def write_file(rel_path, content):
    full_path = os.path.join(SRC_DIR, rel_path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content.strip() + "\n")
    print(f"Wrote {rel_path}")

TYPES_INDEX_TS = """export type Role =
  | 'USER'
  | 'ROLE_USER'
  | 'PROCUREMENT_MANAGER'
  | 'ROLE_PROCUREMENT_MANAGER'
  | 'ADMIN'
  | 'ROLE_ADMIN';

export type ProcurementState =
  | 'SUBMITTED'
  | 'VALIDATING'
  | 'SEARCHING'
  | 'EVALUATING'
  | 'TCO_ANALYSIS'
  | 'RECOMMENDED'
  | 'NEGOTIATING'
  | 'AUTHORIZATION_CHECK'
  | 'WAITING_APPROVAL'
  | 'REVALIDATING'
  | 'PURCHASING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'FAILED'
  | 'WAITING_USER';

export interface User {
  id?: string;
  userId?: string;
  email: string;
  name: string;
  role: Role | string;
  authorizationLimit: number;
  tokenType?: string;
  expiresInMs?: number;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  userId: string;
  email: string;
  name: string;
  role: string;
  authorizationLimit: number;
  expiresInMs: number;
  user?: User;
}

export interface ProcurementSummary {
  id: string;
  category: string;
  quantity: number;
  authorizationLimit: number;
  status: ProcurementState;
  selectedOfferId?: string | null;
  selectedProductName?: string | null;
  selectedVendorName?: string | null;
  constraintCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Constraint {
  id?: string;
  attribute: string;
  operator: string;
  value: string;
  unit?: string | null;
  mandatory: boolean;
}

export interface Preference {
  attribute: string;
  direction: string;
  weight: number;
}

export interface RankedOffer {
  offerId: string;
  productId: string;
  productName: string;
  vendorId: string;
  vendorName: string;
  price: number;
  tco: number;
  unitPrice: number;
  unitTco: number;
  totalScore: number;
  isEligible: boolean;
  satisfactionScore: number;
  preferenceScore: number;
  reliabilityScore: number;
  sellerRating: number;
  warrantyYears: number;
  deliveryDays: number;
  returnWindowDays: number;
  returnPolicy: string;
  specifications: Record<string, any>;
  rank: number;
}

export interface FalseEconomyReport {
  offerId: string;
  productName: string;
  vendorName: string;
  upfrontPrice: number;
  totalTco: number;
  additionalCostVsTopRanked: number;
  primaryDrivers: string[];
  riskSummary: string;
}

export interface RecommendationResponse {
  procurementId: string;
  category: string;
  recommendationType: string;
  bestEligibleOption?: RankedOffer | null;
  topExceptionOption?: RankedOffer | null;
  proposedExceptionOffer?: RankedOffer | null;
  lowestUpfrontOption?: RankedOffer | null;
  lowestTcoOption?: RankedOffer | null;
  explanation: string;
  tradeOffs: string[];
  compliantAlternatives: RankedOffer[];
  falseEconomies: FalseEconomyReport[];
}

export interface TcoBreakdown {
  offerId: string;
  productId: string;
  productName: string;
  vendorId: string;
  vendorName: string;
  purchaseCost: number;
  maintenanceCost: number;
  expectedRepairCost: number;
  downtimeRiskCost: number;
  replacementRiskCost: number;
  warrantyBenefit: number;
  totalTco: number;
  unitPurchaseCost: number;
  unitTco: number;
  quantity: number;
  durationYears: number;
  annualFailureRate: number;
  averageRepairCost: number;
  downtimeCostPerHour: number;
  expectedDowntimeHours: number;
  effectiveWarrantyYears: number;
}

export interface ApprovalResponse {
  approvalId: string;
  procurementId: string;
  proposedOfferId?: string | null;
  proposedProductName?: string | null;
  proposedVendorName?: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAmount: number;
  authorizationLimit: number;
  difference: number;
  exceptionType: string;
  reason: string;
  explanation?: string | null;
  comments?: string | null;
  requestedAt: string;
  decidedAt?: string | null;
  decidedByName?: string | null;
}

export interface RevalidationCheck {
  name: string;
  passed: boolean;
  expectedValue: string;
  actualValue: string;
  message: string;
}

export interface RevalidationResult {
  procurementId: string;
  valid: boolean;
  status: string;
  checkedAt: string;
  checks: RevalidationCheck[];
  message: string;
  nextAction: string;
}

export interface PurchaseOrder {
  id: string;
  procurementId: string;
  vendorId: string;
  vendorName: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  status: 'CONFIRMED' | 'PENDING' | 'CANCELLED';
  createdAt: string;
  confirmedAt: string;
}

export interface AuditEvent {
  id: string;
  eventType: string;
  fromState?: string | null;
  toState?: string | null;
  actor: string;
  details: Record<string, string>;
  timestamp: string;
}

export interface ProcurementAuditResponse {
  procurementId: string;
  eventCount: number;
  events: AuditEvent[];
}

export interface ProcessBriefResult {
  status: 'ok' | 'rejected' | 'needs_clarification' | 'failed' | 'waiting_approval';
  is_procurement: boolean;
  rejection_reason?: string | null;
  clarification_needed?: string | null;
  missing_fields: string[];
  parsed_intent?: {
    category?: string;
    quantity?: number;
    constraints?: Constraint[];
    preferences?: Preference[];
    budget?: number;
    max_delivery_days?: number;
    authorization_limit?: number;
  } | null;
  procurement_id?: string | null;
  backend_status?: ProcurementState | null;
  decision_message?: string | null;
  approval_required: boolean;
  approval?: ApprovalResponse | null;
  recommendation?: RecommendationResponse | null;
  tco_breakdowns?: TcoBreakdown[] | null;
  revalidation?: RevalidationResult | null;
  purchase_order?: PurchaseOrder | null;
  audit_trail?: ProcurementAuditResponse | null;
  explanation?: string | null;
}
"""

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
  localStorage.removeItem('procurement_token_type');
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
  const tokenType = localStorage.getItem('procurement_token_type') || 'Bearer';

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };

  if (token) {
    headers['Authorization'] = tokenType + ' ' + token;
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

export function extractUserFromAuthResponse(authData: AuthResponse | any): User {
  const userId = authData.userId || authData.id || '';
  const role = authData.role || 'USER';
  return {
    id: userId,
    userId: userId,
    email: authData.email || '',
    name: authData.name || '',
    role: role,
    authorizationLimit:
      typeof authData.authorizationLimit === 'number'
        ? authData.authorizationLimit
        : Number(authData.authorizationLimit) || 0,
    tokenType: authData.tokenType || 'Bearer',
    expiresInMs: authData.expiresInMs || 86400000,
  };
}

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const response = await springClient.post<AuthResponse>('/api/auth/login', credentials);
  if (response && response.token) {
    setStoredToken(response.token);
    if (response.tokenType) {
      localStorage.setItem('procurement_token_type', response.tokenType);
    }
    const user = response.user ? response.user : extractUserFromAuthResponse(response);
    localStorage.setItem('procurement_user', JSON.stringify(user));
    return {
      ...response,
      user,
    };
  }
  return response;
}

export function logout(): void {
  removeStoredToken();
  localStorage.removeItem('procurement_user');
  localStorage.removeItem('procurement_token_type');
}

export function getStoredUser(): User | null {
  const userStr = localStorage.getItem('procurement_user');
  if (!userStr || userStr === 'undefined' || userStr === 'null') return null;
  try {
    return JSON.parse(userStr) as User;
  } catch (err) {
    return null;
  }
}
"""

AUTH_CONTEXT_TSX = """import React, { createContext, useContext, useState, useEffect } from 'react';
import { User } from '../types';
import {
  login as apiLogin,
  logout as apiLogout,
  getStoredUser,
  extractUserFromAuthResponse,
  LoginCredentials,
} from '../api/auth';
import { getStoredToken } from '../api/client';

export function isManagerRole(role?: string | null): boolean {
  if (!role) return false;
  const norm = role.toUpperCase().replace('ROLE_', '');
  return norm === 'PROCUREMENT_MANAGER' || norm === 'ADMIN';
}

export function isAdminRole(role?: string | null): boolean {
  if (!role) return false;
  const norm = role.toUpperCase().replace('ROLE_', '');
  return norm === 'ADMIN';
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isManager: boolean;
  isAdmin: boolean;
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => getStoredUser());
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    const handleUnauthorized = () => {
      setUser(null);
      setToken(null);
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
    };
  }, []);

  const login = async (credentials: LoginCredentials) => {
    const res = await apiLogin(credentials);
    const userObj = res.user || extractUserFromAuthResponse(res);
    setToken(res.token);
    setUser(userObj);
  };

  const logout = () => {
    apiLogout();
    setUser(null);
    setToken(null);
  };

  const isManager = isManagerRole(user?.role);
  const isAdmin = isAdminRole(user?.role);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        isManager,
        isAdmin,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
"""

PROTECTED_ROUTE_TSX = """import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole }) => {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950">
        <div className="flex flex-col items-center space-y-4">
          <div className="w-10 h-10 border-4 border-brand-500/20 border-t-brand-500 rounded-full animate-spin"></div>
          <p className="text-slate-400 text-sm font-medium">Verifying authorization...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRole && user) {
    const userRoleNorm = (user.role || '').toUpperCase().replace('ROLE_', '');
    const hasRole = requiredRole.some((r) => r.toUpperCase().replace('ROLE_', '') === userRoleNorm);
    if (!hasRole) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-slate-950 p-6">
          <div className="max-w-md w-full glass-panel p-8 rounded-xl border border-rose-500/30 text-center">
            <div className="w-12 h-12 rounded-full bg-rose-500/10 text-rose-400 flex items-center justify-center mx-auto mb-4 border border-rose-500/20">
              <span className="text-2xl font-bold">!</span>
            </div>
            <h2 className="text-xl font-bold text-slate-100 mb-2">Access Restricted</h2>
            <p className="text-slate-400 text-sm mb-6">
              Your role (<span className="text-rose-300 font-semibold">{user.role}</span>) does not have authorization to view this enterprise resource.
            </p>
            <a
              href="/dashboard"
              className="inline-block px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium rounded-lg transition"
            >
              Return to Dashboard
            </a>
          </div>
        </div>
      );
    }
  }

  return <>{children}</>;
};
"""

SIDEBAR_TSX = """import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getPendingApprovals } from '../../api/approval';
import {
  LayoutDashboard,
  Sparkles,
  Layers,
  CheckSquare,
  ShoppingBag,
  Cpu,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { isManager, user } = useAuth();
  const [pendingCount, setPendingCount] = useState<number>(0);

  useEffect(() => {
    if (isManager) {
      getPendingApprovals()
        .then((res) => {
          if (Array.isArray(res)) setPendingCount(res.length);
        })
        .catch(() => {});
    }
  }, [isManager]);

  const navItems = [
    {
      to: '/dashboard',
      label: 'Dashboard',
      icon: LayoutDashboard,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/procure',
      label: 'New AI Procurement',
      icon: Sparkles,
      highlight: true,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/procurements',
      label: 'Procurements',
      icon: Layers,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/approvals',
      label: 'Approvals',
      icon: CheckSquare,
      badge: pendingCount > 0 ? pendingCount : undefined,
      roles: ['PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
    {
      to: '/purchase-orders',
      label: 'Purchase Orders',
      icon: ShoppingBag,
      roles: ['USER', 'ROLE_USER', 'PROCUREMENT_MANAGER', 'ROLE_PROCUREMENT_MANAGER', 'ADMIN', 'ROLE_ADMIN'],
    },
  ];

  const userRoleNorm = (user?.role || '').toUpperCase().replace('ROLE_', '');
  const visibleItems = navItems.filter((item) => {
    if (!item.roles) return true;
    if (!user) return false;
    return item.roles.some((r) => r.toUpperCase().replace('ROLE_', '') === userRoleNorm);
  });

  return (
    <aside className="w-64 border-r border-slate-800 bg-slate-950/60 backdrop-blur-sm flex flex-col justify-between p-4 min-h-[calc(100vh-4rem)]">
      <div className="space-y-6">
        <div>
          <p className="px-3 text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-3">
            Core Modules
          </p>
          <nav className="space-y-1">
            {visibleItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    "flex items-center justify-between px-3 py-2.5 rounded-lg text-xs font-medium transition duration-150 " +
                    (isActive
                      ? item.highlight
                        ? "bg-brand-600 text-white shadow-lg shadow-brand-500/20"
                        : "bg-slate-800/80 text-brand-400 border border-slate-700/80"
                      : item.highlight
                      ? "text-brand-300 hover:bg-brand-500/10 hover:text-white border border-brand-500/20"
                      : "text-slate-400 hover:bg-slate-900 hover:text-slate-200")
                  }
                >
                  <div className="flex items-center space-x-3">
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                  </div>
                  {item.badge !== undefined && item.badge > 0 && (
                    <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
                      {item.badge}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </nav>
        </div>
      </div>

      {/* Enterprise Architectural Invariant Footer */}
      <div className="p-3 rounded-lg border border-slate-800/80 bg-slate-900/40 text-[11px] text-slate-400 space-y-1.5">
        <div className="flex items-center space-x-1.5 font-semibold text-slate-300">
          <Cpu className="w-3.5 h-3.5 text-brand-400" />
          <span>System Topology</span>
        </div>
        <div className="text-[10px] text-slate-400 leading-relaxed">
          <p><span className="text-slate-300 font-medium">FastAPI:</span> NLP & Clarification</p>
          <p><span className="text-slate-300 font-medium">Spring Boot:</span> Authoritative Engine</p>
        </div>
      </div>
    </aside>
  );
};
"""

FORMAT_TS = """import { ProcurementState, Role } from '../types';

export function formatCurrency(amount?: number | null): string {
  if (amount === undefined || amount === null || isNaN(amount)) return '₹0.00';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatDate(dateStr?: string | null): string {
  if (!dateStr) return 'N/A';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch (err) {
    return dateStr;
  }
}

export function formatRole(role?: Role | string | null): string {
  if (!role) return 'User';
  const norm = role.toUpperCase().replace('ROLE_', '');
  switch (norm) {
    case 'ADMIN':
      return 'Administrator';
    case 'PROCUREMENT_MANAGER':
      return 'Procurement Manager';
    case 'USER':
      return 'Employee';
    default:
      return role;
  }
}

export function getStateBadgeClasses(state?: ProcurementState | null): string {
  switch (state) {
    case 'COMPLETED':
      return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
    case 'WAITING_APPROVAL':
      return 'bg-amber-500/10 text-amber-400 border-amber-500/30 animate-pulse';
    case 'REVALIDATING':
      return 'bg-sky-500/10 text-sky-400 border-sky-500/30';
    case 'PURCHASING':
      return 'bg-cyan-500/10 text-cyan-400 border-cyan-500/30';
    case 'RECOMMENDED':
      return 'bg-purple-500/10 text-purple-400 border-purple-500/30';
    case 'SEARCHING':
    case 'EVALUATING':
    case 'TCO_ANALYSIS':
    case 'VALIDATING':
    case 'AUTHORIZATION_CHECK':
      return 'bg-blue-500/10 text-blue-400 border-blue-500/30';
    case 'WAITING_USER':
      return 'bg-orange-500/10 text-orange-400 border-orange-500/30';
    case 'REJECTED':
      return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
    case 'FAILED':
      return 'bg-red-500/10 text-red-400 border-red-500/30';
    case 'SUBMITTED':
    default:
      return 'bg-slate-700/30 text-slate-300 border-slate-600/40';
  }
}
"""

write_file("types/index.ts", TYPES_INDEX_TS)
write_file("api/client.ts", API_CLIENT_TS)
write_file("api/auth.ts", API_AUTH_TS)
write_file("context/AuthContext.tsx", AUTH_CONTEXT_TSX)
write_file("components/common/ProtectedRoute.tsx", PROTECTED_ROUTE_TSX)
write_file("components/layout/Sidebar.tsx", SIDEBAR_TSX)
write_file("utils/format.ts", FORMAT_TS)

print("All auth files updated and fixed successfully!")
