import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  mutateOrderBundles: vi.fn().mockResolvedValue({
    ok: true,
    createdDocumentIds: [1001],
  }),
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

describe('OrderBundleEditPanel usage search UI', () => {
  it('入力なしで名称プルダウンから用法候補を選択できる', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type }) => {
      if (type === 'youhou') {
        return {
          ok: true,
          items: [
            {
              type: 'youhou',
              code: '0010001',
              name: '1日1回 朝食後',
              timingCode: '01',
              routeCode: 'PO',
              daysLimit: 7,
              dosePerDay: 1,
              note: '朝のみ',
            },
            {
              type: 'youhou',
              code: '0010002',
              name: '1日2回 朝夕食後',
              timingCode: '05',
              routeCode: 'PO',
              daysLimit: 14,
              dosePerDay: 2,
              note: '朝夕の2回投与',
            },
          ],
          totalCount: 2,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);
    fireEvent.change(screen.getByLabelText('開始日'), { target: { value: '2026-02-19' } });

    const usageSelect = screen.getByLabelText('用法') as HTMLSelectElement;

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'youhou',
          keyword: '',
          allowEmpty: true,
          effective: '2026-02-19',
        }),
      ),
    );
    expect(screen.queryByText('1日量 / 備考')).not.toBeInTheDocument();

    let targetOptionValue = '';
    await waitFor(() => {
      const targetOption = Array.from(usageSelect.options).find((option) => option.text === '1日2回 朝夕食後');
      expect(targetOption).toBeDefined();
      targetOptionValue = targetOption?.value ?? '';
      expect(targetOptionValue).not.toBe('');
    });

    await user.selectOptions(usageSelect, targetOptionValue);
    expect(usageSelect.selectedOptions[0]?.text).toBe('1日2回 朝夕食後');
    expect(screen.getByText('タイミング: 毎食後 / 経路: 内服 / 上限日数: 14 / 1日量目安: 2')).toBeInTheDocument();
    expect(screen.getByText('用法マスタ上限日数: 14日')).toBeInTheDocument();
  });

  it('最近使った用法セレクトから選択しても候補と整合する', async () => {
    localStorage.setItem('devFacilityId', 'facility');
    localStorage.setItem('devUserId', 'doctor');
    localStorage.setItem('charts-order-recent-usage:unknown-facility:unknown-user:medOrder', JSON.stringify(['1日1回 朝食後']));
    const searchMock = vi.mocked(fetchOrderMasterSearch);
    searchMock.mockImplementation(async ({ type }) => {
      if (type === 'youhou') {
        return {
          ok: true,
          items: [
            {
              type: 'youhou',
              code: '0010001',
              name: '1日1回 朝食後',
              timingCode: '01',
              routeCode: 'PO',
              daysLimit: 7,
              dosePerDay: 1,
            },
          ],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'youhou', keyword: '', allowEmpty: true }),
      ),
    );

    const usageSelect = screen.getByLabelText('用法') as HTMLSelectElement;
    await user.selectOptions(screen.getByLabelText('最近使った用法'), '1日1回 朝食後');
    await waitFor(() => expect(usageSelect.selectedOptions[0]?.text).toBe('1日1回 朝食後'));
    expect(screen.getByText('タイミング: 朝 / 経路: 内服 / 上限日数: 7 / 1日量目安: 1')).toBeInTheDocument();
  });

  it('readOnly の場合は用法検索が無効化される', async () => {
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

    expect(screen.getByLabelText('用法')).toBeDisabled();
    expect(fetchOrderBundles).toHaveBeenCalled();
    expect(fetchOrderMasterSearch).not.toHaveBeenCalled();
    expect(mutateOrderBundles).not.toHaveBeenCalled();
  });
});
