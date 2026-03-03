import { applyHeaderFlagsToInit } from './header-flags';
import { getDevVolatilePlainPassword } from './devAuthVolatile';
import { applyObservabilityHeaders, captureObservabilityFromResponse } from '../observability/observability';
import { notifySessionExpired } from '../session/sessionExpiry';
import { readStoredSession } from '../session/storedSession';
import { readCsrfToken } from '../security/csrf';

type StoredAuth = {
  facilityId: string;
  userId: string;
  passwordPlain?: string;
  clientUuid?: string;
};

const readAuthFromStorage = (storage: Storage | undefined): StoredAuth | null => {
  if (!storage) return null;
  try {
    const facilityId = storage.getItem('devFacilityId');
    const userId = storage.getItem('devUserId');
    const clientUuid = storage.getItem('devClientUuid') ?? undefined;
    if (!facilityId || !userId) {
      return null;
    }
    return { facilityId, userId, clientUuid };
  } catch {
    return null;
  }
};

function readStoredAuth(): StoredAuth | null {
  if (!import.meta.env.DEV) return null;
  const sessionAuth = readAuthFromStorage(typeof sessionStorage === 'undefined' ? undefined : sessionStorage);
  const localAuth = readAuthFromStorage(typeof localStorage === 'undefined' ? undefined : localStorage);

  // 再ログイン直後の最新資格情報はタブ単位の sessionStorage を優先する。
  if (sessionAuth) {
    return {
      ...sessionAuth,
      passwordPlain: getDevVolatilePlainPassword({
        facilityId: sessionAuth.facilityId,
        userId: sessionAuth.userId,
      }),
      clientUuid: sessionAuth.clientUuid ?? localAuth?.clientUuid,
    };
  }

  if (!localAuth) {
    return null;
  }

  return {
    ...localAuth,
    passwordPlain: getDevVolatilePlainPassword({
      facilityId: localAuth.facilityId,
      userId: localAuth.userId,
    }),
  };
}

export function hasStoredAuth(): boolean {
  return readStoredAuth() !== null;
}

const resolveBaseOrigin = (): string => {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }
  return 'http://localhost';
};

const resolveUrl = (input?: string | URL | null): URL | null => {
  if (!input) return null;
  if (input instanceof URL) return input;
  const trimmed = input.trim();
  if (!trimmed) return null;
  try {
    return new URL(trimmed, resolveBaseOrigin());
  } catch {
    return null;
  }
};

const resolveRequestUrl = (input: RequestInfo | URL): URL | null => {
  if (input instanceof URL) return input;
  if (typeof input === 'string') return resolveUrl(input);
  if (input instanceof Request) return resolveUrl(input.url);
  return null;
};

const isSameOrigin = (url?: URL | null): boolean => {
  if (!url) return false;
  return url.origin === resolveBaseOrigin();
};

function applyAuthHeaders(init?: RequestInit, url?: URL | null): RequestInit {
  if (!import.meta.env.DEV || !url || !isSameOrigin(url) || !isOrcaEndpoint(url)) {
    return init ?? {};
  }
  const stored = readStoredAuth();
  if (!stored) {
    return init ?? {};
  }

  const headers = new Headers(init?.headers ?? {});

  if (stored.passwordPlain && !headers.has('Authorization')) {
    const username = `${stored.facilityId}:${stored.userId}`;
    const token = btoa(unescape(encodeURIComponent(`${username}:${stored.passwordPlain}`)));
    headers.set('Authorization', `Basic ${token}`);
  }

  return { ...(init ?? {}), headers };
}

const normalizeHeaders = (headers?: HeadersInit): Record<string, string> => {
  if (!headers) return {};
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
};

export function buildHttpHeaders(init?: RequestInit, pathname?: string | null): Record<string, string> {
  const url = resolveUrl(pathname);
  const withObservability = applyObservabilityHeaders(init);
  const withFlags = applyHeaderFlagsToInit(withObservability);
  const withAuth = applyAuthHeaders(withFlags, url);
  return normalizeHeaders(withAuth.headers);
}

export type HttpEndpointDefinition = {
  id: string;
  group?: 'outpatient' | 'images';
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'OPTIONS' | 'ANY';
  path: string;
  purpose: string;
  auditMetadata: readonly string[];
  sourceDocs: readonly string[];
};

export const OUTPATIENT_API_ENDPOINTS: readonly HttpEndpointDefinition[] = [
  {
    id: 'appointmentOutpatient',
    group: 'outpatient',
    method: 'ANY',
    path: '/orca/appointments/*',
    purpose: '予約一覧・患者／請求試算・来院状況を取得して ORCA バナーの `runId`/`dataSource` を連携する。',
    auditMetadata: ['runId', 'dataSource', 'cacheHit', 'missingMaster', 'fallbackUsed', 'dataSourceTransition', 'fetchedAt'],
    sourceDocs: ['docs/server-modernization/api-architecture-consolidation-plan.md'],
  },
  {
    id: 'medicalOutpatient',
    group: 'outpatient',
    method: 'ANY',
    path: '/orca21/medicalmodv2/outpatient',
    purpose: 'Charts/DocumentTimeline が表示する外来の Medical record を取得し、`auditEvent` に `recordsReturned`/`outcome` を記録する。',
    auditMetadata: ['runId', 'dataSource', 'cacheHit', 'missingMaster', 'fallbackUsed', 'dataSourceTransition', 'recordsReturned'],
    sourceDocs: [
      'docs/server-modernization/phase2/operations/logs/20251208T124645Z-api-gap-implementation.md',
      'docs/server-modernization/phase2/operations/logs/20251124T073245Z-webclient-master-bridge.md',
      'docs/web-client/architecture/web-client-api-mapping.md',
      'docs/server-modernization/phase2/operations/logs/20251205T090000Z-integration-implementation.md',
      'docs/server-modernization/phase2/operations/logs/20251205T150000Z-integration-implementation.md',
    ],
  },
  {
    id: 'diseaseMutation',
    group: 'outpatient',
    method: 'ANY',
    path: '/orca/disease',
    purpose: 'Charts の病名編集で傷病名を登録・更新・削除し、主/疑い/開始/転帰を監査ログへ連携する。',
    auditMetadata: ['runId', 'operation', 'patientId'],
    sourceDocs: ['docs/web-client/ux/charts-claim-ui-policy.md'],
  },
  {
    id: 'orderBundleMutation',
    group: 'outpatient',
    method: 'ANY',
    path: '/orca/order/bundles',
    purpose: 'Charts の処方（RP）/オーダー束編集でバンドルを登録・更新・削除し、監査イベントへ反映する。',
    auditMetadata: ['runId', 'operation', 'patientId', 'entity'],
    sourceDocs: ['docs/web-client/ux/charts-claim-ui-policy.md'],
  },
  {
    id: 'patientOutpatient',
    group: 'outpatient',
    method: 'ANY',
    path: '/orca12/patientmodv2/outpatient',
    purpose: 'Patients/Administration で患者基本・保険情報を更新し、新規追加・削除・保険変更の `action=ORCA_PATIENT_MUTATION` を生成する。',
    auditMetadata: ['runId', 'dataSource', 'cacheHit', 'missingMaster', 'fallbackUsed', 'operation'],
    sourceDocs: [
      'docs/web-client/architecture/web-client-api-mapping.md',
      'docs/server-modernization/phase2/operations/logs/20251204T064209Z-api-gap.md',
      'docs/server-modernization/phase2/operations/logs/20251205T090000Z-integration-implementation.md',
      'docs/server-modernization/phase2/operations/logs/20251205T150000Z-integration-implementation.md',
    ],
  },
  {
    id: 'patientOutpatientInfo',
    group: 'outpatient',
    method: 'ANY',
    path: '/orca/patients/local-search/*',
    purpose: 'Reception/Patients 用にローカル患者検索を実行し、`missingMaster`/`cacheHit` を含めた `audit` を生成する。',
    auditMetadata: ['runId', 'dataSource', 'cacheHit', 'missingMaster', 'fallbackUsed', 'dataSourceTransition', 'fetchedAt', 'recordsReturned'],
    sourceDocs: ['docs/server-modernization/api-architecture-consolidation-plan.md'],
  },
];

export const KARTE_IMAGE_API_ENDPOINTS: readonly HttpEndpointDefinition[] = [
  {
    id: 'karteImages',
    group: 'images',
    method: 'GET',
    path: '/karte/images',
    purpose: 'カルテ画像の一覧（Charts 添付/画像タブ想定）を取得する。',
    auditMetadata: ['runId', 'traceId', 'recordsReturned', 'fetchedAt'],
    sourceDocs: ['artifacts/api-stability/20251123T130134Z/mocks/images-placeholder.md'],
  },
  {
    id: 'karteIamgesTypo',
    group: 'images',
    method: 'GET',
    path: '/karte/iamges',
    purpose: 'カルテ画像一覧の legacy typo エンドポイント（暫定互換）。',
    auditMetadata: ['runId', 'traceId', 'recordsReturned', 'fetchedAt'],
    sourceDocs: [
      'src/server_modernized_gap_20251221/02_karte_gap/KRT_03_karte_image_PathParam修正.md',
      'docs/web-client/architecture/karte-image-typo-support.md',
    ],
  },
  {
    id: 'karteImageDetail',
    group: 'images',
    method: 'GET',
    path: '/karte/image/{id}',
    purpose: 'カルテ画像の詳細（SchemaModel）を取得する。',
    auditMetadata: ['runId', 'traceId', 'imageId', 'fetchedAt'],
    sourceDocs: ['src/server_modernized_gap_20251221/02_karte_gap/KRT_03_karte_image_PathParam修正.md'],
  },
  {
    id: 'karteAttachmentDetail',
    group: 'images',
    method: 'GET',
    path: '/karte/attachment/{id}',
    purpose: 'カルテ添付ファイルを取得する。',
    auditMetadata: ['runId', 'traceId', 'attachmentId', 'fetchedAt'],
    sourceDocs: ['src/server_modernized_gap_20251221/02_karte_gap/KRT_01_Document更新API.md'],
  },
  {
    id: 'karteDocumentUpdate',
    group: 'images',
    method: 'PUT',
    path: '/karte/document',
    purpose: 'カルテ文書（Document）への添付送信と本文更新を行う。',
    auditMetadata: ['runId', 'traceId', 'documentId', 'attachmentsSent', 'fetchedAt'],
    sourceDocs: ['src/server_modernized_gap_20251221/02_karte_gap/KRT_01_Document更新API.md'],
  },
];

// `resolveMasterSource` が `dataSourceTransition=server` を返す経路ではこの `outpatient` グループを使い、`cacheHit`/`missingMaster` を `telemetryClient` に継承します。
// RUN_ID=20251205T150000Z の統合実装ではこのパス一覧を経由し、`docs/server-modernization/phase2/operations/logs/20251205T150000Z-integration-implementation.md` へ telemetry funnel を記録しています。

export type HttpFetchInit = RequestInit & {
  /**
   * 403（権限不足）をセッション失効扱いとして通知する場合に明示的に有効化する。
   * デフォルトでは 403 では失効通知を行わず、UI 側のエラーバナー/トーストで吸収する。
   */
  notifyForbiddenAsSessionExpiry?: boolean;
  /**
   * 認証エラー検知時のセッション失効通知を抑止する。
   * ORCA 接続など別系統の認証で 401 が返る場合に使用する。
   */
  notifySessionExpired?: boolean;
};

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

const isOrcaEndpoint = (url?: URL | null): boolean => {
  if (!url) return false;
  // NOTE:
  // - `/orca*` / `/api01*` / `/api21` / `/blobapi` are ORCA-family endpoints.
  // - `/karte` / `/odletter` / `/touch` / `/user` are legacy resources that, in DEV,
  //   can also require explicit Basic auth headers when container principal is unavailable.
  // These endpoints must not trigger global session-expired broadcast on 401/403 because
  // they may fail for per-resource auth reasons while the app session is still valid.
  const pattern =
    /^\/(orca\d*|api\/orca(?:\d+)?|api01(rv2)?|api21|blobapi|karte|odletter|touch|user|api\/admin|api\/chart-events|chart-events|api\/realtime|realtime)(\/|$)/;
  return pattern.test(url.pathname);
};

const applyCsrfHeaders = (init?: RequestInit, url?: URL | null): RequestInit => {
  if (!url || !isSameOrigin(url)) return init ?? {};
  const method = (init?.method ?? 'GET').toUpperCase();
  if (SAFE_METHODS.has(method)) return init ?? {};

  const headers = new Headers(init?.headers ?? {});
  if (headers.has('X-CSRF-Token')) return init ?? {};

  const token = readCsrfToken();
  if (!token) return init ?? {};

  headers.set('X-CSRF-Token', token);
  return { ...(init ?? {}), headers };
};

export const shouldNotifySessionExpired = (status: number, init?: HttpFetchInit) => {
  if (init?.notifySessionExpired === false) return false;
  if (status === 403 && !init?.notifyForbiddenAsSessionExpiry) return false;
  if (status !== 401 && status !== 403 && status !== 419 && status !== 440) return false;
  const session = readStoredSession();
  return Boolean(session);
};

export async function httpFetch(input: RequestInfo | URL, init?: HttpFetchInit) {
  const requestUrl = resolveRequestUrl(input);
  // Header flags are applied here to propagate Playwright extraHTTPHeaders.
  // 新しいフラグを追加する場合は header-flags.ts に追記し、この呼び出しで一括適用される前提。
  const initWithFlags = applyHeaderFlagsToInit(applyAuthHeaders(init, requestUrl));
  const initWithCsrf = applyCsrfHeaders(initWithFlags, requestUrl);
  const initWithObservability = applyObservabilityHeaders(initWithCsrf);
  // 認証クッキー（JSESSIONID 等）を常に送るため、デフォルトで include を付与する。
  const credentials = initWithObservability.credentials ?? 'include';
  const response = await fetch(input, { ...initWithObservability, credentials });
  captureObservabilityFromResponse(response);
  const resolvedInit =
    init?.notifySessionExpired === undefined && isOrcaEndpoint(requestUrl)
      ? { ...init, notifySessionExpired: false }
      : init;
  if (shouldNotifySessionExpired(response.status, resolvedInit)) {
    const reason =
      response.status === 403
        ? 'forbidden'
        : response.status === 419 || response.status === 440
          ? 'timeout'
          : 'unauthorized';
    notifySessionExpired(reason, response.status);
  }
  return response;
}
