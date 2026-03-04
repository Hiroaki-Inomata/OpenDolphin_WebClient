import { buildScopedStorageKey } from '../../libs/session/storageScope';

import { normalizeEncounterId, normalizeVisitDate } from './encounterContext';

export type ChartsPatientTab = {
  key: string;
  patientId: string;
  visitDate: string; // YYYY-MM-DD
  appointmentId?: string;
  receptionId?: string;
  name?: string;
  department?: string;
  openedAt: string; // ISO
};

export type ChartsPatientTabsStorage = {
  version: 1;
  updatedAt: string;
  savedAt: string;
  activeKey?: string;
  tabs: ChartsPatientTab[];
};

export const PATIENT_TABS_STORAGE_BASE = 'opendolphin:web-client:charts:patient-tabs';
export const PATIENT_TABS_STORAGE_VERSION = 'v1';
export const PATIENT_TABS_TTL_MS = 2 * 60 * 60 * 1000;

const sanitizePersistedTab = (tab: ChartsPatientTab): ChartsPatientTab => ({
  key: tab.key,
  patientId: tab.patientId,
  visitDate: tab.visitDate,
  appointmentId: tab.appointmentId,
  receptionId: tab.receptionId,
  openedAt: tab.openedAt,
});

const isExpiredSavedAt = (savedAt?: string, now = Date.now()): boolean => {
  if (!savedAt) return true;
  const timestamp = Date.parse(savedAt);
  if (Number.isNaN(timestamp)) return true;
  return now - timestamp > PATIENT_TABS_TTL_MS;
};

export const buildPatientTabKey = (patientId: string, visitDate: string) => `${patientId}::${visitDate}`;

export const applyEncounterTabState = (
  prev: ChartsPatientTabsStorage,
  params: {
    patientId: string;
    visitDate: string;
    appointmentId?: string;
    receptionId?: string;
    name?: string;
    department?: string;
  },
): ChartsPatientTabsStorage => {
  const patientId = normalizeEncounterId(params.patientId);
  const visitDate = normalizeVisitDate(params.visitDate);
  if (!patientId || !visitDate) return prev;
  const key = buildPatientTabKey(patientId, visitDate);
  const existing = prev.tabs.find((tab) => tab.key === key);
  const appointmentId = normalizeEncounterId(params.appointmentId) ?? existing?.appointmentId;
  const receptionId = normalizeEncounterId(params.receptionId) ?? existing?.receptionId;
  const name = typeof params.name === 'string' && params.name.trim() ? params.name.trim() : existing?.name;
  const department =
    typeof params.department === 'string' && params.department.trim()
      ? params.department.trim()
      : existing?.department;
  const nextTab: ChartsPatientTab = {
    key,
    patientId,
    visitDate,
    appointmentId,
    receptionId,
    name,
    department,
    openedAt: existing?.openedAt ?? new Date().toISOString(),
  };
  const tabUnchanged =
    existing !== undefined &&
    existing.patientId === nextTab.patientId &&
    existing.visitDate === nextTab.visitDate &&
    (existing.appointmentId ?? undefined) === (nextTab.appointmentId ?? undefined) &&
    (existing.receptionId ?? undefined) === (nextTab.receptionId ?? undefined) &&
    (existing.name ?? undefined) === (nextTab.name ?? undefined) &&
    (existing.department ?? undefined) === (nextTab.department ?? undefined);
  const activeUnchanged = prev.activeKey === key;
  if (tabUnchanged && activeUnchanged) return prev;

  const nextTabs = existing
    ? tabUnchanged
      ? prev.tabs
      : prev.tabs.map((tab) => (tab.key === key ? nextTab : tab))
    : [...prev.tabs, nextTab];
  return {
    ...prev,
    activeKey: key,
    tabs: nextTabs,
  };
};

export const readChartsPatientTabsStorage = (
  scope?: { facilityId?: string; userId?: string },
): ChartsPatientTabsStorage | null => {
  if (typeof sessionStorage === 'undefined') return null;
  const legacyKey = `${PATIENT_TABS_STORAGE_BASE}:v1`;
  const scopedKey =
    buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope) ??
    legacyKey;
  try {
    const raw = sessionStorage.getItem(scopedKey) ?? sessionStorage.getItem(legacyKey);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<ChartsPatientTabsStorage> | null;
    if (!parsed || parsed.version !== 1 || !Array.isArray(parsed.tabs)) return null;
    const savedAt = typeof parsed.savedAt === 'string' ? parsed.savedAt : typeof parsed.updatedAt === 'string' ? parsed.updatedAt : undefined;
    if (isExpiredSavedAt(savedAt)) {
      sessionStorage.removeItem(scopedKey);
      sessionStorage.removeItem(legacyKey);
      return null;
    }
    const resolvedSavedAt = savedAt ?? new Date().toISOString();
    const resolvedUpdatedAt = typeof parsed.updatedAt === 'string' ? parsed.updatedAt : resolvedSavedAt;

    const normalizedTabs = parsed.tabs.reduce<ChartsPatientTab[]>((acc, tab) => {
      const patientId = normalizeEncounterId(typeof tab.patientId === 'string' ? tab.patientId : undefined);
      const visitDate = normalizeVisitDate(typeof tab.visitDate === 'string' ? tab.visitDate : undefined);
      if (!patientId || !visitDate) return acc;
      const key = typeof tab.key === 'string' && tab.key.trim() ? tab.key.trim() : buildPatientTabKey(patientId, visitDate);
      const normalized: ChartsPatientTab = {
        key,
        patientId,
        visitDate,
        openedAt: typeof tab.openedAt === 'string' ? tab.openedAt : new Date().toISOString(),
      };
      const appointmentId = normalizeEncounterId(typeof tab.appointmentId === 'string' ? tab.appointmentId : undefined);
      const receptionId = normalizeEncounterId(typeof tab.receptionId === 'string' ? tab.receptionId : undefined);
      if (appointmentId) normalized.appointmentId = appointmentId;
      if (receptionId) normalized.receptionId = receptionId;
      acc.push(normalized);
      return acc;
    }, []);

    const activeKey =
      typeof parsed.activeKey === 'string' && parsed.activeKey.trim()
        ? parsed.activeKey.trim()
        : normalizedTabs[0]?.key;

    // migrate legacy to scoped
    const scopedKeyActual = buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope);
    if (scopedKeyActual && !sessionStorage.getItem(scopedKeyActual)) {
      try {
        sessionStorage.setItem(
          scopedKeyActual,
          JSON.stringify({
            version: 1,
            updatedAt: resolvedUpdatedAt,
            savedAt: resolvedSavedAt,
            activeKey,
            tabs: normalizedTabs.map(sanitizePersistedTab),
          }),
        );
        if (scopedKey !== scopedKeyActual) {
          sessionStorage.removeItem(legacyKey);
        }
      } catch {
        // ignore migration errors
      }
    }

    return {
      version: 1,
      updatedAt: resolvedUpdatedAt,
      savedAt: resolvedSavedAt,
      activeKey,
      tabs: normalizedTabs,
    };
  } catch {
    return null;
  }
};

export const writeChartsPatientTabsStorage = (
  state: ChartsPatientTabsStorage,
  scope?: { facilityId?: string; userId?: string },
) => {
  if (typeof sessionStorage === 'undefined') return;
  const now = new Date().toISOString();
  const persisted: ChartsPatientTabsStorage = {
    ...state,
    updatedAt: now,
    savedAt: now,
    tabs: state.tabs.map(sanitizePersistedTab),
  };
  const scopedKey =
    buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope) ??
    `${PATIENT_TABS_STORAGE_BASE}:v1`;
  try {
    sessionStorage.setItem(scopedKey, JSON.stringify(persisted));
  } catch {
    // ignore storage errors
  }
};
