import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, waitFor } from '@testing-library/react';

import { AuthServiceProvider } from '../authService';
import { ChartsPage } from '../pages/ChartsPage';
import { NavigationGuardProvider } from '../../../routes/NavigationGuardProvider';

const session = {
  facilityId: 'facility',
  userId: 'doctor',
  role: 'system_admin',
  displayName: 'Doctor',
  commonName: 'Doctor',
};

vi.mock('@emotion/react', () => ({
  Global: () => null,
  css: () => '',
}));

vi.mock('../../../AppRouter', () => ({
  useSession: () => session,
}));

vi.mock('../useChartsTabLock', () => ({
  useChartsTabLock: () => ({
    status: 'none',
    tabSessionId: 'tab-1',
    storageKey: null,
    isReadOnly: false,
    readOnlyReason: undefined,
    ownerRunId: undefined,
    ownerTabSessionId: undefined,
    expiresAt: undefined,
    forceTakeover: vi.fn(),
  }),
}));

vi.mock('../../../libs/admin/useAdminBroadcast', () => ({
  useAdminBroadcast: () => ({ broadcast: null }),
}));

vi.mock('../../administration/api', () => ({
  fetchEffectiveAdminConfig: vi.fn(async () => ({
    chartsMasterSource: 'server',
    chartsDisplayEnabled: true,
    chartsSendEnabled: true,
    deliveryId: 'DELIVERY-1',
    deliveryVersion: '1',
    deliveredAt: '2026-02-16T10:00:00.000Z',
    runId: 'RUN-ADMIN',
  })),
}));

vi.mock('../../reception/api', () => ({
  fetchClaimFlags: vi.fn(async () => ({
    runId: 'RUN-CLAIM',
    cacheHit: true,
    missingMaster: false,
    fallbackUsed: false,
    dataSourceTransition: 'server',
    bundles: [],
    queueEntries: [],
    recordsReturned: 1,
    hasNextPage: false,
    fetchedAt: '2026-02-16T10:00:00.000Z',
  })),
  fetchAppointmentOutpatients: vi.fn(async () => ({
    runId: 'RUN-APPOINT',
    cacheHit: true,
    missingMaster: false,
    fallbackUsed: false,
    dataSourceTransition: 'server',
    entries: [
      {
        id: 'entry-1',
        patientId: 'P-001',
        name: '患者A',
        status: '診療中',
        source: 'visits',
        appointmentId: 'A-001',
        receptionId: 'R-001',
        visitDate: '2026-02-16',
        department: '内科',
      },
    ],
    page: 1,
    size: 50,
    hasNextPage: false,
    recordsReturned: 1,
    fetchedAt: '2026-02-16T10:00:00.000Z',
  })),
}));

vi.mock('../api', () => ({
  fetchOrcaOutpatientSummary: vi.fn(async () => ({
    runId: 'RUN-SUMMARY',
    cacheHit: true,
    missingMaster: false,
    fallbackUsed: false,
    dataSourceTransition: 'server',
    outcome: 'SUCCESS',
    payload: {},
    recordsReturned: 1,
    fetchedAt: '2026-02-16T10:00:00.000Z',
  })),
}));

vi.mock('../../patients/api', () => ({
  fetchPatients: vi.fn(async () => ({ patients: [] })),
}));

vi.mock('../../outpatient/orcaQueueApi', () => ({
  fetchOrcaQueue: vi.fn(async () => ({
    runId: 'RUN-QUEUE',
    queue: [],
    source: 'mock',
    fetchedAt: '2026-02-16T10:00:00.000Z',
  })),
  fetchOrcaPushEvents: vi.fn(async () => ({
    runId: 'RUN-PUSH',
    events: [],
    fetchedAt: '2026-02-16T10:00:00.000Z',
  })),
}));

vi.mock('../../outpatient/orcaQueueStatus', () => ({
  resolveOrcaSendStatus: () => undefined,
  toClaimQueueEntryFromOrcaQueueEntry: (entry: any) => entry,
}));

vi.mock('../../../libs/http/httpClient', () => ({
  hasStoredAuth: () => true,
}));

vi.mock('../AuthServiceControls', () => ({ AuthServiceControls: () => null }));
vi.mock('../DocumentTimeline', () => ({ DocumentTimeline: () => null }));
vi.mock('../OrcaSummary', () => ({ OrcaSummary: () => null }));
vi.mock('../MedicalOutpatientRecordPanel', () => ({ MedicalOutpatientRecordPanel: () => null }));
vi.mock('../OrcaOriginalPanel', () => ({ OrcaOriginalPanel: () => null }));
vi.mock('../PatientsTab', () => ({ PatientsTab: () => null }));
vi.mock('../TelemetryFunnelPanel', () => ({ TelemetryFunnelPanel: () => null }));
vi.mock('../ChartsActionBar', () => ({ ChartsActionBar: () => null }));
vi.mock('../ChartsPatientSummaryBar', () => ({ ChartsPatientSummaryBar: () => null }));
vi.mock('../DiagnosisEditPanel', () => ({ DiagnosisEditPanel: () => null }));
vi.mock('../DocumentCreatePanel', () => ({ DocumentCreatePanel: () => null }));
vi.mock('../OrderBundleEditPanel', () => ({ OrderBundleEditPanel: () => null }));
vi.mock('../PastHubPanel', () => ({ PastHubPanel: () => null }));
vi.mock('../PatientSummaryPanel', () => ({ PatientSummaryPanel: () => null }));
vi.mock('../../images/components', () => ({ ImageDockedPanel: () => null }));
vi.mock('../../shared/AdminBroadcastBanner', () => ({ AdminBroadcastBanner: () => null }));
vi.mock('../../shared/RunIdBadge', () => ({ RunIdBadge: () => null }));
vi.mock('../../shared/StatusPill', () => ({ StatusPill: () => null }));
vi.mock('../../shared/AuditSummaryInline', () => ({ AuditSummaryInline: () => null }));
vi.mock('../../reception/components/ToneBanner', () => ({ ToneBanner: () => null }));
vi.mock('../styles', () => ({ chartsStyles: '' }));
vi.mock('../../reception/styles', () => ({ receptionStyles: '' }));
vi.mock('../../outpatient/appointmentDataBanner', () => ({ getAppointmentDataBanner: () => null }));

vi.mock('../SoapNotePanel', () => ({
  SoapNotePanel: ({ onDraftDirtyChange, onSyncStateChange }: any) => {
    React.useEffect(() => {
      onDraftDirtyChange?.({
        dirty: true,
        dirtySources: ['soap'],
      });
      onSyncStateChange?.({
        localSaved: false,
        serverSynced: false,
        isSaving: false,
      });
    }, [onDraftDirtyChange, onSyncStateChange]);
    return React.createElement('div', { 'data-test-id': 'soap-note-mock' });
  },
}));

describe('ChartsPage patient tab dirty indicator', () => {
  it('ドラフトが dirty のとき患者タブに未保存ドットを表示する', async () => {
    const patientTabKey = 'P-001::2026-02-16';
    const storageKey = 'opendolphin:web-client:charts:patient-tabs:v1:facility:doctor';
    sessionStorage.setItem(
      storageKey,
      JSON.stringify({
        version: 1,
        updatedAt: '2026-02-16T10:00:00.000Z',
        activeKey: patientTabKey,
        tabs: [
          {
            key: patientTabKey,
            patientId: 'P-001',
            visitDate: '2026-02-16',
            appointmentId: 'A-001',
            receptionId: 'R-001',
            name: '患者A',
            openedAt: '2026-02-16T10:00:00.000Z',
          },
        ],
      }),
    );

    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <AuthServiceProvider initialFlags={{ runId: 'RUN-AUTH', cacheHit: true, missingMaster: false, dataSourceTransition: 'server' }}>
          <MemoryRouter initialEntries={['/f/facility/charts']}>
            <NavigationGuardProvider>
              <ChartsPage />
            </NavigationGuardProvider>
          </MemoryRouter>
        </AuthServiceProvider>
      </QueryClientProvider>,
    );

    await waitFor(() =>
      expect(document.querySelector('[data-test-id="charts-patient-tabs"]')).not.toBeNull(),
    );
    await waitFor(() =>
      expect(document.querySelector('.charts-patient-tabs__dirty-dot')).not.toBeNull(),
    );
  });
});
