import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { PrescriptionOrderEditorPanel } from '../PrescriptionOrderEditorPanel';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';

vi.mock('../orderMasterSearchApi', async () => {
  const actual = await vi.importActual<typeof import('../orderMasterSearchApi')>('../orderMasterSearchApi');
  return {
    ...actual,
    fetchOrderMasterSearch: vi.fn(),
  };
});

const baseMeta = {
  runId: 'RUN-RX-PANEL-TEST',
  cacheHit: true,
  missingMaster: false,
  fallbackUsed: false,
  dataSourceTransition: 'server' as const,
  visitDate: '2026-02-26',
};

const renderPanel = () => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <PrescriptionOrderEditorPanel
        patientId="P-TEST-001"
        meta={baseMeta}
        active
        bundlesOverride={[
          {
            entity: 'medOrder',
            bundleName: '既存RP',
            bundleNumber: '1',
            admin: '1日1回',
            classCode: '212',
            started: '2026-02-26',
            items: [{ name: 'A100 アムロジピン', quantity: '1', unit: '錠', memo: '' }],
          },
        ]}
      />
    </QueryClientProvider>,
  );
};

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('PrescriptionOrderEditorPanel', () => {
  it('3文字以上は自動検索、2文字以下は手動検索ボタンで候補表示する', async () => {
    const user = userEvent.setup();
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'youhou') return { ok: true, items: [], totalCount: 0 };
      if (type === 'drug') {
        return {
          ok: true,
          items: [{ type: 'drug', code: 'A100', name: `${keyword}候補`, unit: '錠' }],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    renderPanel();

    const keywordInput = screen.getByLabelText('キーワード');
    await user.clear(keywordInput);
    await user.type(keywordInput, 'アム');
    const manualSearchButton = await screen.findByRole('button', { name: '検索（2文字以下は明示実行）' });
    expect(manualSearchButton).toBeInTheDocument();

    await user.click(manualSearchButton);
    await waitFor(() => {
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'drug', keyword: 'アム' }));
    });
    expect(screen.getByRole('button', { name: /アム候補/ })).toBeInTheDocument();

    await user.clear(keywordInput);
    await user.type(keywordInput, 'アムロ');
    await waitFor(() => {
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'drug', keyword: 'アムロ' }));
    });
    expect(screen.getByRole('button', { name: /アムロ候補/ })).toBeInTheDocument();
  });

  it('+RP / +薬剤 / 全クリアでRP集合を操作できる', async () => {
    const user = userEvent.setup();
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockResolvedValue({ ok: true, items: [], totalCount: 0 });

    renderPanel();

    const rpPane = screen.getByLabelText('RP一覧');
    expect(within(rpPane).getByText('1件')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '+RP' }));
    expect(within(rpPane).getByText('2件')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '+薬剤' }));
    expect(screen.getAllByPlaceholderText('薬剤名')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: '全クリア' }));
    expect(within(rpPane).getByText('1件')).toBeInTheDocument();
  });

  it('請求用コメントは Shift+Enter で確定し、個別削除できる', async () => {
    const user = userEvent.setup();
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockResolvedValue({ ok: true, items: [], totalCount: 0 });

    renderPanel();

    const claimInput = screen.getByPlaceholderText('請求用コメント（Shift+Enterで確定）');
    await user.type(claimInput, '患者希望コメント');
    await user.keyboard('{Shift>}{Enter}{/Shift}');

    const chip = screen.getByRole('button', { name: '患者希望コメント' });
    expect(chip).toBeInTheDocument();

    await user.click(chip);
    expect(screen.queryByRole('button', { name: '患者希望コメント' })).toBeNull();
  });

  it('日数一括変更は内服/頓服RPのみに反映し、外用RPには反映しない', async () => {
    const user = userEvent.setup();
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockResolvedValue({ ok: true, items: [], totalCount: 0 });

    renderPanel();

    await user.click(screen.getByRole('button', { name: '+RP' }));
    await user.click(screen.getByRole('button', { name: /RP2:/ }));
    await user.click(screen.getByRole('button', { name: '外用' }));

    const daysInput = screen.getByLabelText('日数');
    await user.clear(daysInput);
    await user.type(daysInput, '3');

    const bulkInput = screen.getByLabelText('日数一括変更（内服/頓服のみ）');
    await user.type(bulkInput, '7');
    await user.click(screen.getByRole('button', { name: '一括反映' }));

    await user.click(screen.getByRole('button', { name: /RP1:/ }));
    expect((screen.getByLabelText('日数') as HTMLInputElement).value).toBe('7');

    await user.click(screen.getByRole('button', { name: /RP2:/ }));
    expect((screen.getByLabelText('日数') as HTMLInputElement).value).toBe('3');
  });
});
