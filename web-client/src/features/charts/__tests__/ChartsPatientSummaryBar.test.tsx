import { describe, expect, it } from 'vitest';
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
  it('note が空のときは患者メモパネルを表示しない', () => {
    const { container } = render(
      <ChartsPatientSummaryBar
        {...baseProps}
        patientDisplay={{
          ...baseProps.patientDisplay,
          note: undefined,
        }}
      />,
    );

    expect(screen.getByRole('button', { name: '診察開始' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '更新' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '閉じる' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '患者メモ' })).toBeNull();
    expect(screen.queryByText('患者メモなし')).toBeNull();
    expect(container.querySelector('.charts-patient-summary__layout')?.getAttribute('data-has-memo')).toBe('false');
  });

  it('note があるときは患者メモパネルを表示する', () => {
    const { container } = render(<ChartsPatientSummaryBar {...baseProps} />);

    expect(screen.getByRole('heading', { name: '患者メモ' })).toBeInTheDocument();
    expect(screen.getByText('転倒歴あり。採血時は左腕を優先。')).toBeInTheDocument();
    expect(container.querySelector('.charts-patient-summary__layout')?.getAttribute('data-has-memo')).toBe('true');
  });

  it('住所は詳細を開くまで表示されない', async () => {
    const user = userEvent.setup();
    const addressLabel = '東京都千代田区千代田1-1';
    const zipLabel = '〒100-0001';

    render(
      <ChartsPatientSummaryBar
        {...baseProps}
        patientDisplay={{
          ...baseProps.patientDisplay,
          note: undefined,
        }}
      />,
    );

    expect(screen.getByText(addressLabel)).not.toBeVisible();
    expect(screen.getByText(zipLabel)).not.toBeVisible();

    await user.click(screen.getByText('詳細'));

    expect(screen.getByText(addressLabel)).toBeVisible();
    expect(screen.getByText(zipLabel)).toBeVisible();
  });
});
