import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ChartsPatientSummaryBar } from '../ChartsPatientSummaryBar';

const baseProps = {
  patientDisplay: {
    name: '山田 太郎',
    kana: 'ヤマダ タロウ',
    sex: '男',
    age: '45歳6ヶ月',
    birthDateIso: '1980-05-20',
    zip: '100-0001',
    address: '東京都千代田区千代田1-1',
    note: '転倒歴あり。採血時は左腕を優先。',
  },
  patientId: '000001',
  runId: 'RUN-CHARTS',
};

describe('ChartsPatientSummaryBar', () => {
  it('必要な患者情報と操作ボタンを表示する', () => {
    render(<ChartsPatientSummaryBar {...baseProps} />);

    expect(screen.getByRole('button', { name: '診察開始' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '閉じる' })).toBeInTheDocument();

    expect(screen.getByText('診察券番号')).toBeInTheDocument();
    expect(screen.getByText('000001')).toBeInTheDocument();
    expect(screen.getByText('山田 太郎')).toBeInTheDocument();
    expect(screen.getByText('ヤマダ タロウ')).toBeInTheDocument();
    expect(screen.getByText('性別')).toBeInTheDocument();
    expect(screen.getByText('男')).toBeInTheDocument();
    expect(screen.getByText('45歳6ヶ月')).toBeInTheDocument();
    expect(screen.getByText('1980-05-20')).toBeInTheDocument();
    expect(screen.getByText(/〒100-0001/)).toBeInTheDocument();
    expect(screen.getByText(/東京都千代田区千代田1-1/)).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: '患者メモ' })).toBeInTheDocument();
    expect(screen.getByText('転倒歴あり。採血時は左腕を優先。')).toBeInTheDocument();

    expect(screen.queryByRole('button', { name: '診察終了' })).toBeNull();
    expect(screen.queryByRole('button', { name: '診察中断' })).toBeNull();
    expect(screen.queryByRole('button', { name: '詳細を開く' })).toBeNull();
  });

  it('診察開始とカルテを閉じる操作を呼び出せる', async () => {
    const user = userEvent.setup();
    const onStartEncounter = vi.fn();
    const onCloseChart = vi.fn();
    render(
      <ChartsPatientSummaryBar
        {...baseProps}
        onStartEncounter={onStartEncounter}
        onCloseChart={onCloseChart}
      />,
    );

    await user.click(screen.getByRole('button', { name: '診察開始' }));
    await user.click(screen.getByRole('button', { name: '閉じる' }));

    expect(onStartEncounter).toHaveBeenCalledTimes(1);
    expect(onCloseChart).toHaveBeenCalledTimes(1);
  });

  it('郵便番号と住所は値があるときだけ表示する', () => {
    render(
      <ChartsPatientSummaryBar
        {...baseProps}
        patientDisplay={{
          ...baseProps.patientDisplay,
          zip: undefined,
          address: undefined,
          note: undefined,
        }}
      />,
    );

    expect(screen.queryByText('郵便番号')).toBeNull();
    expect(screen.queryByText('住所')).toBeNull();
    expect(screen.getByText('患者メモなし')).toBeInTheDocument();
  });

  it('性別コードは表示用ラベルへ正規化する', () => {
    render(
      <ChartsPatientSummaryBar
        {...baseProps}
        patientDisplay={{
          ...baseProps.patientDisplay,
          sex: '1',
        }}
      />,
    );

    expect(screen.getByText('性別')).toBeInTheDocument();
    expect(screen.getByText('男')).toBeInTheDocument();
    expect(screen.queryByText('性別: 1')).toBeNull();
  });
});
