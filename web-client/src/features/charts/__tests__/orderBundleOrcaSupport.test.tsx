import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import { fetchOrcaOrderInputSetDetail, fetchOrcaOrderInputSets } from '../orcaOrderInputSetApi';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';

vi.mock('../orderMasterSearchApi', async () => ({
  fetchOrderMasterSearch: vi.fn(),
}));

vi.mock('../orcaOrderInputSetApi', () => ({
  fetchOrcaOrderInputSets: vi.fn(),
  fetchOrcaOrderInputSetDetail: vi.fn(),
}));

const baseProps = {
  patientId: 'P-ORDER-001',
  entity: 'generalOrder',
  title: '一般オーダー',
  bundleLabel: 'オーダー名',
  itemQuantityLabel: '数量',
  meta: {
    runId: 'RUN-ORDER-ORCA-TEST',
    cacheHit: false,
    missingMaster: false,
    fallbackUsed: false,
    dataSourceTransition: 'server' as const,
    visitDate: '2026-03-09',
  },
  variant: 'embedded' as const,
  bundlesOverride: [] as [],
};

const renderPanel = (props?: Partial<typeof baseProps>) => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <OrderBundleEditPanel {...baseProps} {...props} />
    </QueryClientProvider>,
  );
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('OrderBundleEditPanel ORCA support', () => {
  it('点数検索（詳細）は値保持と invalid range を扱う', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });

    renderPanel();

    await user.click(screen.getByRole('button', { name: '開く' }));
    await user.type(screen.getByLabelText('点数From'), '20');
    await user.type(screen.getByLabelText('点数To'), '40');

    expect(screen.getByText('点数: 20〜40')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '閉じる' }));
    await user.click(screen.getByRole('button', { name: '開く' }));

    expect(screen.getByLabelText('点数From')).toHaveValue('20');
    expect(screen.getByLabelText('点数To')).toHaveValue('40');

    await user.clear(screen.getByLabelText('点数To'));
    await user.type(screen.getByLabelText('点数To'), '10');

    expect(screen.getByText('点数From は 点数To 以下で入力してください。')).toBeInTheDocument();
  });

  it('空フォームでは ORCA診療セットを即時反映する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });
    vi.mocked(fetchOrcaOrderInputSets).mockResolvedValue({
      ok: true,
      status: 200,
      totalCount: 1,
      items: [{ setCode: 'P02001', name: '処置セット', entity: 'generalOrder', itemCount: 2 }],
    });
    vi.mocked(fetchOrcaOrderInputSetDetail).mockResolvedValue({
      ok: true,
      status: 200,
      setCode: 'P02001',
      bundle: {
        entity: 'generalOrder',
        bundleName: '創傷処置セット',
        bundleNumber: '1',
        admin: '適宜',
        memo: '消毒後に実施',
        started: '2026-03-09',
        bodyPart: { code: '002001', name: '膝関節', quantity: '1', unit: '部位', memo: '' },
        items: [
          { code: '140000610', name: '創傷処置（１００ｃｍ２未満）', quantity: '1', unit: '回', memo: '' },
          { code: 'M001', name: '処置材料A', quantity: '1', unit: '個', memo: '' },
        ],
      },
    });

    renderPanel();

    await user.type(screen.getByPlaceholderText('診療セット名またはコード'), '処置');
    await user.click(screen.getByRole('button', { name: 'セット検索' }));
    await user.click(await screen.findByRole('button', { name: /P02001.*処置セット.*反映/ }));

    expect(screen.queryByText('診療セットを反映しますか？')).toBeNull();
    expect(screen.getByLabelText('オーダー名')).toHaveValue('創傷処置セット');
    expect(screen.getByLabelText('部位', { selector: 'input' })).toHaveValue('膝関節');
  });

  it('非空フォームでは confirm 後に ORCA診療セットを反映する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });
    vi.mocked(fetchOrcaOrderInputSets).mockResolvedValue({
      ok: true,
      status: 200,
      totalCount: 1,
      items: [{ setCode: 'P02001', name: '処置セット', entity: 'generalOrder', itemCount: 2 }],
    });
    vi.mocked(fetchOrcaOrderInputSetDetail).mockResolvedValue({
      ok: true,
      status: 200,
      setCode: 'P02001',
      bundle: {
        entity: 'generalOrder',
        bundleName: '創傷処置セット',
        bundleNumber: '1',
        admin: '適宜',
        started: '2026-03-09',
        items: [{ code: '140000610', name: '創傷処置（１００ｃｍ２未満）', quantity: '1', unit: '回', memo: '' }],
      },
    });

    renderPanel();

    await user.type(screen.getByLabelText('オーダー名'), '既存内容');
    await user.type(screen.getByPlaceholderText('診療セット名またはコード'), '処置');
    await user.click(screen.getByRole('button', { name: 'セット検索' }));
    await user.click(await screen.findByRole('button', { name: /P02001.*処置セット.*反映/ }));

    expect(await screen.findByText('診療セットを反映しますか？')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'キャンセル' }));
    expect(screen.getByLabelText('オーダー名')).toHaveValue('既存内容');

    await user.click(screen.getByRole('button', { name: /P02001.*処置セット.*反映/ }));
    await user.click(await screen.findByRole('button', { name: '反映する' }));

    expect(screen.getByLabelText('オーダー名')).toHaveValue('創傷処置セット');
  });

  it('entity 不一致の診療セットは warning で中断する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });
    vi.mocked(fetchOrcaOrderInputSets).mockResolvedValue({
      ok: true,
      status: 200,
      totalCount: 1,
      items: [{ setCode: 'P02001', name: '処置セット', entity: 'generalOrder', itemCount: 2 }],
    });
    vi.mocked(fetchOrcaOrderInputSetDetail).mockResolvedValue({
      ok: true,
      status: 200,
      setCode: 'P02001',
      bundle: {
        entity: 'radiologyOrder',
        bundleName: '画像セット',
        bundleNumber: '1',
        items: [{ code: '170017510', name: 'ＣＴ撮影', quantity: '1', unit: '回', memo: '' }],
      },
    });

    renderPanel();

    await user.type(screen.getByPlaceholderText('診療セット名またはコード'), '処置');
    await user.click(screen.getByRole('button', { name: 'セット検索' }));
    await user.click(await screen.findByRole('button', { name: /P02001.*処置セット.*反映/ }));

    expect(await screen.findByText('entity が一致しないため診療セットを反映できません。')).toBeInTheDocument();
    expect(screen.getByLabelText('オーダー名')).toHaveValue('');
  });
});
