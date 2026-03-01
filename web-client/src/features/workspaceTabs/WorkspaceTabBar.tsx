import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { buildScopedStorageKey } from '../../libs/session/storageScope';
import { buildFacilityPath } from '../../routes/facilityRoutes';
import { useAppNavigation } from '../../routes/useAppNavigation';
import {
  CHARTS_PATIENT_TABS_UPDATED_EVENT,
  dispatchChartsPatientTabsUpdated,
  dispatchWorkspaceChartsTabRequest,
} from './workspaceTabEvents';

type WorkspaceTabBarProps = {
  facilityId?: string;
  userId?: string;
  role?: string;
};

type ChartsPatientTab = {
  key: string;
  patientId: string;
  visitDate: string;
  appointmentId?: string;
  receptionId?: string;
  name?: string;
  department?: string;
  openedAt?: string;
};

type ChartsPatientTabsStorage = {
  version: 1;
  updatedAt: string;
  activeKey?: string;
  tabs: ChartsPatientTab[];
};

const PATIENT_TABS_STORAGE_BASE = 'opendolphin:web-client:charts:patient-tabs';
const PATIENT_TABS_STORAGE_VERSION = 'v1';
const LEGACY_PATIENT_TABS_STORAGE_KEY = `${PATIENT_TABS_STORAGE_BASE}:v1`;

const normalizeText = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
};

const createInitialTabsState = (): ChartsPatientTabsStorage => ({
  version: 1,
  updatedAt: new Date().toISOString(),
  activeKey: undefined,
  tabs: [],
});

const normalizeTab = (raw: unknown): ChartsPatientTab | null => {
  if (!raw || typeof raw !== 'object') return null;
  const source = raw as Partial<ChartsPatientTab>;
  const patientId = normalizeText(source.patientId);
  const visitDate = normalizeText(source.visitDate);
  if (!patientId || !visitDate) return null;
  const key = normalizeText(source.key) ?? `${patientId}::${visitDate}`;
  const normalized: ChartsPatientTab = {
    key,
    patientId,
    visitDate,
  };
  const appointmentId = normalizeText(source.appointmentId);
  const receptionId = normalizeText(source.receptionId);
  const name = normalizeText(source.name);
  const department = normalizeText(source.department);
  const openedAt = normalizeText(source.openedAt);
  if (appointmentId) normalized.appointmentId = appointmentId;
  if (receptionId) normalized.receptionId = receptionId;
  if (name) normalized.name = name;
  if (department) normalized.department = department;
  if (openedAt) normalized.openedAt = openedAt;
  return normalized;
};

const readChartsPatientTabsStorage = (scope?: { facilityId?: string; userId?: string }): ChartsPatientTabsStorage | null => {
  if (typeof sessionStorage === 'undefined') return null;
  const scopedKey =
    buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope) ?? LEGACY_PATIENT_TABS_STORAGE_KEY;
  try {
    const raw = sessionStorage.getItem(scopedKey) ?? sessionStorage.getItem(LEGACY_PATIENT_TABS_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<ChartsPatientTabsStorage> | null;
    if (!parsed || !Array.isArray(parsed.tabs)) return null;
    const tabs = parsed.tabs.reduce<ChartsPatientTab[]>((acc, entry) => {
      const tab = normalizeTab(entry);
      if (tab) acc.push(tab);
      return acc;
    }, []);
    const rawActiveKey = normalizeText(parsed.activeKey);
    const hasActiveKey = rawActiveKey ? tabs.some((tab) => tab.key === rawActiveKey) : false;
    return {
      version: 1,
      updatedAt: normalizeText(parsed.updatedAt) ?? new Date().toISOString(),
      activeKey: hasActiveKey ? rawActiveKey : tabs[0]?.key,
      tabs,
    };
  } catch {
    return null;
  }
};

const writeChartsPatientTabsStorage = (state: ChartsPatientTabsStorage, scope?: { facilityId?: string; userId?: string }) => {
  if (typeof sessionStorage === 'undefined') return;
  const scopedKey =
    buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope) ?? LEGACY_PATIENT_TABS_STORAGE_KEY;
  try {
    sessionStorage.setItem(scopedKey, JSON.stringify(state));
  } catch {
    // ignore storage errors
  }
};

const formatTabLabel = (tab: ChartsPatientTab) => {
  const patientName = normalizeText(tab.name) ?? '患者';
  const department = normalizeText(tab.department);
  if (!department) return patientName;
  return `${patientName}（${department}）`;
};

export function WorkspaceTabBar({ facilityId, userId, role }: WorkspaceTabBarProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const appNav = useAppNavigation({ facilityId, userId });
  const dynamicListRef = useRef<HTMLDivElement | null>(null);
  const overflowRootRef = useRef<HTMLDivElement | null>(null);
  const [hasOverflow, setHasOverflow] = useState(false);
  const [overflowOpen, setOverflowOpen] = useState(false);

  const storageScope = useMemo(() => ({ facilityId, userId }), [facilityId, userId]);
  const loadPatientTabs = useCallback(
    () => readChartsPatientTabsStorage(storageScope) ?? createInitialTabsState(),
    [storageScope],
  );
  const [patientTabsState, setPatientTabsState] = useState<ChartsPatientTabsStorage>(loadPatientTabs);

  const dynamicTabs = patientTabsState.tabs;
  const isChartsScreen = /\/charts\/?$/.test(location.pathname);
  const isChartsArea = ['charts', 'print', 'orderSets'].includes(appNav.currentScreen);
  const activeDynamicKey = isChartsScreen ? patientTabsState.activeKey : undefined;
  const hasActiveDynamic = Boolean(activeDynamicKey && dynamicTabs.some((tab) => tab.key === activeDynamicKey));
  const isSystemAdmin = role === 'system_admin';

  const activeFixedKey = useMemo(() => {
    if (appNav.currentScreen === 'reception') return 'reception';
    if (appNav.currentScreen === 'patients') return 'patients';
    if (appNav.currentScreen === 'admin') return 'admin';
    if (isChartsArea && !hasActiveDynamic) return 'charts';
    return undefined;
  }, [appNav.currentScreen, hasActiveDynamic, isChartsArea]);

  const refreshPatientTabs = useCallback(() => {
    setPatientTabsState(loadPatientTabs());
  }, [loadPatientTabs]);

  const updateOverflow = useCallback(() => {
    const list = dynamicListRef.current;
    if (!list) {
      setHasOverflow(false);
      return;
    }
    setHasOverflow(list.scrollWidth > list.clientWidth + 1);
  }, []);

  useEffect(() => {
    refreshPatientTabs();
  }, [refreshPatientTabs]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const handleTabsUpdated = () => refreshPatientTabs();
    window.addEventListener(CHARTS_PATIENT_TABS_UPDATED_EVENT, handleTabsUpdated);
    return () => {
      window.removeEventListener(CHARTS_PATIENT_TABS_UPDATED_EVENT, handleTabsUpdated);
    };
  }, [refreshPatientTabs]);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const rafId = window.requestAnimationFrame(updateOverflow);
    return () => {
      window.cancelAnimationFrame(rafId);
    };
  }, [dynamicTabs, updateOverflow]);

  useEffect(() => {
    const list = dynamicListRef.current;
    if (!list || typeof window === 'undefined') return;
    const handleResize = () => updateOverflow();
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(handleResize) : null;
    observer?.observe(list);
    window.addEventListener('resize', handleResize);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', handleResize);
    };
  }, [updateOverflow, dynamicTabs.length]);

  useEffect(() => {
    if (!hasOverflow) {
      setOverflowOpen(false);
    }
  }, [hasOverflow]);

  useEffect(() => {
    if (!overflowOpen || typeof window === 'undefined') return;
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOverflowOpen(false);
      }
    };
    const handleOutsideClick = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (overflowRootRef.current?.contains(target)) return;
      setOverflowOpen(false);
    };
    window.addEventListener('keydown', handleEscape);
    window.addEventListener('mousedown', handleOutsideClick);
    return () => {
      window.removeEventListener('keydown', handleEscape);
      window.removeEventListener('mousedown', handleOutsideClick);
    };
  }, [overflowOpen]);

  const selectDynamicTab = useCallback(
    (tab: ChartsPatientTab) => {
      setOverflowOpen(false);
      if (isChartsScreen) {
        dispatchWorkspaceChartsTabRequest({ action: 'select', key: tab.key });
        return;
      }
      appNav.openCharts({
        encounter: {
          patientId: tab.patientId,
          appointmentId: tab.appointmentId,
          receptionId: tab.receptionId,
          visitDate: tab.visitDate,
        },
      });
    },
    [appNav, isChartsScreen],
  );

  const closeDynamicTab = useCallback(
    (key: string) => {
      if (isChartsScreen) {
        dispatchWorkspaceChartsTabRequest({ action: 'close', key });
        return;
      }

      const currentState = readChartsPatientTabsStorage(storageScope) ?? patientTabsState;
      const index = currentState.tabs.findIndex((tab) => tab.key === key);
      if (index < 0) return;
      const nextTabs = currentState.tabs.filter((tab) => tab.key !== key);
      const nextActiveTab = nextTabs[index - 1] ?? nextTabs[index] ?? nextTabs[0] ?? undefined;
      const nextState: ChartsPatientTabsStorage = {
        version: 1,
        updatedAt: new Date().toISOString(),
        activeKey: nextActiveTab?.key,
        tabs: nextTabs,
      };
      writeChartsPatientTabsStorage(nextState, storageScope);
      setPatientTabsState(nextState);
      dispatchChartsPatientTabsUpdated();
    },
    [isChartsScreen, patientTabsState, storageScope],
  );

  const handleOpenAdministration = useCallback(() => {
    navigate(buildFacilityPath(facilityId, '/administration'));
  }, [facilityId, navigate]);

  return (
    <div className="app-shell__tabbar">
      <div className="workspace-tabs" role="tablist" aria-label="ワークスペースタブ">
        <div className="workspace-tabs__fixed">
          <button
            type="button"
            role="tab"
            className={`workspace-tabs__tab${activeFixedKey === 'reception' ? ' is-active' : ''}`}
            aria-selected={activeFixedKey === 'reception'}
            tabIndex={activeFixedKey === 'reception' ? 0 : -1}
            onClick={() => appNav.openReception()}
          >
            受付
          </button>
          <button
            type="button"
            role="tab"
            className={`workspace-tabs__tab${activeFixedKey === 'charts' ? ' is-active' : ''}`}
            aria-selected={activeFixedKey === 'charts'}
            tabIndex={activeFixedKey === 'charts' ? 0 : -1}
            onClick={() => appNav.openCharts()}
          >
            カルテ
          </button>
          <button
            type="button"
            role="tab"
            className={`workspace-tabs__tab${activeFixedKey === 'patients' ? ' is-active' : ''}`}
            aria-selected={activeFixedKey === 'patients'}
            tabIndex={activeFixedKey === 'patients' ? 0 : -1}
            onClick={() => appNav.openPatients()}
          >
            患者管理
          </button>
          {isSystemAdmin ? (
            <button
              type="button"
              role="tab"
              className={`workspace-tabs__tab${activeFixedKey === 'admin' ? ' is-active' : ''}`}
              aria-selected={activeFixedKey === 'admin'}
              tabIndex={activeFixedKey === 'admin' ? 0 : -1}
              onClick={handleOpenAdministration}
            >
              管理
            </button>
          ) : null}
        </div>

        <div className="workspace-tabs__dynamic-area">
          <div className="workspace-tabs__dynamic" ref={dynamicListRef}>
            {dynamicTabs.map((tab) => {
              const label = formatTabLabel(tab);
              const isActive = activeDynamicKey === tab.key;
              return (
                <div key={tab.key} className="workspace-tabs__item">
                  <button
                    type="button"
                    role="tab"
                    className={`workspace-tabs__tab${isActive ? ' is-active' : ''}`}
                    aria-selected={isActive}
                    tabIndex={isActive ? 0 : -1}
                    title={label}
                    onClick={() => selectDynamicTab(tab)}
                  >
                    {label}
                  </button>
                  <button
                    type="button"
                    className="workspace-tabs__close"
                    aria-label={`${label}を閉じる`}
                    onClick={(event) => {
                      event.stopPropagation();
                      closeDynamicTab(tab.key);
                    }}
                  >
                    ×
                  </button>
                </div>
              );
            })}
          </div>

          {hasOverflow ? (
            <div className="workspace-tabs__overflow" ref={overflowRootRef}>
              <button
                type="button"
                className="workspace-tabs__overflow-toggle"
                aria-label="患者タブ一覧を開く"
                aria-expanded={overflowOpen}
                onClick={() => setOverflowOpen((prev) => !prev)}
              >
                &gt;&gt;
              </button>
              {overflowOpen ? (
                <div className="workspace-tabs__overflow-panel" aria-label="患者タブ一覧">
                  {dynamicTabs.map((tab) => {
                    const label = formatTabLabel(tab);
                    const isActive = activeDynamicKey === tab.key;
                    return (
                      <div key={`overflow-${tab.key}`} className="workspace-tabs__overflow-entry">
                        <button
                          type="button"
                          role="tab"
                          className={`workspace-tabs__tab${isActive ? ' is-active' : ''}`}
                          aria-selected={isActive}
                          tabIndex={isActive ? 0 : -1}
                          onClick={() => selectDynamicTab(tab)}
                        >
                          {label}
                        </button>
                        <button
                          type="button"
                          className="workspace-tabs__close"
                          aria-label={`${label}を閉じる`}
                          onClick={(event) => {
                            event.stopPropagation();
                            closeDynamicTab(tab.key);
                          }}
                        >
                          ×
                        </button>
                      </div>
                    );
                  })}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
