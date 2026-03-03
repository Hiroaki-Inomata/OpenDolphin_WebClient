/* @vitest-environment node */
import { afterEach, describe, expect, it, vi } from 'vitest';

import { httpFetch } from '../../libs/http/httpClient';
import {
  TOUCH_ADM_PHR_DISABLED_MESSAGE,
  TOUCH_ADM_PHR_ENDPOINTS,
  buildTouchAdmPhrUrl,
  requestTouchAdmPhr,
} from './touchAdmPhrApi';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe('touchAdmPhrApi (disabled mode)', () => {
  it('requestTouchAdmPhr は通信せず disabled/404 を返す', async () => {
    const response = await requestTouchAdmPhr({
      method: 'POST',
      path: '/touch/disabled',
      query: 'offset=0&limit=10',
      body: '{}',
      contentType: 'json',
      accept: 'application/json',
    });

    expect(response.ok).toBe(false);
    expect(response.status).toBe(404);
    expect(response.statusText).toBe('Not Found');
    expect(response.mode).toBe('json');
    expect(response.contentType).toBe('application/json');
    expect(response.raw).toContain('disabled');
    expect(response.raw).toContain(TOUCH_ADM_PHR_DISABLED_MESSAGE);
    expect(response.runId).toBeTruthy();
    expect(response.traceId).toBeTruthy();
    expect(httpFetch).not.toHaveBeenCalled();
  });

  it('endpoint 定義は disabled reason を持つ', () => {
    expect(TOUCH_ADM_PHR_ENDPOINTS.length).toBeGreaterThan(0);
    expect(TOUCH_ADM_PHR_ENDPOINTS.every((endpoint) => Boolean(endpoint.disabledReason))).toBe(true);

    const endpoint = TOUCH_ADM_PHR_ENDPOINTS[0];
    expect(endpoint?.buildPath({})).toBe('/touch/disabled');
    expect(buildTouchAdmPhrUrl('/touch/disabled')).toContain('/touch/disabled');
  });
});
