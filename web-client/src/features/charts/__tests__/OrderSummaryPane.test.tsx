import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

import { OrderSummaryPane } from '../OrderSummaryPane';

describe('OrderSummaryPane', () => {
  it('空カテゴリは非表示で文書カテゴリのみ表示し、レールボタンを出さない', () => {
    render(<OrderSummaryPane orderBundles={[]} prescriptionBundles={[]} />);

    const pane = screen.getByLabelText('オーダー概要');

    expect(pane.querySelector('.soap-note__order-group[data-group="document"]')).not.toBeNull();
    expect(pane.querySelector('.soap-note__order-group[data-group="prescription"]')).toBeNull();
    expect(pane.querySelector('.soap-note__order-group[data-group="injection"]')).toBeNull();
    expect(pane.querySelector('.soap-note__order-group[data-group="treatment"]')).toBeNull();
    expect(pane.querySelector('.soap-note__order-group[data-group="test"]')).toBeNull();
    expect(pane.querySelector('.soap-note__order-group[data-group="charge"]')).toBeNull();

    expect(screen.getByRole('button', { name: '文書を編集' })).toBeInTheDocument();
    expect(pane.querySelector('.soap-note__order-group-rail')).toBeNull();
    expect(pane.querySelector('.soap-note__right-dock-button')).toBeNull();
    expect(screen.queryByText('該当オーダーなし')).toBeNull();
  });
});
