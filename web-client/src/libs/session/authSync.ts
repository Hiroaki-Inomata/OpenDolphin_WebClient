import type { DataSourceTransition } from '../observability/types';
import { AUTH_BROADCAST_CHANNEL } from './authStorage';

export type SharedAuthFlags = {
  runId: string;
  missingMaster: boolean;
  cacheHit: boolean;
  dataSourceTransition: DataSourceTransition;
  fallbackUsed: boolean;
};

export type SharedAuthSession = {
  facilityId: string;
  userId: string;
  clientUuid?: string;
  runId: string;
};

type SharedEnvelope<T> = {
  version: 1;
  sessionKey?: string;
  payload: T;
  updatedAt: string;
};

type AuthBroadcastMessage =
  | { version: 1; type: 'session:update'; envelope: SharedEnvelope<SharedAuthSession>; origin: string }
  | { version: 1; type: 'session:clear'; origin: string }
  | { version: 1; type: 'flags:update'; envelope: SharedEnvelope<SharedAuthFlags>; origin: string }
  | { version: 1; type: 'flags:clear'; origin: string };

const TAB_ORIGIN =
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `tab-${Math.random().toString(16).slice(2)}-${Date.now().toString(16)}`;

const nowIso = () => new Date().toISOString();

const buildSessionKey = (session: { facilityId?: string; userId?: string } | null | undefined) => {
  if (!session?.facilityId || !session?.userId) return undefined;
  return `${session.facilityId}:${session.userId}`;
};

const toSharedSessionPayload = (session: SharedAuthSession): SharedAuthSession => ({
  facilityId: session.facilityId,
  userId: session.userId,
  clientUuid: session.clientUuid,
  runId: session.runId,
});

const validateSharedSession = (session: SharedAuthSession | null | undefined): session is SharedAuthSession => {
  if (!session) return false;
  return typeof session.facilityId === 'string' && typeof session.userId === 'string' && typeof session.runId === 'string';
};

const validateSharedFlags = (flags: SharedAuthFlags | null | undefined): flags is SharedAuthFlags => {
  if (!flags) return false;
  return (
    typeof flags.runId === 'string' &&
    typeof flags.missingMaster === 'boolean' &&
    typeof flags.cacheHit === 'boolean' &&
    typeof flags.fallbackUsed === 'boolean' &&
    typeof flags.dataSourceTransition === 'string'
  );
};

let latestSharedSessionEnvelope: SharedEnvelope<SharedAuthSession> | null = null;
let latestSharedFlagsEnvelope: SharedEnvelope<SharedAuthFlags> | null = null;

const LEGACY_SHARED_SESSION_STORAGE_KEY = 'opendolphin:web-client:auth:shared-session:v1';
const LEGACY_SHARED_FLAGS_STORAGE_KEY = 'opendolphin:web-client:auth:shared-flags:v1';

const clearLegacySharedStorage = () => {
  if (typeof localStorage === 'undefined') return;
  [LEGACY_SHARED_SESSION_STORAGE_KEY, LEGACY_SHARED_FLAGS_STORAGE_KEY].forEach((key) => {
    try {
      localStorage.removeItem(key);
    } catch {
      // ignore storage cleanup failures
    }
  });
};

const postAuthMessage = (message: AuthBroadcastMessage) => {
  if (typeof BroadcastChannel === 'undefined') return;
  try {
    const channel = new BroadcastChannel(AUTH_BROADCAST_CHANNEL);
    channel.postMessage(message);
    channel.close();
  } catch {
    // ignore broadcast failures
  }
};

export function persistSharedSession(session: SharedAuthSession, origin = TAB_ORIGIN) {
  clearLegacySharedStorage();
  const payload = toSharedSessionPayload(session);
  const envelope: SharedEnvelope<SharedAuthSession> = {
    version: 1,
    sessionKey: buildSessionKey(payload),
    payload,
    updatedAt: nowIso(),
  };
  latestSharedSessionEnvelope = envelope;
  postAuthMessage({ version: 1, type: 'session:update', envelope, origin });
}

export function persistSharedAuthFlags(sessionKey: string | undefined, flags: SharedAuthFlags, origin = TAB_ORIGIN) {
  if (!sessionKey) return;
  clearLegacySharedStorage();
  const envelope: SharedEnvelope<SharedAuthFlags> = {
    version: 1,
    sessionKey,
    payload: flags,
    updatedAt: nowIso(),
  };
  latestSharedFlagsEnvelope = envelope;
  postAuthMessage({ version: 1, type: 'flags:update', envelope, origin });
}

export function clearSharedAuth(origin = TAB_ORIGIN) {
  clearLegacySharedStorage();
  latestSharedSessionEnvelope = null;
  latestSharedFlagsEnvelope = null;
  postAuthMessage({ version: 1, type: 'session:clear', origin });
  postAuthMessage({ version: 1, type: 'flags:clear', origin });
}

export function clearSharedAuthFlags(origin = TAB_ORIGIN) {
  clearLegacySharedStorage();
  latestSharedFlagsEnvelope = null;
  postAuthMessage({ version: 1, type: 'flags:clear', origin });
}

export function clearSharedAuthSession(origin = TAB_ORIGIN) {
  clearLegacySharedStorage();
  latestSharedSessionEnvelope = null;
  postAuthMessage({ version: 1, type: 'session:clear', origin });
}

export function restoreSharedAuthToSessionStorage(options?: { sessionKey?: string }) {
  clearLegacySharedStorage();
  const sharedSession = latestSharedSessionEnvelope;
  const derivedSessionKey = buildSessionKey(sharedSession?.payload);
  const targetSessionKey = options?.sessionKey ?? derivedSessionKey;
  const sharedFlags = latestSharedFlagsEnvelope;
  const resolvedFlags = sharedFlags && sharedFlags.sessionKey === targetSessionKey ? sharedFlags.payload : null;

  return {
    session: sharedSession?.payload ?? null,
    flags: resolvedFlags,
  };
}

export function resolveLatestSharedRunId(sessionKey?: string): string | undefined {
  const flagsEnvelope = latestSharedFlagsEnvelope;
  if (flagsEnvelope && (!sessionKey || flagsEnvelope.sessionKey === sessionKey)) {
    return flagsEnvelope.payload.runId;
  }
  const sessionEnvelope = latestSharedSessionEnvelope;
  if (sessionEnvelope && (!sessionKey || sessionEnvelope.sessionKey === sessionKey)) {
    return sessionEnvelope.payload.runId;
  }
  return undefined;
}

export function subscribeSharedAuth(options: {
  sessionKey?: string;
  onSession?: (session: SharedAuthSession, meta: { updatedAt: string; sessionKey?: string }) => void;
  onFlags?: (flags: SharedAuthFlags, meta: { updatedAt: string; sessionKey?: string }) => void;
  onClear?: () => void;
}) {
  const handlers: Array<() => void> = [];
  const { sessionKey, onSession, onFlags, onClear } = options;

  const handleFlagsEnvelope = (envelope: SharedEnvelope<SharedAuthFlags>) => {
    if (sessionKey && envelope.sessionKey !== sessionKey) return;
    if (!validateSharedFlags(envelope.payload)) return;
    onFlags?.(envelope.payload, { updatedAt: envelope.updatedAt, sessionKey: envelope.sessionKey });
  };

  const handleSessionEnvelope = (envelope: SharedEnvelope<SharedAuthSession>) => {
    if (!validateSharedSession(envelope.payload)) return;
    if (sessionKey && envelope.sessionKey && envelope.sessionKey !== sessionKey) return;
    onSession?.(envelope.payload, { updatedAt: envelope.updatedAt, sessionKey: envelope.sessionKey });
  };

  const onMessage = (payload: AuthBroadcastMessage) => {
    if (!payload || payload.version !== 1) return;
    switch (payload.type) {
      case 'session:update':
        latestSharedSessionEnvelope = payload.envelope;
        handleSessionEnvelope(payload.envelope);
        break;
      case 'flags:update':
        latestSharedFlagsEnvelope = payload.envelope;
        handleFlagsEnvelope(payload.envelope);
        break;
      case 'session:clear':
        latestSharedSessionEnvelope = null;
        onClear?.();
        break;
      case 'flags:clear':
        latestSharedFlagsEnvelope = null;
        onClear?.();
        break;
      default:
    }
  };

  try {
    if (typeof BroadcastChannel !== 'undefined') {
      const channel = new BroadcastChannel(AUTH_BROADCAST_CHANNEL);
      const listener = (event: MessageEvent<AuthBroadcastMessage>) => onMessage(event.data);
      channel.addEventListener('message', listener);
      handlers.push(() => {
        channel.removeEventListener('message', listener);
        channel.close();
      });
    }
  } catch {
    // ignore broadcast errors
  }

  return () => handlers.forEach((dispose) => dispose());
}
