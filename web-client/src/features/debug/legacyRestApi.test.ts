import { describe, expect, it, vi } from 'vitest';

import * as httpClient from '../../libs/http/httpClient';
import { __legacyRestTestUtils, buildLegacyRestUrl, requestLegacyRest } from './legacyRestApi';

describe('legacyRestApi', () => {
  it('buildLegacyRestUrl adds query when provided', () => {
    expect(buildLegacyRestUrl('/pvt', 'patientId=00001')).toBe('/pvt?patientId=00001');
    expect(buildLegacyRestUrl('/pvt?sort=desc', 'page=1')).toBe('/pvt?sort=desc&page=1');
    expect(buildLegacyRestUrl('/pvt', '  ')).toBe('/pvt');
  });

  it('parseMaybeJson respects content type and payload shape', () => {
    const { parseMaybeJson } = __legacyRestTestUtils;
    expect(parseMaybeJson('{"ok":true}', 'application/json')).toEqual({ ok: true });
    expect(parseMaybeJson('not-json', 'application/json')).toBeUndefined();
    expect(parseMaybeJson(' {"x":1}', 'text/plain')).toEqual({ x: 1 });
    expect(parseMaybeJson('hello', 'text/plain')).toBeUndefined();
  });

  it('resolveResponseMode returns binary for non-text types', () => {
    const { resolveResponseMode } = __legacyRestTestUtils;
    expect(resolveResponseMode('application/json')).toBe('json');
    expect(resolveResponseMode('text/plain')).toBe('text');
    expect(resolveResponseMode('application/xml')).toBe('text');
    expect(resolveResponseMode('application/pdf')).toBe('binary');
    expect(resolveResponseMode(undefined)).toBe('text');
  });

  it('rejects absolute http/https path before sending request', async () => {
    const fetchSpy = vi.spyOn(httpClient, 'httpFetch').mockResolvedValue(new Response(null, { status: 200 }));

    const result = await requestLegacyRest({
      method: 'GET',
      path: 'https://evil.example/pvt',
    });

    expect(result.ok).toBe(false);
    expect(result.error).toBe('invalid_path');
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});
