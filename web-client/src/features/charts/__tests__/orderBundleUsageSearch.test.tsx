import { afterEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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

describe('OrderBundleEditPanel usage search UI', () => {
  it('用法入力欄に入力した文字列で候補を選択できる', async () => {
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

    const usageInput = screen.getByLabelText('用法') as HTMLInputElement;
    await user.type(usageInput, '朝');

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'youhou', keyword: '朝', effective: '2026-02-19' }),
      ),
    );
    expect(screen.getByText('上限日数')).toBeInTheDocument();
    expect(screen.getByText('1日量 / 備考')).toBeInTheDocument();
    expect(screen.getByText('朝夕の2回投与', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('1日2回 朝夕食後')).toBeInTheDocument();

    await user.click(screen.getByText('1日2回 朝夕食後').closest('button')!);
    expect(usageInput.value).toBe('0010002 1日2回 朝夕食後');
  });

  it('用法欄の入力候補から blur で補完できる', async () => {
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
              unit: '',
            },
          ],
          totalCount: 1,
        };
      }
      return { ok: true, items: [], totalCount: 0 };
    });

    const user = userEvent.setup();
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    const usageInput = screen.getByLabelText('用法') as HTMLInputElement;
    await user.type(usageInput, '0010001 1日1回 朝食後');
    await user.tab();

    await waitFor(() =>
      expect(searchMock).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'youhou', keyword: '0010001 1日1回 朝食後' }),
      ),
    );
    expect(usageInput.value).toBe('0010001 1日1回 朝食後');
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
  });
});
