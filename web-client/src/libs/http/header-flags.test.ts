import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { persistHeaderFlags, resolveHeaderOverrides } from './header-flags';

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

const ensureLocalStorage = () => {
  if (typeof localStorage !== 'undefined') return;
  Object.defineProperty(globalThis, 'localStorage', {
    value: new StorageMock(),
    configurable: true,
    writable: true,
  });
};

describe('header-flags local override control', () => {
  beforeEach(() => {
    ensureLocalStorage();
    localStorage.clear();
    vi.unstubAllEnvs();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('reads localStorage overrides only when local override is allowed', () => {
    localStorage.setItem('useMockOrcaQueue', '1');
    localStorage.setItem('verifyAdminDelivery', '0');
    localStorage.setItem('mswFault', 'network_error');
    localStorage.setItem('mswDelayMs', '1200');

    const resolved = resolveHeaderOverrides({ localOverrideAllowed: true });
    expect(resolved.useMockOrcaQueue).toBe(true);
    expect(resolved.verifyAdminDelivery).toBe(false);
    expect(resolved.mswFault).toBe('network_error');
    expect(resolved.mswDelayMs).toBe(1200);
  });

  it('ignores localStorage overrides when local override is disabled', () => {
    vi.stubEnv('VITE_USE_MOCK_ORCA_QUEUE', '0');
    vi.stubEnv('VITE_VERIFY_ADMIN_DELIVERY', '1');
    vi.stubEnv('VITE_MSW_FAULT', 'env_fault');
    vi.stubEnv('VITE_MSW_DELAY_MS', '250');

    localStorage.setItem('useMockOrcaQueue', '1');
    localStorage.setItem('verifyAdminDelivery', '0');
    localStorage.setItem('mswFault', 'network_error');
    localStorage.setItem('mswDelayMs', '1200');

    const resolved = resolveHeaderOverrides({ localOverrideAllowed: false });
    expect(resolved).toEqual({
      useMockOrcaQueue: false,
      verifyAdminDelivery: true,
      mswFault: 'env_fault',
      mswDelayMs: 250,
    });
  });

  it('does not write localStorage flags when local override is disabled', () => {
    persistHeaderFlags(
      {
        useMockOrcaQueue: true,
        verifyAdminDelivery: true,
        mswFault: 'timeout',
        mswDelayMs: 1500,
      },
      { localOverrideAllowed: false },
    );

    expect(localStorage.getItem('useMockOrcaQueue')).toBeNull();
    expect(localStorage.getItem('verifyAdminDelivery')).toBeNull();
    expect(localStorage.getItem('mswFault')).toBeNull();
    expect(localStorage.getItem('mswDelayMs')).toBeNull();
  });
});
