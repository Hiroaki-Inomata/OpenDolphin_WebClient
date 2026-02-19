import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import { mutateOrderBundles } from '../orderBundleApi';
import { fetchOrderMasterSearch } from '../orderMasterSearchApi';
import { parseOrcaOrderItemMemo } from '../orcaOrderItemMeta';

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

vi.mock('../contraindicationCheckApi', async () => ({
  buildContraindicationCheckRequestXml: vi.fn().mockReturnValue('<data />'),
  fetchContraindicationCheckXml: vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    rawXml: '<data />',
    apiResult: '00',
    apiResultMessage: 'OK',
    results: [],
    symptomInfo: [],
    missingTags: [],
  }),
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
const injectionProps = {
  ...baseProps,
  entity: 'injectionOrder',
  title: '注射編集',
  bundleLabel: '注射オーダー名',
  itemQuantityLabel: '回数',
};

const recentUsageStorageKey = 'charts-order-recent-usage:unknown-facility:unknown-user:medOrder';

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
    const usageInput = screen.getByLabelText('用法') as HTMLInputElement;
    await user.type(usageInput, '1回');
    await waitFor(() => expect(screen.getByText('1回')).toBeInTheDocument());
    await user.click(screen.getByText('1回').closest('button')!);
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

    const nameInputs = screen.getAllByPlaceholderText('薬剤名') as HTMLInputElement[];
    await user.type(nameInputs[0], 'A');
    await user.type(nameInputs[1], 'B');

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

    const nameInputs = screen.getAllByPlaceholderText('薬剤名') as HTMLInputElement[];
    await user.type(nameInputs[0], 'アムロジピン');

    await user.click(screen.getByRole('button', { name: '院内' }));
    await user.click(screen.getByRole('button', { name: '頓用' }));

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

  it('外用の混合トグルで混合コメント行が保存 payload に追加される', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await user.type(screen.getByPlaceholderText('薬剤名'), 'アムロジピン');
    await selectUsage(user);

    await user.click(screen.getByRole('button', { name: '外用' }));
    await user.click(screen.getByLabelText('混合'));

    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    const mutateMock = vi.mocked(mutateOrderBundles);
    await waitFor(() => expect(mutateMock).toHaveBeenCalled());
    const payload = mutateMock.mock.calls[0]?.[0];
    const items = payload?.operations?.[0]?.items ?? [];
    expect(items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: '810000001',
          name: '混合',
          memo: '__mixing_comment__',
        }),
      ]),
    );
  });

  it('一般名指示と薬剤コメントが薬剤行ごとに memo meta へ共存保存される', async () => {
    const user = userEvent.setup();
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'youhou' && keyword.trim().length > 0) {
        return {
          ok: true,
          items: [{ type: 'youhou', name: '1回' }],
          totalCount: 1,
        };
      }
      if (type === 'drug' && keyword.trim().length > 0) {
        return {
          ok: true,
          items: [
            {
              type: 'drug',
              code: '612345678',
              name: 'アムロジピン',
              unit: '錠',
              note: '元メモ',
            },
          ],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const nameInput = screen.getByPlaceholderText('薬剤名') as HTMLInputElement;
    await user.click(nameInput);
    await user.type(nameInput, 'アムロジピン');
    const suggestion = await screen.findByRole('button', { name: /612345678/ });
    await user.click(suggestion);

    const genericGroup = screen.getByRole('group', { name: '一般名' });
    const genericOnButton = within(genericGroup).getByRole('button', { name: '一般名' });
    await waitFor(() => expect(genericOnButton).toBeEnabled());
    await user.click(genericOnButton);
    await user.type(screen.getByLabelText('薬剤コメント 1'), '食後');

    const usageInput = screen.getByLabelText('用法') as HTMLInputElement;
    await user.type(usageInput, '1回');
    await waitFor(() => expect(screen.getByText('1回')).toBeInTheDocument());
    await user.click(screen.getByText('1回').closest('button')!);

    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    const mutateMock = vi.mocked(mutateOrderBundles);
    await waitFor(() => expect(mutateMock).toHaveBeenCalled());

    const payload = mutateMock.mock.calls[0]?.[0];
    const items = payload?.operations?.[0]?.items ?? [];
    const { meta, memoText } = parseOrcaOrderItemMemo(items[0]?.memo);
    expect(meta).toMatchObject({
      genericFlg: 'yes',
      userComment: '食後',
    });
    expect(memoText).toBe('元メモ');
  });

  it('空白のみ薬剤コメントは memo meta から除去される', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await user.type(screen.getByPlaceholderText('薬剤名'), 'アムロジピン');
    await user.type(screen.getByLabelText('薬剤コメント 1'), '   ');
    await selectUsage(user);

    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    const mutateMock = vi.mocked(mutateOrderBundles);
    await waitFor(() => expect(mutateMock).toHaveBeenCalled());

    const payload = mutateMock.mock.calls[0]?.[0];
    const items = payload?.operations?.[0]?.items ?? [];
    const itemMemo = items[0]?.memo ?? '';
    const { meta, memoText } = parseOrcaOrderItemMemo(itemMemo);

    expect(itemMemo.startsWith('__orca_meta__:')).toBe(false);
    expect(meta.userComment).toBeUndefined();
    expect(memoText).toBe('');
  });

  it('最近使った用法セレクトで用法欄を上書きできる', async () => {
    const user = userEvent.setup();
    localStorage.setItem(recentUsageStorageKey, JSON.stringify(['1日2回 朝夕食後', '1日1回 朝']));
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await user.selectOptions(screen.getByLabelText('最近使った用法'), '1日2回 朝夕食後');
    expect((screen.getByLabelText('用法') as HTMLInputElement).value).toBe('1日2回 朝夕食後');
  });

  it('保存成功時に最近使った用法履歴へ追加される', async () => {
    mockUsageMaster();
    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await user.type(screen.getByPlaceholderText('薬剤名'), 'アムロジピン');
    await selectUsage(user);
    await user.click(screen.getByRole('button', { name: '保存して追加' }));

    await waitFor(() => expect(vi.mocked(mutateOrderBundles)).toHaveBeenCalled());
    const stored = localStorage.getItem(recentUsageStorageKey);
    expect(stored).toBeTruthy();
    expect(JSON.parse(stored ?? '[]')[0]).toBe('1回');
  });

  it('injectionOrder でも用法候補を利用でき、経路コード順で表示される', async () => {
    const user = userEvent.setup();
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'youhou' && keyword.trim().length > 0) {
        return {
          ok: true,
          items: [
            { type: 'youhou', code: 'Y900', name: '外用候補', routeCode: 'TOP', timingCode: '03' },
            { type: 'youhou', code: 'Y100', name: '静注候補', routeCode: 'IV', timingCode: '03' },
          ],
          totalCount: 2,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    renderWithClient(<OrderBundleEditPanel {...injectionProps} />);

    const usageInput = screen.getByLabelText('投与指示') as HTMLInputElement;
    await user.type(usageInput, '候補');

    const rows = await screen.findAllByRole('button', { name: /候補/ });
    expect(rows[0]).toHaveTextContent('静注候補');
    expect(rows[1]).toHaveTextContent('外用候補');

    await user.click(rows[0]);
    expect(usageInput.value).toBe('Y100 静注候補');
    expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'youhou', keyword: '候補' }));
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
    const nameInput = screen.getByPlaceholderText('薬剤名') as HTMLInputElement;
    expect(nameInput).toBeDisabled();
    const deleteButtons = screen.getAllByLabelText(/削除/);
    deleteButtons.forEach((button) => expect(button).toBeDisabled());
  });

  it('行削除で最終行が初期化される', async () => {
    const user = userEvent.setup();

    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const nameInput = screen.getByPlaceholderText('薬剤名') as HTMLInputElement;
    await user.type(nameInput, 'A');
    await user.click(screen.getByLabelText(/削除/));

    const cleared = screen.getAllByPlaceholderText('薬剤名') as HTMLInputElement[];
    expect(cleared).toHaveLength(1);
    expect(cleared[0].value).toBe('');
  });
});
