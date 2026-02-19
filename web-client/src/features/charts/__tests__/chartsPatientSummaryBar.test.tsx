import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ChartsPatientSummaryBar } from '../ChartsPatientSummaryBar';

vi.mock('../../shared/RunIdBadge', () => ({
  RunIdBadge: ({ runId, className }: { runId?: string; className?: string }) => (
    <span className={className} data-testid="runid-badge-mock">
      {runId ?? 'RUN'}
    </span>
  ),
}));

vi.mock('../../shared/PatientMetaRow', () => ({
  PatientMetaRow: () => <div data-testid="patient-meta-row-mock" />,
}));

const baseProps = {
  patientDisplay: {
    name: '山田 太郎',
    kana: 'ヤマダ タロウ',
    sex: '男',
    age: '45歳',
    birthDateEra: '昭和55年5月20日',
    birthDateIso: '1980-05-20',
    note: 'アレルギー: ペニシリン注意。転倒歴あり。',
    status: '診療中',
    department: '内科',
    physician: '田中医師',
    insurance: '国保',
    visitDate: '2026-02-16',
    appointmentTime: '09:30',
  },
  patientId: '000001',
  receptionId: 'R-001',
  appointmentId: 'A-001',
  runId: 'RUN-CHARTS',
  allergies: [
    { factor: 'ペニシリン', severity: '重度', identifiedDate: '2024-01-15', memo: '皮疹あり' },
    { factor: 'エビ', severity: '中等度', identifiedDate: '2022-11-03', memo: '発赤' },
  ],
};

describe('ChartsPatientSummaryBar', () => {
  it('初期表示は補助パネルを閉じ、診療操作ボタンを表示する', () => {
    render(<ChartsPatientSummaryBar {...baseProps} />);

    const memoPanel = document.getElementById('charts-patient-summary-memo');
    const allergiesPanel = document.getElementById('charts-patient-summary-allergies');

    expect(memoPanel).not.toBeNull();
    expect(allergiesPanel).not.toBeNull();
    expect(memoPanel?.hidden).toBe(true);
    expect(allergiesPanel?.hidden).toBe(true);
    expect(screen.getByRole('button', { name: '診察終了' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '診察中断' })).toBeInTheDocument();
  });

  it('ボタン操作で診療イベントとメモ/アレルギー展開が動作する', async () => {
    const user = userEvent.setup();
    const onFinishEncounter = vi.fn();
    const onPauseEncounter = vi.fn();
    render(<ChartsPatientSummaryBar {...baseProps} onFinishEncounter={onFinishEncounter} onPauseEncounter={onPauseEncounter} />);

    const memoPanel = document.getElementById('charts-patient-summary-memo');
    const allergiesPanel = document.getElementById('charts-patient-summary-allergies');

    await user.click(screen.getByRole('button', { name: '診察終了' }));
    await user.click(screen.getByRole('button', { name: '診察中断' }));
    await user.click(screen.getByRole('button', { name: /^アレルギー\/メモ:/ }));
    await user.click(screen.getByRole('button', { name: 'アレルギーあり（2）' }));

    expect(onFinishEncounter).toHaveBeenCalledTimes(1);
    expect(onPauseEncounter).toHaveBeenCalledTimes(1);
    expect(memoPanel?.hidden).toBe(false);
    expect(allergiesPanel?.hidden).toBe(false);
    expect(screen.getByText('ペニシリン')).toBeInTheDocument();
  });

  it('患者切替時に展開状態をリセットする', async () => {
    const user = userEvent.setup();
    const { rerender } = render(<ChartsPatientSummaryBar {...baseProps} />);

    await user.click(screen.getByRole('button', { name: /^アレルギー\/メモ:/ }));
    await user.click(screen.getByRole('button', { name: 'アレルギーあり（2）' }));

    rerender(
      <ChartsPatientSummaryBar
        {...baseProps}
        patientId="000002"
        appointmentId="A-002"
        patientDisplay={{
          ...baseProps.patientDisplay,
          name: '鈴木 花子',
        }}
      />,
    );

    expect(document.getElementById('charts-patient-summary-memo')?.hidden).toBe(true);
    expect(document.getElementById('charts-patient-summary-allergies')?.hidden).toBe(true);
  });
});
