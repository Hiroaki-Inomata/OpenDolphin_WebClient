import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

import { SecurityMisconfigBanner } from '../SecurityMisconfigBanner';

vi.mock('../../libs/security/csrf', () => ({
  readCsrfToken: vi.fn(() => undefined),
}));

describe('SecurityMisconfigBanner', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('PROD 相当かつ CSRF token 未設定のときバナーを表示する', () => {
    vi.stubEnv('PROD', true);

    render(<SecurityMisconfigBanner />);

    expect(screen.getByRole('alert')).toHaveTextContent('セキュリティトークン未設定のため更新操作ができません。管理者に連絡してください。');
  });
});
