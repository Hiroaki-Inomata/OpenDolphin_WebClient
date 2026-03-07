import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { LoginScreen, normalizeSessionResult } from '../LoginScreen';

describe('LoginScreen', () => {
  it('aria-labelledby が見出しIDと一致している', () => {
    const { container } = render(<LoginScreen />);

    const section = container.querySelector('section.login-card');
    const heading = screen.getByRole('heading', { level: 1, name: 'OpenDolphin Web ログイン' });

    expect(section).not.toBeNull();
    expect(section).toHaveAttribute('aria-labelledby', 'login-heading');
    expect(heading).toHaveAttribute('id', 'login-heading');
  });

  it('normalizeSessionResult は server の userPk を保持する', () => {
    const result = normalizeSessionResult(
      { facilityId: '0001', userId: 'doctor01', userPk: 101, roles: ['doctor'] },
      { facilityId: '0001', userId: 'doctor01', clientUuid: 'client-1', runId: 'RUN-1' },
    );

    expect(result.userPk).toBe(101);
  });
});
