import { httpFetch } from '../../libs/http/httpClient';
import { generateRunId, getObservabilityMeta, updateObservabilityMeta } from '../../libs/observability/observability';
import { importPatientsFromOrca } from '../outpatient/orcaPatientImportApi';
import { buildPatientImportFailureMessage, isRecoverableOrcaNotFound } from '../shared/orcaPatientImportRecovery';
import type { OrcaResponseErrorKind } from '../shared/orcaApiResponse';
import { parseOrcaApiResponse } from '../shared/orcaApiResponse';

export type DiseaseEntry = {
  diagnosisId?: number;
  diagnosisName?: string;
  diagnosisCode?: string;
  departmentCode?: string;
  insuranceCombinationNumber?: string;
  startDate?: string;
  endDate?: string;
  outcome?: string;
  category?: string;
  suspectedFlag?: string;
};

export type DiseaseImportResponse = {
  ok?: boolean;
  status?: number;
  message?: string;
  errorCode?: string;
  errorKind?: OrcaResponseErrorKind;
  routeMismatch?: boolean;
  patientId?: string;
  baseDate?: string;
  apiResult?: string;
  apiResultMessage?: string;
  runId?: string;
  diseases?: DiseaseEntry[];
  patientImportAttempted?: boolean;
  patientImportStatus?: number;
};

type FetchDiseasesParams = {
  patientId: string;
  from?: string;
  to?: string;
  activeOnly?: boolean;
};

export type DiseaseMutationOperation = {
  operation: 'create' | 'update' | 'delete';
  diagnosisId?: number;
  diagnosisName?: string;
  diagnosisCode?: string;
  departmentCode?: string;
  insuranceCombinationNumber?: string;
  startDate?: string;
  endDate?: string;
  outcome?: string;
  category?: string;
  suspectedFlag?: string;
  note?: string;
};

export type DiseaseMutationResult = {
  ok: boolean;
  runId?: string;
  message?: string;
  createdDiagnosisIds?: number[];
  updatedDiagnosisIds?: number[];
  removedDiagnosisIds?: number[];
  raw?: unknown;
};

export async function fetchDiseases(params: FetchDiseasesParams): Promise<DiseaseImportResponse> {
  const runId = getObservabilityMeta().runId ?? generateRunId();
  updateObservabilityMeta({ runId });
  const query = new URLSearchParams();
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  if (params.activeOnly) query.set('activeOnly', 'true');
  const queryString = query.toString();
  const response = await httpFetch(`/orca/disease/import/${encodeURIComponent(params.patientId)}${queryString ? `?${queryString}` : ''}`);
  const parsed = await parseOrcaApiResponse(response, { fallbackMessage: '病名情報の取得に失敗しました。' });
  if (parsed.ok && !parsed.json) {
    return {
      ok: false,
      status: parsed.status,
      message: '病名情報APIがJSON以外を返しました。ルーティング設定を確認してください。',
      errorKind: 'route_not_found',
      routeMismatch: true,
      runId,
      diseases: [],
    };
  }
  const json = (parsed.json ?? {}) as DiseaseImportResponse;
  return {
    ...json,
    ok: parsed.ok,
    status: parsed.status,
    message: parsed.message,
    errorCode: parsed.ok ? undefined : parsed.errorCode,
    errorKind: parsed.ok ? undefined : parsed.errorKind,
    routeMismatch: parsed.ok ? false : parsed.routeMismatch,
    runId: json.runId ?? parsed.runId ?? runId,
    diseases: Array.isArray(json.diseases) ? json.diseases : [],
  };
}

export async function fetchDiseasesWithPatientImportRecovery(
  params: FetchDiseasesParams,
): Promise<DiseaseImportResponse> {
  const primary = await fetchDiseases(params);
  if (primary.ok) return primary;

  if (
    !isRecoverableOrcaNotFound({
      patientId: params.patientId,
      status: primary.status,
      errorCode: primary.errorCode,
      errorKind: primary.errorKind,
    })
  ) {
    return primary;
  }

  const importResult = await importPatientsFromOrca({
    patientIds: [params.patientId],
    runId: primary.runId,
  });

  if (!importResult.ok) {
    return {
      ...primary,
      ok: false,
      diseases: [],
      runId: importResult.runId ?? primary.runId,
      status: importResult.status || primary.status,
      message: buildPatientImportFailureMessage('病名情報', importResult),
      errorCode: importResult.errorCode ?? primary.errorCode,
      errorKind: importResult.errorKind ?? primary.errorKind,
      routeMismatch: importResult.routeMismatch ?? primary.routeMismatch,
      patientImportAttempted: true,
      patientImportStatus: importResult.status,
    };
  }

  const retried = await fetchDiseases(params);
  return {
    ...retried,
    runId: retried.runId ?? importResult.runId ?? primary.runId,
    patientImportAttempted: true,
    patientImportStatus: importResult.status,
  };
}

export async function mutateDiseases(params: {
  patientId: string;
  operations: DiseaseMutationOperation[];
}): Promise<DiseaseMutationResult> {
  const runId = getObservabilityMeta().runId ?? generateRunId();
  updateObservabilityMeta({ runId });
  const response = await httpFetch('/orca/disease', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ patientId: params.patientId, operations: params.operations }),
  });
  const json = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  const message =
    typeof json.message === 'string'
      ? (json.message as string)
      : typeof json.apiResultMessage === 'string'
        ? (json.apiResultMessage as string)
        : undefined;
  return {
    ok: response.ok,
    runId: typeof json.runId === 'string' ? (json.runId as string) : runId,
    message,
    createdDiagnosisIds: Array.isArray(json.createdDiagnosisIds) ? (json.createdDiagnosisIds as number[]) : undefined,
    updatedDiagnosisIds: Array.isArray(json.updatedDiagnosisIds) ? (json.updatedDiagnosisIds as number[]) : undefined,
    removedDiagnosisIds: Array.isArray(json.removedDiagnosisIds) ? (json.removedDiagnosisIds as number[]) : undefined,
    raw: json,
  };
}
