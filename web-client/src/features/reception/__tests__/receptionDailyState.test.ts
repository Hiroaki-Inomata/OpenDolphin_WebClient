import { beforeEach, describe, expect, it } from 'vitest';

import type { ReceptionEntry } from '../../outpatient/types';
import {
  listReceptionSnapshotDates,
  resolveReceptionEntriesForDate,
  upsertReceptionStatusOverride,
} from '../receptionDailyState';

const buildEntry = (overrides: Partial<ReceptionEntry> = {}): ReceptionEntry => ({
  id: overrides.id ?? 'row-1',
  appointmentId: overrides.appointmentId,
  receptionId: overrides.receptionId ?? 'R-001',
  patientId: overrides.patientId ?? 'P-001',
  name: overrides.name ?? '山田太郎',
  status: overrides.status ?? '受付中',
  visitDate: overrides.visitDate ?? '2026-02-11',
  appointmentTime: overrides.appointmentTime ?? '09:00',
  acceptanceTime: overrides.acceptanceTime ?? '09:00',
  source: overrides.source ?? 'visits',
  department: overrides.department ?? '内科',
  physician: overrides.physician ?? '10001',
  insurance: overrides.insurance ?? '保険',
  kana: overrides.kana,
  birthDate: overrides.birthDate,
  sex: overrides.sex,
  reservationTime: overrides.reservationTime,
  note: overrides.note,
});

describe('receptionDailyState', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('stores daily entries and restores them when incoming entries are empty', () => {
    const date = '2026-02-11';
    const first = resolveReceptionEntriesForDate({
      date,
      incomingEntries: [buildEntry()],
    });
    expect(first.source).toBe('live');
    expect(first.entries).toHaveLength(1);

    const restored = resolveReceptionEntriesForDate({
      date,
      incomingEntries: [],
    });
    expect(restored.source).toBe('snapshot');
    expect(restored.entries).toHaveLength(1);
    expect(restored.entries[0]?.patientId).toBe('P-001');
  });

  it('keeps higher-priority status when demotion is not allowed', () => {
    const date = '2026-02-11';
    resolveReceptionEntriesForDate({
      date,
      incomingEntries: [buildEntry({ status: '受付中' })],
    });

    upsertReceptionStatusOverride({
      date,
      patientId: 'P-001',
      status: '診療中',
      source: 'charts_open',
    });
    upsertReceptionStatusOverride({
      date,
      patientId: 'P-001',
      status: '受付中',
      source: 'manual',
    });

    const resolved = resolveReceptionEntriesForDate({
      date,
      incomingEntries: [],
    });
    expect(resolved.entries[0]?.status).toBe('診療中');
  });

  it('returns snapshot dates in descending order', () => {
    resolveReceptionEntriesForDate({
      date: '2026-02-10',
      incomingEntries: [buildEntry({ id: 'row-a', patientId: 'P-A', visitDate: '2026-02-10' })],
    });
    resolveReceptionEntriesForDate({
      date: '2026-02-11',
      incomingEntries: [buildEntry({ id: 'row-b', patientId: 'P-B', visitDate: '2026-02-11' })],
    });

    expect(listReceptionSnapshotDates(undefined, 10).slice(0, 2)).toEqual(['2026-02-11', '2026-02-10']);
  });
});

