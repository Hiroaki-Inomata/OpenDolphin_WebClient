import { useEffect, useMemo, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { v4 as uuidv4 } from 'uuid';

import { httpFetch } from './libs/http/httpClient';
import { generateRunId, updateObservabilityMeta } from './libs/observability/observability';
import { consumeSessionExpiredNotice } from './libs/session/sessionExpiry';
import { logAuditEvent } from './libs/audit/auditLogger';
import { resolveLoginFailureMessage } from './features/login/loginErrorMessage';

const resolveApiBaseUrl = () => {
  const raw = (import.meta.env.VITE_API_BASE_URL ?? '/api').trim().replace(/\/$/, '');
  return raw || '/api';
};
const API_BASE_URL = resolveApiBaseUrl();
const SYSTEM_ICON_URL = `${import.meta.env.BASE_URL}LogoImage/MainLogo.png`;

const createClientUuid = (seed?: string) => {
  if (seed?.trim()) {
    return seed.trim();
  }
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return uuidv4();
};

const SESSION_LOGIN_ENDPOINT = `${API_BASE_URL}/session/login`;

const resolveLoginTimeoutMs = () => {
  const raw = import.meta.env.VITE_LOGIN_TIMEOUT_MS ?? import.meta.env.VITE_HTTP_TIMEOUT_MS ?? '';
  const parsed = Number(raw);
  if (!Number.isFinite(parsed) || parsed <= 0) return 10000;
  return parsed;
};

const waitMs = (duration: number) => new Promise<void>((resolve) => setTimeout(resolve, duration));

const normalize = (value: string) => value.trim();

const normalizeRoles = (roles?: Array<string | { role?: string }>) => {
  if (!roles) return [];
  return roles
    .map((entry) => (typeof entry === 'string' ? entry : entry?.role))
    .filter((role): role is string => Boolean(role));
};

const inferRole = (_userId: string, roles?: Array<string | { role?: string }>) => {
  const normalized = normalizeRoles(roles);
  if (normalized.length > 0) return normalized[0];
  return 'unknown';
};

type LoginFormValues = {
  facilityId: string;
  userId: string;
  password: string;
  clientUuid: string;
};

type FieldKey = keyof LoginFormValues;

type LoginStatus = 'idle' | 'loading' | 'success' | 'error';

export interface SessionAuthResponse {
  facilityId?: string;
  userId?: string;
  displayName?: string;
  commonName?: string;
  roles?: Array<string | { role?: string }>;
  clientUuid?: string;
  runId?: string;
}

export type LoginResult = {
  facilityId: string;
  userId: string;
  displayName?: string;
  commonName?: string;
  clientUuid: string;
  runId: string;
  role: string;
  roles?: string[];
};

const resolveAbsoluteApiBaseUrl = () => {
  if (typeof window === 'undefined') return null;
  try {
    return new URL(API_BASE_URL, window.location.origin);
  } catch {
    return null;
  }
};

const assertLoginTargetIsAllowed = () => {
  if (typeof window === 'undefined' || window.location.protocol !== 'https:') {
    return;
  }
  const absoluteBaseUrl = resolveAbsoluteApiBaseUrl();
  if (absoluteBaseUrl?.protocol === 'http:') {
    throw new Error('HTTPS 画面から HTTP 接続先へは送れません。設定を修正してください。');
  }
};

export const normalizeSessionResult = (
  data: SessionAuthResponse,
  fallback: {
    facilityId: string;
    userId: string;
    clientUuid: string;
    runId: string;
  },
): LoginResult => {
  const normalizedRoles = normalizeRoles(data.roles);
  return {
    facilityId: data.facilityId ?? fallback.facilityId,
    userId: data.userId ?? fallback.userId,
    displayName: data.displayName,
    commonName: data.commonName,
    clientUuid: data.clientUuid ?? fallback.clientUuid,
    runId: data.runId ?? fallback.runId,
    role: inferRole(fallback.userId, normalizedRoles),
    roles: normalizedRoles,
  };
};

type LoginScreenProps = {
  onLoginSuccess?: (result: LoginResult) => void;
  initialFacilityId?: string;
  lockFacilityId?: boolean;
};

export const LoginScreen = ({ onLoginSuccess, initialFacilityId, lockFacilityId = false }: LoginScreenProps) => {
  const [values, setValues] = useState<LoginFormValues>(() => ({
    facilityId: initialFacilityId ?? '',
    userId: '',
    password: '',
    clientUuid: '',
  }));
  const [errors, setErrors] = useState<Partial<Record<FieldKey, string>>>({});
  const [status, setStatus] = useState<LoginStatus>('idle');
  const [feedback, setFeedback] = useState<string | null>(null);
  const [profile, setProfile] = useState<LoginResult | null>(null);

  const isLoading = status === 'loading';
  const isSuccess = status === 'success';
  const normalizedFacilityId = normalize(values.facilityId);
  const normalizedUserId = normalize(values.userId);
  const shouldLockFacility = lockFacilityId && Boolean(normalizedFacilityId);
  const resolvedFacilityId = shouldLockFacility ? (initialFacilityId ?? values.facilityId) : values.facilityId;
  const normalizedResolvedFacilityId = normalize(resolvedFacilityId);
  const canSubmit = Boolean(normalizedResolvedFacilityId && normalizedUserId && values.password && !isLoading);

  useEffect(() => {
    const notice = consumeSessionExpiredNotice();
    if (notice?.message) {
      setFeedback(notice.message);
      setStatus('error');
    }
  }, []);

  useEffect(() => {
    if (!initialFacilityId) return;
    setValues((prev) =>
      prev.facilityId === initialFacilityId ? prev : { ...prev, facilityId: initialFacilityId },
    );
  }, [initialFacilityId]);

  const handleChange = (key: FieldKey) => (event: ChangeEvent<HTMLInputElement>) => {
    setValues((prev) => ({ ...prev, [key]: event.target.value }));
  };
  const handleFacilityChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (shouldLockFacility) return;
    handleChange('facilityId')(event);
  };

  const validate = (form: LoginFormValues) => {
    const next: Partial<Record<FieldKey, string>> = {};
    if (!normalize(form.facilityId)) {
      next.facilityId = '施設IDを入力してください。';
    }
    if (!normalize(form.userId)) {
      next.userId = 'ユーザーIDを入力してください。';
    }
    if (!form.password) {
      next.password = 'パスワードを入力してください。';
    }
    return next;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFeedback(null);
    setProfile(null);

    const generatedClientUuid = createClientUuid();
    const normalizedValues: LoginFormValues = {
      facilityId: normalize(resolvedFacilityId),
      userId: normalize(values.userId),
      password: values.password,
      clientUuid: generatedClientUuid,
    };
    setValues((prev) => ({ ...prev, clientUuid: generatedClientUuid }));

    const nextErrors = validate(normalizedValues);
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      setStatus('error');
      return;
    }

    setErrors({});
    setStatus('loading');

    const runId = generateRunId();
    updateObservabilityMeta({ runId, traceId: undefined });

    try {
      logAuditEvent({
        runId,
        source: 'auth',
        note: 'login attempt',
        payload: {
          action: 'login',
          screen: 'login',
          facilityId: normalizedValues.facilityId,
          userId: normalizedValues.userId,
        },
      });
      const result = await performLogin(normalizedValues, runId);
      setProfile(result);
      setFeedback('ログインに成功しました。');
      setStatus('success');
      logAuditEvent({
        runId: result.runId,
        source: 'auth',
        note: 'login success',
        payload: {
          action: 'login',
          screen: 'login',
          outcome: 'success',
          facilityId: result.facilityId,
          userId: result.userId,
          role: result.role,
          roles: result.roles,
        },
      });
      onLoginSuccess?.(result);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'ログインに失敗しました。';
      setFeedback(message);
      setStatus('error');
      logAuditEvent({
        runId,
        source: 'auth',
        note: 'login denied',
        payload: {
          action: 'login',
          screen: 'login',
          outcome: 'denied',
          facilityId: normalizedValues.facilityId,
          userId: normalizedValues.userId,
          reason: message,
        },
      });
    }
  };

  const buttonLabel = useMemo(() => {
    if (isLoading) {
      return 'ログイン中…';
    }
    if (isSuccess) {
      return '再ログイン';
    }
    return 'ログイン';
  }, [isLoading, isSuccess]);

  return (
    <main className="login-shell">
      <section className="login-card" aria-labelledby="login-heading">
        <header className="login-card__header">
          <h1 id="login-heading" className="login-card__title">
            OpenDolphin Web ログイン
          </h1>
          <div className="login-brand">
            <div className="login-brand__badge">
              <img src={SYSTEM_ICON_URL} alt="OpenDolphin システムアイコン" />
            </div>

          </div>

        </header>

        <form className="login-form" onSubmit={handleSubmit} noValidate>
          <label className="field">
            <span>施設ID</span>
            <input
              id="login-facility-id"
              name="loginFacilityId"
              type="text"
              autoComplete="organization"
              value={resolvedFacilityId}
              onChange={handleFacilityChange}
              placeholder="例: 0001"
              disabled={isLoading}
            />
            {errors.facilityId ? <span className="field-error">{errors.facilityId}</span> : null}
          </label>

          <label className="field">
            <span>ユーザーID</span>
            <input
              id="login-user-id"
              name="loginUserId"
              type="text"
              autoComplete="username"
              value={values.userId}
              onChange={handleChange('userId')}
              placeholder="例: doctor01"
              disabled={isLoading}
            />
            {errors.userId ? <span className="field-error">{errors.userId}</span> : null}
          </label>

          <label className="field">
            <span>パスワード</span>
            <input
              id="login-password"
              name="loginPassword"
              type="password"
              autoComplete="current-password"
              value={values.password}
              onChange={handleChange('password')}
              placeholder="パスワード"
              disabled={isLoading}
            />
            {errors.password ? <span className="field-error">{errors.password}</span> : null}
          </label>

          <div className="login-form__actions">
            <button type="submit" disabled={!canSubmit}>
              {buttonLabel}
            </button>
          </div>

          {feedback ? (
            <div className={`status-message ${isSuccess ? 'is-success' : 'is-error'}`} role="status">
              {feedback}
              {isSuccess && profile ? (
                <p className="status-message__detail">
                  サインインユーザー: {profile.displayName ?? profile.commonName ?? `${profile.facilityId}:${profile.userId}`}
                </p>
              ) : null}
            </div>
          ) : null}
        </form>
      </section>
    </main >
  );
};

const performLogin = async (payload: LoginFormValues, runId: string): Promise<LoginResult> => {
  const clientUuid = createClientUuid(payload.clientUuid);
  assertLoginTargetIsAllowed();
  const timeoutMs = resolveLoginTimeoutMs();
  const sendLogin = async (signal?: AbortSignal) =>
    httpFetch(SESSION_LOGIN_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include',
      notifySessionExpired: false,
      cache: 'no-store',
      body: JSON.stringify({
        facilityId: payload.facilityId,
        userId: payload.userId,
        password: payload.password,
        clientUuid,
      }),
      signal,
    });

  const executeWithTimeout = async () => {
    const endpoint = SESSION_LOGIN_ENDPOINT;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const abortListener = () => {
      console.warn('[login][/api/session/login] aborted', {
        endpoint,
        protocol: typeof window !== 'undefined' ? window.location.protocol : 'unknown',
        attempt: 'pending',
      });
    };
    controller.signal.addEventListener('abort', abortListener);
    console.info('[login][/api/session/login] request start', {
      endpoint,
      timeoutMs,
      protocol: typeof window !== 'undefined' ? window.location.protocol : 'unknown',
    });
    try {
      const response = await sendLogin(controller.signal);
      console.info('[login][/api/session/login] request complete', {
        endpoint,
        status: response.status,
        ok: response.ok,
      });
      return response;
    } finally {
      clearTimeout(timer);
      controller.signal.removeEventListener('abort', abortListener);
    }
  };

  const shouldRetry = (error: unknown) => {
    if (error instanceof DOMException && error.name === 'AbortError') return true;
    if (error instanceof Error) {
      const message = error.message.toLowerCase();
      return (
        message.includes('abort') ||
        message.includes('aborted') ||
        message.includes('err_aborted') ||
        message.includes('failed to fetch') ||
        message.includes('networkerror') ||
        message.includes('timeout')
      );
    }
    return false;
  };

  let response: Response | null = null;
  const maxAttempts = 2;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      response = await executeWithTimeout();
      break;
    } catch (error) {
      try {
        const errorName = error instanceof Error ? error.name : typeof error;
        const errorMessage = error instanceof Error ? error.message : String(error);
        const errorStack = error instanceof Error ? error.stack : undefined;
        const currentProtocol = typeof window !== 'undefined' ? window.location.protocol : 'unknown';
        console.warn('[login][/api/session/login] request failed', {
          endpoint: SESSION_LOGIN_ENDPOINT,
          protocol: currentProtocol,
          attempt,
          errorName,
          errorMessage,
          errorStack,
        });
      } catch {
        // ignore logging errors
      }
      if (attempt < maxAttempts && shouldRetry(error)) {
        await waitMs(400);
        continue;
      }
      if (shouldRetry(error)) {
        const base = '通信がタイムアウトまたは中断されました。時間をおいて再試行してください。';
        throw new Error(base);
      }
      throw error;
    }
  }

  if (!response) {
    throw new Error('ログイン応答を取得できませんでした。');
  }

  if (!response.ok) {
    const body = await response.text();
    throw new Error(
      resolveLoginFailureMessage({
        status: response.status,
        bodyText: body,
        statusText: response.statusText,
        retryAfter: response.headers.get('Retry-After') ?? undefined,
      }),
    );
  }

  const data = (await response.json()) as SessionAuthResponse;
  return normalizeSessionResult(data, {
    facilityId: payload.facilityId,
    userId: payload.userId,
    clientUuid,
    runId,
  });
};
