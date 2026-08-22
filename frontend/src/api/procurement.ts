import { springClient, pythonClient } from './client';
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
