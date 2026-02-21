import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { FocusTrapDialog } from '../FocusTrapDialog';

describe('FocusTrapDialog', () => {
  it('closeOnBackdrop=false の場合は背景クリックで閉じない', () => {
    const onClose = vi.fn();
    render(
      <FocusTrapDialog open title="確認" onClose={onClose} closeOnBackdrop={false} testId="dialog-backdrop">
        <button type="button">実行</button>
      </FocusTrapDialog>,
    );

    const backdrop = document.querySelector('[data-test-id="dialog-backdrop"]');
    expect(backdrop).not.toBeNull();
    fireEvent.mouseDown(backdrop as Element);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('showCloseButton=false の場合は閉じるボタンを表示しない', () => {
    render(
      <FocusTrapDialog open title="確認" onClose={vi.fn()} showCloseButton={false}>
        <button type="button">実行</button>
      </FocusTrapDialog>,
    );

    expect(screen.queryByRole('button', { name: 'ダイアログを閉じる' })).not.toBeInTheDocument();
  });

  it('closeOnEscape=false の場合は Esc で閉じない', () => {
    const onClose = vi.fn();
    render(
      <FocusTrapDialog open title="確認" onClose={onClose} closeOnEscape={false}>
        <button type="button">実行</button>
      </FocusTrapDialog>,
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('既定では Esc で閉じる', () => {
    const onClose = vi.fn();
    render(
      <FocusTrapDialog open title="確認" onClose={onClose}>
        <button type="button">実行</button>
      </FocusTrapDialog>,
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
