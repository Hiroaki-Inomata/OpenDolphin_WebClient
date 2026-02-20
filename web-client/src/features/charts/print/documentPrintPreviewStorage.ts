import type { ChartsPrintMeta } from './outpatientClinicalDocument';
import type { DocumentType } from '../documentTemplates';
import { buildScopedStorageKey, type StorageScope } from '../../../libs/session/storageScope';

export type DocumentOutputMode = 'print' | 'pdf';

export type DocumentPrintEntry = {
  id: string;
  type: DocumentType;
  issuedAt: string;
  title: string;
  savedAt: string;
  templateId: string;
  templateLabel: string;
  form: Record<string, string>;
  patientId: string;
};

export type DocumentPrintPreviewState = {
  document: DocumentPrintEntry;
  meta: ChartsPrintMeta;
  actor: string;
  facilityId: string;
  initialOutputMode?: DocumentOutputMode;
};

const STORAGE_BASE = 'opendolphin:web-client:charts:printPreview:document';
const OUTPUT_RESULT_BASE = 'opendolphin:web-client:charts:printResult:document';
const STORAGE_VERSION = 'v2';
const LEGACY_STORAGE_KEY = `${STORAGE_BASE}:v1`;
const LEGACY_OUTPUT_KEY = `${OUTPUT_RESULT_BASE}:v1`;
const MAX_AGE_MS = 10 * 60 * 1000;

type StoredEnvelope = {
  storedAt: string;
  value: DocumentPrintPreviewState;
};

type ScopedIdentity = {
  facilityId: string;
  userId: string;
};

const normalizeText = (value?: string) => value?.trim() ?? '';

const resolveScopedIdentity = (scope?: StorageScope): ScopedIdentity | null => {
  const facilityId = normalizeText(scope?.facilityId);
  const userId = normalizeText(scope?.userId);
  if (!facilityId || !userId) return null;
  return { facilityId, userId };
};

const parseActorScope = (actor?: string): ScopedIdentity | null => {
  if (!actor) return null;
  const trimmed = actor.trim();
  const separator = trimmed.indexOf(':');
  if (separator <= 0 || separator >= trimmed.length - 1) return null;
  const facilityId = trimmed.slice(0, separator).trim();
  const userId = trimmed.slice(separator + 1).trim();
  if (!facilityId || !userId) return null;
  return { facilityId, userId };
};

const isPreviewStateScopedTo = (value: DocumentPrintPreviewState, scope: ScopedIdentity): boolean => {
  const facilityId = normalizeText(value.facilityId);
  if (facilityId !== scope.facilityId) return false;
  const actorScope = parseActorScope(value.actor);
  if (!actorScope) return false;
  return actorScope.facilityId === scope.facilityId && actorScope.userId === scope.userId;
};

const resolveScopedKey = (base: string, scope?: StorageScope): string | null => {
  const scopedIdentity = resolveScopedIdentity(scope);
  if (!scopedIdentity) return null;
  return buildScopedStorageKey(base, STORAGE_VERSION, scopedIdentity);
};

const parseStoredEnvelope = (raw: string): StoredEnvelope | null => {
  const parsed = JSON.parse(raw) as Partial<StoredEnvelope> | null;
  if (!parsed || typeof parsed !== 'object') return null;
  if (!parsed.storedAt || !parsed.value) return null;
  return { storedAt: parsed.storedAt, value: parsed.value as DocumentPrintPreviewState };
};

export type DocumentOutputResult = {
  documentId: string;
  outcome: 'success' | 'failed' | 'blocked' | 'completed';
  mode?: DocumentOutputMode;
  at: string;
  detail?: string;
  runId?: string;
  traceId?: string;
  endpoint?: string;
  httpStatus?: number;
};

export function saveDocumentPrintPreview(value: DocumentPrintPreviewState, scope?: StorageScope) {
  if (typeof sessionStorage === 'undefined') return;
  const envelope: StoredEnvelope = { storedAt: new Date().toISOString(), value };
  try {
    const key = resolveScopedKey(STORAGE_BASE, scope);
    if (!key) {
      sessionStorage.removeItem(LEGACY_STORAGE_KEY);
      return;
    }
    sessionStorage.setItem(key, JSON.stringify(envelope));
    sessionStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function loadDocumentPrintPreview(
  scope?: StorageScope,
): { value: DocumentPrintPreviewState; storedAt: string } | null {
  if (typeof sessionStorage === 'undefined') return null;
  const scopedIdentity = resolveScopedIdentity(scope);
  if (!scopedIdentity) return null;
  try {
    const scopedKey = buildScopedStorageKey(STORAGE_BASE, STORAGE_VERSION, scopedIdentity);
    if (!scopedKey) return null;
    let raw = sessionStorage.getItem(scopedKey);
    let source: 'scoped' | 'legacy' = 'scoped';
    if (!raw) {
      const legacyRaw = sessionStorage.getItem(LEGACY_STORAGE_KEY);
      if (legacyRaw) {
        const legacyEnvelope = parseStoredEnvelope(legacyRaw);
        if (legacyEnvelope && isPreviewStateScopedTo(legacyEnvelope.value, scopedIdentity)) {
          raw = legacyRaw;
          source = 'legacy';
        }
      }
    }
    if (!raw) return null;
    const parsed = parseStoredEnvelope(raw);
    if (!parsed) return null;
    if (!isPreviewStateScopedTo(parsed.value, scopedIdentity)) {
      if (source === 'legacy') {
        sessionStorage.removeItem(LEGACY_STORAGE_KEY);
      }
      return null;
    }
    const storedAtMs = new Date(parsed.storedAt).getTime();
    if (Number.isNaN(storedAtMs)) return null;
    if (Date.now() - storedAtMs > MAX_AGE_MS) {
      if (source === 'legacy') {
        sessionStorage.removeItem(LEGACY_STORAGE_KEY);
      } else {
        sessionStorage.removeItem(scopedKey);
      }
      return null;
    }
    if (source === 'legacy') {
      sessionStorage.setItem(scopedKey, raw);
      sessionStorage.removeItem(LEGACY_STORAGE_KEY);
    }
    return parsed;
  } catch {
    return null;
  }
}

export function clearDocumentPrintPreview(scope?: StorageScope) {
  if (typeof sessionStorage === 'undefined') return;
  try {
    const key = resolveScopedKey(STORAGE_BASE, scope);
    if (key) {
      sessionStorage.removeItem(key);
    }
    sessionStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // ignore
  }
}

export function saveDocumentOutputResult(value: DocumentOutputResult, scope?: StorageScope) {
  if (typeof sessionStorage === 'undefined') return;
  try {
    const key = resolveScopedKey(OUTPUT_RESULT_BASE, scope);
    if (!key) {
      sessionStorage.removeItem(LEGACY_OUTPUT_KEY);
      return;
    }
    sessionStorage.setItem(key, JSON.stringify(value));
    sessionStorage.removeItem(LEGACY_OUTPUT_KEY);
  } catch {
    // ignore
  }
}

export function loadDocumentOutputResult(scope?: StorageScope): DocumentOutputResult | null {
  if (typeof sessionStorage === 'undefined') return null;
  try {
    const scopedKey = resolveScopedKey(OUTPUT_RESULT_BASE, scope);
    if (!scopedKey) return null;
    const raw = sessionStorage.getItem(scopedKey);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as DocumentOutputResult;
    if (!parsed || typeof parsed !== 'object' || !parsed.documentId) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function clearDocumentOutputResult(scope?: StorageScope) {
  if (typeof sessionStorage === 'undefined') return;
  try {
    const key = resolveScopedKey(OUTPUT_RESULT_BASE, scope);
    if (key) {
      sessionStorage.removeItem(key);
    }
    sessionStorage.removeItem(LEGACY_OUTPUT_KEY);
  } catch {
    // ignore
  }
}
