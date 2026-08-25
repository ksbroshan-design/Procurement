import { springClient } from './client';
import { PurchaseOrder } from '../types';

export async function listPurchaseOrders(): Promise<PurchaseOrder[]> {
  return springClient.get<PurchaseOrder[]>('/api/procurements/purchase-orders');
}

export async function getPurchaseOrder(procurementId: string): Promise<PurchaseOrder> {
  return springClient.get<PurchaseOrder>('/api/procurements/' + procurementId + '/purchase-order');
}
