import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { useAppNavigation } from '../useAppNavigation';

const guardedNavigateMock = vi.hoisted(() => vi.fn());

vi.mock('../../features/charts/authService', () => ({
  useAuthService: () => ({
    flags: {
      runId: 'RUN-NAV',
      missingMaster: false,
      cacheHit: false,
      dataSourceTransition: 'server',
      fallbackUsed: false,
    },
  }),
}));

vi.mock('../NavigationGuardProvider', () => ({
  useNavigationGuard: () => ({
    registerDirty: vi.fn(),
    isDirty: false,
    dirtySources: [],
    guardedNavigate: guardedNavigateMock,
  }),
}));

function NavigationHarness() {
  const appNav = useAppNavigation({ facilityId: '0001', userId: 'user01' });

  return (
    <div>
      <button
        type="button"
        onClick={() =>
          appNav.openPrintOutpatient({
            state: { entryId: 'ENTRY-001' },
            from: 'charts',
            returnTo: '/f/0001/charts?patientId=P-001',
          })
        }
      >
        open-outpatient
      </button>
      <button
        type="button"
        onClick={() =>
          appNav.openPrintDocument({
            state: { documentId: 'DOC-001' },
            from: 'charts',
            returnTo: '/f/0001/charts?patientId=P-001&appointmentId=A-001',
          })
        }
      >
        open-document
      </button>
    </div>
  );
}

describe('useAppNavigation print routing', () => {
  beforeEach(() => {
    guardedNavigateMock.mockReset();
  });

  it('openPrintOutpatient は returnTo を URL と state に保持する', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/f/0001/charts?patientId=P-001']}>
        <NavigationHarness />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'open-outpatient' }));

    expect(guardedNavigateMock).toHaveBeenCalledTimes(1);
    const [to, options] = guardedNavigateMock.mock.calls[0] as [string, { state?: Record<string, unknown> }];
    const parsed = new URL(to, 'https://app.invalid');

    expect(parsed.pathname).toBe('/f/0001/charts/print/outpatient');
    expect(parsed.searchParams.get('from')).toBe('charts');
    expect(parsed.searchParams.get('returnTo')).toBe('/f/0001/charts?patientId=P-001');
    expect(options.state?.from).toBe('charts');
    expect(options.state?.returnTo).toBe('/f/0001/charts?patientId=P-001');
  });

  it('openPrintDocument は returnTo を URL と state に保持する', async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter initialEntries={['/f/0001/charts?patientId=P-001']}>
        <NavigationHarness />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'open-document' }));

    expect(guardedNavigateMock).toHaveBeenCalledTimes(1);
    const [to, options] = guardedNavigateMock.mock.calls[0] as [string, { state?: Record<string, unknown> }];
    const parsed = new URL(to, 'https://app.invalid');

    expect(parsed.pathname).toBe('/f/0001/charts/print/document');
    expect(parsed.searchParams.get('from')).toBe('charts');
    expect(parsed.searchParams.get('returnTo')).toBe('/f/0001/charts?patientId=P-001&appointmentId=A-001');
    expect(options.state?.from).toBe('charts');
    expect(options.state?.returnTo).toBe('/f/0001/charts?patientId=P-001&appointmentId=A-001');
  });
});
