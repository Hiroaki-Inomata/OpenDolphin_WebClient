import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import { fetchOrderBundles, mutateOrderBundles } from '../orderBundleApi';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';

vi.mock('../orderBundleApi', async () => ({
  fetchOrderBundles: vi.fn().mockResolvedValue({
    ok: true,
    bundles: [],
    patientId: 'P-1',
  }),
  mutateOrderBundles: vi.fn(),
}));

vi.mock('../orderMasterSearchApi', async () => ({
  fetchOrderMasterSearch: vi.fn(),
}));

vi.mock('../stampApi', async () => ({
  fetchUserProfile: vi.fn().mockResolvedValue({ ok: true, id: 1, userId: 'facility:doctor' }),
  fetchStampTree: vi.fn().mockResolvedValue({ ok: true, trees: [] }),
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

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  localStorage.clear();
});

describe('OrderBundleEditPanel bundle number UI', () => {
  const mockUsageMaster = () => {
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type }) => {
      if (type === 'youhou') {
        return {
          ok: true,
          items: [{ type: 'youhou', name: '1日1回' }],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });
  };

  const selectUsage = async (user: ReturnType<typeof userEvent.setup>) => {
    const usageSelect = screen.getByLabelText('用法') as HTMLSelectElement;
    let optionValue = '';
    await waitFor(() => {
      const targetOption = Array.from(usageSelect.options).find((option) => option.text === '1日1回');
      expect(targetOption).toBeDefined();
      optionValue = targetOption?.value ?? '';
      expect(optionValue).not.toBe('');
    });
    await user.selectOptions(usageSelect, optionValue);
    expect(usageSelect.selectedOptions[0]?.text).toBe('1日1回');
  };

  it('用法入力後に日数入力が編集可能になる', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const bundleNumberInput = screen.getByLabelText('日数');
    expect(bundleNumberInput).toBeDisabled();

    await selectUsage(user);
    expect(bundleNumberInput).toBeEnabled();
  });

  it('頓用/臨時では回数ラベルと説明文になる', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await selectUsage(user);
    expect(screen.getByLabelText('日数')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '頓用' }));

    const bundleNumberInput = screen.getByLabelText('回数') as HTMLInputElement;
    expect(bundleNumberInput).toBeInTheDocument();
    expect(bundleNumberInput.placeholder).toBe('例: 1');
    expect(screen.getByText('頓用/臨時は回数として扱われます。')).toBeInTheDocument();
  });

  it('保存時に処方区分メタが送信される', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    vi.mocked(mutateOrderBundles).mockResolvedValueOnce({ ok: true, runId: 'RUN-ORDER' });
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await user.type(screen.getByPlaceholderText('薬剤名'), 'アムロジピン');
    await selectUsage(user);
    await user.click(screen.getByRole('button', { name: '院内' }));
    await user.click(screen.getByRole('button', { name: '頓用' }));
    await user.clear(screen.getByLabelText('回数'));
    await user.type(screen.getByLabelText('回数'), '3');

    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    const mutateMock = vi.mocked(mutateOrderBundles);
    await waitFor(() => expect(mutateMock).toHaveBeenCalled());
    const payload = mutateMock.mock.calls[0]?.[0];
    const operation = payload?.operations?.[0];
    expect(operation?.classCode).toBe('221');
    expect(operation?.classCodeSystem).toBe('Claim007');
    expect(operation?.className).toBe('頓服薬剤（院内処方）');
  });

  it('保存後の再編集で日数/回数と値が復元される', async () => {
    const user = userEvent.setup();
    const fetchMock = vi.mocked(fetchOrderBundles);
    fetchMock.mockResolvedValueOnce({
      ok: true,
      patientId: 'P-1',
      bundles: [
        {
          documentId: 10,
          moduleId: 20,
          entity: 'medOrder',
          bundleName: 'RP-B',
          bundleNumber: '2',
          classCode: '221',
          classCodeSystem: 'Claim007',
          className: '頓服薬剤（院内処方）',
          admin: '1回',
          adminMemo: '',
          memo: '',
          started: '2025-12-30',
          items: [{ name: 'ロキソニン', quantity: '1', unit: '錠', memo: '' }],
        },
      ],
    });

    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const editButton = await screen.findByRole('button', { name: '編集' });
    await user.click(editButton);

    const bundleNumberInput = screen.getByLabelText('回数') as HTMLInputElement;
    expect(bundleNumberInput.value).toBe('2');
    const usageSelect = screen.getByLabelText('用法') as HTMLSelectElement;
    expect(usageSelect.selectedOptions[0]?.text).toBe('1回');
    expect(screen.getByText('頓用/臨時は回数として扱われます。')).toBeInTheDocument();
  });
});
