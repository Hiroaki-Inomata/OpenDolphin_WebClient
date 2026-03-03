import { describe, beforeEach, it, expect, vi } from 'vitest';
import { act, render, waitFor } from '@testing-library/react';
import { applyObservabilityHeaders, updateObservabilityMeta } from '../../observability/observability';
import { AuthServiceProvider } from '../../../features/charts/authService';
import { clearSharedAuthFlags, persistSharedAuthFlags, persistSharedSession, restoreSharedAuthToSessionStorage } from '../authSync';
import { AUTH_FLAGS_STORAGE_KEY, AUTH_SESSION_STORAGE_KEY } from '../authStorage';

class FakeBroadcastChannel {
  static listeners = new Map<string, Set<(event: MessageEvent) => void>>();
  name: string;
  handlers = new Set<(event: MessageEvent) => void>();

  constructor(name: string) {
    this.name = name;
    if (!FakeBroadcastChannel.listeners.has(name)) {
      FakeBroadcastChannel.listeners.set(name, new Set());
    }
  }

  postMessage(data: unknown) {
    const listeners = FakeBroadcastChannel.listeners.get(this.name);
    listeners?.forEach((handler) => handler({ data } as MessageEvent));
  }

  addEventListener(event: string, handler: (event: MessageEvent) => void) {
    if (event !== 'message') return;
    const listeners = FakeBroadcastChannel.listeners.get(this.name);
    listeners?.add(handler);
    this.handlers.add(handler);
  }

  removeEventListener(event: string, handler: (event: MessageEvent) => void) {
    if (event !== 'message') return;
    const listeners = FakeBroadcastChannel.listeners.get(this.name);
    listeners?.delete(handler);
    this.handlers.delete(handler);
  }

  close() {
    this.handlers.forEach((handler) => this.removeEventListener('message', handler));
  }
}

class StorageMock implements Storage {
  private data = new Map<string, string>();

  get length() {
    return this.data.size;
  }

  clear(): void {
    this.data.clear();
  }

  getItem(key: string): string | null {
    return this.data.has(key) ? this.data.get(key)! : null;
  }

  key(index: number): string | null {
    return Array.from(this.data.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.data.delete(key);
  }

  setItem(key: string, value: string): void {
    this.data.set(key, value);
  }
}

const ensureWebStorage = () => {
  if (typeof localStorage === 'undefined') {
    Object.defineProperty(globalThis, 'localStorage', {
      value: new StorageMock(),
      configurable: true,
      writable: true,
    });
  }
  if (typeof sessionStorage === 'undefined') {
    Object.defineProperty(globalThis, 'sessionStorage', {
      value: new StorageMock(),
      configurable: true,
      writable: true,
    });
  }
};

describe('auth sync / runId propagation', () => {
  beforeEach(() => {
    ensureWebStorage();
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    // @ts-expect-error test stub
    globalThis.BroadcastChannel = FakeBroadcastChannel;
    updateObservabilityMeta({ runId: undefined as unknown as string, traceId: undefined });
  });

  it('propagates runId updates from another tab into request headers', async () => {
    const sessionKey = '0001:user1';
    const initialRunId = 'RUN-OLD';
    const nextRunId = 'RUN-NEW';

    sessionStorage.setItem(
      AUTH_SESSION_STORAGE_KEY,
      JSON.stringify({
        facilityId: '0001',
        userId: 'user1',
        role: 'doctor',
        runId: initialRunId,
      }),
    );

    render(
      <AuthServiceProvider
        sessionKey={sessionKey}
        initialFlags={{ runId: initialRunId, cacheHit: false, missingMaster: true, dataSourceTransition: 'snapshot' }}
      >
        <div data-testid="probe" />
      </AuthServiceProvider>,
    );

    const initial = applyObservabilityHeaders();
    expect((initial.headers as Record<string, string>)['X-Run-Id']).toBe(initialRunId);

    await act(async () => {
      persistSharedAuthFlags(sessionKey, {
        runId: nextRunId,
        cacheHit: true,
        missingMaster: false,
        fallbackUsed: false,
        dataSourceTransition: 'server',
      });
    });

    await waitFor(() => {
      const updated = applyObservabilityHeaders();
      expect((updated.headers as Record<string, string>)['X-Run-Id']).toBe(nextRunId);
    });
  });

  it('clears old runId when flags:clear is broadcast', async () => {
    const sessionKey = '0001:user1';
    const initialRunId = 'RUN-OLD';

    sessionStorage.setItem(
      AUTH_SESSION_STORAGE_KEY,
      JSON.stringify({
        facilityId: '0001',
        userId: 'user1',
        role: 'doctor',
        runId: initialRunId,
      }),
    );

    render(
      <AuthServiceProvider
        sessionKey={sessionKey}
        initialFlags={{ runId: initialRunId, cacheHit: false, missingMaster: true, dataSourceTransition: 'snapshot' }}
      >
        <div data-testid="probe" />
      </AuthServiceProvider>,
    );

    const before = applyObservabilityHeaders();
    expect((before.headers as Record<string, string>)['X-Run-Id']).toBe(initialRunId);

    await act(async () => {
      clearSharedAuthFlags();
    });

    await waitFor(() => {
      const after = applyObservabilityHeaders();
      const runId = (after.headers as Record<string, string>)['X-Run-Id'];
      expect(runId).toBeDefined();
      expect(runId).not.toBe(initialRunId);
    });
  });

  it('ignores stored auth flags with mismatched runId on init', async () => {
    const sessionKey = '0001:user1';
    const oldRunId = 'RUN-OLD';
    const newRunId = 'RUN-NEW';

    sessionStorage.setItem(
      AUTH_SESSION_STORAGE_KEY,
      JSON.stringify({
        facilityId: '0001',
        userId: 'user1',
        role: 'doctor',
        runId: newRunId,
      }),
    );
    sessionStorage.setItem(
      AUTH_FLAGS_STORAGE_KEY,
      JSON.stringify({
        sessionKey,
        updatedAt: new Date().toISOString(),
        flags: {
          runId: oldRunId,
          cacheHit: true,
          missingMaster: false,
          fallbackUsed: false,
          dataSourceTransition: 'server',
        },
      }),
    );

    render(
      <AuthServiceProvider
        sessionKey={sessionKey}
        initialFlags={{ runId: newRunId, cacheHit: false, missingMaster: true, dataSourceTransition: 'snapshot' }}
      >
        <div data-testid="probe" />
      </AuthServiceProvider>,
    );

    const headers = applyObservabilityHeaders();
    expect((headers.headers as Record<string, string>)['X-Run-Id']).toBe(newRunId);
  });

  it('stores only minimal shared session payload in localStorage', () => {
    persistSharedSession({
      facilityId: '0001',
      userId: 'user1',
      role: 'doctor',
      clientUuid: 'client-1',
      runId: 'RUN-1',
    });

    const raw = localStorage.getItem('opendolphin:web-client:auth:shared-session:v1');
    expect(raw).toBeTruthy();
    const parsed = JSON.parse(raw ?? '{}') as { payload?: Record<string, unknown> };
    expect(parsed.payload).toMatchObject({
      facilityId: '0001',
      userId: 'user1',
      role: 'doctor',
      clientUuid: 'client-1',
      runId: 'RUN-1',
    });
    expect(parsed.payload).not.toHaveProperty('displayName');
    expect(parsed.payload).not.toHaveProperty('commonName');
    expect(parsed.payload).not.toHaveProperty('roles');
  });

  it('expires shared auth session after 1 hour TTL', () => {
    localStorage.setItem(
      'opendolphin:web-client:auth:shared-session:v1',
      JSON.stringify({
        version: 1,
        sessionKey: '0001:user1',
        updatedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        payload: {
          facilityId: '0001',
          userId: 'user1',
          role: 'doctor',
          runId: 'RUN-OLD',
        },
      }),
    );

    const restored = restoreSharedAuthToSessionStorage({ sessionKey: '0001:user1' });
    expect(restored.session).toBeNull();
    expect(localStorage.getItem('opendolphin:web-client:auth:shared-session:v1')).toBeNull();
  });
});
