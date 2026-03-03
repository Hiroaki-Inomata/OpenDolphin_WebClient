import { beforeEach, describe, expect, it, vi } from 'vitest';

import { updatePatientMemo } from './patientMemoApi';
import { httpFetch } from '../../libs/http/httpClient';

vi.mock('../../libs/http/httpClient', () => ({
  httpFetch: vi.fn(),
}));

vi.mock('../../libs/observability/observability', () => ({
  getObservabilityMeta: vi.fn(() => ({ runId: 'RUN-TEST', traceId: 'TRACE-TEST' })),
  updateObservabilityMeta: vi.fn(),
}));

const mockHttpFetch = vi.mocked(httpFetch);

describe('updatePatientMemo', () => {
  beforeEach(() => {
    mockHttpFetch.mockReset();
  });

  it('memo を XML エスケープして送信する', async () => {
    mockHttpFetch.mockResolvedValue(
      new Response(
        '<data><patient_memomodres><Api_Result>00</Api_Result><Api_Result_Message>OK</Api_Result_Message></patient_memomodres></data>',
        { status: 200, headers: { 'Content-Type': 'application/xml' } },
      ),
    );

    await updatePatientMemo({
      patientId: '0001',
      memo: '"<tag>"',
      performDate: '2026-03-03',
    });

    const requestBody = mockHttpFetch.mock.calls[0]?.[1]?.body;
    const xml = typeof requestBody === 'string' ? requestBody : '';

    expect(xml).toContain('&quot;&lt;tag&gt;&quot;');
    expect(xml).not.toContain('"<tag>"');
  });
});
