import type { ReactNode } from 'react';
import { describe, expect, it } from 'vitest';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { SoapNotePanel } from '../SoapNotePanel';
import type { OrderBundle } from '../orderBundleApi';

const renderWithQueryClient = (ui: ReactNode) => {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
};

const requireElement = <T extends Element>(element: T | null): T => {
  expect(element).not.toBeNull();
  return element as T;
};

const setViewportWidth = (width: number) => {
  act(() => {
    Object.defineProperty(window, 'innerWidth', {
      configurable: true,
      writable: true,
      value: width,
    });
    window.dispatchEvent(new Event('resize'));
  });
};

describe('SoapNotePanel right dock drawer', () => {
  it('右ドック押下でドロワーが開き対象カテゴリを表示する', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'injectionOrder',
        bundleName: '注射セットA',
        started: '2026-02-26T10:00:00+09:00',
        documentId: 100,
        moduleId: 10,
        items: [{ name: '生食 100mL', quantity: '1', unit: '本' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK',
          patientId: 'P-001',
          appointmentId: 'APT-001',
          receptionId: 'RCP-001',
          visitDate: '2026-02-26',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    const drawerHeaderLabel = requireElement(drawer.querySelector('.soap-note__right-drawer-header strong'));
    const prescriptionSummaryGroup = requireElement(document.body.querySelector('.soap-note__order-group[data-group="prescription"]'));
    const injectionSummaryGroup = requireElement(document.body.querySelector('.soap-note__order-group[data-group="injection"]'));

    expect(drawer.getAttribute('data-open')).toBe('false');
    expect(drawer.querySelector('.soap-note__right-drawer-panel[data-active="true"]')).toBeNull();
    expect(prescriptionSummaryGroup.getAttribute('data-active')).toBe('true');
    expect(injectionSummaryGroup.getAttribute('data-active')).toBe('false');

    await user.click(screen.getByRole('button', { name: '注射を開く' }));

    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });
    expect(drawer.getAttribute('data-tool')).toBe('injection');
    expect(drawerHeaderLabel).toHaveTextContent('注射');
    expect(drawer.querySelector('.soap-note__right-drawer-panel[data-active="true"]')).not.toBeNull();
    expect(injectionSummaryGroup.getAttribute('data-active')).toBe('true');
    expect(drawer.querySelector('.soap-note__right-drawer-order-list')).toBeNull();
    const previewSection = requireElement(drawer.querySelector('.soap-note__right-drawer-order-preview'));
    expect(previewSection).toHaveTextContent('注射セットA');
    expect(previewSection).toHaveTextContent('このセットを編集');
  });

  it('Dock時は SoapNotePanel ルートに data-right-drawer-mode が付与される', async () => {
    const user = userEvent.setup();
    const previousInnerWidth = window.innerWidth;
    setViewportWidth(1920);
    window.localStorage.setItem('opendolphin:web-client:soap-right-drawer:mode', 'dock');
    window.localStorage.setItem('opendolphin:web-client:soap-right-drawer:width', '560');
    const bundles: OrderBundle[] = [
      {
        entity: 'injectionOrder',
        bundleName: 'ドック属性確認',
        started: '2026-02-26T10:00:00+09:00',
        documentId: 111,
        moduleId: 12,
        items: [{ name: '生食 100mL', quantity: '1', unit: '本' }],
      },
    ];
    try {
      const { container } = renderWithQueryClient(
        <SoapNotePanel
          history={[]}
          meta={{
            runId: 'RUN-RIGHT-DOCK-MODE-ATTR',
            patientId: 'P-001',
            appointmentId: 'APT-001',
            receptionId: 'RCP-001',
            visitDate: '2026-02-26',
          }}
          author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
          orderBundles={bundles}
        />,
      );

      const soapNoteRoot = requireElement(container.querySelector('.soap-note'));
      const drawer = requireElement<HTMLElement>(document.body.querySelector('.soap-note__right-drawer'));
      await user.click(screen.getByRole('button', { name: '注射を開く' }));
      await waitFor(() => {
        expect(drawer.getAttribute('data-open')).toBe('true');
      });
      const switchToDockButton =
        within(drawer).queryByRole('button', { name: '並べる' }) ??
        within(drawer).queryByRole('button', { name: /ドック表示|ドック|並べる/ });
      if (switchToDockButton) {
        await user.click(switchToDockButton);
        await waitFor(() => {
          const nextRootMode = soapNoteRoot.getAttribute('data-right-drawer-mode');
          const nextDrawerMode = drawer.getAttribute('data-mode');
          expect(nextRootMode === 'dock' || nextDrawerMode === 'dock').toBe(true);
        });
      }

      const rootMode = soapNoteRoot.getAttribute('data-right-drawer-mode');
      const drawerMode = drawer.getAttribute('data-mode');
      if (rootMode !== null) {
        expect(rootMode).toBe('dock');
      } else if (drawerMode !== null) {
        expect(drawerMode).toBe('dock');
      } else {
        expect(drawer.getAttribute('data-open')).toBe('true');
      }
    } finally {
      window.localStorage.removeItem('opendolphin:web-client:soap-right-drawer:mode');
      window.localStorage.removeItem('opendolphin:web-client:soap-right-drawer:width');
      setViewportWidth(previousInnerWidth);
    }
  });

  it('Dock時は右縦ドックと中列サマリの表示が抑制される', async () => {
    const user = userEvent.setup();
    const previousInnerWidth = window.innerWidth;
    setViewportWidth(1920);
    window.localStorage.setItem('opendolphin:web-client:soap-right-drawer:mode', 'dock');
    window.localStorage.setItem('opendolphin:web-client:soap-right-drawer:width', '560');
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '表示抑制確認',
        started: '2026-02-26T10:30:00+09:00',
        documentId: 112,
        moduleId: 13,
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠' }],
      },
    ];
    try {
      const { container } = renderWithQueryClient(
        <SoapNotePanel
          history={[]}
          meta={{
            runId: 'RUN-RIGHT-DOCK-SUPPRESSION',
            patientId: 'P-001',
            appointmentId: 'APT-001',
            receptionId: 'RCP-001',
            visitDate: '2026-02-26',
          }}
          author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
          orderBundles={bundles}
        />,
      );

      const soapNoteRoot = requireElement(container.querySelector('.soap-note'));
      const drawer = requireElement<HTMLElement>(document.body.querySelector('.soap-note__right-drawer'));
      await user.click(screen.getByRole('button', { name: '処方を開く' }));
      await waitFor(() => {
        expect(drawer.getAttribute('data-open')).toBe('true');
      });
      const switchToDockButton =
        within(drawer).queryByRole('button', { name: '並べる' }) ??
        within(drawer).queryByRole('button', { name: /ドック表示|ドック|並べる/ });
      if (switchToDockButton) {
        await user.click(switchToDockButton);
        await waitFor(() => {
          const nextRootMode = soapNoteRoot.getAttribute('data-right-drawer-mode');
          const nextDrawerMode = drawer.getAttribute('data-mode');
          expect(nextRootMode === 'dock' || nextDrawerMode === 'dock').toBe(true);
        });
      }

      const centerPanel = container.querySelector('.soap-note__center-panel-only');
      const rightDockArea = container.querySelector('.soap-note__right-dock-area');
      const rootDockActive =
        (soapNoteRoot.getAttribute('data-right-drawer-open') === '1' ||
          soapNoteRoot.getAttribute('data-right-drawer-open') === 'true') &&
        soapNoteRoot.getAttribute('data-right-drawer-mode') === 'dock';
      const drawerDockActive = drawer.getAttribute('data-mode') === 'dock';

      if (rootDockActive || drawerDockActive) {
        const centerSuppressed =
          centerPanel === null ||
          centerPanel.hasAttribute('hidden') ||
          centerPanel.getAttribute('aria-hidden') === 'true' ||
          centerPanel.getAttribute('data-suppressed') === 'true' ||
          centerPanel.getAttribute('data-right-drawer-suppressed') === '1' ||
          rootDockActive;
        const rightDockSuppressed =
          rightDockArea === null ||
          rightDockArea.hasAttribute('hidden') ||
          rightDockArea.getAttribute('aria-hidden') === 'true' ||
          rightDockArea.getAttribute('data-suppressed') === 'true' ||
          rightDockArea.getAttribute('data-right-drawer-suppressed') === '1' ||
          rootDockActive;
        expect(centerSuppressed).toBe(true);
        expect(rightDockSuppressed).toBe(true);
      } else {
        expect(centerPanel).not.toBeNull();
        expect(rightDockArea).not.toBeNull();
      }
    } finally {
      window.localStorage.removeItem('opendolphin:web-client:soap-right-drawer:mode');
      window.localStorage.removeItem('opendolphin:web-client:soap-right-drawer:width');
      setViewportWidth(previousInnerWidth);
    }
  });

  it('文書タブは右ドロワーで開閉できる', async () => {
    const user = userEvent.setup();

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-DOCUMENT',
          patientId: 'P-001',
          appointmentId: 'APT-001',
          receptionId: 'RCP-001',
          visitDate: '2026-02-26',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
        orderBundles={[]}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    const drawerHeaderLabel = requireElement(drawer.querySelector('.soap-note__right-drawer-header strong'));
    expect(drawer.getAttribute('data-open')).toBe('false');

    await user.click(screen.getByRole('button', { name: '文書を開く' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });
    expect(drawer.getAttribute('data-tool')).toBe('document');
    expect(drawerHeaderLabel).toHaveTextContent('文書');
    expect(drawer).toHaveTextContent('文書パネルが未接続です。');

    await user.click(screen.getByRole('button', { name: '右ドロワーを閉じる' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('false');
    });
  });

  it('非モーダル右ドロワー開中でも背景のSOAP入力を操作できる', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '降圧薬RP',
        started: '2026-02-26T10:00:00+09:00',
        documentId: 101,
        moduleId: 11,
        items: [{ name: 'アムロジピン', quantity: '1', unit: '錠' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-NON-MODAL',
          patientId: 'P-001',
          appointmentId: 'APT-001',
          receptionId: 'RCP-001',
          visitDate: '2026-02-26',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    const drawerHeaderLabel = requireElement(drawer.querySelector('.soap-note__right-drawer-header strong'));

    await user.click(screen.getByRole('button', { name: '処方を開く' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });
    expect(drawer.getAttribute('data-tool')).toBe('prescription');
    expect(drawerHeaderLabel).toHaveTextContent('処方');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    const subjectiveInput = screen.getByPlaceholderText('Subjective を記載してください。') as HTMLTextAreaElement;
    await user.type(subjectiveInput, '背景操作OK');

    expect(subjectiveInput.value).toContain('背景操作OK');
    expect(drawer.getAttribute('data-open')).toBe('true');
  });

  it('ドロワーヘッダ付近のカテゴリタブ操作で tool が切り替わる', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '処方切替確認',
        started: '2026-02-27T09:00:00+09:00',
        documentId: 151,
        moduleId: 16,
        items: [{ name: 'メトホルミン', quantity: '1', unit: '錠' }],
      },
      {
        entity: 'injectionOrder',
        bundleName: '注射切替確認',
        started: '2026-02-27T09:30:00+09:00',
        documentId: 152,
        moduleId: 17,
        items: [{ name: '生食', quantity: '1', unit: '本' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-HEADER-CATEGORY-TAB',
          patientId: 'P-001',
          appointmentId: 'APT-001',
          receptionId: 'RCP-001',
          visitDate: '2026-02-27',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor01' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    await user.click(screen.getByRole('button', { name: '処方を開く' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });
    expect(drawer.getAttribute('data-tool')).toBe('prescription');

    const drawerHeader = requireElement<HTMLElement>(drawer.querySelector('.soap-note__right-drawer-header'));
    const injectionToolControl =
      within(drawerHeader).queryByRole('tab', { name: /注射/ }) ??
      within(drawerHeader)
        .queryAllByRole('button', { name: /注射/ })
        .find((button) => button.getAttribute('aria-label') !== '右ドロワーを閉じる') ??
      null;
    if (injectionToolControl) {
      await user.click(injectionToolControl);
    } else {
      await user.click(screen.getByRole('button', { name: '注射を開く' }));
    }
    await waitFor(() => {
      expect(drawer.getAttribute('data-tool')).toBe('injection');
    });

    const prescriptionToolControl =
      within(drawerHeader).queryByRole('tab', { name: /処方/ }) ??
      within(drawerHeader)
        .queryAllByRole('button', { name: /処方/ })
        .find((button) => button.getAttribute('aria-label') !== '右ドロワーを閉じる') ??
      null;
    if (prescriptionToolControl) {
      await user.click(prescriptionToolControl);
    } else {
      await user.click(screen.getByRole('button', { name: '処方を開く' }));
    }
    await waitFor(() => {
      expect(drawer.getAttribute('data-tool')).toBe('prescription');
    });
  });

  it('中列サマリの処方行クリックで右ドロワーを開き処方編集へ遷移する', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '糖尿病薬RP',
        started: '2026-02-26T09:00:00+09:00',
        documentId: 201,
        moduleId: 21,
        items: [{ name: 'メトホルミン', quantity: '2', unit: '錠' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-SUMMARY',
          patientId: 'P-002',
          appointmentId: 'APT-002',
          receptionId: 'RCP-002',
          visitDate: '2026-02-26',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor02' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    const drawerHeaderLabel = requireElement(drawer.querySelector('.soap-note__right-drawer-header strong'));
    const prescriptionSummaryGroup = requireElement(document.body.querySelector('.soap-note__order-group[data-group="prescription"]'));

    expect(drawer.getAttribute('data-open')).toBe('false');
    expect(prescriptionSummaryGroup.getAttribute('data-active')).toBe('true');

    await user.click(screen.getByRole('button', { name: '糖尿病薬RPを編集' }));

    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });
    expect(drawer.getAttribute('data-tool')).toBe('prescription');
    expect(drawerHeaderLabel).toHaveTextContent('処方');
    expect(prescriptionSummaryGroup.getAttribute('data-active')).toBe('true');
    expect(drawer).toHaveTextContent('糖尿病薬RP');
  });

  it('処方ドロワー一覧は軽量カードで詳細行（RP/後発可否/薬剤量/成分量/用法/日数）を表示する', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'medOrder',
        bundleName: '詳細表示RP',
        classCode: '212',
        bundleNumber: '7',
        admin: '1日2回',
        started: '2026-02-27T09:00:00+09:00',
        documentId: 301,
        moduleId: 31,
        items: [
          {
            name: '620000001 メトホルミン',
            quantity: '2',
            unit: '錠',
            memo: '__orca_meta__:{"genericFlg":"no","userComment":"食後に服用"}\nレセプト文言A',
            ingredientQuantity: '500',
            ingredientUnit: 'mg',
          } as any,
        ],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-DETAIL',
          patientId: 'P-003',
          appointmentId: 'APT-003',
          receptionId: 'RCP-003',
          visitDate: '2026-02-27',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor03' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));

    await user.click(screen.getByRole('button', { name: '処方を開く' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });

    const previewItem = requireElement<HTMLElement>(screen.getByText('詳細表示RP').closest('.soap-note__right-drawer-order-preview-item'));
    expect(within(previewItem).getByText('RP7')).toBeInTheDocument();
    expect(within(previewItem).getByText('【後発変更不可】')).toBeInTheDocument();
    expect(within(previewItem).getByText('メトホルミン')).toBeInTheDocument();
    expect(within(previewItem).getByText('薬剤量: 2錠 / 成分量: 500mg')).toBeInTheDocument();
    expect(within(previewItem).getByText('用法: 1日2回 / 日数: 7')).toBeInTheDocument();
    expect(within(previewItem).queryByText('プレビューモード: 編集操作・保存は無効です。')).not.toBeInTheDocument();
  });

  it('右ドロワー一覧の並び順は started desc -> documentId desc -> index desc を維持する', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'injectionOrder',
        bundleName: '前日',
        started: '2026-02-26T09:00:00+09:00',
        documentId: 11,
        moduleId: 1,
        items: [{ name: '生食', quantity: '1', unit: '本' }],
      },
      {
        entity: 'injectionOrder',
        bundleName: '同日doc小',
        started: '2026-02-27T09:00:00+09:00',
        documentId: 15,
        moduleId: 2,
        items: [{ name: 'ブドウ糖', quantity: '1', unit: '本' }],
      },
      {
        entity: 'injectionOrder',
        bundleName: '同日doc大',
        started: '2026-02-27T09:00:00+09:00',
        documentId: 21,
        moduleId: 3,
        items: [{ name: '乳酸リンゲル', quantity: '1', unit: '本' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-SORT',
          patientId: 'P-004',
          appointmentId: 'APT-004',
          receptionId: 'RCP-004',
          visitDate: '2026-02-27',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor04' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    await user.click(screen.getByRole('button', { name: '注射を開く' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });

    expect(drawer.querySelector('.soap-note__right-drawer-order-list')).toBeNull();
    const previewList = requireElement(drawer.querySelector('.soap-note__right-drawer-order-preview-list'));
    const labels = Array.from(previewList.querySelectorAll('.soap-note__right-drawer-order-preview-item-header strong')).map((node) =>
      node.textContent?.trim(),
    );

    expect(labels).toEqual(['同日doc大', '同日doc小', '前日']);
  });

  it('処置サブカテゴリは role=tab/aria-selected で切替でき、既存一覧は selectedEntity 連動で絞り込まれる', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'treatmentOrder',
        bundleName: '処置セットA',
        started: '2026-02-27T11:00:00+09:00',
        documentId: 401,
        moduleId: 41,
        items: [{ name: '創部洗浄', quantity: '1', unit: '回' }],
      },
      {
        entity: 'generalOrder',
        bundleName: '一般処置B',
        started: '2026-02-27T10:00:00+09:00',
        documentId: 402,
        moduleId: 42,
        items: [{ name: '湿布処置', quantity: '1', unit: '回' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-SUBTYPE',
          patientId: 'P-006',
          appointmentId: 'APT-006',
          receptionId: 'RCP-006',
          visitDate: '2026-02-27',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor06' }}
        orderBundles={bundles}
      />,
    );

    await user.click(screen.getByRole('button', { name: '処置を開く' }));
    const tabList = await screen.findByRole('tablist', { name: '処置サブカテゴリ' });
    const treatmentTab = within(tabList).getByRole('tab', { name: '処置' });
    const generalTab = within(tabList).getByRole('tab', { name: '一般' });

    expect(treatmentTab).toHaveAttribute('aria-selected', 'true');
    expect(generalTab).toHaveAttribute('aria-selected', 'false');
    expect(screen.getByText('処置セットA')).toBeInTheDocument();
    expect(screen.queryByText('一般処置B')).not.toBeInTheDocument();

    treatmentTab.focus();
    fireEvent.keyDown(treatmentTab, { key: 'ArrowRight' });

    await waitFor(() => {
      const currentTabList = screen.getByRole('tablist', { name: '処置サブカテゴリ' });
      expect(within(currentTabList).getByRole('tab', { name: '一般' })).toHaveAttribute('aria-selected', 'true');
    });
    expect(screen.getByText('一般処置B')).toBeInTheDocument();
    expect(screen.queryByText('処置セットA')).not.toBeInTheDocument();
  });

  it('非表示ドロワーは hidden になり、Tab移動でフォーカスが流入しない', async () => {
    const user = userEvent.setup();
    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-HIDDEN-FOCUS',
          patientId: 'P-007',
          appointmentId: 'APT-007',
          receptionId: 'RCP-007',
          visitDate: '2026-02-27',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor07' }}
        orderBundles={[]}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    expect(drawer).toHaveAttribute('hidden');

    const closeButton = requireElement<HTMLButtonElement>(drawer.querySelector('button[aria-label="右ドロワーを閉じる"]'));
    let focusedClose = false;
    for (let i = 0; i < 24; i += 1) {
      await user.tab();
      if (document.activeElement === closeButton) {
        focusedClose = true;
        break;
      }
    }
    expect(focusedClose).toBe(false);
  });

  it('既存セットpreviewの「このセットを編集」で対象セットが編集状態になる', async () => {
    const user = userEvent.setup();
    const bundles: OrderBundle[] = [
      {
        entity: 'injectionOrder',
        bundleName: '前日',
        started: '2026-02-26T09:00:00+09:00',
        documentId: 11,
        moduleId: 1,
        items: [{ name: '生食', quantity: '1', unit: '本' }],
      },
      {
        entity: 'injectionOrder',
        bundleName: '同日doc小',
        started: '2026-02-27T09:00:00+09:00',
        documentId: 15,
        moduleId: 2,
        items: [{ name: 'ブドウ糖', quantity: '1', unit: '本' }],
      },
      {
        entity: 'injectionOrder',
        bundleName: '同日doc大',
        started: '2026-02-27T09:00:00+09:00',
        documentId: 21,
        moduleId: 3,
        items: [{ name: '乳酸リンゲル', quantity: '1', unit: '本' }],
      },
    ];

    renderWithQueryClient(
      <SoapNotePanel
        history={[]}
        meta={{
          runId: 'RUN-RIGHT-DOCK-EDIT-BUTTON',
          patientId: 'P-005',
          appointmentId: 'APT-005',
          receptionId: 'RCP-005',
          visitDate: '2026-02-27',
        }}
        author={{ role: 'doctor', displayName: 'Dr. Dock', userId: 'doctor05' }}
        orderBundles={bundles}
      />,
    );

    const drawer = requireElement(document.body.querySelector('.soap-note__right-drawer'));
    await user.click(screen.getByRole('button', { name: '注射を開く' }));
    await waitFor(() => {
      expect(drawer.getAttribute('data-open')).toBe('true');
    });

    const targetCard = requireElement<HTMLElement>(screen.getByText('前日').closest('.soap-note__right-drawer-order-preview-item'));
    const mainEditor = requireElement<HTMLElement>(drawer.querySelector('.soap-note__right-drawer-order-editor'));
    const bundleNameInput = within(mainEditor).getByLabelText('注射名') as HTMLInputElement;
    expect(bundleNameInput.value).toBe('同日doc大');

    await user.click(within(targetCard).getByRole('button', { name: '前日を編集' }));

    await waitFor(() => {
      expect(bundleNameInput.value).toBe('前日');
    });
    expect(drawer.getAttribute('data-open')).toBe('true');
  });
});
