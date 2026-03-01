import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import { fetchContraindicationCheckXml } from '../contraindicationCheckApi';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';
import type { OrderBundle } from '../orderBundleApi';

vi.mock('../orderBundleApi', async () => ({
  fetchOrderBundles: vi.fn().mockResolvedValue({
    ok: true,
    bundles: [],
    patientId: 'P-1',
  }),
  mutateOrderBundles: vi.fn().mockResolvedValue({ ok: true, runId: 'RUN-ORDER' }),
}));

vi.mock('../orderMasterSearchApi', async () => ({
  fetchOrderMasterSearch: vi.fn(),
}));

vi.mock('../contraindicationCheckApi', async () => ({
  buildContraindicationCheckRequestXml: vi.fn().mockReturnValue('<data />'),
  fetchContraindicationCheckXml: vi.fn(),
}));

vi.mock('../stampApi', async () => ({
  fetchUserProfile: vi.fn().mockResolvedValue({ ok: true, id: 1, userId: 'facility:doctor' }),
  fetchStampTree: vi.fn().mockResolvedValue({
    ok: true,
    trees: [
      {
        treeName: '個人',
        entity: 'medOrder',
        stampList: [
          {
            name: '降圧セット',
            entity: 'medOrder',
            stampId: 'STAMP-1',
          },
        ],
      },
    ],
  }),
  fetchStampDetail: vi.fn(),
}));

const renderWithClient = (ui: ReactElement) => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
};

const baseProps = {
  patientId: 'P-1',
  entity: 'medOrder',
  title: '処方編集',
  bundleLabel: 'RP名',
  itemQuantityLabel: '用量',
  meta: {
    runId: 'RUN-ORDER',
    cacheHit: false,
    missingMaster: false,
    fallbackUsed: false,
    dataSourceTransition: 'server' as const,
  },
};

const buildHistoryBundle = (overrides: Partial<OrderBundle> = {}): OrderBundle => ({
  documentId: 100,
  moduleId: 200,
  bundleName: '降圧薬セット',
  admin: '1日1回 朝',
  bundleNumber: '7',
  started: '2025-12-01',
  items: [{ code: 'A100', name: 'アムロジピン', quantity: '1', unit: '錠' }],
  ...overrides,
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  localStorage.clear();
});

describe('OrderBundleEditPanel contraindication warning', () => {
  it('禁忌チェック警告と症状情報を表示する', async () => {
    const user = userEvent.setup();
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    sessionStorage.setItem('devFacilityId', 'facility');
    sessionStorage.setItem('devUserId', 'doctor');

    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({
      ok: true,
      items: [],
      totalCount: 0,
    });

    vi.mocked(fetchContraindicationCheckXml).mockResolvedValue({
      ok: true,
      status: 200,
      rawXml: '<data />',
      apiResult: 'E20',
      apiResultMessage: 'warning',
      results: [
        {
          medicationCode: 'A100',
          medicationName: 'アムロジピン',
          medicalResult: '0',
          medicalResultMessage: 'OK',
          warnings: [
            {
              contraCode: 'C001',
              contraName: 'ContraSample',
              contextClass: '1',
            },
          ],
        },
      ],
      symptomInfo: [{ code: 'S001', content: 'Headache' }],
      missingTags: [],
    } as any);

    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        historyCopyRequest={{ requestId: 'history-copy-1', bundle: buildHistoryBundle() }}
        onHistoryCopyConsumed={vi.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByDisplayValue('降圧薬セット')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /保存して追加/ }));

    await waitFor(() => expect(fetchContraindicationCheckXml).toHaveBeenCalled());

    expect(screen.getByText(/禁忌チェックで警告があります/)).toBeInTheDocument();
    expect(screen.getAllByText(/アムロジピン × ContraSample/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/症状: S001 Headache/).length).toBeGreaterThan(0);
  });

  it('症状情報がない場合は症状表示を出さない', async () => {
    const user = userEvent.setup();
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    sessionStorage.setItem('devFacilityId', 'facility');
    sessionStorage.setItem('devUserId', 'doctor');

    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({
      ok: true,
      items: [],
      totalCount: 0,
    });

    vi.mocked(fetchContraindicationCheckXml).mockResolvedValue({
      ok: true,
      status: 200,
      rawXml: '<data />',
      apiResult: 'E20',
      apiResultMessage: 'warning',
      results: [
        {
          medicationCode: 'A200',
          medicationName: 'ロサルタン',
          medicalResult: '0',
          medicalResultMessage: 'OK',
          warnings: [
            {
              contraCode: 'C010',
              contraName: 'ContraOther',
              contextClass: '1',
            },
          ],
        },
      ],
      symptomInfo: [],
      missingTags: [],
    } as any);

    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        historyCopyRequest={{
          requestId: 'history-copy-2',
          bundle: buildHistoryBundle({
            bundleName: 'ARBセット',
            items: [{ code: 'A200', name: 'ロサルタン', quantity: '1', unit: '錠' }],
          }),
        }}
        onHistoryCopyConsumed={vi.fn()}
      />,
    );

    await waitFor(() => expect(screen.getByDisplayValue('ARBセット')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /保存して追加/ }));

    await waitFor(() => expect(fetchContraindicationCheckXml).toHaveBeenCalled());

    expect(screen.getByText(/禁忌チェックで警告があります/)).toBeInTheDocument();
    expect(screen.getAllByText(/ロサルタン × ContraOther/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/症状:/)).not.toBeInTheDocument();
  });
});
