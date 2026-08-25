import { springClient } from './client';
import { ProcurementAuditResponse } from '../types';

export async function getAuditTrail(procurementId: string): Promise<ProcurementAuditResponse> {
  return springClient.get<ProcurementAuditResponse>('/api/procurements/' + procurementId + '/audit');
}
