import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { loadOutpatientSavedViews, upsertOutpatientSavedView } from '../savedViews';

const STORAGE_KEY = 'opendolphin:web-client:outpatient-saved-views:v1';

describe('savedViews security', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-04T00:00:00.000Z'));
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  it('keyword を含む view を保存しても永続化しない', () => {
    upsertOutpatientSavedView({
      label: '受付向け',
      filters: {
        keyword: '山田',
        department: '01',
        physician: 'D01',
        paymentMode: 'insurance',
        sort: 'name',
        date: '2026-03-04',
      },
    });

    const raw = localStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw ?? '[]') as Array<{ filters?: { keyword?: string } }>;
    expect(parsed[0]?.filters?.keyword).toBeUndefined();

    const loaded = loadOutpatientSavedViews();
    expect(loaded[0]?.filters.keyword).toBeUndefined();
  });

  it('旧データ(keywordあり)を load すると keyword を削除して再保存する', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify([
        {
          id: 'legacy-1',
          label: '旧データ',
          updatedAt: '2026-03-03T12:00:00.000Z',
          filters: {
            keyword: '佐藤',
            department: '02',
          },
        },
      ]),
    );

    const loaded = loadOutpatientSavedViews();
    expect(loaded[0]?.filters.keyword).toBeUndefined();

    const migratedRaw = localStorage.getItem(STORAGE_KEY);
    const migrated = JSON.parse(migratedRaw ?? '[]') as Array<{ filters?: { keyword?: string } }>;
    expect(migrated[0]?.filters?.keyword).toBeUndefined();
  });
});
