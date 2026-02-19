const DEFAULT_API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');
const RECEPTION_STREAM_PATH = '/realtime/reception';
const DEFAULT_RETRY_DELAY_MS = 1500;
const DEFAULT_MAX_RETRY_DELAY_MS = 10_000;

const isReceptionRealtimeStreamDisabled = () => import.meta.env.VITE_DISABLE_RECEPTION_REALTIME === '1';

export type ReceptionRealtimeConnectionStatus =
  | 'connecting'
  | 'open'
  | 'reconnecting'
  | 'closed'
  | 'unavailable';

export type ReceptionRealtimeEvent = {
  type?: string;
  facilityId?: string;
  date?: string;
  patientId?: string;
  requestNumber?: string;
  revision?: number;
  updatedAt?: string;
  runId?: string;
};

export type ReceptionRealtimeStreamOptions = {
  apiBaseUrl?: string;
  retryDelayMs?: number;
  maxRetryDelayMs?: number;
  onStatusChange?: (status: ReceptionRealtimeConnectionStatus) => void;
  onMessage?: (message: ReceptionRealtimeEvent) => void;
  onError?: (error: Error) => void;
};

const toError = (error: unknown) => (error instanceof Error ? error : new Error(String(error)));

const parseEvent = (payload: string, eventType?: string, lastEventId?: string): ReceptionRealtimeEvent | null => {
  if (!payload || !payload.trim()) {
    if (!eventType) return null;
    return { type: eventType };
  }
  try {
    const raw = JSON.parse(payload) as Record<string, unknown>;
    const revisionFromLastEventId = lastEventId ? Number.parseInt(lastEventId, 10) : undefined;
    const revision =
      typeof raw.revision === 'number'
        ? raw.revision
        : Number.isFinite(revisionFromLastEventId)
          ? revisionFromLastEventId
          : undefined;
    return {
      type: typeof raw.type === 'string' ? raw.type : eventType,
      facilityId: typeof raw.facilityId === 'string' ? raw.facilityId : undefined,
      date: typeof raw.date === 'string' ? raw.date : undefined,
      patientId: typeof raw.patientId === 'string' ? raw.patientId : undefined,
      requestNumber: typeof raw.requestNumber === 'string' ? raw.requestNumber : undefined,
      revision,
      updatedAt: typeof raw.updatedAt === 'string' ? raw.updatedAt : undefined,
      runId: typeof raw.runId === 'string' ? raw.runId : undefined,
    };
  } catch {
    if (!eventType) return null;
    return { type: eventType };
  }
};

export function startReceptionRealtimeStream(options: ReceptionRealtimeStreamOptions) {
  const {
    apiBaseUrl = DEFAULT_API_BASE_URL,
    retryDelayMs = DEFAULT_RETRY_DELAY_MS,
    maxRetryDelayMs = DEFAULT_MAX_RETRY_DELAY_MS,
    onStatusChange,
    onMessage,
    onError,
  } = options;

  if (isReceptionRealtimeStreamDisabled()) {
    onStatusChange?.('unavailable');
    return () => {};
  }
  if (typeof window === 'undefined' || typeof window.EventSource !== 'function') {
    onStatusChange?.('unavailable');
    return () => {};
  }

  const streamUrl = `${apiBaseUrl}${RECEPTION_STREAM_PATH}`;
  let currentSource: EventSource | null = null;
  let reconnectTimer: number | null = null;
  let reconnectDelay = Math.max(250, retryDelayMs);
  let stopped = false;
  let connectedAtLeastOnce = false;

  const clearReconnectTimer = () => {
    if (reconnectTimer === null) return;
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  };

  const closeCurrentSource = () => {
    if (!currentSource) return;
    currentSource.close();
    currentSource = null;
  };

  const emitStatus = (status: ReceptionRealtimeConnectionStatus) => {
    onStatusChange?.(status);
  };

  const handleMessageEvent = (eventType: string, event: MessageEvent<string>) => {
    const parsed = parseEvent(event.data, eventType, event.lastEventId);
    if (parsed) {
      onMessage?.(parsed);
    }
  };

  const scheduleReconnect = () => {
    if (stopped) return;
    emitStatus('reconnecting');
    clearReconnectTimer();
    closeCurrentSource();
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = null;
      connect();
    }, reconnectDelay);
    reconnectDelay = Math.min(reconnectDelay * 2, maxRetryDelayMs);
  };

  const connect = () => {
    if (stopped) return;
    emitStatus(connectedAtLeastOnce ? 'reconnecting' : 'connecting');
    try {
      const source = new window.EventSource(streamUrl, { withCredentials: true });
      currentSource = source;

      source.onopen = () => {
        connectedAtLeastOnce = true;
        reconnectDelay = Math.max(250, retryDelayMs);
        emitStatus('open');
      };

      source.onmessage = (event) => handleMessageEvent('message', event);
      source.addEventListener('reception.updated', (event) =>
        handleMessageEvent('reception.updated', event as MessageEvent<string>),
      );
      source.addEventListener('reception.replay-gap', (event) =>
        handleMessageEvent('reception.replay-gap', event as MessageEvent<string>),
      );
      source.addEventListener('reception.keepalive', () => {
        // no-op
      });

      source.onerror = () => {
        if (stopped) return;
        onError?.(new Error('reception realtime stream error'));
        scheduleReconnect();
      };
    } catch (error) {
      onError?.(toError(error));
      scheduleReconnect();
    }
  };

  connect();

  return () => {
    stopped = true;
    clearReconnectTimer();
    closeCurrentSource();
    emitStatus('closed');
  };
}
