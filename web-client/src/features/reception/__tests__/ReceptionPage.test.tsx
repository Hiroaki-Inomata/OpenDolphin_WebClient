import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ReceptionPage } from '../pages/ReceptionPage';
import type { AppointmentPayload, ClaimOutpatientPayload, ReceptionEntry } from '../../outpatient/types';
import { postOrcaMedicalModV2Xml } from '../../charts/orcaClaimApi';

const baseClaimData: ClaimOutpatientPayload = {
  runId: 'RUN-CLAIM',
  missingMaster: false,
  cacheHit: false,
  fallbackUsed: false,
  dataSourceTransition: 'server',
  fetchedAt: '2026-01-29T00:00:00Z',
  bundles: [],
  queueEntries: [],
};

const baseAppointmentData: AppointmentPayload = {
  runId: 'RUN-APPOINT',
  missingMaster: false,
  cacheHit: false,
  fallbackUsed: false,
  dataSourceTransition: 'server',
  fetchedAt: '2026-01-29T00:00:00Z',
  entries: [] as ReceptionEntry[],
  recordsReturned: 0,
  raw: {},
};

let mockClaimData = { ...baseClaimData };
let mockAppointmentData = { ...baseAppointmentData };
let mockMutationResult: any = null;
let mockMutationQueue: any[] = [];
let mockMutationPending = false;
let mockClaimSendCache: Record<string, { invoiceNumber?: string; dataId?: string; sendStatus?: 'success' | 'error' }> =
  {};
let mockSearchParams = new URLSearchParams();
const mockInvalidateQueries = vi.fn(async () => undefined);

const mockAuthFlags = {
  runId: 'RUN-AUTH',
  missingMaster: false,
  cacheHit: false,
  dataSourceTransition: 'server' as const,
  fallbackUsed: false,
};

const mockAuthActions = {
  setCacheHit: vi.fn(),
  setMissingMaster: vi.fn(),
  setDataSourceTransition: vi.fn(),
  setFallbackUsed: vi.fn(),
  bumpRunId: vi.fn(),
};

vi.mock('@emotion/react', () => ({
  Global: () => null,
  css: () => '',
}));

vi.mock('../../charts/authService', () => ({
  applyAuthServicePatch: (patch: any, previous: any) => ({ ...previous, ...patch }),
  useAuthService: () => ({
    flags: mockAuthFlags,
    ...mockAuthActions,
  }),
}));

vi.mock('../../../routes/useAppNavigation', () => ({
  useAppNavigation: () => ({
    currentUrl: '/f/FAC-TEST/reception',
    currentScreen: 'reception',
    fromCandidate: null,
    returnToCandidate: null,
    safeReturnToCandidate: null,
    carryover: {},
    external: {},
    encounter: {},
    openReception: vi.fn(),
    openPatients: vi.fn(),
    openCharts: vi.fn(),
    openOrderSets: vi.fn(),
    openPrintOutpatient: vi.fn(),
    openPrintDocument: vi.fn(),
    openMobileImages: vi.fn(),
  }),
}));

vi.mock('../../shared/ReturnToBar', () => ({
  ReturnToBar: () => null,
}));

vi.mock('../../shared/AdminBroadcastBanner', () => ({
  AdminBroadcastBanner: () => <div data-testid="admin-broadcast" />,
}));

vi.mock('../components/OrderConsole', () => ({
  OrderConsole: () => (
    <section role="region" aria-label="オーダー概要" data-testid="order-console">
      <div>請求状態</div>
      <div>会計待ち</div>
      <div>合計金額/診療時間</div>
      <div>送信キャッシュ</div>
      <div>ORCAキュー</div>
      <button type="button">Charts 新規タブ</button>
    </section>
  ),
}));

vi.mock('../components/ReceptionAuditPanel', () => ({
  ReceptionAuditPanel: () => <div data-testid="reception-audit" />,
}));

vi.mock('../components/ReceptionExceptionList', () => ({
  ReceptionExceptionList: () => <div data-testid="reception-exceptions" />,
}));

vi.mock('../../shared/autoRefreshNotice', () => ({
  OUTPATIENT_AUTO_REFRESH_INTERVAL_MS: 90_000,
  resolveAutoRefreshIntervalMs: (value: number) => value,
  useAutoRefreshNotice: () => null,
}));

vi.mock('../../../libs/admin/useAdminBroadcast', () => ({
  useAdminBroadcast: () => ({ broadcast: null }),
}));

vi.mock('../../../libs/ui/appToast', () => ({
  useAppToast: () => ({ enqueue: vi.fn(), dismiss: vi.fn() }),
}));

vi.mock('../../../AppRouter', () => ({
  useSession: () => ({ facilityId: 'FAC-TEST', userId: 'user01' }),
}));

vi.mock('../../../libs/audit/auditLogger', () => ({
  getAuditEventLog: () => [],
  logAuditEvent: () => ({ timestamp: new Date().toISOString() }),
  logUiState: () => ({ timestamp: new Date().toISOString() }),
}));

vi.mock('../../outpatient/savedViews', () => ({
  loadOutpatientSavedViews: () => [],
  removeOutpatientSavedView: () => [],
  upsertOutpatientSavedView: () => [],
  resolvePaymentMode: (insurance?: string) => {
    if (!insurance) return undefined;
    const normalized = insurance.toLowerCase();
    if (normalized.includes('自費') || normalized.includes('self')) return 'self';
    return 'insurance';
  },
}));

vi.mock('../../charts/orcaClaimSendCache', () => ({
  loadOrcaClaimSendCache: () => mockClaimSendCache,
  saveOrcaClaimSendCache: (entry: { patientId: string }) => {
    mockClaimSendCache = { ...mockClaimSendCache, [entry.patientId]: entry as any };
  },
}));

vi.mock('../../charts/orcaClaimApi', () => ({
  buildMedicalModV2RequestXml: vi.fn().mockReturnValue('<data></data>'),
  postOrcaMedicalModV2Xml: vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    rawXml: '<xml></xml>',
    apiResult: '00',
    apiResultMessage: 'OK',
    invoiceNumber: 'INV-001',
    dataId: 'DATA-001',
    missingTags: [],
    runId: 'RUN-ORCA',
    traceId: 'TRACE-ORCA',
  }),
}));

vi.mock('../../charts/orderBundleApi', () => ({
  fetchOrderBundles: vi.fn(async () => ({ ok: true, bundles: [], recordsReturned: 0 })),
}));

vi.mock('../exceptionLogic', () => ({
  buildExceptionAuditDetails: () => ({}),
  buildQueuePhaseSummary: () => ({
    shouldWarn: false,
    summary: 'ok',
  }),
  resolveExceptionDecision: () => ({
    kind: undefined,
    detail: '',
    nextAction: '—',
    reasons: {},
  }),
}));

vi.mock('../../outpatient/orcaQueueStatus', () => ({
  ORCA_QUEUE_STALL_THRESHOLD_MS: 120_000,
  resolveOrcaSendStatus: () => ({
    key: 'waiting',
    label: '待ち',
    tone: 'warning',
    isStalled: false,
  }),
}));

vi.mock('../../outpatient/appointmentDataBanner', () => ({
  getAppointmentDataBanner: () => null,
  countAppointmentDataIntegrity: () => ({
    missingPatientId: 0,
    missingAppointmentId: 0,
    missingReceptionId: 0,
  }),
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: { queryKey: unknown[] }) => {
    const key = options.queryKey[0];
    if (key === 'outpatient-claim-flags') {
      return {
        data: mockClaimData,
        dataUpdatedAt: 0,
        isError: false,
        error: null,
        isFetching: false,
        isLoading: false,
        refetch: vi.fn(),
      };
    }
    if (key === 'outpatient-appointments') {
      return {
        data: mockAppointmentData,
        dataUpdatedAt: 0,
        isError: false,
        error: null,
        isFetching: false,
        isLoading: false,
        refetch: vi.fn(),
      };
    }
    return {
      data: undefined,
      dataUpdatedAt: 0,
      isError: false,
      error: null,
      isFetching: false,
      isLoading: false,
      refetch: vi.fn(),
    };
  },
  useMutation: (options?: {
    onSuccess?: (data: any, variables: any, context: unknown) => void;
    onError?: (error: unknown, variables: any, context: unknown) => void;
  }) => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn(async (variables?: unknown) => {
      const nextResult = mockMutationQueue.length > 0 ? mockMutationQueue.shift() : (mockMutationResult ?? {});
      if (nextResult instanceof Error) {
        options?.onError?.(nextResult, variables, undefined);
        throw nextResult;
      }
      options?.onSuccess?.(nextResult, variables, undefined);
      return nextResult;
    }),
    isPending: mockMutationPending,
  }),
  useQueryClient: () => ({
    getQueryState: () => ({ dataUpdatedAt: 0, fetchFailureCount: 0 }),
    setQueryData: vi.fn((_: unknown, updater: any) => {
      if (typeof updater === 'function') {
        const next = updater(mockAppointmentData);
        if (next) mockAppointmentData = next;
        return next;
      }
      mockAppointmentData = updater;
      return updater;
    }),
    invalidateQueries: mockInvalidateQueries,
  }),
}));

vi.mock('react-router-dom', () => ({
  MemoryRouter: ({ children }: { children: React.ReactNode }) => children,
  useNavigate: () => vi.fn(),
  useSearchParams: () => [mockSearchParams, vi.fn()],
}));

const renderReceptionPage = () => {
  render(
    <MemoryRouter initialEntries={['/reception']}>
      <ReceptionPage runId="RUN-INIT" />
    </MemoryRouter>,
  );
  screen.getByRole('heading', { name: '診察待ち' });
};

beforeEach(() => {
  mockClaimData = { ...baseClaimData };
  mockAppointmentData = { ...baseAppointmentData };
  mockMutationResult = null;
  mockMutationQueue = [];
  mockMutationPending = false;
  mockClaimSendCache = {};
  mockSearchParams = new URLSearchParams();
  mockInvalidateQueries.mockClear();
  localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('ReceptionPage accept UX', () => {
  it('auto-fills patientId/payment mode on selection', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-1',
        patientId: 'P-001',
        receptionId: 'R-001',
        name: '山田太郎',
        appointmentTime: '09:00',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
      {
        id: 'row-2',
        patientId: 'P-002',
        receptionId: 'R-002',
        name: '佐藤花子',
        appointmentTime: '10:00',
        department: '内科',
        status: '受付中',
        insurance: '自費',
        source: 'visits',
      },
    ];

    const user = userEvent.setup();
    renderReceptionPage();

    const acceptSection = screen.getByRole('region', { name: '当日受付' });
    const form = within(acceptSection);
    const patientInput = form.getByLabelText('患者ID');

    await waitFor(() => {
      expect(patientInput).toHaveValue('P-001');
    });

    await user.click(form.getByRole('button', { name: '詳細' }));
    const paymentSelect = form.getByLabelText(/保険\/自費/);
    expect(paymentSelect).toHaveValue('insurance');

    const row2 = screen.getByRole('row', { name: /佐藤花子/ });
    await user.click(row2);

    await waitFor(() => {
      expect(patientInput).toHaveValue('P-002');
    });
    expect(paymentSelect).toHaveValue('self');
  });

  it('does not overwrite manual input when auto-fill signature changes', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-1',
        patientId: 'P-010',
        receptionId: 'R-010',
        name: '田中一郎',
        appointmentTime: '09:00',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
      {
        id: 'row-2',
        patientId: 'P-020',
        receptionId: 'R-020',
        name: '田中二郎',
        appointmentTime: '10:00',
        department: '内科',
        status: '受付中',
        insurance: '自費',
        source: 'visits',
      },
    ];

    const user = userEvent.setup();
    renderReceptionPage();

    const acceptSection = screen.getByRole('region', { name: '当日受付' });
    const form = within(acceptSection);
    const patientInput = form.getByLabelText('患者ID');

    await waitFor(() => {
      expect(patientInput).toHaveValue('P-010');
    });
    await user.clear(patientInput);
    await user.type(patientInput, 'MANUAL-999');

    await user.click(form.getByRole('button', { name: '詳細' }));
    const paymentSelect = form.getByLabelText(/保険\/自費/);
    expect(paymentSelect).toHaveValue('insurance');

    const row2 = screen.getByRole('row', { name: /田中二郎/ });
    await user.click(row2);

    expect(patientInput).toHaveValue('MANUAL-999');
    expect(paymentSelect).toHaveValue('self');
  });

  it('enables cancel action only when entry has a receptionId', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-1',
        patientId: 'P-100',
        appointmentId: 'A-100',
        name: '受付IDなし患者',
        appointmentTime: '09:00',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
      {
        id: 'row-2',
        patientId: 'P-200',
        receptionId: 'R-200',
        name: '取消可能患者',
        appointmentTime: '10:00',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
    ];

    const user = userEvent.setup();
    renderReceptionPage();

    const row1 = screen.getByRole('row', { name: /受付IDなし患者/ });
    expect(within(row1).getByRole('button', { name: '受付取消' })).toBeDisabled();

    const row2 = screen.getByRole('row', { name: /取消可能患者/ });
    expect(within(row2).getByRole('button', { name: '受付取消' })).toBeEnabled();
  });

  it('shows Api_Result and duration in the result area after submit', async () => {
    mockAppointmentData.entries = [];
    mockMutationResult = {
      runId: 'RUN-VISIT',
      traceId: 'TRACE-VISIT',
      apiResult: '00',
      apiResultMessage: 'OK',
      requestNumber: '01',
      acceptanceId: 'R-555',
      acceptanceDate: '2026-01-29',
      acceptanceTime: '09:10:00',
      patient: {
        patientId: 'P-555',
        name: '送信患者',
      },
    };

    const user = userEvent.setup();
    renderReceptionPage();

    const acceptSection = screen.getByRole('region', { name: '当日受付' });
    const form = within(acceptSection);

    await user.type(form.getByLabelText('患者ID'), 'P-555');
    const submitButton = form.getByRole('button', { name: '予約外受付' });
    await user.click(submitButton);

    const resultHeading = await screen.findByRole('heading', { name: '送信結果' });
    const resultArea = (resultHeading.closest('[role="status"]') ?? resultHeading.parentElement ?? resultHeading) as HTMLElement;
    const resultScope = within(resultArea);

    expect(resultScope.getByText('Api_Result: 00')).toBeInTheDocument();
    const durationText = resultScope.getByText(/所要時間:/);
    expect(durationText.textContent).toMatch(/所要時間: \d+ ms/);
  });
});

describe('ReceptionPage section collapse defaults', () => {
  it('keeps 会計済み section collapsed by default', () => {
    renderReceptionPage();

    const completedHeading = screen.getByRole('heading', { name: '会計済み' });
    const section = completedHeading.closest('section');
    expect(section).not.toBeNull();
    const toggleButton = within(section as HTMLElement).getByRole('button', { name: '開く' });
    expect(toggleButton).toHaveAttribute('aria-expanded', 'false');
    expect(within(section as HTMLElement).queryByRole('table')).toBeNull();
  });
});

describe('ReceptionPage list and side pane guidance', () => {
  it('highlights selected row and expands details on selection', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-1',
        patientId: 'P-001',
        receptionId: 'R-001',
        name: '山田太郎',
        appointmentTime: '09:00',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
      {
        id: 'row-2',
        patientId: 'P-002',
        receptionId: 'R-002',
        name: '佐藤花子',
        appointmentTime: '10:00',
        department: '外科',
        status: '診療中',
        insurance: '自費',
        source: 'visits',
      },
    ];

    const user = userEvent.setup();
    renderReceptionPage();

    const row1 = screen.getByRole('row', { name: /山田太郎/ });
    const row2 = screen.getByRole('row', { name: /佐藤花子/ });

    expect(row1).toHaveClass('reception-table__row--selected');
    expect(row2).not.toHaveClass('reception-table__row--selected');

    await user.click(row2);

    expect(row1).not.toHaveClass('reception-table__row--selected');
    expect(row2).toHaveClass('reception-table__row--selected');
  });

  it('shows patient search and accept form in the right column; medical record preview opens in a modal (debug panels hidden by default)', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-3',
        patientId: 'P-010',
        receptionId: 'R-010',
        appointmentId: 'A-010',
        name: '集約患者',
        kana: 'シュウヤク',
        appointmentTime: '11:30',
        department: '内科',
        physician: 'Dr. Test',
        status: '会計待ち',
        insurance: '保険',
        source: 'visits',
      },
    ];
    const user = userEvent.setup();
    renderReceptionPage();

    expect(screen.getByRole('region', { name: '患者検索' })).toBeInTheDocument();

    const acceptSection = screen.getByRole('region', { name: '当日受付' });
    await waitFor(() => {
      expect(within(acceptSection).getByLabelText('患者ID')).toHaveValue('P-010');
    });

    // Preview medical records in a modal (no new tab).
    const row = screen.getByRole('row', { name: /集約患者/ });
    await user.click(within(row).getByRole('button', { name: '過去カルテ' }));
    const dialog = (await screen.findByRole('dialog', { name: /過去カルテ/ })) as HTMLElement;
    expect(within(dialog).getByText(/患者ID:\s*P-010/)).toBeInTheDocument();
    await waitFor(() => {
      expect(within(dialog).getByText('過去カルテがありません。')).toBeInTheDocument();
    });
    await user.click(within(dialog).getByRole('button', { name: '閉じる' }));
    expect(screen.queryByRole('dialog', { name: /過去カルテ/ })).toBeNull();

    // Debug panels should not be visible by default.
    expect(screen.queryByTestId('order-console')).toBeNull();
    expect(screen.queryByTestId('reception-audit')).toBeNull();
  });

  it('removes direct chart-open form from 当日受付 header', () => {
    renderReceptionPage();
    const acceptSection = screen.getByRole('region', { name: '当日受付' });
    expect(within(acceptSection).queryByLabelText('患者IDでカルテを開く')).toBeNull();
  });

  it('shows patient-search details only for selected result', async () => {
    mockMutationQueue.push({
      patients: [
        {
          patientId: 'P-101',
          name: '検索患者一',
          kana: 'ケンサクカンジャイチ',
          birthDate: '1980-01-01',
          sex: 'M',
          insurance: '保険',
          lastVisit: '2026-02-10',
        },
        {
          patientId: 'P-102',
          name: '検索患者二',
          kana: 'ケンサクカンジャニ',
          birthDate: '1990-02-02',
          sex: 'F',
          insurance: '自費',
          lastVisit: '2026-02-12',
        },
      ],
      recordsReturned: 2,
      runId: 'RUN-SEARCH',
    });

    const user = userEvent.setup();
    renderReceptionPage();

    const patientSearch = screen.getByRole('region', { name: '患者検索' });
    await user.type(within(patientSearch).getByLabelText('患者ID'), 'P-1');
    await user.click(within(patientSearch).getByRole('button', { name: '検索' }));

    const resultPanel = await screen.findByRole('region', { name: '患者検索結果パネル' });
    await waitFor(() => {
      expect(within(resultPanel).getByText('検索患者一')).toBeInTheDocument();
    });

    expect(within(resultPanel).queryByText('生年月日: 1980-01-01')).toBeNull();
    expect(within(resultPanel).queryByRole('button', { name: 'カルテを開く' })).toBeNull();

    await user.click(within(resultPanel).getByRole('button', { name: /検索患者一/ }));

    expect(within(resultPanel).getByText('生年月日: 1980-01-01')).toBeInTheDocument();
    expect(within(resultPanel).getByRole('button', { name: 'カルテを開く' })).toBeInTheDocument();
    expect(within(resultPanel).queryByText('生年月日: 1990-02-02')).toBeNull();
  });

  it('paginates patient-search results when the hit count is large', async () => {
    mockMutationQueue.push({
      patients: Array.from({ length: 55 }, (_, index) => {
        const suffix = String(index + 1).padStart(3, '0');
        return {
          patientId: `P-${suffix}`,
          name: `ページ患者${suffix}`,
        };
      }),
      recordsReturned: 55,
      runId: 'RUN-SEARCH-PAGE',
    });

    const user = userEvent.setup();
    renderReceptionPage();

    const patientSearch = screen.getByRole('region', { name: '患者検索' });
    await user.type(within(patientSearch).getByLabelText('患者ID'), 'P-');
    await user.click(within(patientSearch).getByRole('button', { name: '検索' }));

    const resultPanel = await screen.findByRole('region', { name: '患者検索結果パネル' });
    await waitFor(() => {
      expect(within(resultPanel).getByText('ページ患者001')).toBeInTheDocument();
      expect(within(resultPanel).getByRole('navigation', { name: '検索結果ページ' })).toBeInTheDocument();
    });

    expect(within(resultPanel).getByText('ページ患者050')).toBeInTheDocument();
    expect(within(resultPanel).queryByText('ページ患者051')).toBeNull();
    expect(within(resultPanel).getByText('1 / 2')).toBeInTheDocument();

    const pager = within(resultPanel).getByRole('navigation', { name: '検索結果ページ' });
    await user.click(within(pager).getByRole('button', { name: '次へ' }));

    await waitFor(() => {
      expect(within(resultPanel).getByText('ページ患者051')).toBeInTheDocument();
    });
    expect(within(resultPanel).queryByText('ページ患者001')).toBeNull();
    expect(within(resultPanel).getByText('2 / 2')).toBeInTheDocument();
  });

  it('closes the floating patient-search result panel', async () => {
    mockMutationQueue.push({
      patients: [
        {
          patientId: 'P-301',
          name: 'クローズ確認患者',
        },
      ],
      recordsReturned: 1,
      runId: 'RUN-SEARCH-CLOSE',
    });

    const user = userEvent.setup();
    renderReceptionPage();

    const patientSearch = screen.getByRole('region', { name: '患者検索' });
    await user.type(within(patientSearch).getByLabelText('患者ID'), 'P-3');
    await user.click(within(patientSearch).getByRole('button', { name: '検索' }));

    const resultPanel = await screen.findByRole('region', { name: '患者検索結果パネル' });
    expect(within(resultPanel).getByText('クローズ確認患者')).toBeInTheDocument();

    await user.click(within(resultPanel).getByRole('button', { name: '閉じる' }));

    await waitFor(() => {
      expect(screen.queryByRole('region', { name: '患者検索結果パネル' })).toBeNull();
    });
  });
});

describe('ReceptionPage status/date/card action UX', () => {
  it('defaults date filter to visitDate from URL (non-charts navigation)', () => {
    mockSearchParams = new URLSearchParams('visitDate=2026-02-03');
    renderReceptionPage();
    expect(screen.getByText('日付: 2026-02-03')).toBeInTheDocument();
  });

  it('defaults date filter to today when opened from charts (visitDate is only a hint)', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-02-13T00:00:00Z'));
    try {
      mockSearchParams = new URLSearchParams('from=charts&visitDate=2026-02-03');
      renderReceptionPage();
      expect(screen.getByText('日付: 2026-02-13')).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows row action buttons for charts/history/cancel', () => {
    mockAppointmentData.entries = [
      {
        id: 'row-card-1',
        patientId: 'P-301',
        receptionId: 'R-301',
        name: 'カード患者',
        appointmentTime: '09:30',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
    ];
    renderReceptionPage();

    const row = screen.getByRole('row', { name: /カード患者/ });
    expect(within(row).getByRole('button', { name: 'カルテを開く' })).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: '過去カルテ' })).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: '受付取消' })).toBeInTheDocument();
  });

  it('moves 会計待ち entries under 診察終了 tab filtering', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-tab-1',
        patientId: 'P-401',
        receptionId: 'R-401',
        name: '受付患者',
        appointmentTime: '08:30',
        department: '内科',
        status: '受付中',
        insurance: '保険',
        source: 'visits',
      },
      {
        id: 'row-tab-2',
        patientId: 'P-402',
        receptionId: 'R-402',
        name: '診察後患者',
        appointmentTime: '10:15',
        department: '外科',
        status: '会計待ち',
        insurance: '保険',
        source: 'visits',
      },
    ];

    renderReceptionPage();
    const board = screen.getByRole('region', { name: 'ステータス別患者一覧' });
    const afterColumn = within(board).getByRole('region', { name: '診察終了リスト' });
    expect(within(afterColumn).getByText('診察後患者')).toBeInTheDocument();
    expect(within(afterColumn).queryByText('受付患者')).toBeNull();
  });

  it('shows 会計送信 button on 診察終了 rows and moves them to 会計済み on success', async () => {
    mockAppointmentData.entries = [
      {
        id: 'row-claim-1',
        patientId: 'P-501',
        receptionId: 'R-501',
        name: '診察終了患者',
        appointmentTime: '11:00',
        department: '01 内科',
        status: '会計待ち',
        insurance: '保険',
        source: 'visits',
      },
    ];

    const user = userEvent.setup();
    renderReceptionPage();

    const afterSection = screen.getByRole('region', { name: '診察終了リスト' });
    const row = within(afterSection).getByRole('row', { name: /診察終了患者/ });
    await user.click(within(row).getByRole('button', { name: '会計送信' }));

    await waitFor(() => expect(vi.mocked(postOrcaMedicalModV2Xml)).toHaveBeenCalled());

    const completedColumn = screen.getByRole('region', { name: /会計済み/ });
    const toggle = within(completedColumn).getByRole('button', { name: '開く' });
    await user.click(toggle);
    const completedRow = await within(completedColumn).findByRole('row', { name: /診察終了患者/ });
    expect(completedRow).toBeInTheDocument();
  });
});

describe('ReceptionPage realtime sync', () => {
  it('invalidates appointment queries when realtime update arrives', async () => {
    class MockEventSource {
      static instances: MockEventSource[] = [];
      onopen: ((this: EventSource, ev: Event) => unknown) | null = null;
      onmessage: ((this: EventSource, ev: MessageEvent<string>) => unknown) | null = null;
      onerror: ((this: EventSource, ev: Event) => unknown) | null = null;
      private listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>();

      constructor(_url: string, _init?: EventSourceInit) {
        MockEventSource.instances.push(this);
      }

      addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
        if (typeof listener !== 'function') return;
        const list = this.listeners.get(type) ?? [];
        list.push(listener as (event: MessageEvent<string>) => void);
        this.listeners.set(type, list);
      }

      removeEventListener(type: string, listener: EventListenerOrEventListenerObject) {
        if (typeof listener !== 'function') return;
        const list = this.listeners.get(type);
        if (!list) return;
        this.listeners.set(
          type,
          list.filter((candidate) => candidate !== listener),
        );
      }

      close() {
        // no-op
      }

      emit(type: string, data: string) {
        const event = new MessageEvent<string>(type, { data, lastEventId: '11' });
        const list = this.listeners.get(type) ?? [];
        list.forEach((listener) => listener(event));
      }
    }

    vi.stubGlobal('EventSource', MockEventSource as unknown as typeof EventSource);

    renderReceptionPage();

    await waitFor(() => expect(MockEventSource.instances.length).toBeGreaterThan(0));
    const source = MockEventSource.instances.at(-1)!;
    source.emit(
      'reception.updated',
      JSON.stringify({
        type: 'reception.updated',
        patientId: 'P-001',
      }),
    );

    await waitFor(() =>
      expect(mockInvalidateQueries).toHaveBeenCalledWith({
        queryKey: ['outpatient-appointments'],
      }),
    );
    expect(mockInvalidateQueries).toHaveBeenCalledWith({
      queryKey: ['orca-queue'],
    });
  });
});
