import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

import { DirtyStateBar } from '../components/DirtyStateBar';

describe('DirtyStateBar', () => {
  it('dirty 状態を表示する', () => {
    render(<DirtyStateBar dirty updatedAt="2026-02-16T12:34:56Z" />);

    expect(screen.getByText('変更あり（未保存）')).toBeInTheDocument();
    expect(screen.getByText(/最終保存:/)).toBeInTheDocument();
  });

  it('clean 状態を表示する', () => {
    render(<DirtyStateBar dirty={false} updatedAt="" />);

    expect(screen.getByText('保存済み')).toBeInTheDocument();
  });
});
