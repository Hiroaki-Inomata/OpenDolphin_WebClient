import type { OrcaPatientImportResult } from '../outpatient/orcaPatientImportApi';
import type { OrcaResponseErrorKind } from './orcaApiResponse';

const RECOVERABLE_NOT_FOUND_CODES = new Set(['patient_not_found', 'karte_not_found']);

const normalizeToken = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

const resolveImportFailureCause = (result: OrcaPatientImportResult): string | undefined => {
  return normalizeToken(result.errorCode) ?? normalizeToken(result.errorCategory);
};

export const isOrcaPatientId = (patientId: string): boolean => /^\d+$/.test(patientId.trim());

export const isRecoverableOrcaNotFound = (params: {
  patientId: string;
  status?: number;
  errorCode?: string;
  errorKind?: OrcaResponseErrorKind;
}): boolean => {
  if (!isOrcaPatientId(params.patientId)) return false;
  if (params.status !== 404) return false;
  const code = normalizeToken(params.errorCode);
  if (!code || !RECOVERABLE_NOT_FOUND_CODES.has(code)) return false;
  if (params.errorKind && params.errorKind !== 'business_not_found') return false;
  return true;
};

export const buildPatientImportFailureMessage = (contextLabel: string, result: OrcaPatientImportResult): string => {
  const cause = resolveImportFailureCause(result);
  const runIdSuffix = result.runId ? ` (runId=${result.runId})` : '';

  if (result.errorKind === 'auth') {
    const causeText = cause ? `reason=${cause}` : 'reason=authentication_failed';
    return `${contextLabel} の再取得前に患者取込が認証エラーで失敗しました（${causeText}）。ORCA認証情報を確認してください。${runIdSuffix}`;
  }

  if (result.errorKind === 'route_not_found' || result.routeMismatch) {
    return `${contextLabel} の再取得前に患者取込APIの経路不一致を検知しました。Vite の /orca リライト設定と VITE_ORCA_API_PATH_PREFIX を確認してください。${runIdSuffix}`;
  }

  const fallback = normalizeToken(result.error) ?? `HTTP ${result.status}`;
  return `${contextLabel} の再取得前に患者取込が失敗しました: ${fallback}${runIdSuffix}`;
};
