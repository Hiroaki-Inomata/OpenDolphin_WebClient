import { beforeEach, describe, expect, it, vi } from 'vitest';

import { httpFetch } from '../../libs/http/httpClient';
import { resetAccessUserPassword } from './accessManagementApi';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

const mockHttpFetch = vi.mocked(httpFetch);

beforeEach(() => {
  mockHttpFetch.mockReset();
});

describe('accessManagementApi', () => {
  it('パスワードリセットは 204 No Content を本文なしで成功扱いにする', async () => {
    mockHttpFetch.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(
      resetAccessUserPassword(101, {
        totpCode: '123456',
        temporaryPassword: 'TempPass#2026',
      }),
    ).resolves.toBeUndefined();

    expect(mockHttpFetch).toHaveBeenCalledWith(
      '/api/admin/access/users/101/password-reset',
      expect.objectContaining({
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        notifySessionExpired: false,
      }),
    );
    expect(mockHttpFetch.mock.calls[0]?.[1]?.body).toBe(
      JSON.stringify({
        totpCode: '123456',
        temporaryPassword: 'TempPass#2026',
      }),
    );
  });
});
