import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { buildScopedStorageKey } from '../../../libs/session/storageScope';
import {
  PATIENT_TABS_STORAGE_BASE,
  PATIENT_TABS_STORAGE_VERSION,
  readChartsPatientTabsStorage,
  writeChartsPatientTabsStorage,
  type ChartsPatientTabsStorage,
} from '../patientTabsStorage';

const scope = { facilityId: '0001', userId: 'doctor01' };
const storageKey =
  buildScopedStorageKey(PATIENT_TABS_STORAGE_BASE, PATIENT_TABS_STORAGE_VERSION, scope) ??
  `${PATIENT_TABS_STORAGE_BASE}:${PATIENT_TABS_STORAGE_VERSION}`;

describe('patientTabsStorage security', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-04T00:00:00.000Z'));
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('write で name/department を永続化しない', () => {
    const state: ChartsPatientTabsStorage = {
      version: 1,
      updatedAt: '2026-03-04T00:00:00.000Z',
      savedAt: '2026-03-04T00:00:00.000Z',
      activeKey: 'P-001::2026-03-04',
      tabs: [
        {
          key: 'P-001::2026-03-04',
          patientId: 'P-001',
          visitDate: '2026-03-04',
          openedAt: '2026-03-04T00:00:00.000Z',
          name: '山田 太郎',
          department: '内科',
        },
      ],
    };

    writeChartsPatientTabsStorage(state, scope);

    const raw = sessionStorage.getItem(storageKey);
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw ?? '{}') as ChartsPatientTabsStorage;
    expect(parsed.tabs[0]).not.toHaveProperty('name');
    expect(parsed.tabs[0]).not.toHaveProperty('department');
  });

  it('read で旧データの name/department を破棄して復元する', () => {
    sessionStorage.setItem(
      storageKey,
      JSON.stringify({
        version: 1,
        updatedAt: '2026-03-04T00:10:00.000Z',
        savedAt: '2026-03-04T00:10:00.000Z',
        activeKey: 'P-001::2026-03-04',
        tabs: [
          {
            key: 'P-001::2026-03-04',
            patientId: 'P-001',
            visitDate: '2026-03-04',
            openedAt: '2026-03-04T00:10:00.000Z',
            name: '旧氏名',
            department: '旧科',
          },
        ],
      }),
    );

    const restored = readChartsPatientTabsStorage(scope);
    expect(restored).not.toBeNull();
    expect(restored?.tabs[0]).not.toHaveProperty('name');
    expect(restored?.tabs[0]).not.toHaveProperty('department');
  });

  it('TTL 超過データは read 時に削除して null を返す', () => {
    sessionStorage.setItem(
      storageKey,
      JSON.stringify({
        version: 1,
        updatedAt: '2026-03-03T20:00:00.000Z',
        savedAt: '2026-03-03T20:00:00.000Z',
        activeKey: 'P-001::2026-03-03',
        tabs: [
          {
            key: 'P-001::2026-03-03',
            patientId: 'P-001',
            visitDate: '2026-03-03',
            openedAt: '2026-03-03T20:00:00.000Z',
          },
        ],
      }),
    );

    const restored = readChartsPatientTabsStorage(scope);
    expect(restored).toBeNull();
    expect(sessionStorage.getItem(storageKey)).toBeNull();
  });
});
