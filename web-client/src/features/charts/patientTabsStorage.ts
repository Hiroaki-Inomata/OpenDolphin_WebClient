import { buildScopedStorageKey, toScopeSuffix } from '../../libs/session/storageScope';

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
const GLOBAL_SCOPE_KEY = '__global__';
const volatilePatientTabsByScope = new Map<string, ChartsPatientTabsStorage>();

const sanitizePersistedTab = (tab: ChartsPatientTab): ChartsPatientTab => ({
  key: tab.key,
  patientId: tab.patientId,
  visitDate: tab.visitDate,
  appointmentId: tab.appointmentId,
  receptionId: tab.receptionId,
  openedAt: tab.openedAt,
});

export const buildPatientTabKey = (patientId: string, visitDate: string) => `${patientId}::${visitDate}`;

const resolveScopeKey = (scope?: { facilityId?: string; userId?: string }) => toScopeSuffix(scope) ?? GLOBAL_SCOPE_KEY;

const cloneState = (state: ChartsPatientTabsStorage): ChartsPatientTabsStorage => ({
  version: state.version,
  updatedAt: state.updatedAt,
  savedAt: state.savedAt,
  activeKey: state.activeKey,
  tabs: state.tabs.map((tab) => ({ ...tab })),
});

const cleanupLegacyPatientTabsStorage = (scope?: { facilityId?: string; userId?: string }) => {
  if (typeof sessionStorage === 'undefined') return;
  try {
    const scopedKey = buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope);
    if (scopedKey) {
      sessionStorage.removeItem(scopedKey);
    }
    sessionStorage.removeItem(`${PATIENT_TABS_STORAGE_BASE}:v1`);
  } catch {
    // ignore cleanup errors
  }
};

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
  cleanupLegacyPatientTabsStorage(scope);
  const scopeKey = resolveScopeKey(scope);
  const scopedState = volatilePatientTabsByScope.get(scopeKey);
  if (scopedState) {
    return cloneState(scopedState);
  }
  if (scopeKey === GLOBAL_SCOPE_KEY) {
    return null;
  }
  const globalState = volatilePatientTabsByScope.get(GLOBAL_SCOPE_KEY);
  if (!globalState) {
    return null;
  }
  const cloned = cloneState(globalState);
  volatilePatientTabsByScope.set(scopeKey, cloneState(cloned));
  volatilePatientTabsByScope.delete(GLOBAL_SCOPE_KEY);
  return cloned;
};

export const writeChartsPatientTabsStorage = (
  state: ChartsPatientTabsStorage,
  scope?: { facilityId?: string; userId?: string },
) => {
  cleanupLegacyPatientTabsStorage(scope);
  const normalized: ChartsPatientTabsStorage = {
    ...state,
    tabs: state.tabs.map(sanitizePersistedTab),
  };
  const scopeKey = resolveScopeKey(scope);
  if (normalized.tabs.length === 0 && !normalized.activeKey) {
    volatilePatientTabsByScope.delete(scopeKey);
    return;
  }
  volatilePatientTabsByScope.set(scopeKey, cloneState(normalized));
};

export const clearChartsPatientTabsStorage = (scope?: { facilityId?: string; userId?: string }) => {
  cleanupLegacyPatientTabsStorage(scope);
  if (!scope) {
    volatilePatientTabsByScope.clear();
    return;
  }
  volatilePatientTabsByScope.delete(resolveScopeKey(scope));
  volatilePatientTabsByScope.delete(GLOBAL_SCOPE_KEY);
};
