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
  const scopedKey =
    buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, {
      facilityId: FACILITY_ID,
      userId: USER_ID,
    }) ?? `${PATIENT_TABS_STORAGE_BASE}:v1`;

  const tabKey = buildPatientTabKey('00000001', '2026-03-01');
  const state: ChartsPatientTabsStorage = {
    version: 1,
    updatedAt: '2026-03-01T04:11:12.000Z',
    activeKey: tabKey,
    tabs: [
      {
        key: tabKey,
        patientId: '00000001',
        visitDate: '2026-03-01',
        appointmentId: '1001',
        receptionId: '2001',
        name: '山田太郎',
        department: '内科',
        openedAt: '2026-03-01T04:11:12.000Z',
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
  it('患者タブクリックで charts へ遷移する', async () => {
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

    const patientTab = await screen.findByRole('tab', { name: '山田太郎（内科）' });
    expect(patientTab).toBeInTheDocument();

    await user.click(patientTab);

    await waitFor(() => {
      expect(window.location.pathname).toBe('/f/0001/charts');
    });
  });
});
