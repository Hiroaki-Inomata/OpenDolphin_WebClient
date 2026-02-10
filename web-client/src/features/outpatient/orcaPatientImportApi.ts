import { httpFetch } from '../../libs/http/httpClient';
import { generateRunId, getObservabilityMeta, updateObservabilityMeta } from '../../libs/observability/observability';

export type OrcaPatientImportResult = {
  ok: boolean;
  runId: string;
  status: number;
  payload?: any;
  error?: string;
};

export async function importPatientsFromOrca(params: {
  patientIds: string[];
  includeInsurance?: boolean;
  runId?: string;
}): Promise<OrcaPatientImportResult> {
  const runId = params.runId ?? getObservabilityMeta().runId ?? generateRunId();
  updateObservabilityMeta({ runId });

  if (!params.patientIds?.length) {
    return { ok: false, runId, status: 0, error: 'patientIds is required' };
  }

  const response = await httpFetch('/orca/patients/import', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      patientIds: params.patientIds,
      includeInsurance: Boolean(params.includeInsurance),
    }),
  });

  const status = response.status;
  const text = await response.text().catch(() => '');
  const json = (() => {
    try {
      return text ? (JSON.parse(text) as any) : null;
    } catch {
      return null;
    }
  })();

  if (!response.ok) {
    const message = typeof json?.message === 'string' ? json.message : typeof json?.error === 'string' ? json.error : response.statusText;
    return { ok: false, runId, status, payload: json ?? text, error: message || `HTTP ${status}` };
  }

  return { ok: true, runId, status, payload: json ?? text };
}

