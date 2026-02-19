import { describe, expect, it } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement } from 'react';

import { OrderDockPanel } from '../OrderDockPanel';

const renderWithClient = (ui: ReactElement) => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
};

const baseMeta = {
  runId: 'RUN-ORDER-DOCK',
  cacheHit: false,
  missingMaster: false,
  fallbackUsed: false,
  dataSourceTransition: 'server' as const,
};

describe('OrderDockPanel category quick-add', () => {
  it('主要カテゴリ導線を常時表示し quick-add / group-add の data-test-id を保持する', () => {
    renderWithClient(
      <OrderDockPanel
        patientId="P-100"
        meta={baseMeta}
        visitDate="2026-02-17"
        orderBundles={[
          {
            entity: 'medOrder',
            bundleName: '内服セット',
            started: '2026-02-17',
            items: [{ name: 'A100 アムロジピン', quantity: '1', unit: '錠', memo: '' }],
          } as any,
        ]}
      />,
    );

    expect(screen.getByText('+処方')).toBeInTheDocument();
    expect(screen.getByText('+注射')).toBeInTheDocument();
    expect(screen.getByText('+処置')).toBeInTheDocument();
    expect(screen.getByText('+検査')).toBeInTheDocument();
    expect(screen.getByText('+算定')).toBeInTheDocument();
    expect(document.querySelector('[data-test-id="order-dock-quick-add-prescription"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-quick-add-injection"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-quick-add-treatment"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-quick-add-test"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-quick-add-charge"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-group-add-prescription"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-group-add-injection"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-group-add-treatment"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-group-add-test"]')).not.toBeNull();
    expect(document.querySelector('[data-test-id="order-dock-group-add-charge"]')).not.toBeNull();
  });

  it('カテゴリ候補からインライン編集を開いても検索UIが残り、閉じるで閉じる', async () => {
    const user = userEvent.setup();
    renderWithClient(<OrderDockPanel patientId="P-100" meta={baseMeta} visitDate="2026-02-17" orderBundles={[]} />);

    const scenarios = [
      { category: 'prescription', keyword: '処方', candidateLabel: '処方を新規追加', expectedTitle: '処方' },
      { category: 'injection', keyword: '注射', candidateLabel: '注射を新規追加', expectedTitle: '注射' },
      { category: 'treatment', keyword: '処置', candidateLabel: '処置を新規追加', expectedTitle: '処置' },
      { category: 'test', keyword: '検査', candidateLabel: '検査を新規追加', expectedTitle: '検査' },
      { category: 'charge', keyword: '基本料', candidateLabel: '基本料を新規追加', expectedTitle: '基本料' },
    ] as const;

    for (const scenario of scenarios) {
      const searchInput = screen.getByRole('searchbox', { name: 'オーダー検索' });
      const categorySelect = screen.getByRole('combobox', { name: 'カテゴリ選択' });
      await user.selectOptions(categorySelect, scenario.category);
      await user.clear(searchInput);
      await user.type(searchInput, scenario.keyword);

      const listbox = await screen.findByRole('listbox', { name: '検索候補' });
      const candidateButton = within(listbox).getByRole('button', { name: new RegExp(scenario.candidateLabel) });
      await user.click(candidateButton);

      expect(screen.getByLabelText(`${scenario.expectedTitle}入力`)).toBeInTheDocument();
      expect(screen.getByRole('searchbox', { name: 'オーダー検索' })).toBeInTheDocument();

      await user.click(screen.getByRole('button', { name: '閉じる' }));
      expect(screen.queryByLabelText(`${scenario.expectedTitle}入力`)).not.toBeInTheDocument();
      expect(screen.getByRole('searchbox', { name: 'オーダー検索' })).toBeInTheDocument();
    }
  });

  it('quick-add は主要カテゴリの新規入力を開く', async () => {
    const user = userEvent.setup();
    renderWithClient(
      <OrderDockPanel
        patientId="P-100"
        meta={baseMeta}
        visitDate="2026-02-17"
        orderBundles={[
          {
            entity: 'medOrder',
            bundleName: '既存処方',
            started: '2026-02-17',
            items: [{ name: 'A100 アムロジピン', quantity: '1', unit: '錠', memo: '' }],
          } as any,
        ]}
      />,
    );

    await user.click(screen.getByRole('button', { name: '+処置' }));
    expect(screen.getByLabelText('処置入力')).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'オーダー検索' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '閉じる' }));

    await user.click(screen.getByRole('button', { name: '+検査' }));
    expect(screen.getByLabelText('検査入力')).toBeInTheDocument();
    expect(screen.getByRole('searchbox', { name: 'オーダー検索' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '閉じる' }));

    await user.click(screen.getByRole('button', { name: '+算定' }));
    expect(screen.getByLabelText('基本料入力')).toBeInTheDocument();
  });

  it('medOrder item memo の userComment を表示し __orca_meta__ は露出しない', () => {
    const fullComment = '朝夕食後に服用してください。眠気が強い場合は中止してください。';
    renderWithClient(
      <OrderDockPanel
        patientId="P-100"
        meta={baseMeta}
        visitDate="2026-02-17"
        orderBundles={[
          {
            entity: 'medOrder',
            bundleName: 'コメント付き処方',
            started: '2026-02-17',
            items: [
              {
                name: 'A100 アムロジピン',
                quantity: '1',
                unit: '錠',
                memo: `__orca_meta__:${JSON.stringify({ userComment: fullComment })}\n内部メモ`,
              },
            ],
          } as any,
        ]}
      />,
    );

    const commentChip = screen.getByTitle(`コメント:${fullComment}`);
    expect(commentChip).toHaveTextContent(/^コメント:/);
    expect(commentChip.textContent).not.toBe(`コメント:${fullComment}`);
    expect(screen.queryByText(/__orca_meta__/)).not.toBeInTheDocument();
  });
});
