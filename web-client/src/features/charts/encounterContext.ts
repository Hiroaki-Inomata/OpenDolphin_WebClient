import { buildScopedStorageKey, type StorageScope } from '../../libs/session/storageScope';

export type OutpatientEncounterContext = {
  patientId?: string;
  appointmentId?: string;
  receptionId?: string;
  visitDate?: string; // YYYY-MM-DD
};

export type ChartsNavigationMeta = {
  runId?: string;
};

export type ReceptionCarryoverParams = {
  kw?: string;
  dept?: string;
  phys?: string;
  pay?: string;
  sort?: string;
  date?: string;
};

const STORAGE_BASE_KEY = 'opendolphin:web-client:charts:encounter-context';
const STORAGE_VERSION = 'v2';
const LEGACY_STORAGE_KEY = `${STORAGE_BASE_KEY}:v1`;
const ENCOUNTER_STORAGE_TTL_MS = 2 * 60 * 60 * 1000;

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const RUN_ID_RE = /^\d{8}T\d{6}Z$/;
const NUMERIC_ID_RE = /^\d+$/;

type EncounterStorageEnvelope = {
  version: 2;
  savedAt: string;
  context: OutpatientEncounterContext;
};

export const normalizeEncounterId = (value?: string | null): string | undefined => {
  if (!value) return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

export const normalizeEncounterContext = (context?: OutpatientEncounterContext | null): OutpatientEncounterContext => {
  if (!context) {
    return {
      patientId: undefined,
      appointmentId: undefined,
      receptionId: undefined,
      visitDate: undefined,
    };
  }
  return {
    patientId: normalizeEncounterId(context.patientId),
    appointmentId: normalizeEncounterId(context.appointmentId),
    receptionId: normalizeEncounterId(context.receptionId),
    visitDate: normalizeVisitDate(context.visitDate),
  };
};

type EncounterEntryLike = {
  patientId?: string;
  id?: string;
  appointmentId?: string;
  receptionId?: string;
};

export const resolveEncounterPatientIdFromEntry = (entry?: EncounterEntryLike): string | undefined => {
  const directPatientId = normalizeEncounterId(entry?.patientId);
  if (directPatientId) return directPatientId;

  const fallbackId = normalizeEncounterId(entry?.id);
  if (!fallbackId || !NUMERIC_ID_RE.test(fallbackId)) return undefined;

  // 受付/予約IDが数値の場合は row id と同値になり得るため、患者ID代替としては扱わない。
  const receptionId = normalizeEncounterId(entry?.receptionId);
  if (receptionId && receptionId === fallbackId) return undefined;
  const appointmentId = normalizeEncounterId(entry?.appointmentId);
  if (appointmentId && appointmentId === fallbackId) return undefined;

  return fallbackId;
};

export const CHARTS_CONTEXT_QUERY_KEYS = {
  patientId: 'patientId',
  appointmentId: 'appointmentId',
  receptionId: 'receptionId',
  visitDate: 'visitDate',
} as const;

export const CHARTS_META_QUERY_KEYS = {
  runId: 'runId',
} as const;

export const RECEPTION_CARRYOVER_QUERY_KEYS = {
  keyword: 'kw',
  department: 'dept',
  physician: 'phys',
  payment: 'pay',
  sort: 'sort',
  date: 'date',
} as const;

export const normalizeVisitDate = (value?: string): string | undefined => {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!DATE_RE.test(trimmed)) return undefined;
  return trimmed;
};

export const normalizeRunId = (value?: string): string | undefined => {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!RUN_ID_RE.test(trimmed)) return undefined;
  return trimmed;
};

export const hasEncounterContext = (context?: OutpatientEncounterContext | null): boolean => {
  const normalized = normalizeEncounterContext(context);
  return Boolean(
    normalized.receptionId || normalized.appointmentId || normalized.patientId || normalized.visitDate,
  );
};

export const parseChartsEncounterContext = (search: string): OutpatientEncounterContext => {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const patientId = normalizeEncounterId(params.get(CHARTS_CONTEXT_QUERY_KEYS.patientId));
  const appointmentId = normalizeEncounterId(params.get(CHARTS_CONTEXT_QUERY_KEYS.appointmentId));
  const receptionId = normalizeEncounterId(params.get(CHARTS_CONTEXT_QUERY_KEYS.receptionId));
  const visitDate = normalizeVisitDate(params.get(CHARTS_CONTEXT_QUERY_KEYS.visitDate) ?? undefined);
  return { patientId, appointmentId, receptionId, visitDate };
};

export const stripChartsEncounterParams = (search: string): string => {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  params.delete(CHARTS_CONTEXT_QUERY_KEYS.patientId);
  params.delete(CHARTS_CONTEXT_QUERY_KEYS.appointmentId);
  params.delete(CHARTS_CONTEXT_QUERY_KEYS.receptionId);
  params.delete(CHARTS_CONTEXT_QUERY_KEYS.visitDate);
  const query = params.toString();
  return query ? `?${query}` : '';
};

export const persistChartsEncounterContextFromSearch = (
  search: string,
  scope?: StorageScope,
): OutpatientEncounterContext | null => {
  const context = parseChartsEncounterContext(search);
  if (!context.patientId) return null;
  storeChartsEncounterContext(context, scope);
  return context;
};

export const parseChartsNavigationMeta = (search: string): ChartsNavigationMeta => {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const runId = normalizeRunId(params.get(CHARTS_META_QUERY_KEYS.runId) ?? undefined);
  return { runId };
};

export const parseReceptionCarryoverParams = (search: string): ReceptionCarryoverParams => {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const keyword = params.get(RECEPTION_CARRYOVER_QUERY_KEYS.keyword) ?? undefined;
  const department = params.get(RECEPTION_CARRYOVER_QUERY_KEYS.department) ?? undefined;
  const physician = params.get(RECEPTION_CARRYOVER_QUERY_KEYS.physician) ?? undefined;
  const payment = params.get(RECEPTION_CARRYOVER_QUERY_KEYS.payment) ?? undefined;
  const sort = params.get(RECEPTION_CARRYOVER_QUERY_KEYS.sort) ?? undefined;
  const date = params.get(RECEPTION_CARRYOVER_QUERY_KEYS.date) ?? undefined;
  return { kw: keyword, dept: department, phys: physician, pay: payment, sort, date };
};

export const buildChartsEncounterSearch = (
  context: OutpatientEncounterContext,
  carryover: ReceptionCarryoverParams = {},
  meta: ChartsNavigationMeta = {},
): string => {
  const normalizedContext = normalizeEncounterContext(context);
  const params = new URLSearchParams();
  if (normalizedContext.patientId) params.set(CHARTS_CONTEXT_QUERY_KEYS.patientId, normalizedContext.patientId);
  if (normalizedContext.appointmentId) params.set(CHARTS_CONTEXT_QUERY_KEYS.appointmentId, normalizedContext.appointmentId);
  if (normalizedContext.receptionId) params.set(CHARTS_CONTEXT_QUERY_KEYS.receptionId, normalizedContext.receptionId);
  const normalizedDate = normalizeVisitDate(normalizedContext.visitDate);
  if (normalizedDate) params.set(CHARTS_CONTEXT_QUERY_KEYS.visitDate, normalizedDate);
  const normalizedRunId = normalizeRunId(meta.runId);
  if (normalizedRunId) params.set(CHARTS_META_QUERY_KEYS.runId, normalizedRunId);
  if (carryover.kw) params.set(RECEPTION_CARRYOVER_QUERY_KEYS.keyword, carryover.kw);
  if (carryover.dept) params.set(RECEPTION_CARRYOVER_QUERY_KEYS.department, carryover.dept);
  if (carryover.phys) params.set(RECEPTION_CARRYOVER_QUERY_KEYS.physician, carryover.phys);
  if (carryover.pay) params.set(RECEPTION_CARRYOVER_QUERY_KEYS.payment, carryover.pay);
  if (carryover.sort) params.set(RECEPTION_CARRYOVER_QUERY_KEYS.sort, carryover.sort);
  if (carryover.date) params.set(RECEPTION_CARRYOVER_QUERY_KEYS.date, carryover.date);
  const query = params.toString();
  return query ? `?${query}` : '';
};

export const buildChartsUrl = (
  context: OutpatientEncounterContext,
  carryover?: ReceptionCarryoverParams,
  meta?: ChartsNavigationMeta,
  basePath = '/charts',
): string => `${basePath}${buildChartsEncounterSearch(context, carryover, meta)}`;

const parseSavedAt = (value: unknown): Date | null => {
  if (typeof value !== 'string') return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const loadFromRaw = (raw: string | null): { context: OutpatientEncounterContext | null; expired: boolean } => {
  if (!raw) return { context: null, expired: false };
  const parsed = JSON.parse(raw) as unknown;
  if (!parsed || typeof parsed !== 'object') {
    return { context: null, expired: false };
  }
  const record = parsed as Record<string, unknown>;
  if (
    record.version === 2 &&
    typeof record.context === 'object' &&
    record.context !== null
  ) {
    const savedAt = parseSavedAt(record.savedAt);
    if (!savedAt || Date.now() - savedAt.getTime() > ENCOUNTER_STORAGE_TTL_MS) {
      return { context: null, expired: true };
    }
    const context = record.context as Record<string, unknown>;
    return {
      context: normalizeEncounterContext({
        patientId: typeof context.patientId === 'string' ? context.patientId : undefined,
        appointmentId: typeof context.appointmentId === 'string' ? context.appointmentId : undefined,
        receptionId: typeof context.receptionId === 'string' ? context.receptionId : undefined,
        visitDate: typeof context.visitDate === 'string' ? context.visitDate : undefined,
      }),
      expired: false,
    };
  }
  return {
    context: normalizeEncounterContext({
      patientId: typeof record.patientId === 'string' ? record.patientId : undefined,
      appointmentId: typeof record.appointmentId === 'string' ? record.appointmentId : undefined,
      receptionId: typeof record.receptionId === 'string' ? record.receptionId : undefined,
      visitDate: typeof record.visitDate === 'string' ? record.visitDate : undefined,
    }),
    expired: false,
  };
};

export const storeChartsEncounterContext = (context: OutpatientEncounterContext, scope?: StorageScope) => {
  if (typeof sessionStorage === 'undefined') return;
  const normalized = normalizeEncounterContext(context);
  const scopedKey = buildScopedStorageKey(STORAGE_BASE_KEY, STORAGE_VERSION, scope) ?? LEGACY_STORAGE_KEY;
  try {
    const payload: EncounterStorageEnvelope = {
      version: 2,
      savedAt: new Date().toISOString(),
      context: normalized,
    };
    sessionStorage.setItem(scopedKey, JSON.stringify(payload));
  } catch {
    // storage が使えない環境ではスキップ
  }
};

export const loadChartsEncounterContext = (scope?: StorageScope): OutpatientEncounterContext | null => {
  if (typeof sessionStorage === 'undefined') return null;
  try {
    const scopedKey = buildScopedStorageKey(STORAGE_BASE_KEY, STORAGE_VERSION, scope);
    const rawScoped = scopedKey ? sessionStorage.getItem(scopedKey) : null;
    if (rawScoped) {
      const loaded = loadFromRaw(rawScoped);
      if (loaded.expired) {
        if (scopedKey) {
          sessionStorage.removeItem(scopedKey);
        }
        return null;
      }
      return loaded.context;
    }

    // legacy fallback
    const legacyRaw = sessionStorage.getItem(LEGACY_STORAGE_KEY);
    const legacyLoaded = loadFromRaw(legacyRaw);
    if (legacyLoaded.expired) {
      sessionStorage.removeItem(LEGACY_STORAGE_KEY);
      return null;
    }
    const legacy = legacyLoaded.context;
    if (legacy && scopedKey) {
      try {
        const payload: EncounterStorageEnvelope = {
          version: 2,
          savedAt: new Date().toISOString(),
          context: legacy,
        };
        sessionStorage.setItem(scopedKey, JSON.stringify(payload));
        sessionStorage.removeItem(LEGACY_STORAGE_KEY);
      } catch {
        // ignore migration errors
      }
    }
    return legacy;
  } catch {
    return null;
  }
};
