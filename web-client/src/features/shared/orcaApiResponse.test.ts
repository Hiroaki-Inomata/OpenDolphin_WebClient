import { describe, expect, it } from 'vitest';

import { looksLikeHtmlResponse, parseOrcaApiResponse } from './orcaApiResponse';

describe('orcaApiResponse', () => {
  it('classifies 404 with patient_not_found as business_not_found', async () => {
    const response = new Response(
      JSON.stringify({
        code: 'patient_not_found',
        message: 'Patient not found',
        runId: 'RUN-404',
      }),
      {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      },
    );

    const parsed = await parseOrcaApiResponse(response);
    expect(parsed.ok).toBe(false);
    expect(parsed.errorKind).toBe('business_not_found');
    expect(parsed.errorCode).toBe('patient_not_found');
    expect(parsed.routeMismatch).toBe(false);
    expect(parsed.runId).toBe('RUN-404');
  });

  it('classifies 404 html as route_not_found', async () => {
    const response = new Response('<!doctype html><html><body>Not Found</body></html>', {
      status: 404,
      headers: { 'Content-Type': 'text/html; charset=utf-8' },
    });

    const parsed = await parseOrcaApiResponse(response);
    expect(parsed.ok).toBe(false);
    expect(parsed.errorKind).toBe('route_not_found');
    expect(parsed.routeMismatch).toBe(true);
  });

  it('classifies 401 as auth error', async () => {
    const response = new Response(
      JSON.stringify({
        reason: 'authentication_failed',
        message: 'Authentication required',
      }),
      {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      },
    );

    const parsed = await parseOrcaApiResponse(response);
    expect(parsed.ok).toBe(false);
    expect(parsed.errorKind).toBe('auth');
    expect(parsed.errorCode).toBe('authentication_failed');
  });

  it('detects html body signatures', () => {
    expect(looksLikeHtmlResponse('<!doctype html><html>')).toBe(true);
    expect(looksLikeHtmlResponse('{"apiResult":"00"}')).toBe(false);
  });
});
