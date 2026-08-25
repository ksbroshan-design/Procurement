import { springClient } from './client';
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
