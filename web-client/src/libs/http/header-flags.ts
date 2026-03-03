// RUN_ID=20251202T090000Z
// Playwright の extraHTTPHeaders から渡されるフラグを、フロント側で API リクエストに伝播させるためのユーティリティ。
// httpClient.ts の共通 fetch ラッパーで applyHeaderFlagsToInit を呼び出し、全リクエスト共通ヘッダーとして適用する。

import { readStoredSession } from '../session/storedSession';
import { isSystemAdminRole } from '../auth/roles';
import { MSW_QUERY_PARAM } from '../devtools/mockGate';

export type HeaderFlags = {
  mswFault?: string;
  mswDelayMs?: number;
};

export type HeaderOverrideFlags = {
  mswFault?: string;
  mswDelayMs?: number;
};

const isMswFaultInjectionAllowed = (): boolean => {
  // Gate: DEV + explicit env + explicit URL param. Never allow in production-like builds.
  if (!import.meta.env.DEV) return false;
  if (import.meta.env.VITE_ENABLE_MSW !== '1') return false;
  if (import.meta.env.VITE_DISABLE_MSW === '1') return false;
  if (typeof window === 'undefined') return false;
  const sessionRole = readStoredSession()?.role;
  const debugPagesEnabled = import.meta.env.VITE_ENABLE_DEBUG_PAGES === '1';
  const debugUiEnabled = import.meta.env.VITE_ENABLE_DEBUG_UI === '1';
  if (!debugPagesEnabled && !debugUiEnabled) return false;
  if (!isSystemAdminRole(sessionRole)) return false;
  try {
    const url = new URL(window.location.href);
    // 事故防止のため、明示的に ?msw=1 のページのみ注入を許可する（E2E/デバッグ用）。
    return url.searchParams.get(MSW_QUERY_PARAM) === '1';
  } catch {
    return false;
  }
};

export function readHeaderFlagsFromEnv(): HeaderFlags {
  const delayRaw = import.meta.env.VITE_MSW_DELAY_MS;
  const delay = typeof delayRaw === 'string' && delayRaw.trim().length > 0 ? Number(delayRaw) : undefined;
  return {
    mswFault: typeof import.meta.env.VITE_MSW_FAULT === 'string' ? import.meta.env.VITE_MSW_FAULT : undefined,
    mswDelayMs: Number.isFinite(delay as number) ? (delay as number) : undefined,
  };
}

export function buildHeaderOverrides(flags: HeaderOverrideFlags) {
  const allowMswFaultHeaders = isMswFaultInjectionAllowed();
  const overrides: Record<string, string> = {};
  if (allowMswFaultHeaders && flags.mswFault && flags.mswFault.trim().length > 0) {
    overrides['x-msw-fault'] = flags.mswFault.trim();
  }
  if (
    allowMswFaultHeaders &&
    typeof flags.mswDelayMs === 'number' &&
    Number.isFinite(flags.mswDelayMs) &&
    flags.mswDelayMs > 0
  ) {
    overrides['x-msw-delay-ms'] = String(Math.floor(flags.mswDelayMs));
  }
  return overrides;
}

function normalizeHeaders(headers?: HeadersInit): Record<string, string> {
  if (!headers) {
    return {};
  }
  if (headers instanceof Headers) {
    const result: Record<string, string> = {};
    headers.forEach((value, key) => {
      result[key] = value;
    });
    return result;
  }
  if (Array.isArray(headers)) {
    return headers.reduce<Record<string, string>>((acc, [key, value]) => {
      acc[key] = value;
      return acc;
    }, {});
  }
  return { ...headers };
}

export const isLocalOverrideAllowed = (): boolean => import.meta.env.DEV;

const readStoredFlag = (key: string): string | null => {
  if (typeof localStorage === 'undefined') return null;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
};

const writeStoredFlag = (key: string, value: string) => {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(key, value);
  } catch {
    // ignore storage errors
  }
};

const removeStoredFlag = (key: string) => {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.removeItem(key);
  } catch {
    // ignore storage errors
  }
};

export function resolveHeaderOverrides(options?: { localOverrideAllowed?: boolean }): HeaderOverrideFlags {
  const envFault = typeof import.meta.env.VITE_MSW_FAULT === 'string' ? import.meta.env.VITE_MSW_FAULT : undefined;
  const delayRaw = import.meta.env.VITE_MSW_DELAY_MS;
  const envDelay =
    typeof delayRaw === 'string' && delayRaw.trim().length > 0 ? Number(delayRaw) : undefined;
  const allowLocal = options?.localOverrideAllowed ?? isLocalOverrideAllowed();

  if (!allowLocal) {
    return {
      mswFault: envFault,
      mswDelayMs: Number.isFinite(envDelay as number) ? (envDelay as number) : undefined,
    };
  }

  const storedFault = readStoredFlag('mswFault');
  const storedDelay = readStoredFlag('mswDelayMs');
  const parsedDelay = storedDelay ? Number(storedDelay) : undefined;

  return {
    mswFault: envFault ?? (storedFault && storedFault.trim().length > 0 ? storedFault : undefined),
    mswDelayMs:
      (Number.isFinite(envDelay as number) ? (envDelay as number) : undefined) ??
      (typeof parsedDelay === 'number' && Number.isFinite(parsedDelay) && parsedDelay > 0 ? parsedDelay : undefined),
  };
}

export function resolveHeaderFlags(options?: { localOverrideAllowed?: boolean }): HeaderFlags {
  return resolveHeaderOverrides(options);
}

export function persistHeaderFlags(partial: Partial<HeaderFlags>, options?: { localOverrideAllowed?: boolean }) {
  const allowLocal = options?.localOverrideAllowed ?? isLocalOverrideAllowed();
  if (!allowLocal) return;
  if (partial.mswFault !== undefined) {
    const value = partial.mswFault?.trim() ?? '';
    if (value.length === 0) {
      removeStoredFlag('mswFault');
    } else {
      writeStoredFlag('mswFault', value);
    }
  }
  if (partial.mswDelayMs !== undefined) {
    if (typeof partial.mswDelayMs !== 'number' || !Number.isFinite(partial.mswDelayMs) || partial.mswDelayMs <= 0) {
      removeStoredFlag('mswDelayMs');
    } else {
      writeStoredFlag('mswDelayMs', String(Math.floor(partial.mswDelayMs)));
    }
  }
}

export function applyHeaderFlagsToInit(init?: RequestInit): RequestInit {
  const overrides = buildHeaderOverrides(resolveHeaderOverrides());
  // init 側で明示的に指定されたヘッダーは優先し、フラグは不足分を埋めるだけにする。
  const mergedHeaders = {
    ...overrides,
    ...normalizeHeaders(init?.headers),
  } as Record<string, string>;
  return { ...(init ?? {}), headers: mergedHeaders };
}
