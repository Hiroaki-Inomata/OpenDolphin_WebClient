import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { shouldNotifySessionExpired } from './httpClient';

const AUTH_KEY = 'opendolphin:web-client:auth';

const setSession = () => {
  sessionStorage.setItem(
    AUTH_KEY,
    JSON.stringify({ facilityId: 'f001', userId: 'user01', role: 'doctor', runId: 'run-1' }),
  );
};

const setDevAuth = (
  storage: Storage = localStorage,
  values: {
    facilityId?: string;
    userId?: string;
    passwordPlain?: string;
    passwordMd5?: string;
    clientUuid?: string;
  } = {},
) => {
  storage.setItem('devFacilityId', values.facilityId ?? 'f001');
  storage.setItem('devUserId', values.userId ?? 'user01');
  storage.setItem('devPasswordPlain', values.passwordPlain ?? 'plain-password');
  storage.setItem('devPasswordMd5', values.passwordMd5 ?? 'md5-password');
  storage.setItem('devClientUuid', values.clientUuid ?? 'client-uuid-1');
};

const mockFetchSequence = (statuses: number[]) => {
  const queue = [...statuses];
  vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
    const status = queue.shift() ?? 200;
    return Promise.resolve(new Response(null, { status }));
  });
};

const importSubjects = async () => {
  const sessionExpiry = await import('../session/sessionExpiry');
  const httpClient = await import('./httpClient');
  return { sessionExpiry, httpClient };
};

describe('shouldNotifySessionExpired', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('returns false when no stored session exists', () => {
    expect(shouldNotifySessionExpired(401)).toBe(false);
    expect(shouldNotifySessionExpired(419)).toBe(false);
  });

  it('returns true for 401 when a session exists', () => {
    setSession();
    expect(shouldNotifySessionExpired(401)).toBe(true);
  });

  it('returns false when notifySessionExpired is disabled', () => {
    setSession();
    expect(shouldNotifySessionExpired(401, { notifySessionExpired: false })).toBe(false);
    expect(shouldNotifySessionExpired(419, { notifySessionExpired: false })).toBe(false);
  });

  it('returns true for 419 and 440 when a session exists', () => {
    setSession();
    expect(shouldNotifySessionExpired(419)).toBe(true);
    expect(shouldNotifySessionExpired(440)).toBe(true);
  });

  it('ignores 403 by default even when a session exists', () => {
    setSession();
    expect(shouldNotifySessionExpired(403)).toBe(false);
  });

  it('notifies for 403 only when explicitly opted-in', () => {
    setSession();
    expect(shouldNotifySessionExpired(403, { notifyForbiddenAsSessionExpiry: true })).toBe(true);
  });

  it('returns false for non expiry statuses', () => {
    setSession();
    expect(shouldNotifySessionExpired(500)).toBe(false);
  });
});

describe('httpFetch session expiry debounce', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-19T00:00:00Z'));
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('debounces consecutive 401 responses', async () => {
    setSession();
    mockFetchSequence([401, 401]);
    const { sessionExpiry, httpClient } = await importSubjects();
    vi.spyOn(sessionExpiry, 'notifySessionExpired');
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent');

    await httpClient.httpFetch('/dummy');
    vi.advanceTimersByTime(1_000);
    await httpClient.httpFetch('/dummy');

    expect(dispatchSpy).toHaveBeenCalledTimes(1);
  });

  it('debounces mixed 419 then 440 responses', async () => {
    setSession();
    mockFetchSequence([419, 440]);
    const { sessionExpiry, httpClient } = await importSubjects();
    vi.spyOn(sessionExpiry, 'notifySessionExpired');
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent');

    await httpClient.httpFetch('/dummy');
    vi.advanceTimersByTime(2_000);
    await httpClient.httpFetch('/dummy');

    expect(dispatchSpy).toHaveBeenCalledTimes(1);
  });

  it('does not notify for repeated 403 responses without opt-in', async () => {
    setSession();
    mockFetchSequence([403, 403]);
    const { sessionExpiry, httpClient } = await importSubjects();
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent');
    vi.spyOn(sessionExpiry, 'notifySessionExpired');

    await httpClient.httpFetch('/dummy');
    await httpClient.httpFetch('/dummy');

    expect(dispatchSpy).not.toHaveBeenCalled();
  });
});

describe('httpFetch session expiry reasons', () => {
  beforeEach(() => {
    vi.resetModules();
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('maps 403 to forbidden only when opted-in', async () => {
    setSession();
    mockFetchSequence([403]);
    const { sessionExpiry, httpClient } = await importSubjects();
    const notifySpy = vi.spyOn(sessionExpiry, 'notifySessionExpired');

    await httpClient.httpFetch('/dummy', { notifyForbiddenAsSessionExpiry: true });
    expect(notifySpy).toHaveBeenCalledWith('forbidden', 403);

    notifySpy.mockClear();
    mockFetchSequence([403]);
    await httpClient.httpFetch('/dummy');
    expect(notifySpy).not.toHaveBeenCalled();
  });

  it('maps 401 to unauthorized and 419 to timeout', async () => {
    setSession();
    mockFetchSequence([401]);
    const { sessionExpiry, httpClient } = await importSubjects();
    const notifySpy = vi.spyOn(sessionExpiry, 'notifySessionExpired');

    await httpClient.httpFetch('/dummy');
    expect(notifySpy).toHaveBeenCalledWith('unauthorized', 401);

    notifySpy.mockClear();
    mockFetchSequence([419]);
    await httpClient.httpFetch('/dummy');
    expect(notifySpy).toHaveBeenCalledWith('timeout', 419);
  });

  it('does not notify for ORCA endpoints by default', async () => {
    setSession();
    mockFetchSequence([401]);
    const { sessionExpiry, httpClient } = await importSubjects();
    const notifySpy = vi.spyOn(sessionExpiry, 'notifySessionExpired');

    await httpClient.httpFetch('/orca/appointments/list');
    expect(notifySpy).not.toHaveBeenCalled();
  });

  it('does not notify for /karte and /odletter endpoints on 401', async () => {
    setSession();
    const { sessionExpiry, httpClient } = await importSubjects();
    const notifySpy = vi.spyOn(sessionExpiry, 'notifySessionExpired');

    mockFetchSequence([401]);
    await httpClient.httpFetch('/karte/pid/00001,2000-01-01%2000%3A00%3A00', { method: 'GET' });
    expect(notifySpy).not.toHaveBeenCalled();

    mockFetchSequence([401]);
    await httpClient.httpFetch('/odletter/list/1', { method: 'GET' });
    expect(notifySpy).not.toHaveBeenCalled();
  });

  it('attaches auth headers for ORCA and KARTE-family endpoints in DEV', async () => {
    setSession();
    setDevAuth();
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 200 }));
    const { httpClient } = await importSubjects();

    await httpClient.httpFetch('/api/admin/orca/connection', { method: 'GET' });
    const nonOrcaHeaders = new Headers((fetchSpy.mock.calls[0]?.[1] as RequestInit | undefined)?.headers ?? {});
    expect(nonOrcaHeaders.has('Authorization')).toBe(false);
    expect(nonOrcaHeaders.has('userName')).toBe(false);
    expect(nonOrcaHeaders.has('password')).toBe(false);

    await httpClient.httpFetch('/orca/appointments/list', { method: 'GET' });
    const orcaHeaders = new Headers((fetchSpy.mock.calls[1]?.[1] as RequestInit | undefined)?.headers ?? {});
    expect(orcaHeaders.has('Authorization') || orcaHeaders.has('userName') || orcaHeaders.has('password')).toBe(true);

    await httpClient.httpFetch('/karte/pid/00001,2000-01-01%2000%3A00%3A00', { method: 'GET' });
    const karteHeaders = new Headers((fetchSpy.mock.calls[2]?.[1] as RequestInit | undefined)?.headers ?? {});
    expect(karteHeaders.has('Authorization') || karteHeaders.has('userName') || karteHeaders.has('password')).toBe(true);
  });

  it('prefers tab-local auth after re-login when localStorage has stale credentials', async () => {
    setSession();
    setDevAuth(localStorage, {
      userId: 'dolphindev',
      passwordPlain: 'dolphin-pass',
      passwordMd5: 'legacy-md5',
      clientUuid: 'legacy-client',
    });
    setDevAuth(sessionStorage, {
      userId: 'ormaster',
      passwordPlain: 'change_me',
      passwordMd5: 'latest-md5',
      clientUuid: 'latest-client',
    });

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 200 }));
    const { httpClient } = await importSubjects();

    await httpClient.httpFetch('/orca/appointments/list', { method: 'GET' });
    const headers = new Headers((fetchSpy.mock.calls[0]?.[1] as RequestInit | undefined)?.headers ?? {});
    const authorization = headers.get('Authorization');
    if (authorization) {
      const token = authorization.replace(/^Basic\s+/i, '');
      expect(atob(token)).toBe('ormaster:change_me');
    } else {
      expect(headers.get('userName')).toBe('f001:ormaster');
    }
    expect(headers.get('X-Facility-Id')).toBe('f001');
  });

  it('does not notify for /api21 and /blobapi endpoints on 401/403', async () => {
    setSession();
    const { sessionExpiry, httpClient } = await importSubjects();
    const notifySpy = vi.spyOn(sessionExpiry, 'notifySessionExpired');

    mockFetchSequence([401]);
    await httpClient.httpFetch('/api21/medicalmodv2', { method: 'POST' });
    expect(notifySpy).not.toHaveBeenCalled();

    mockFetchSequence([403]);
    await httpClient.httpFetch('/api21/medicalmodv2', { method: 'POST' });
    expect(notifySpy).not.toHaveBeenCalled();

    mockFetchSequence([401]);
    await httpClient.httpFetch('/blobapi/xxxx', { method: 'GET' });
    expect(notifySpy).not.toHaveBeenCalled();

    mockFetchSequence([403]);
    await httpClient.httpFetch('/blobapi/xxxx', { method: 'GET' });
    expect(notifySpy).not.toHaveBeenCalled();
  });
});
