import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const shared = vi.hoisted(() => ({
  state: {
    meta: {
      runId: 'RUN-OLD',
      traceId: 'TRACE-OLD',
    } as {
      runId?: string;
      traceId?: string;
    },
  },
}));

vi.mock('../../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

vi.mock('../../../libs/observability/observability', () => ({
  getObservabilityMeta: vi.fn(() => ({ ...shared.state.meta })),
  updateObservabilityMeta: vi.fn((next: Record<string, unknown>) => {
    const filtered = Object.fromEntries(Object.entries(next).filter(([, value]) => value !== undefined));
    shared.state.meta = { ...shared.state.meta, ...filtered };
  }),
}));

import { httpFetch } from '../../../libs/http/httpClient';
import { fetchOrcaPushEvents, fetchOrcaQueue } from '../orcaQueueApi';

const mockHttpFetch = vi.mocked(httpFetch);

describe('orcaQueueApi fetchOrcaPushEvents', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubEnv('VITE_DISABLE_ORCA_POLLING', '0');
    shared.state.meta = {
      runId: 'RUN-OLD',
      traceId: 'TRACE-OLD',
    };
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('pusheventgetv2 に JSON リクエストを POST し、必須ヘッダーを送る', async () => {
    mockHttpFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          pusheventgetv2res: {
            Api_Result: '00',
            Api_Result_Message: 'OK',
            Event_Information: [],
          },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    await fetchOrcaPushEvents({
      event: 'medical',
      user: 'ORCAUSER',
      startTime: '090000',
      endTime: '180000',
    });

    expect(mockHttpFetch).toHaveBeenCalledTimes(1);
    const [endpoint, init] = mockHttpFetch.mock.calls[0] ?? [];
    expect(endpoint).toBe('/orca/pusheventgetv2');
    expect(init).toMatchObject({
      method: 'POST',
      notifySessionExpired: false,
      headers: {
        'Content-Type': 'application/json; charset=UTF-8',
        Accept: 'application/json',
      },
    });

    const bodyText = String((init as RequestInit).body ?? '');
    const requestBody = JSON.parse(bodyText) as Record<string, unknown>;
    expect(requestBody).toEqual({
      pusheventgetv2req: {
        event: 'medical',
        user: 'ORCAUSER',
        start_time: '090000',
        end_time: '180000',
      },
    });
  });

  it('Api_Result と Event_Information を従来どおり正規化する', async () => {
    mockHttpFetch.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          pusheventgetv2res: {
            Api_Result: '00',
            Api_Result_Message: '処理終了',
            Event_Information: [
              {
                id: 'event-1',
                event: 'medical',
                user: 'operator',
                timestamp: '2026-02-22T09:00:00+09:00',
                body: {
                  Patient_ID: '000001',
                },
              },
            ],
          },
        }),
        {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'x-run-id': 'RUN-PUSH-1',
            'x-trace-id': 'TRACE-PUSH-1',
          },
        },
      ),
    );

    const result = await fetchOrcaPushEvents();

    expect(result.ok).toBe(true);
    expect(result.apiOk).toBe(true);
    expect(result.apiResult).toBe('00');
    expect(result.apiResultMessage).toBe('処理終了');
    expect(result.warning).toBeUndefined();
    expect(result.runId).toBe('RUN-PUSH-1');
    expect(result.traceId).toBe('TRACE-PUSH-1');
    expect(result.events).toHaveLength(1);
    expect(result.events[0]).toMatchObject({
      eventId: 'event-1',
      event: 'medical',
      user: 'operator',
      timestamp: '2026-02-22T09:00:00+09:00',
      patientId: '000001',
    });
  });
});

describe('orcaQueueApi fetchOrcaQueue', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubEnv('VITE_DISABLE_ORCA_POLLING', '0');
    shared.state.meta = {
      runId: 'RUN-OLD',
      traceId: 'TRACE-OLD',
    };
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('enabled=false のとき queue API を呼ばず空レスポンスを返す', async () => {
    const result = await fetchOrcaQueue(undefined, { enabled: false });

    expect(mockHttpFetch).not.toHaveBeenCalled();
    expect(result.queue).toEqual([]);
    expect(result.runId).toBe('RUN-OLD');
    expect(result.traceId).toBe('TRACE-OLD');
  });

  it('403 は未利用扱いとして空レスポンスを返す', async () => {
    mockHttpFetch.mockResolvedValueOnce(new Response('{}', { status: 403, headers: { 'x-run-id': 'RUN-403' } }));

    const result = await fetchOrcaQueue();

    expect(mockHttpFetch).toHaveBeenCalledTimes(1);
    expect(result.queue).toEqual([]);
    expect(result.runId).toBe('RUN-OLD');
  });

  it('404 は未提供扱いとして空レスポンスを返す', async () => {
    mockHttpFetch.mockResolvedValueOnce(new Response('{}', { status: 404 }));

    const result = await fetchOrcaQueue();

    expect(mockHttpFetch).toHaveBeenCalledTimes(1);
    expect(result.queue).toEqual([]);
  });
});
