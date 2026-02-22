import { describe, expect, it } from 'vitest';

import { buildPatientImportFailureMessage, isRecoverableOrcaNotFound } from './orcaPatientImportRecovery';

describe('orcaPatientImportRecovery', () => {
  it('treats patient_not_found/karte_not_found 404 on numeric patient id as recoverable', () => {
    expect(
      isRecoverableOrcaNotFound({
        patientId: '000123',
        status: 404,
        errorCode: 'patient_not_found',
        errorKind: 'business_not_found',
      }),
    ).toBe(true);
    expect(
      isRecoverableOrcaNotFound({
        patientId: '000123',
        status: 404,
        errorCode: 'karte_not_found',
        errorKind: 'business_not_found',
      }),
    ).toBe(true);
    expect(
      isRecoverableOrcaNotFound({
        patientId: 'P-001',
        status: 404,
        errorCode: 'patient_not_found',
        errorKind: 'business_not_found',
      }),
    ).toBe(false);
  });

  it('builds explicit auth failure message', () => {
    const message = buildPatientImportFailureMessage('病名情報', {
      ok: false,
      runId: 'RUN-TEST',
      status: 401,
      errorKind: 'auth',
      errorCode: 'authentication_failed',
      error: 'unauthorized',
    });

    expect(message).toContain('認証エラー');
    expect(message).toContain('authentication_failed');
    expect(message).toContain('RUN-TEST');
  });

  it('builds route mismatch message', () => {
    const message = buildPatientImportFailureMessage('オーダー情報', {
      ok: false,
      runId: 'RUN-TEST',
      status: 404,
      errorKind: 'route_not_found',
      routeMismatch: true,
      error: 'not found',
    });

    expect(message).toContain('経路不一致');
    expect(message).toContain('VITE_ORCA_API_PATH_PREFIX');
  });
});
