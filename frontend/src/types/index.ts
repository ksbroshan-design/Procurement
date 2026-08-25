export type Role =
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
  vendorName: string;
  vendorId?: string;
  quantity: number;
  horizonYears: number;

  // Unit costs
  unitPurchaseCost: number;
  unitMaintenanceCost: number;
  unitExpectedRepairCost: number;
  unitExpectedDowntimeCost: number;
  unitReplacementCost: number;
  unitWarrantyBenefit: number;
  unitTco: number;

  // Total procurement costs (unit * quantity)
  totalPurchaseCost: number;
  totalMaintenanceCost: number;
  totalExpectedRepairCost: number;
  totalExpectedDowntimeCost: number;
  totalReplacementCost: number;
  totalWarrantyBenefit: number;
  totalTco: number;

  // Reliability & Warranty Context
  failureRate: number;
  averageRepairCost: number;
  averageDowntimeCost: number;
  warrantyYears: number;
  warrantyType: string;
  dataGrounded?: boolean;
  assumptions?: string[];

  // Optional legacy aliases for backwards compatibility
  purchaseCost?: number;
  maintenanceCost?: number;
  expectedRepairCost?: number;
  downtimeRiskCost?: number;
  replacementRiskCost?: number;
  warrantyBenefit?: number;
  durationYears?: number;
  annualFailureRate?: number;
  downtimeCostPerHour?: number;
  expectedDowntimeHours?: number;
  effectiveWarrantyYears?: number;
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
