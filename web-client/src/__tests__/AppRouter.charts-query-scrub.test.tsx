import React from 'react';
import { render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

import { AppRouterWithNavigation } from '../AppRouter';
import { loadChartsEncounterContext } from '../features/charts/encounterContext';

vi.mock('../styles/app-shell.css', () => ({}));
vi.mock('../libs/observability/observability', () => ({
  updateObservabilityMeta: vi.fn(),
  resolveAriaLive: () => 'polite',
  getObservabilityMeta: () => ({}),
}));
vi.mock('../libs/observability/runIdCopy', () => ({
  copyRunIdToClipboard: vi.fn().mockResolvedValue(undefined),
  copyTextToClipboard: vi.fn().mockResolvedValue(undefined),
}));
vi.mock('../libs/audit/auditLogger', () => ({
  logAuditEvent: vi.fn(),
}));
vi.mock('../features/login/recentFacilityStore', () => ({
  addRecentFacility: vi.fn(),
}));
vi.mock('../libs/session/sessionExpiry', () => ({
  SESSION_EXPIRED_EVENT: 'session-expired',
  clearSessionExpiredNotice: vi.fn(),
}));
vi.mock('../libs/ui/appToast', () => ({
  AppToastProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock('../features/shared/ChartEventStreamBridge', () => ({
  ChartEventStreamBridge: () => null,
}));
vi.mock('../features/shared/MockModeBanner', () => ({
  MockModeBanner: () => null,
}));
vi.mock('../features/workspaceTabs/WorkspaceTabBar', () => ({
  WorkspaceTabBar: () => null,
}));
vi.mock('../features/charts/authService', async () => {
  const ReactModule = await import('react');
  const AuthContext = ReactModule.createContext({ flags: {} });
  return {
    AuthServiceProvider: ({ children }: { children: React.ReactNode }) => (
      <AuthContext.Provider value={{ flags: {} }}>{children}</AuthContext.Provider>
    ),
    useAuthService: () => ReactModule.useContext(AuthContext),
    clearStoredAuthFlags: vi.fn(),
  };
});
vi.mock('../features/charts/pages/ChartsPage', () => ({
  ChartsPage: () => <div data-testid="charts-page">charts</div>,
}));
vi.mock('../features/reception/pages/ReceptionPage', () => ({
  ReceptionPage: () => <div data-testid="reception-page">reception</div>,
}));
vi.mock('../features/charts/pages/ChartsOutpatientPrintPage', () => ({
  ChartsOutpatientPrintPage: () => <div data-testid="charts-outpatient-print">print</div>,
}));
vi.mock('../features/charts/pages/ChartsDocumentPrintPage', () => ({
  ChartsDocumentPrintPage: () => <div data-testid="charts-document-print">doc-print</div>,
}));
vi.mock('../features/charts/pages/OrderSetEditorPage', () => ({
  OrderSetEditorPage: () => <div data-testid="order-set-editor">order-sets</div>,
}));
vi.mock('../features/patients/PatientsPage', () => ({
  PatientsPage: () => <div data-testid="patients-page">patients</div>,
}));
vi.mock('../features/administration/AdministrationPage', () => ({
  AdministrationPage: () => <div data-testid="administration-page">admin</div>,
}));
vi.mock('../features/debug/DebugHubPage', () => ({
  DebugHubPage: () => <div data-testid="debug-hub-page">debug-hub</div>,
}));
vi.mock('../features/debug/OrcaApiConsolePage', () => ({
  OrcaApiConsolePage: () => <div data-testid="debug-orca-api">debug-orca</div>,
}));
vi.mock('../features/debug/LegacyRestConsolePage', () => ({
  LegacyRestConsolePage: () => <div data-testid="debug-legacy-rest">debug-legacy</div>,
}));
vi.mock('../features/images/pages/MobileImagesUploadPage', () => ({
  MobileImagesUploadPage: () => <div data-testid="mobile-images-page">mobile-images</div>,
}));
vi.mock('../features/debug/MobilePatientPickerDemoPage', () => ({
  MobilePatientPickerDemoPage: () => <div data-testid="mobile-picker-page">mobile-picker</div>,
}));
vi.mock('../features/administration/orcaConnectionApi', () => ({
  testOrcaConnection: vi.fn(),
}));
vi.mock('../features/login/loginRouteState', () => ({
  resolveSwitchContext: () => undefined,
}));
vi.mock('../routes/NavigationGuardProvider', async () => {
  return {
    NavigationGuardProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    resolveScreenKey: () => 'charts',
    useNavigationGuard: () => ({
      isDirty: false,
      dirtySources: [],
      guardedNavigate: vi.fn(),
      confirmNavigation: vi.fn(),
    }),
  };
});

const AUTH_STORAGE_KEY = 'opendolphin:web-client:auth';

const buildRouter = (initialEntries: string[]) =>
  createMemoryRouter(
    [{ path: '*', element: <AppRouterWithNavigation /> }],
    { initialEntries },
  );

describe('AppRouter charts query scrub', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    sessionStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        facilityId: '0001',
        userId: 'user-1',
        role: 'doctor',
        runId: 'run-001',
      }),
    );
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('charts queryから encounter パラメータを除去し、context を保存する', async () => {
    const router = buildRouter(['/f/0001/charts?patientId=00001&visitDate=2026-01-01']);
    render(<RouterProvider router={router} />);

    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/f/0001/charts');
      expect(router.state.location.search).toBe('');
    });

    const saved = loadChartsEncounterContext({ facilityId: '0001', userId: 'user-1' });
    expect(saved).toEqual({
      patientId: '00001',
      appointmentId: undefined,
      receptionId: undefined,
      visitDate: '2026-01-01',
    });
  });
});
