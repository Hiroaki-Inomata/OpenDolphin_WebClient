import { ensureObservabilityMeta, getObservabilityMeta } from '../../libs/observability/observability';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '');

export const TOUCH_ADM_PHR_DISABLED_MESSAGE =
  'Touch/ADM/PHR/Demo API は server-modernized 側で無効化されています。クライアントからの送信は実行されません。';

export type TouchAdmPhrMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'OPTIONS' | 'HEAD';
export type TouchAdmPhrContentType = 'json' | 'text' | 'form' | 'xml';

export type TouchAdmPhrContext = {
  facilityId?: string;
  userId?: string;
  patientId?: string;
  patientPk?: string;
  accessKey?: string;
};

export type TouchAdmPhrEndpoint = {
  id: string;
  group: 'touch' | 'adm10' | 'adm20' | 'phr' | 'demo';
  label: string;
  method: TouchAdmPhrMethod;
  description: string;
  buildPath: (context: TouchAdmPhrContext) => string;
  buildQuery?: (context: TouchAdmPhrContext) => string | undefined;
  buildBody?: (context: TouchAdmPhrContext) => string | undefined;
  defaultContentType?: TouchAdmPhrContentType;
  accept?: string;
  stub?: boolean;
  stubHint?: string;
  disabledReason?: string;
};

export const TOUCH_ADM_PHR_ENDPOINTS: readonly TouchAdmPhrEndpoint[] = [
  {
    id: 'touch-adm-phr-disabled',
    group: 'touch',
    label: 'Touch/ADM/PHR/Demo (disabled)',
    method: 'GET',
    description: 'サーバー側で無効化済みのため、通信は実行しません。',
    buildPath: () => '/touch/disabled',
    disabledReason: TOUCH_ADM_PHR_DISABLED_MESSAGE,
  },
];

export type TouchAdmPhrRequest = {
  method: TouchAdmPhrMethod;
  path: string;
  query?: string;
  body?: string;
  contentType?: TouchAdmPhrContentType;
  accept?: string;
};

export type TouchAdmPhrResponseMode = 'json' | 'text' | 'binary';

export type TouchAdmPhrResponse = {
  ok: boolean;
  status: number;
  statusText?: string;
  raw: string;
  json?: unknown;
  mode: TouchAdmPhrResponseMode;
  binarySize?: number;
  contentType?: string;
  headers: Record<string, string>;
  runId?: string;
  traceId?: string;
};

export const buildTouchAdmPhrUrl = (path: string, query?: string) => {
  const normalizedPath = (() => {
    if (!API_BASE_URL) return path;
    if (/^https?:\/\//i.test(path)) return path;
    if (path.startsWith(API_BASE_URL)) return path;
    if (path.startsWith('/')) return `${API_BASE_URL}${path}`;
    return `${API_BASE_URL}/${path}`;
  })();
  if (!query) return normalizedPath;
  const trimmed = query.trim();
  if (!trimmed) return normalizedPath;
  if (normalizedPath.includes('?')) {
    return `${normalizedPath}&${trimmed}`;
  }
  return `${normalizedPath}?${trimmed}`;
};

export async function requestTouchAdmPhr(request: TouchAdmPhrRequest): Promise<TouchAdmPhrResponse> {
  const beforeMeta = ensureObservabilityMeta();
  const target = buildTouchAdmPhrUrl(request.path, request.query);
  const message = `${TOUCH_ADM_PHR_DISABLED_MESSAGE} (${request.method} ${target})`;
  const payload = {
    ok: false,
    status: 404,
    disabled: true,
    message,
    endpoint: target,
  };
  const meta = getObservabilityMeta();

  return {
    ok: false,
    status: 404,
    statusText: 'Not Found',
    raw: JSON.stringify(payload),
    json: payload,
    mode: 'json',
    contentType: 'application/json',
    headers: {
      'x-touch-adm-phr-disabled': 'true',
    },
    runId: meta.runId ?? beforeMeta.runId,
    traceId: meta.traceId ?? beforeMeta.traceId,
  };
}
