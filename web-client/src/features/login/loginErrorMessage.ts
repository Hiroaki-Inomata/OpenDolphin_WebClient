type LoginErrorJson = {
  error?: unknown;
  code?: unknown;
  errorCode?: unknown;
  errorCategory?: unknown;
  message?: unknown;
  reason?: unknown;
  details?: {
    reason?: unknown;
  };
};

export type LoginFailureKind =
  | 'generic'
  | 'factor2_required'
  | 'factor2_invalid'
  | 'factor2_session_missing'
  | 'factor2_session_expired';

export type LoginFailureResolution = {
  kind: LoginFailureKind;
  message: string;
};

const normalizeText = (value: unknown): string | undefined => {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
};

const parseLoginErrorJson = (bodyText: string): LoginErrorJson | null => {
  const trimmed = bodyText.trim();
  if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return null;
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
    return parsed as LoginErrorJson;
  } catch {
    return null;
  }
};

const resolveReason = (payload: LoginErrorJson | null): string | undefined =>
  normalizeText(payload?.reason) ??
  normalizeText(payload?.errorCode) ??
  normalizeText(payload?.code) ??
  normalizeText(payload?.errorCategory) ??
  normalizeText(payload?.details?.reason) ??
  normalizeText(payload?.error);

const resolveRetryAfterSeconds = (retryAfter: string | undefined): number | undefined => {
  const normalized = normalizeText(retryAfter);
  if (!normalized) return undefined;

  const parsed = Number.parseInt(normalized, 10);
  if (!Number.isNaN(parsed) && parsed > 0) {
    return parsed;
  }
  return undefined;
};

const resolveTooManyRequestsMessage = (retryAfter: string | undefined): string => {
  const seconds = resolveRetryAfterSeconds(retryAfter);
  if (seconds) {
    return `ログイン試行回数が上限に達しました。${seconds}秒後に再試行してください。`;
  }
  return 'ログイン試行回数が上限に達しました。しばらく待ってから再試行してください。';
};

const resolveAuthFailureMessage = (reason: string | undefined, status: number): string => {
  const normalizedReason = reason?.toLowerCase();
  if (normalizedReason === 'factor2_required') {
    return '二要素認証コードを入力してください。';
  }
  if (normalizedReason === 'factor2_invalid') {
    return '認証コードが正しくありません。';
  }
  if (normalizedReason === 'factor2_session_missing' || normalizedReason === 'factor2_session_expired') {
    return '二要素認証セッションが無効です。最初からログインし直してください。';
  }
  if (normalizedReason === 'authentication_failed' || normalizedReason === 'unauthorized') {
    return 'ログインに失敗しました。施設ID・ユーザーID・パスワードを確認してください。';
  }
  if (normalizedReason === 'principal_unresolved') {
    return 'ログインに失敗しました。施設IDの入力が正しいか確認してください。';
  }
  if (normalizedReason === 'header_auth_disabled' || normalizedReason === 'header_authentication_disabled') {
    return 'ログインに失敗しました。認証方式の設定が一致していません。管理者へ連絡してください。';
  }
  if (status === 403) {
    return 'ログインに失敗しました。このアカウントにはアクセス権限がありません。';
  }
  return 'ログインに失敗しました。入力内容を確認して再試行してください。';
};

export const resolveLoginFailure = (params: {
  status: number;
  bodyText?: string;
  statusText?: string;
  retryAfter?: string;
}): LoginFailureResolution => {
  const { status, bodyText = '', statusText, retryAfter } = params;
  const parsed = parseLoginErrorJson(bodyText);
  const reason = resolveReason(parsed)?.toLowerCase();

  if (status === 401 && reason === 'factor2_required') {
    return { kind: 'factor2_required', message: '二要素認証コードを入力してください。' };
  }
  if (status === 401 && reason === 'factor2_invalid') {
    return { kind: 'factor2_invalid', message: '認証コードが正しくありません。' };
  }
  if (status === 401 && reason === 'factor2_session_missing') {
    return { kind: 'factor2_session_missing', message: '二要素認証セッションが無効です。最初からログインし直してください。' };
  }
  if (status === 401 && reason === 'factor2_session_expired') {
    return { kind: 'factor2_session_expired', message: '二要素認証セッションが無効です。最初からログインし直してください。' };
  }

  if (status === 429) {
    return { kind: 'generic', message: resolveTooManyRequestsMessage(retryAfter) };
  }

  if (status === 401 || status === 403) {
    return { kind: 'generic', message: resolveAuthFailureMessage(reason, status) };
  }

  if (status === 404) {
    return { kind: 'generic', message: 'ログイン先が見つかりません。接続先設定を確認してください。' };
  }

  if (status >= 500) {
    return { kind: 'generic', message: 'ログインに失敗しました。サーバー側でエラーが発生しています。時間をおいて再試行してください。' };
  }

  const message = normalizeText(parsed?.message);
  if (message) {
    return { kind: 'generic', message: `ログインに失敗しました。${message}` };
  }

  const resolvedStatusText = normalizeText(statusText);
  if (resolvedStatusText) {
    return { kind: 'generic', message: `ログインに失敗しました（HTTP ${status}: ${resolvedStatusText}）。` };
  }
  return { kind: 'generic', message: `ログインに失敗しました（HTTP ${status}）。` };
};

export const resolveLoginFailureMessage = (params: {
  status: number;
  bodyText?: string;
  statusText?: string;
  retryAfter?: string;
}): string => resolveLoginFailure(params).message;
