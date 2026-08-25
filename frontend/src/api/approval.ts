import { springClient } from './client';
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
