import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { PrescriptionOrderEditorPanel } from '../PrescriptionOrderEditorPanel';
import { fetchOrcaGenericPrice } from '../orcaGenericPriceApi';
import { fetchOrcaOrderInputSetDetail, fetchOrcaOrderInputSets } from '../orcaOrderInputSetApi';
import { checkOrcaOrderInteractions } from '../orcaOrderInteractionApi';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';
import { savePrescriptionOrder } from '../prescriptionOrderApi';

vi.mock('../orderMasterSearchApi', async () => {
  const actual = await vi.importActual<typeof import('../orderMasterSearchApi')>('../orderMasterSearchApi');
  return {
    ...actual,
    fetchOrderMasterSearch: vi.fn(),
  };
});

vi.mock('../orcaGenericPriceApi', () => ({
  fetchOrcaGenericPrice: vi.fn(),
}));

vi.mock('../orcaOrderInputSetApi', () => ({
  fetchOrcaOrderInputSets: vi.fn(),
  fetchOrcaOrderInputSetDetail: vi.fn(),
}));

vi.mock('../orcaOrderInteractionApi', () => ({
  checkOrcaOrderInteractions: vi.fn(),
}));

vi.mock('../prescriptionOrderApi', async () => {
  const actual = await vi.importActual<typeof import('../prescriptionOrderApi')>('../prescriptionOrderApi');
  return {
    ...actual,
    fetchPrescriptionOrder: vi.fn().mockResolvedValue({ ok: true, sourceBundles: [] }),
    savePrescriptionOrder: vi.fn().mockResolvedValue({ ok: true }),
  };
});

const baseMeta = {
  runId: 'RUN-RX-ORCA-TEST',
  cacheHit: true,
  missingMaster: false,
  fallbackUsed: false,
  dataSourceTransition: 'server' as const,
  visitDate: '2026-03-09',
};

const renderPanel = (bundlesOverride = [
  {
    entity: 'medOrder',
    bundleName: '既存RP',
    bundleNumber: '7',
    admin: '1日1回 朝食後',
    classCode: '212',
    started: '2026-03-09',
    items: [{ code: 'A100', name: '既存薬', quantity: '1', unit: '錠', memo: '' }],
  },
]) => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <PrescriptionOrderEditorPanel patientId="P-RX-001" meta={baseMeta} active bundlesOverride={bundlesOverride} />
    </QueryClientProvider>,
  );
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('PrescriptionOrderEditorPanel ORCA support', () => {
  it('最低薬価を候補一覧と選択中 helper line に表示する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockImplementation(async ({ type, keyword }) => {
      if (type === 'youhou') return { ok: true, items: [], totalCount: 0 };
      if (type === 'drug' && keyword === 'アムロ') {
        return {
          ok: true,
          items: [{ type: 'drug', code: '620000001', name: 'アムロジピン', unit: '錠' }],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });
    vi.mocked(fetchOrcaGenericPrice).mockResolvedValue({
      ok: true,
      status: 200,
      item: { code: '620000001', minPrice: 12.34, unit: '錠' },
    });

    renderPanel();

    await user.type(screen.getByLabelText('キーワード'), 'アムロ');

    const candidateRow = await screen.findByRole('button', { name: /620000001/ });
    await waitFor(() => {
      expect(fetchOrcaGenericPrice).toHaveBeenCalledWith({ srycd: '620000001', effective: '2026-03-09' });
    });
    expect(within(candidateRow).getByText('12.34')).toBeInTheDocument();

    await user.click(candidateRow);

    expect(await screen.findByText('最低薬価: 12.34')).toBeInTheDocument();
  });

  it('ORCA入力セットを末尾 RP に追加し既存 RP を保持する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });
    vi.mocked(fetchOrcaOrderInputSets).mockResolvedValue({
      ok: true,
      status: 200,
      totalCount: 1,
      items: [{ setCode: 'P01001', name: '降圧セット', entity: 'medOrder', itemCount: 2 }],
    });
    vi.mocked(fetchOrcaOrderInputSetDetail).mockResolvedValue({
      ok: true,
      status: 200,
      setCode: 'P01001',
      bundle: {
        entity: 'medOrder',
        bundleName: '降圧セット',
        bundleNumber: '14',
        admin: '1日1回 朝食後',
        started: '2026-03-09',
        items: [{ code: '620000001', name: 'アムロジピン', quantity: '1', unit: '錠', memo: '' }],
      },
    });

    renderPanel();

    await user.type(screen.getByPlaceholderText('入力セット名またはコード'), '降圧');
    await user.click(screen.getByRole('button', { name: '入力セット検索' }));
    await user.click(await screen.findByRole('button', { name: /P01001.*降圧セット.*RPへ反映/ }));

    const rpPane = screen.getByLabelText('RP一覧');
    expect(within(rpPane).getByText('2件')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /RP1: 既存RP/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /RP2: 降圧セット/ })).toBeInTheDocument();
  });

  it('相互作用ありでは確認 dialog を出し、続行時だけ保存する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });
    vi.mocked(checkOrcaOrderInteractions).mockResolvedValue({
      ok: true,
      status: 200,
      totalCount: 1,
      pairs: [{ code1: '620000001', code2: '620000003', interactionName: '併用注意', message: '相互作用が検出されました' }],
    });

    renderPanel([
      {
        entity: 'medOrder',
        bundleName: '既存RP',
        bundleNumber: '7',
        admin: '1日1回 朝食後',
        classCode: '212',
        started: '2026-03-09',
        items: [
          { code: '620000001', name: '薬A', quantity: '1', unit: '錠', memo: '' },
          { code: '620000001', name: '薬A重複', quantity: '1', unit: '錠', memo: '' },
          { code: '620000003', name: '薬B', quantity: '1', unit: '錠', memo: '' },
        ],
      },
    ]);

    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(checkOrcaOrderInteractions).toHaveBeenCalledWith({ codes: ['620000001', '620000003'] });
    });
    expect(await screen.findByText('相互作用チェックの警告')).toBeInTheDocument();
    expect(savePrescriptionOrder).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: '編集に戻る' }));
    expect(savePrescriptionOrder).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: '保存' }));
    await user.click(await screen.findByRole('button', { name: '今回だけ無視して保存' }));

    await waitFor(() => {
      expect(savePrescriptionOrder).toHaveBeenCalledTimes(1);
    });
  });

  it('相互作用 API エラー時は warning 後に保存を継続する', async () => {
    const user = userEvent.setup();
    vi.mocked(fetchOrderMasterSearch).mockResolvedValue({ ok: true, items: [], totalCount: 0 });
    vi.mocked(checkOrcaOrderInteractions).mockRejectedValue(new Error('interaction failed'));

    renderPanel([
      {
        entity: 'medOrder',
        bundleName: '既存RP',
        bundleNumber: '7',
        admin: '1日1回 朝食後',
        classCode: '212',
        started: '2026-03-09',
        items: [
          { code: '620000001', name: '薬A', quantity: '1', unit: '錠', memo: '' },
          { code: '620000003', name: '薬B', quantity: '1', unit: '錠', memo: '' },
        ],
      },
    ]);

    await user.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => {
      expect(savePrescriptionOrder).toHaveBeenCalledTimes(1);
    });
    expect(checkOrcaOrderInteractions).toHaveBeenCalledWith(
      expect.objectContaining({ codes: ['620000001', '620000003'] }),
    );
  });
});
