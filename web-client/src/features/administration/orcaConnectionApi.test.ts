import { beforeEach, describe, expect, it, vi } from 'vitest';

import { httpFetch } from '../../libs/http/httpClient';
import { fetchOrcaConnectionConfig, saveOrcaConnectionConfig, testOrcaConnection } from './orcaConnectionApi';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

vi.mock('../../libs/observability/observability', () => ({
  ensureObservabilityMeta: vi.fn(() => ({ runId: 'RUN-BEFORE', traceId: 'TRACE-BEFORE' })),
  getObservabilityMeta: vi.fn(() => ({ runId: 'RUN-AFTER', traceId: 'TRACE-AFTER' })),
}));

const mockHttpFetch = vi.mocked(httpFetch);

beforeEach(() => {
  mockHttpFetch.mockReset();
});

describe('orcaConnectionApi', () => {
  it('設定取得で notifySessionExpired=false を指定する', async () => {
    mockHttpFetch.mockResolvedValue(
      new Response(JSON.stringify({ ok: false, error: 'unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrcaConnectionConfig();

    expect(mockHttpFetch).toHaveBeenCalledWith(
      '/api/admin/orca/connection',
      expect.objectContaining({
        method: 'GET',
        notifySessionExpired: false,
      }),
    );
    expect(result.status).toBe(401);
    expect(result.ok).toBe(false);
  });

  it('設定保存で notifySessionExpired=false を指定する', async () => {
    mockHttpFetch.mockResolvedValue(
      new Response(JSON.stringify({ ok: false, error: 'unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await saveOrcaConnectionConfig({
      useWeborca: true,
      serverUrl: 'https://example.invalid',
      port: 443,
      username: 'trial',
      password: 'secret',
      clientAuthEnabled: false,
    });

    expect(mockHttpFetch).toHaveBeenCalledWith(
      '/api/admin/orca/connection',
      expect.objectContaining({
        method: 'PUT',
        notifySessionExpired: false,
        body: expect.any(FormData),
      }),
    );
    expect(result.status).toBe(401);
    expect(result.ok).toBe(false);
  });

  it('接続テストで notifySessionExpired=false を指定する', async () => {
    mockHttpFetch.mockResolvedValue(
      new Response(JSON.stringify({ ok: false, error: 'unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await testOrcaConnection();

    expect(mockHttpFetch).toHaveBeenCalledWith(
      '/api/admin/orca/connection/test',
      expect.objectContaining({
        method: 'POST',
        notifySessionExpired: false,
      }),
    );
    expect(result.status).toBe(401);
    expect(result.ok).toBe(false);
  });
});

