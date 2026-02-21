import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { LoginScreen } from '../LoginScreen';

describe('LoginScreen', () => {
  it('aria-labelledby が見出しIDと一致している', () => {
    const { container } = render(<LoginScreen />);

    const section = container.querySelector('section.login-card');
    const heading = screen.getByRole('heading', { level: 1, name: 'OpenDolphin Web ログイン' });

    expect(section).not.toBeNull();
    expect(section).toHaveAttribute('aria-labelledby', 'login-heading');
    expect(heading).toHaveAttribute('id', 'login-heading');
  });
});
