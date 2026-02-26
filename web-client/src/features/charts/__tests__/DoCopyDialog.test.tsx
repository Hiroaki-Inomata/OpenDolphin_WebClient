import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { DoCopyDialog, type DoCopyDialogState } from '../DoCopyDialog';

describe('DoCopyDialog overwrite behavior', () => {
  it('未選択時は転記元があるセクションのみ既定選択して適用する', async () => {
    const user = userEvent.setup();
    const onApply = vi.fn();
    const onUndo = vi.fn();
    const onClose = vi.fn();
    const state: DoCopyDialogState = {
      open: true,
      applied: false,
      selectedSections: [],
      sections: [
        {
          section: 'subjective',
          source: { authoredAt: '2026-02-26T09:00:00Z', authorRole: 'doctor', body: '転記元S' },
          target: { body: '転記先S' },
        },
        {
          section: 'objective',
          source: { authoredAt: '2026-02-26T09:00:00Z', authorRole: 'doctor', body: '' },
          target: { body: '転記先O' },
        },
      ],
    };

    render(<DoCopyDialog state={state} onApply={onApply} onUndo={onUndo} onClose={onClose} />);
    await user.click(screen.getByRole('button', { name: '適用' }));

    expect(onApply).toHaveBeenCalledWith(['subjective']);
    expect(onUndo).not.toHaveBeenCalled();
  });

  it('Do入力の上書き対象はチェックしたセクションに限定できる', async () => {
    const user = userEvent.setup();
    const onApply = vi.fn();
    const onUndo = vi.fn();
    const onClose = vi.fn();
    const state: DoCopyDialogState = {
      open: true,
      applied: false,
      selectedSections: ['subjective', 'objective'],
      sections: [
        {
          section: 'subjective',
          source: { authoredAt: '2026-02-26T09:00:00Z', authorRole: 'doctor', body: '転記元S' },
          target: { body: '転記先S' },
        },
        {
          section: 'objective',
          source: { authoredAt: '2026-02-26T09:00:00Z', authorRole: 'doctor', body: '転記元O' },
          target: { body: '転記先O' },
        },
      ],
    };

    render(<DoCopyDialog state={state} onApply={onApply} onUndo={onUndo} onClose={onClose} />);

    await user.click(screen.getByRole('checkbox', { name: 'Objective' }));
    await user.click(screen.getByRole('button', { name: '適用' }));

    expect(onApply).toHaveBeenCalledWith(['subjective']);
    expect(onUndo).not.toHaveBeenCalled();
  });
});
