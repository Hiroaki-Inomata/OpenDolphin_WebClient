import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import { fetchOrderBundles } from '../orderBundleApi';
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
      queries: {
        retry: false,
      },
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

describe('OrderBundleEditPanel master search UI', () => {
  it('検索条件変更時に結果が更新される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ keyword }) => {
      if (keyword.includes('ベル')) {
        return {
          ok: true,
          items: [
            {
              type: 'generic-class',
              code: 'B200',
              name: 'ベルベリン',
              unit: '包',
            },
          ],
          totalCount: 1,
        };
      }
      return {
        ok: true,
        items: [
          {
            type: 'generic-class',
            code: 'A100',
            name: 'アムロジピン',
            unit: '錠',
          },
        ],
        totalCount: 1,
      };
    });

    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const itemNameInput = screen.getByPlaceholderText('項目名');
    await user.type(itemNameInput, 'アム');

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'generic-class', keyword: 'アム' })),
    );
    expect(screen.getByText('アムロジピン')).toBeInTheDocument();

    await user.clear(itemNameInput);
    await user.type(itemNameInput, 'ベル');

    await waitFor(() => expect(screen.getByText('ベルベリン')).toBeInTheDocument());
  });

  it('検索結果の行選択で項目が追加される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async () => ({
      ok: true,
      items: [
        {
          type: 'generic-class',
          code: 'A100',
          name: 'アムロジピン',
          unit: '錠',
        },
      ],
      totalCount: 1,
    }));

    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const itemNameInput = screen.getByPlaceholderText('項目名');
    await user.type(itemNameInput, 'アム');

    await waitFor(() => expect(screen.getByText('アムロジピン')).toBeInTheDocument());

    const rowButton = screen.getByText('アムロジピン').closest('button');
    expect(rowButton).not.toBeNull();
    await user.click(rowButton!);

    const selectedItemNameInput = screen.getByPlaceholderText('項目名') as HTMLInputElement;
    expect(selectedItemNameInput.value).toBe('A100 アムロジピン');
  });

  it('項目名入力のリアルタイム候補で主項目を補完できる', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'generic-class' && keyword.includes('アム')) {
        return {
          ok: true,
          items: [
            {
              type: 'generic-class',
              code: 'A100',
              name: 'アムロジピン',
              unit: '錠',
              note: '予測候補',
            },
          ],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    const { container } = renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const itemNameInput = screen.getByPlaceholderText('項目名') as HTMLInputElement;
    await user.type(itemNameInput, 'アム');
    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'generic-class', keyword: 'アム' })),
    );

    const predictiveOption = container.querySelector('datalist[id$="-item-predictive-list"] option[value="A100 アムロジピン"]');
    expect(predictiveOption).not.toBeNull();

    await user.clear(itemNameInput);
    await user.type(itemNameInput, 'A100 アムロジピン');
    await user.tab();

    await waitFor(() => expect(itemNameInput.value).toBe('A100 アムロジピン'));
    const itemUnitInput = container.querySelector<HTMLInputElement>('input[id$="-item-unit-0"]');
    expect(itemUnitInput?.value).toBe('錠');
  });

  it('readOnly の場合は検索入力が無効化される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        meta={{
          ...baseProps.meta,
          readOnly: true,
          readOnlyReason: '閲覧専用',
        }}
      />,
    );

    expect(screen.getByPlaceholderText('項目名')).toBeDisabled();
    expect(screen.getByRole('button', { name: '処方薬剤' })).toBeDisabled();
    expect(fetchOrderBundles).toHaveBeenCalled();
  });

  it('リハビリ部位検索で選択した部位が反映される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type }) => {
      if (type === 'bodypart') {
        return {
          ok: true,
          items: [
            {
              type: 'bodypart',
              code: '002001',
              name: '膝関節',
            },
          ],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        entity="generalOrder"
        title="オーダー編集"
        bundleLabel="オーダー名"
        itemQuantityLabel="数量"
      />,
    );

    const keywordInput = screen.getByLabelText('部位検索', {
      selector: 'input[id$="-bodypart-keyword"]',
    });
    await user.type(keywordInput, '膝');

    const searchButton = screen.getByRole('button', { name: '部位検索' });
    await user.click(searchButton);

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'bodypart', keyword: '膝' })),
    );
    await waitFor(() => expect(screen.getByText('膝関節')).toBeInTheDocument());

    const rowButton = screen.getByText('膝関節').closest('button');
    expect(rowButton).not.toBeNull();
    await user.click(rowButton!);

    const bodyPartInput = screen.getByLabelText('部位', {
      selector: 'input[id$="-bodypart"]',
    }) as HTMLInputElement;
    expect(bodyPartInput.value).toBe('膝関節');
  });

  it('readOnly の場合は放射線の部位/コメント入力が無効化される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');

    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        entity="radiologyOrder"
        title="放射線"
        bundleLabel="放射線オーダー名"
        meta={{
          ...baseProps.meta,
          readOnly: true,
          readOnlyReason: '閲覧専用',
        }}
      />,
    );

    expect(screen.getByLabelText('部位')).toBeDisabled();
    expect(screen.getByLabelText('部位検索')).toBeDisabled();
    expect(screen.getByRole('button', { name: '部位検索' })).toBeDisabled();
    expect(screen.getByPlaceholderText('コード')).toBeDisabled();
    expect(screen.getByPlaceholderText('コメント内容')).toBeDisabled();
    const addButtons = screen.getAllByRole('button', { name: '追加' });
    addButtons.forEach((button) => expect(button).toBeDisabled());
  });

  it('generalOrder の場合はリハビリ部位検索が表示される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');

    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        entity="generalOrder"
        title="オーダー編集"
        bundleLabel="オーダー名"
        itemQuantityLabel="数量"
      />,
    );

    expect(screen.getByLabelText('部位')).toBeEnabled();
    expect(screen.getByLabelText('部位検索')).toBeEnabled();
    expect(screen.getByRole('button', { name: '部位検索' })).toBeEnabled();
  });

  it('注射オーダーでは注射専用フォームが表示される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');

    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        entity="injectionOrder"
        title="注射"
        bundleLabel="注射名"
        itemQuantityLabel="数量"
      />,
    );

    expect(screen.getByText('注射専用フォームです。注射薬剤と注射手技を分けて検索できます。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '注射薬剤' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '注射手技' })).toBeInTheDocument();
    expect(screen.queryByText('用法候補')).toBeNull();
    expect(screen.getByLabelText('投与指示')).toBeInTheDocument();
  });

  it('放射線オーダーでは画像専用フォームの検索プリセットが表示される', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');

    renderWithClient(
      <OrderBundleEditPanel
        {...baseProps}
        entity="radiologyOrder"
        title="放射線"
        bundleLabel="放射線オーダー名"
        itemQuantityLabel="数量"
      />,
    );

    expect(screen.getByText('画像検査専用フォームです。画像検査点数・器材・造影薬剤を個別に検索できます。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '画像検査' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '画像器材' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '造影薬剤' })).toBeInTheDocument();
    expect(screen.getByLabelText('検査指示')).toBeInTheDocument();
  });

  it('コメント候補の行選択でコメントコードを追加できる', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'comment' && keyword.includes('服薬')) {
        return {
          ok: true,
          items: [
            {
              type: 'comment',
              code: '0082',
              name: '服薬指示',
              category: 'comment',
              note: 'RP',
            },
          ],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    const { container } = renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const commentDraftNameInput = container.querySelector<HTMLInputElement>('input[id$="-comment-draft-name"]');
    expect(commentDraftNameInput).not.toBeNull();
    await user.type(commentDraftNameInput!, '服薬');

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'comment', keyword: '服薬' })),
    );
    await waitFor(() => expect(screen.getByText('服薬指示')).toBeInTheDocument());

    await user.click(screen.getByText('服薬指示').closest('button')!);
    await user.click(screen.getByRole('button', { name: 'コメント追加' }));

    const commentCodeInput = container.querySelector<HTMLInputElement>('input[id$="-comment-code-0"]');
    const commentNameInput = container.querySelector<HTMLInputElement>('input[id$="-comment-name-0"]');
    expect(commentCodeInput?.value).toBe('0082');
    expect(commentNameInput?.value).toBe('服薬指示');
  });

  it('コメント内容入力欄の blur 補完でコメント追加できる', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'comment' && keyword.includes('服薬')) {
        return {
          ok: true,
          items: [{ type: 'comment', code: '0082', name: '服薬指示', unit: '', note: '' }],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    const { container } = renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const commentDraftNameInput = container.querySelector<HTMLInputElement>('input[id$="-comment-draft-name"]');
    expect(commentDraftNameInput).not.toBeNull();
    await user.type(commentDraftNameInput!, '服薬指示');
    await user.tab();
    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(expect.objectContaining({ type: 'comment', keyword: '服薬指示' })),
    );

    const addButton = screen.getByRole('button', { name: 'コメント追加' });
    await user.click(addButton);

    const commentCodeInput = container.querySelector<HTMLInputElement>('input[id$="-comment-code-0"]');
    const commentNameInput = container.querySelector<HTMLInputElement>('input[id$="-comment-name-0"]');
    expect(commentCodeInput?.value).toBe('0082');
    expect(commentNameInput?.value).toBe('服薬指示');
    expect(commentCodeInput).toHaveAttribute('readonly');
    expect(commentNameInput).toHaveAttribute('readonly');
  });

  it('コード検索で返る選択式コメント候補をコメントコードへ追加できる', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type, keyword }) => {
      if (type === 'generic-class' && keyword === '1234') {
        return {
          ok: true,
          items: [],
          totalCount: 0,
          correctionMeta: {
            apiResult: '00',
            apiResultMessage: '処理終了',
            validTo: '9999-12-31',
          },
          correctionCandidates: [],
          selectionComments: [
            {
              code: '0082',
              name: '食後',
              category: '1',
              itemNumber: '01',
              itemNumberBranch: '00',
            },
          ],
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    const { container } = renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const itemNameInput = container.querySelector<HTMLInputElement>('input[id$="-item-name-0"]');
    expect(itemNameInput).not.toBeNull();
    await user.type(itemNameInput!, '1234');

    await waitFor(() => expect(screen.getByText('選択式コメント候補（medicationgetv2）')).toBeInTheDocument());
    const selectionCommentButton = screen.getAllByText('食後')[0]?.closest('button');
    expect(selectionCommentButton).not.toBeNull();
    await user.click(selectionCommentButton!);

    const commentCodeInput = container.querySelector<HTMLInputElement>('input[id$="-comment-code-0"]');
    const commentNameInput = container.querySelector<HTMLInputElement>('input[id$="-comment-name-0"]');
    expect(commentCodeInput?.value).toBe('0082');
    expect(commentNameInput?.value).toBe('食後');
  });
});
