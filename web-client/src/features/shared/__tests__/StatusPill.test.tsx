import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { StatusPill } from '../StatusPill';

const getPill = () => {
  const value = screen.getByText('稼働');
  return value.closest('.status-pill');
};

describe('StatusPill', () => {
  it('デフォルトでは live region 属性を付与しない', () => {
    render(<StatusPill label="状態" value="稼働" />);

    const pill = getPill();
    expect(pill).not.toBeNull();
    expect(pill).not.toHaveAttribute('role');
    expect(pill).not.toHaveAttribute('aria-live');
    expect(pill).not.toHaveAttribute('aria-atomic');
  });

  it('ariaLive 指定時のみ live region として扱う', () => {
    render(<StatusPill label="状態" value="稼働" ariaLive="polite" />);

    const pill = getPill();
    expect(pill).not.toBeNull();
    expect(pill).toHaveAttribute('role', 'status');
    expect(pill).toHaveAttribute('aria-live', 'polite');
    expect(pill).toHaveAttribute('aria-atomic', 'true');
  });

  it('ariaLive=off の明示時は live region 属性を付与しない', () => {
    render(<StatusPill label="状態" value="稼働" ariaLive="off" />);

    const pill = getPill();
    expect(pill).not.toBeNull();
    expect(pill).not.toHaveAttribute('role');
    expect(pill).not.toHaveAttribute('aria-live');
    expect(pill).not.toHaveAttribute('aria-atomic');
  });
});
