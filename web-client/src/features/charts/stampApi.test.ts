/* @vitest-environment node */
import { afterEach, describe, expect, it, vi } from 'vitest';

import { httpFetch } from '../../libs/http/httpClient';
import { fetchStampDetail, fetchStampTree } from './stampApi';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe('stampApi (touch disabled mode)', () => {
  it('fetchStampTree は /touch を呼ばず 404 を返す', async () => {
    const result = await fetchStampTree(1);

    expect(result.ok).toBe(false);
    expect(result.status).toBe(404);
    expect(result.trees).toEqual([]);
    expect(result.message).toContain('無効化');
    expect(result.runId).toBeTruthy();
    expect(httpFetch).not.toHaveBeenCalled();
  });

  it('fetchStampDetail は /touch を呼ばず 404 を返す', async () => {
    const result = await fetchStampDetail('STAMP-1');

    expect(result.ok).toBe(false);
    expect(result.status).toBe(404);
    expect(result.stampId).toBe('STAMP-1');
    expect(result.stamp).toBeUndefined();
    expect(result.message).toContain('無効化');
    expect(result.runId).toBeTruthy();
    expect(httpFetch).not.toHaveBeenCalled();
  });
});
