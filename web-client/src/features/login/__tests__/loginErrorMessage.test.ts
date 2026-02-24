import { describe, expect, it } from 'vitest';

import { resolveLoginFailureMessage } from '../loginErrorMessage';

describe('resolveLoginFailureMessage', () => {
  it('maps unauthorized JSON to user-friendly credential guidance', () => {
    const message = resolveLoginFailureMessage({
      status: 401,
      bodyText: JSON.stringify({
        error: 'unauthorized',
        reason: 'authentication_failed',
        message: 'Authentication required',
      }),
    });

    expect(message).toContain('施設ID・ユーザーID・パスワード');
  });

  it('maps principal_unresolved to facility guidance', () => {
    const message = resolveLoginFailureMessage({
      status: 401,
      bodyText: JSON.stringify({
        error: 'unauthorized',
        reason: 'principal_unresolved',
      }),
    });

    expect(message).toContain('施設ID');
  });

  it('maps 403 to permission guidance', () => {
    const message = resolveLoginFailureMessage({
      status: 403,
      bodyText: JSON.stringify({
        error: 'forbidden',
      }),
    });

    expect(message).toContain('アクセス権限');
  });

  it('maps 404 to endpoint guidance', () => {
    const message = resolveLoginFailureMessage({
      status: 404,
      bodyText: '<!doctype html><html><body>Not Found</body></html>',
    });

    expect(message).toContain('ログイン先が見つかりません');
  });

  it('maps 5xx to retry guidance', () => {
    const message = resolveLoginFailureMessage({
      status: 500,
      bodyText: 'Internal Server Error',
    });

    expect(message).toContain('時間をおいて再試行');
  });
});

