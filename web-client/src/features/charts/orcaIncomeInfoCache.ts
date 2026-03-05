type StorageScope = { facilityId?: string | null; userId?: string | null };
const CACHE_TTL_MS = 2 * 60 * 60 * 1000;

export type OrcaIncomeInfoCacheEntry = {
  patientId: string;
  performMonth?: string;
  // NOTE: invoiceNumbers は PHI になり得るため永続化しない（常に空配列で保持）。
  invoiceNumbers: string[];
  fetchedAt: string;
  apiResult?: string;
  apiResultMessage?: string;
  runId?: string;
  traceId?: string;
};

type OrcaIncomeInfoCacheStore = Record<string, OrcaIncomeInfoCacheEntry>;
const AUTH_STORAGE_KEY = 'opendolphin:web-client:auth';
const volatileIncomeInfoCache = new Map<string, OrcaIncomeInfoCacheEntry>();

const buildKey = (scope: StorageScope) => {
  const facility = scope.facilityId ?? 'unknown-facility';
  const user = scope.userId ?? 'unknown-user';
  return `charts:orca-income-info:${facility}:${user}`;
};

const buildVolatileKey = (scope: StorageScope, patientId: string) => `${buildKey(scope)}:${patientId}`;

const resolveScope = (scope: StorageScope): StorageScope => {
  if (scope.facilityId && scope.userId) return scope;
  if (typeof sessionStorage === 'undefined') return scope;
  try {
    const raw = sessionStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return scope;
    const parsed = JSON.parse(raw) as { facilityId?: string; userId?: string };
    return {
      facilityId: scope.facilityId ?? parsed.facilityId ?? undefined,
      userId: scope.userId ?? parsed.userId ?? undefined,
    };
  } catch {
    return scope;
  }
};

const normalizeInvoiceNumbers = (values: string[] | undefined): string[] => {
  if (!Array.isArray(values)) return [];
  const seen = new Set<string>();
  const normalized: string[] = [];
  values.forEach((value) => {
    const trimmed = typeof value === 'string' ? value.trim() : '';
    if (!trimmed || seen.has(trimmed)) return;
    seen.add(trimmed);
    normalized.push(trimmed);
  });
  return normalized;
};

export function saveOrcaIncomeInfoCache(value: OrcaIncomeInfoCacheEntry, scope: StorageScope) {
  if (typeof sessionStorage === 'undefined') return;
  if (!value.patientId) return;
  const resolvedScope = resolveScope(scope);
  const key = buildKey(resolvedScope);
  const fetchedAt = value.fetchedAt || new Date().toISOString();
  const volatilePayload: OrcaIncomeInfoCacheEntry = {
    ...value,
    invoiceNumbers: normalizeInvoiceNumbers(value.invoiceNumbers),
    fetchedAt,
  };
  volatileIncomeInfoCache.set(buildVolatileKey(resolvedScope, value.patientId), volatilePayload);
  const payload: OrcaIncomeInfoCacheEntry = {
    ...value,
    invoiceNumbers: [],
    fetchedAt,
  };
  const store = loadOrcaIncomeInfoCache(resolvedScope) ?? {};
  store[value.patientId] = payload;
  sessionStorage.setItem(key, JSON.stringify(store));
}

const isExpired = (fetchedAt?: string) => {
  if (!fetchedAt) return true;
  const timestamp = Date.parse(fetchedAt);
  if (Number.isNaN(timestamp)) return true;
  return Date.now() - timestamp > CACHE_TTL_MS;
};

const normalizeEntry = (entry: Partial<OrcaIncomeInfoCacheEntry> | null | undefined): OrcaIncomeInfoCacheEntry | null => {
  if (!entry) return null;
  const patientId = typeof entry.patientId === 'string' ? entry.patientId.trim() : '';
  if (!patientId) return null;
  const fetchedAt = typeof entry.fetchedAt === 'string' ? entry.fetchedAt : undefined;
  if (isExpired(fetchedAt)) return null;
  const resolvedFetchedAt = fetchedAt ?? new Date().toISOString();
  return {
    patientId,
    performMonth: typeof entry.performMonth === 'string' ? entry.performMonth : undefined,
    invoiceNumbers: [],
    fetchedAt: resolvedFetchedAt,
    apiResult: typeof entry.apiResult === 'string' ? entry.apiResult : undefined,
    apiResultMessage: typeof entry.apiResultMessage === 'string' ? entry.apiResultMessage : undefined,
    runId: typeof entry.runId === 'string' ? entry.runId : undefined,
    traceId: typeof entry.traceId === 'string' ? entry.traceId : undefined,
  };
};

export function loadOrcaIncomeInfoCache(scope: StorageScope): OrcaIncomeInfoCacheStore | null {
  if (typeof sessionStorage === 'undefined') return null;
  const resolvedScope = resolveScope(scope);
  const key = buildKey(resolvedScope);
  const raw = sessionStorage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as OrcaIncomeInfoCacheStore | OrcaIncomeInfoCacheEntry | null;
    if (!parsed || typeof parsed !== 'object') {
      sessionStorage.removeItem(key);
      return null;
    }

    const normalizedStore: OrcaIncomeInfoCacheStore = {};
    let changed = false;

    if ('fetchedAt' in parsed || 'patientId' in parsed) {
      const single = normalizeEntry(parsed as OrcaIncomeInfoCacheEntry);
      if (single) {
        normalizedStore[single.patientId] = single;
        changed = true;
      } else {
        sessionStorage.removeItem(key);
        return null;
      }
    } else {
      Object.entries(parsed as OrcaIncomeInfoCacheStore).forEach(([patientId, entry]) => {
        const normalized = normalizeEntry({
          ...(entry ?? {}),
          patientId,
        });
        if (!normalized) {
          changed = true;
          return;
        }
        if (normalized.patientId !== patientId) {
          changed = true;
        }
        normalizedStore[normalized.patientId] = normalized;
      });
    }

    const values = Object.values(normalizedStore);
    if (values.length === 0) {
      sessionStorage.removeItem(key);
      return null;
    }

    if (changed || JSON.stringify(parsed) !== JSON.stringify(normalizedStore)) {
      try {
        sessionStorage.setItem(key, JSON.stringify(normalizedStore));
      } catch {
        // ignore rewrite failures
      }
    }
    return normalizedStore;
  } catch {
    sessionStorage.removeItem(key);
    return null;
  }
}

export function getOrcaIncomeInfoEntry(scope: StorageScope, patientId?: string | null) {
  if (!patientId) return null;
  const resolvedScope = resolveScope(scope);
  const volatileKey = buildVolatileKey(resolvedScope, patientId);
  const volatile = volatileIncomeInfoCache.get(volatileKey);
  if (volatile) {
    if (!isExpired(volatile.fetchedAt)) return volatile;
    volatileIncomeInfoCache.delete(volatileKey);
  }
  const store = loadOrcaIncomeInfoCache(resolvedScope);
  return store?.[patientId] ?? null;
}
