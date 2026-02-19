import { beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchOrderMasterSearch } from './orderMasterSearchApi';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

vi.mock('../../libs/observability/observability', () => ({
  ensureObservabilityMeta: vi.fn(() => ({ runId: 'RUN-TEST' })),
  getObservabilityMeta: vi.fn(() => ({ runId: 'RUN-TEST' })),
}));

vi.mock('./orcaMedicationGetApi', () => ({
  buildMedicationGetRequestXml: vi.fn(() => '<xml />'),
  fetchOrcaMedicationGetXml: vi.fn(async () => ({
    ok: true,
    apiResult: '00',
    apiResultMessage: 'ok',
    medication: null,
    selections: [],
  })),
}));

describe('fetchOrderMasterSearch auth routing', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem('devFacilityId', 'f001');
    localStorage.setItem('devUserId', 'user01');
    localStorage.setItem('devPasswordPlain', 'plainpass');
  });

  it('uses connected account authorization header (drug)', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'drug', keyword: 'アム' });

    expect(result.ok).toBe(true);
    expect(vi.mocked(httpFetch).mock.calls[0]?.[0]).toContain('/orca/master/drug?');
    const init = vi.mocked(httpFetch).mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    const expectedToken = btoa(unescape(encodeURIComponent('user01:plainpass')));
    expect(headers.get('Authorization')).toBe(`Basic ${expectedToken}`);
    expect(headers.get('X-Facility-Id')).toBe('f001');
    expect(init?.notifySessionExpired).toBe(false);
  });

  it('routes bodypart search to /orca/master/bodypart with the same master auth headers', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'bodypart', keyword: '腹' });

    expect(result.ok).toBe(true);
    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/bodypart?');
    expect(requestUrl).not.toContain('category=2');
    const init = vi.mocked(httpFetch).mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get('Authorization')).toMatch(/^Basic /);
    expect(headers.get('X-Facility-Id')).toBe('f001');
  });

  it('routes comment search to /orca/master/comment with master auth headers', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'comment', keyword: '別途' });

    expect(result.ok).toBe(true);
    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/comment?');
    expect(requestUrl).not.toContain('category=8');
    const init = vi.mocked(httpFetch).mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get('Authorization')).toMatch(/^Basic /);
    expect(headers.get('X-Facility-Id')).toBe('f001');
  });

  it('routes material search to /orca/master/material with master auth headers', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'material', keyword: 'カテーテル' });

    expect(result.ok).toBe(true);
    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/material?');
    const init = vi.mocked(httpFetch).mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get('Authorization')).toMatch(/^Basic /);
    expect(headers.get('X-Facility-Id')).toBe('f001');
  });

  it('routes kensa-sort search to /orca/master/kensa-sort with master auth headers', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'kensa-sort', keyword: '血液' });

    expect(result.ok).toBe(true);
    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/kensa-sort?');
    const init = vi.mocked(httpFetch).mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get('Authorization')).toMatch(/^Basic /);
    expect(headers.get('X-Facility-Id')).toBe('f001');
  });

  it('does not force default category for plain etensu search', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'etensu', keyword: 'カテーテル' });

    expect(result.ok).toBe(true);
    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/etensu?');
    expect(requestUrl).not.toContain('category=1');
  });

  it('etensu 検索で page/size を明示した場合はクエリへ反映する', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ totalCount: 0, items: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await fetchOrderMasterSearch({ type: 'etensu', keyword: 'カテーテル', page: 2, size: 500 });

    expect(result.ok).toBe(true);
    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/etensu?');
    expect(requestUrl).toContain('page=2');
    expect(requestUrl).toContain('size=500');
  });

  it('treats TENSU_NOT_FOUND as empty result for etensu family searches', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          code: 'TENSU_NOT_FOUND',
          message: 'no etensu entries matched',
        }),
        {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    const result = await fetchOrderMasterSearch({ type: 'etensu', keyword: 'zz' });

    expect(result.ok).toBe(true);
    expect(result.items).toEqual([]);
    expect(result.totalCount).toBe(0);
  });

  it('youhou 検索で effective を付与し、拡張項目をマッピングする', async () => {
    const { httpFetch } = await import('../../libs/http/httpClient');
    vi.mocked(httpFetch).mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          totalCount: 1,
          items: [
            {
              code: '0010001',
              name: '1日3回 毎食後',
              timingCode: '05',
              routeCode: 'PO',
              daysLimit: 14,
              dosePerDay: 3,
              youhouCode: '0101',
            },
          ],
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );

    const result = await fetchOrderMasterSearch({
      type: 'youhou',
      keyword: '毎食後',
      effective: '2026-02-19',
    });

    const requestUrl = vi.mocked(httpFetch).mock.calls[0]?.[0] ?? '';
    expect(requestUrl).toContain('/orca/master/youhou?');
    expect(requestUrl).toContain('effective=2026-02-19');
    expect(result.ok).toBe(true);
    expect(result.items[0]).toMatchObject({
      type: 'youhou',
      code: '0010001',
      name: '1日3回 毎食後',
      timingCode: '05',
      routeCode: 'PO',
      daysLimit: 14,
      dosePerDay: 3,
      youhouCode: '0101',
    });
  });
});
