import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import { mutateOrderBundles } from '../orderBundleApi';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';

vi.mock('../orderBundleApi', async () => ({
  fetchOrderBundles: vi.fn().mockResolvedValue({
    ok: true,
    bundles: [],
    patientId: 'P-1',
  }),
  mutateOrderBundles: vi.fn().mockResolvedValue({ ok: true, runId: 'RUN-ORDER' }),
}));

vi.mock('../stampApi', async () => ({
  fetchUserProfile: vi.fn().mockResolvedValue({ ok: true, id: 1, userId: 'facility:doctor' }),
  fetchStampTree: vi.fn().mockResolvedValue({ ok: true, trees: [] }),
  fetchStampDetail: vi.fn(),
}));

vi.mock('../orderMasterSearchApi', async () => ({
  fetchOrderMasterSearch: vi.fn(),
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

describe('OrderBundleEditPanel item actions', () => {
  const mockUsageMaster = () => {
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'youhou' && keyword.trim().length > 0) {
        return {
          ok: true,
          items: [{ type: 'youhou', name: '1回' }],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });
  };

  const selectUsage = async (user: ReturnType<typeof userEvent.setup>) => {
    const usageSelect = screen.getByLabelText('用法') as HTMLSelectElement;
    await user.type(
      screen.getByLabelText('キーワード', {
        selector: 'input[id$="-usage-keyword"]',
      }),
      '1回',
    );
    await waitFor(() => expect(usageSelect.querySelector('option[value="1回"]')).not.toBeNull());
    await user.selectOptions(usageSelect, '1回');
  };

  it('入力順が保存 payload に反映される', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const itemSectionLabel = screen
      .getAllByText('処方薬剤')
      .find((node) => node.tagName.toLowerCase() === 'strong');
    const itemSection = itemSectionLabel?.closest('.charts-side-panel__subsection') as HTMLElement | null;
    if (!itemSection) throw new Error('処方薬剤セクションが見つかりません');
    await user.click(within(itemSection).getByRole('button', { name: '追加' }));

    const nameInputs = screen.getAllByPlaceholderText('項目名') as HTMLInputElement[];
    await user.type(nameInputs[0], 'A');
    await user.type(nameInputs[1], 'B');

    await user.type(screen.getByLabelText('RP名'), '降圧薬');
    await selectUsage(user);

    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    const mutateMock = vi.mocked(mutateOrderBundles);
    await waitFor(() => expect(mutateMock).toHaveBeenCalled());

    const payload = mutateMock.mock.calls[0]?.[0];
    const items = payload?.operations?.[0]?.items ?? [];
    expect(items.map((item: { name: string }) => item.name)).toEqual(['A', 'B']);
  });

  it('頓用/院内の選択とRP名補正が保存 payload に反映される', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const nameInputs = screen.getAllByPlaceholderText('項目名') as HTMLInputElement[];
    await user.type(nameInputs[0], 'アムロジピン');

    await user.click(screen.getByLabelText('院内'));
    await user.click(screen.getByLabelText('頓用'));

    await selectUsage(user);
    await user.clear(screen.getByLabelText('回数'));
    await user.type(screen.getByLabelText('回数'), '3');

    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    const mutateMock = vi.mocked(mutateOrderBundles);
    await waitFor(() => expect(mutateMock).toHaveBeenCalled());

    const payload = mutateMock.mock.calls[0]?.[0];
    const operation = payload?.operations?.[0];
    expect(operation?.bundleName).toBe('アムロジピン');
    expect(operation?.bundleNumber).toBe('3');
    expect(operation?.admin).toBe('1回');
    expect(operation?.classCode).toBe('221');
    expect(operation?.classCodeSystem).toBe('Claim007');
    expect(operation?.className).toBe('頓服薬剤（院内処方）');
  });

  it.each([
    ['readOnly', { readOnly: true, readOnlyReason: '閲覧専用' }],
    ['missingMaster', { missingMaster: true }],
    ['fallbackUsed', { fallbackUsed: true }],
  ])('編集ガード中(%s)は追加/入力/行削除が無効化される', (_, meta) => {
    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        meta={{
          ...baseProps.meta,
          ...meta,
        }}
      />,
    );

    expect(screen.getByRole('button', { name: '追加' })).toBeDisabled();
    const nameInput = screen.getByPlaceholderText('項目名') as HTMLInputElement;
    expect(nameInput).toBeDisabled();
    const deleteButtons = screen.getAllByLabelText(/削除/);
    deleteButtons.forEach((button) => expect(button).toBeDisabled());
  });

  it('行削除で最終行が初期化される', async () => {
    const user = userEvent.setup();

    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const nameInput = screen.getByPlaceholderText('項目名') as HTMLInputElement;
    await user.type(nameInput, 'A');
    await user.click(screen.getByLabelText(/削除/));

    const cleared = screen.getAllByPlaceholderText('項目名') as HTMLInputElement[];
    expect(cleared).toHaveLength(1);
    expect(cleared[0].value).toBe('');
  });
});
