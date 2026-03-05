import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchPatientImageList, uploadPatientImageViaXhr } from '../mobileApi';
import { buildHttpHeaders, httpFetch } from '../../../libs/http/httpClient';

vi.mock('../../../libs/observability/observability', () => ({
  ensureObservabilityMeta: () => ({ runId: 'RUN-TEST', traceId: 'TRACE-TEST' }),
  getObservabilityMeta: () => ({ runId: 'RUN-TEST', traceId: 'TRACE-TEST' }),
  captureObservabilityFromResponse: vi.fn(),
}));

vi.mock('../../../libs/http/httpClient', () => ({
  buildHttpHeaders: vi.fn((init?: RequestInit) => {
    const headers = new Headers(init?.headers ?? {});
    const normalized: Record<string, string> = {};
    headers.forEach((value, key) => {
      normalized[key] = value;
    });
    return normalized;
  }),
  httpFetch: vi.fn(),
}));

describe('mobileApi feature header', () => {
  const mockBuildHttpHeaders = vi.mocked(buildHttpHeaders);
  const mockHttpFetch = vi.mocked(httpFetch);
  const originalXhr = globalThis.XMLHttpRequest;

  beforeEach(() => {
    mockBuildHttpHeaders.mockClear();
    mockHttpFetch.mockReset();
  });

  afterEach(() => {
    globalThis.XMLHttpRequest = originalXhr;
  });

  it('fetchPatientImageList は X-Client-Feature-Images のみを付与する', async () => {
    mockHttpFetch.mockResolvedValueOnce(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await fetchPatientImageList('123');

    const [init] = mockBuildHttpHeaders.mock.calls[0] ?? [];
    const headers = new Headers((init as RequestInit | undefined)?.headers ?? {});
    expect(headers.get('X-Client-Feature-Images')).toBe('1');
    expect(headers.get('X-Feature-Images')).toBeNull();
  });

  it('uploadPatientImageViaXhr は legacy header を送らない', async () => {
    class MockXMLHttpRequest {
      static last: MockXMLHttpRequest | null = null;
      upload = { addEventListener: vi.fn() };
      status = 200;
      responseText = '{}';
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      ontimeout: (() => void) | null = null;
      private requestHeaders = new Map<string, string>();

      constructor() {
        MockXMLHttpRequest.last = this;
      }

      open() {
        // noop
      }

      setRequestHeader(key: string, value: string) {
        this.requestHeaders.set(key.toLowerCase(), value);
      }

      getAllResponseHeaders() {
        return 'content-type: application/json';
      }

      send() {
        this.onload?.();
      }

      readHeader(key: string) {
        return this.requestHeaders.get(key.toLowerCase());
      }
    }

    globalThis.XMLHttpRequest = MockXMLHttpRequest as unknown as typeof XMLHttpRequest;

    await uploadPatientImageViaXhr({
      patientId: '123',
      file: new File(['hello'], 'test.png', { type: 'image/png' }),
    });

    expect(MockXMLHttpRequest.last?.readHeader('X-Client-Feature-Images')).toBe('1');
    expect(MockXMLHttpRequest.last?.readHeader('X-Feature-Images')).toBeUndefined();
  });
});
