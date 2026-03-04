import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AppRouter } from '../AppRouter';
import {
  PATIENT_TABS_STORAGE_BASE,
  PATIENT_TABS_STORAGE_VERSION,
  buildPatientTabKey,
  type ChartsPatientTabsStorage,
} from '../features/charts/patientTabsStorage';
import { buildScopedStorageKey } from '../libs/session/storageScope';

const AUTH_STORAGE_KEY = 'opendolphin:web-client:auth';

const FACILITY_ID = '0001';
const USER_ID = 'user01';
const RUN_ID = '20260301T041112Z';

const setAuthSession = () => {
  sessionStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      facilityId: FACILITY_ID,
      userId: USER_ID,
      role: 'doctor',
      runId: RUN_ID,
      displayName: USER_ID,
    }),
  );
};

const setPatientTabsStorage = () => {
  const now = new Date().toISOString();
  const scopedKey =
    buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, {
      facilityId: FACILITY_ID,
      userId: USER_ID,
    }) ?? `${PATIENT_TABS_STORAGE_BASE}:v1`;

  const tabKey = buildPatientTabKey('00000001', '2026-03-01');
  const state: ChartsPatientTabsStorage = {
    version: 1,
    updatedAt: now,
    savedAt: now,
    activeKey: tabKey,
    tabs: [
      {
        key: tabKey,
        patientId: '00000001',
        visitDate: '2026-03-01',
        appointmentId: '1001',
        receptionId: '2001',
        openedAt: now,
      },
    ],
  };

  sessionStorage.setItem(scopedKey, JSON.stringify(state));
};

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  localStorage.clear();
  window.history.pushState({}, '', '/');
});

describe('WorkspaceTabBar navigation', () => {
  it('患者カルテタブが患者管理の右側に表示され、クリックで charts へ遷移する', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient();

    localStorage.clear();
    sessionStorage.clear();
    setAuthSession();
    setPatientTabsStorage();

    window.history.pushState({}, '', '/f/0001/reception');

    render(
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>,
    );

    expect(window.location.pathname).toBe('/f/0001/reception');

    const patientTab = await screen.findByRole('tab', { name: '患者' });
    expect(patientTab).toBeInTheDocument();
    const tabs = screen.getAllByRole('tab');
    expect(tabs.some((tab) => tab.textContent?.includes('受付'))).toBe(true);
    expect(tabs.some((tab) => tab.textContent?.includes('患者管理'))).toBe(true);
    expect(tabs.filter((tab) => tab.textContent?.includes('患者')).length).toBeGreaterThanOrEqual(1);

    await user.click(patientTab);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/f/0001/charts');
    });
  });

  it('受付画面で患者タブの✗を押しても charts へ遷移せずタブだけ閉じる', async () => {
    const user = userEvent.setup();
    const queryClient = new QueryClient();

    localStorage.clear();
    sessionStorage.clear();
    setAuthSession();
    setPatientTabsStorage();

    window.history.pushState({}, '', '/f/0001/reception');

    render(
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>,
    );

    const closeButton = await screen.findByRole('button', { name: '患者を閉じる' });
    await user.click(closeButton);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/f/0001/reception');
    });
    await waitFor(() => {
      expect(screen.queryByRole('tab', { name: '患者' })).toBeNull();
    });
  });
});
