import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import type { ReactElement } from 'react';

import { OrderBundleEditPanel } from '../OrderBundleEditPanel';
import {
  clearStampClipboard,
  deleteLocalStamp,
  loadLocalStamps,
  loadStampClipboard,
  saveStampClipboard,
  saveLocalStamp,
  updateLocalStamp,
} from '../stampStorage';

const FACILITY_ID = '0001';
const USER_ID = 'user01';

vi.mock('../orderBundleApi', async () => ({
  fetchOrderBundles: vi.fn().mockResolvedValue({
    ok: true,
    bundles: [],
    patientId: 'P-1',
  }),
  mutateOrderBundles: vi.fn(),
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

beforeEach(() => {
  localStorage.setItem('devFacilityId', FACILITY_ID);
  localStorage.setItem('devUserId', USER_ID);
  sessionStorage.clear();
});

afterEach(() => {
  cleanup();
  localStorage.clear();
  sessionStorage.clear();
  vi.clearAllMocks();
});

describe('OrderBundleEditPanel stamp placement', () => {
  it('オーダー入力UIにはスタンプ編集UIを表示しない', () => {
    renderWithClient(<OrderBundleEditPanel {...baseProps} />);

    expect(screen.queryByText('スタンプ保存/取り込み')).toBeNull();
    expect(screen.queryByLabelText('スタンプ名称')).toBeNull();
    expect(screen.queryByRole('button', { name: 'スタンプ保存' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'スタンプ取り込み' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'スタンプコピー' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'スタンプペースト' })).toBeNull();

    expect(screen.getByText('頻用オーダー（患者優先）')).toBeInTheDocument();
  });
});

describe('stampStorage helpers', () => {
  const userName = 'facility:doctor';

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('loadLocalStamps は不正JSONを空配列にフォールバックする', () => {
    localStorage.setItem(`web-client:order-stamps:${userName}`, 'not-json');
    expect(loadLocalStamps(userName)).toEqual([]);
  });

  it('loadLocalStamps はレガシーキーを移行する', () => {
    const legacyKey = 'web-client:order-stamps::';
    localStorage.setItem(
      legacyKey,
      JSON.stringify([
        {
          id: 'legacy-1',
          name: '移行スタンプ',
          category: 'テスト',
          target: 'medOrder',
          entity: 'medOrder',
          savedAt: new Date().toISOString(),
          bundle: {
            bundleName: '移行スタンプ',
            admin: '',
            bundleNumber: '1',
            adminMemo: '',
            memo: '',
            startDate: '2026-01-01',
            items: [],
          },
        },
      ]),
    );

    const migrated = loadLocalStamps(userName);
    expect(migrated).toHaveLength(1);
    expect(localStorage.getItem(`web-client:order-stamps:${userName}`)).toContain('移行スタンプ');
  });

  it('loadStampClipboard はレガシークリップボードを移行する', () => {
    const legacyKey = 'web-client:order-stamps:clipboard::';
    sessionStorage.setItem(
      legacyKey,
      JSON.stringify({
        savedAt: new Date().toISOString(),
        source: 'local',
        name: '旧クリップ',
        category: '',
        target: 'medOrder',
        entity: 'medOrder',
        bundle: {
          bundleName: '旧クリップ',
          admin: '',
          bundleNumber: '1',
          adminMemo: '',
          memo: '',
          startDate: '2026-01-01',
          items: [],
        },
      }),
    );

    const loaded = loadStampClipboard(userName);
    expect(loaded?.name).toBe('旧クリップ');
    expect(sessionStorage.getItem(`web-client:order-stamps:clipboard:${userName}`)).toContain('旧クリップ');
  });

  it('clearStampClipboard は保存済みキーを削除する', () => {
    saveStampClipboard(userName, {
      savedAt: new Date().toISOString(),
      source: 'local',
      name: 'クリップ',
      category: '',
      target: 'medOrder',
      entity: 'medOrder',
      bundle: {
        bundleName: 'クリップ',
        admin: '',
        bundleNumber: '1',
        adminMemo: '',
        memo: '',
        startDate: '2026-01-01',
        items: [],
      },
    });
    clearStampClipboard(userName);
    expect(loadStampClipboard(userName)).toBeNull();
  });

  it('updateLocalStamp は既存スタンプを更新する', () => {
    const saved = saveLocalStamp(userName, {
      name: '更新前',
      category: '分類A',
      target: 'medOrder',
      entity: 'medOrder',
      bundle: {
        bundleName: '更新前',
        admin: '',
        bundleNumber: '1',
        adminMemo: '',
        memo: '',
        startDate: '2026-01-01',
        items: [{ name: '項目A', quantity: '1', unit: '錠', memo: '' }],
      },
    });

    const updated = updateLocalStamp(userName, saved.id, {
      name: '更新後',
      category: '分類B',
      target: 'medOrder',
      entity: 'medOrder',
      bundle: {
        ...saved.bundle,
        bundleName: '更新後',
        items: [{ name: '項目B', quantity: '2', unit: '錠', memo: '' }],
      },
    });

    expect(updated?.name).toBe('更新後');
    expect(loadLocalStamps(userName)[0]?.name).toBe('更新後');
  });

  it('deleteLocalStamp は対象スタンプを削除する', () => {
    const saved = saveLocalStamp(userName, {
      name: '削除対象',
      category: '分類A',
      target: 'medOrder',
      entity: 'medOrder',
      bundle: {
        bundleName: '削除対象',
        admin: '',
        bundleNumber: '1',
        adminMemo: '',
        memo: '',
        startDate: '2026-01-01',
        items: [{ name: '項目A', quantity: '1', unit: '錠', memo: '' }],
      },
    });

    const removed = deleteLocalStamp(userName, saved.id);

    expect(removed).toBe(true);
    expect(loadLocalStamps(userName)).toHaveLength(0);
  });
});
