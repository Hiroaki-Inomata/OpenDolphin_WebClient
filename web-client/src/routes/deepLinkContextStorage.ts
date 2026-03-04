export const DEEPLINK_CTX_KEY = 'opendolphin:web-client:deeplink-context';

const DEEPLINK_CTX_TTL_MS = 2 * 60 * 60 * 1000;
const DEEPLINK_VALUE_KEYS = [
  'patientId',
  'appointmentId',
  'receptionId',
  'visitDate',
  'invoiceNumber',
  'kw',
  'keyword',
] as const;

type DeepLinkValueKey = (typeof DEEPLINK_VALUE_KEYS)[number];

export type DeepLinkContext = {
  savedAt: string;
  values: Partial<Record<DeepLinkValueKey, string>>;
};

const parseSavedAtMs = (value: unknown): number | null => {
  if (typeof value !== 'string') return null;
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? null : parsed;
};

const normalizeValue = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

const sanitizeValues = (source: unknown): DeepLinkContext['values'] => {
  if (!source || typeof source !== 'object') return {};
  const record = source as Record<string, unknown>;
  const values: DeepLinkContext['values'] = {};
  DEEPLINK_VALUE_KEYS.forEach((key) => {
    const normalized = normalizeValue(record[key]);
    if (normalized) {
      values[key] = normalized;
    }
  });
  return values;
};

export function clearDeepLinkContext(): void {
  if (typeof sessionStorage === 'undefined') return;
  try {
    sessionStorage.removeItem(DEEPLINK_CTX_KEY);
  } catch {
    // ignore storage errors
  }
}

export function loadDeepLinkContext(): DeepLinkContext | null {
  if (typeof sessionStorage === 'undefined') return null;
  try {
    const raw = sessionStorage.getItem(DEEPLINK_CTX_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object') {
      clearDeepLinkContext();
      return null;
    }
    const record = parsed as Record<string, unknown>;
    const savedAtMs = parseSavedAtMs(record.savedAt);
    if (savedAtMs === null || Date.now() - savedAtMs > DEEPLINK_CTX_TTL_MS) {
      clearDeepLinkContext();
      return null;
    }
    return {
      savedAt: new Date(savedAtMs).toISOString(),
      values: sanitizeValues(record.values),
    };
  } catch {
    clearDeepLinkContext();
    return null;
  }
}

export function saveDeepLinkContext(values: DeepLinkContext['values']): void {
  if (typeof sessionStorage === 'undefined') return;
  try {
    const current = loadDeepLinkContext();
    const nextValues: DeepLinkContext['values'] = {
      ...(current?.values ?? {}),
      ...sanitizeValues(values),
    };
    const payload: DeepLinkContext = {
      savedAt: new Date().toISOString(),
      values: nextValues,
    };
    sessionStorage.setItem(DEEPLINK_CTX_KEY, JSON.stringify(payload));
  } catch {
    // ignore storage errors
  }
}
