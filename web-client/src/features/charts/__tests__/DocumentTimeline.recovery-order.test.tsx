import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

import { DocumentTimeline } from '../DocumentTimeline';
import { AppToastProvider } from '../../../libs/ui/appToast';

vi.mock('@emotion/react', () => ({
  Global: () => null,
  css: () => '',
}));

const defaultFlags = {
  runId: 'RUN-DOC',
  missingMaster: true,
  cacheHit: false,
  dataSourceTransition: 'server',
  fallbackUsed: false,
};
let mockFlags = { ...defaultFlags };
let mockSession: { role?: string } | null = { role: 'system_admin' };

vi.mock('../authService', () => ({
  useAuthService: () => ({
    flags: mockFlags,
  }),
}));

vi.mock('../../../AppRouter', () => ({
  useOptionalSession: () => mockSession,
}));

vi.mock('../../../libs/ui/appToast', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../libs/ui/appToast')>();
  return {
    ...actual,
    useAppToast: () => ({ enqueue: vi.fn(), dismiss: vi.fn() }),
  };
});

describe('DocumentTimeline recovery order', () => {
  beforeEach(() => {
    mockFlags = { ...defaultFlags };
    mockSession = { role: 'system_admin' };
  });

  it('renders alert -> banner -> details in order', () => {
    mockFlags = { ...defaultFlags, missingMaster: true };
    const { container } = render(
      <AppToastProvider value={{ enqueue: vi.fn(), dismiss: vi.fn() }}>
        <DocumentTimeline claimData={{ missingMaster: true } as any} />
      </AppToastProvider>,
    );

    const alert = container.querySelector('.document-timeline__alert');
    const banner = container.querySelector('.tone-banner');
    const details = container.querySelector('.document-timeline__controls');

    expect(alert).toBeTruthy();
    expect(banner).toBeTruthy();
    expect(details).toBeTruthy();
    if (!alert || !banner || !details) return;

    expect(alert.compareDocumentPosition(banner) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(banner.compareDocumentPosition(details) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('admin かつ retrySupported=true のときだけ再送 CTA を表示する', () => {
    mockFlags = { ...defaultFlags, missingMaster: false, cacheHit: true, fallbackUsed: false };
    render(
      <AppToastProvider value={{ enqueue: vi.fn(), dismiss: vi.fn() }}>
        <DocumentTimeline
          entries={[
            {
              id: 'entry-1',
              patientId: 'P-1',
              name: 'テスト患者',
              appointmentTime: '09:00',
              status: '診療中',
              source: 'visits',
            },
          ]}
          selectedPatientId="P-1"
          orcaQueue={{
            retrySupported: true,
            ok: true,
            status: 200,
            queue: [
              {
                patientId: 'P-1',
                status: 'failed',
                error: 'send failed',
                lastDispatchAt: '2026-01-29T00:00:00Z',
              },
            ],
          }}
        />
      </AppToastProvider>,
    );

    const buttons = screen.getAllByRole('button', { name: 'ORCA再送を試行' });
    expect(buttons).toHaveLength(1);
  });

  it('非 admin では再送 CTA を表示しない', () => {
    mockFlags = { ...defaultFlags, missingMaster: false, cacheHit: true, fallbackUsed: false };
    mockSession = { role: 'doctor' };
    render(
      <AppToastProvider value={{ enqueue: vi.fn(), dismiss: vi.fn() }}>
        <DocumentTimeline
          entries={[
            {
              id: 'entry-1',
              patientId: 'P-1',
              name: 'テスト患者',
              appointmentTime: '09:00',
              status: '診療中',
              source: 'visits',
            },
          ]}
          selectedPatientId="P-1"
          orcaQueue={{
            retrySupported: true,
            ok: true,
            status: 200,
            queue: [{ patientId: 'P-1', status: 'failed', error: 'send failed', lastDispatchAt: '2026-01-29T00:00:00Z' }],
          }}
        />
      </AppToastProvider>,
    );

    expect(screen.queryByRole('button', { name: 'ORCA再送を試行' })).toBeNull();
  });

  it('retrySupported=false では再送 CTA を表示しない', () => {
    mockFlags = { ...defaultFlags, missingMaster: false, cacheHit: true, fallbackUsed: false };
    render(
      <AppToastProvider value={{ enqueue: vi.fn(), dismiss: vi.fn() }}>
        <DocumentTimeline
          entries={[
            {
              id: 'entry-1',
              patientId: 'P-1',
              name: 'テスト患者',
              appointmentTime: '09:00',
              status: '診療中',
              source: 'visits',
            },
          ]}
          selectedPatientId="P-1"
          orcaQueue={{
            retrySupported: false,
            ok: true,
            status: 200,
            queue: [{ patientId: 'P-1', status: 'failed', error: 'send failed', lastDispatchAt: '2026-01-29T00:00:00Z' }],
          }}
        />
      </AppToastProvider>,
    );

    expect(screen.queryByRole('button', { name: 'ORCA再送を試行' })).toBeNull();
  });
});
