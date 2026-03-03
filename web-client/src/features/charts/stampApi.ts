import { httpFetch } from '../../libs/http/httpClient';
import { generateRunId, getObservabilityMeta, updateObservabilityMeta } from '../../libs/observability/observability';

export type UserProfileResult = {
  ok: boolean;
  runId?: string;
  status?: number;
  id?: number;
  userId?: string;
  message?: string;
};

export type StampTreeEntry = {
  name: string;
  role?: string;
  entity: string;
  memo?: string;
  stampId: string;
};

export type StampTree = {
  treeName?: string;
  entity: string;
  treeOrder?: string;
  stampList: StampTreeEntry[];
};

export type StampTreeResult = {
  ok: boolean;
  runId?: string;
  status?: number;
  trees: StampTree[];
  message?: string;
};

export type StampBundleItemJson = {
  name?: string;
  number?: string;
  unit?: string;
  memo?: string;
};

export type StampBundleJson = {
  className?: string;
  classCode?: string;
  classCodeSystem?: string;
  admin?: string;
  adminCode?: string;
  adminCodeSystem?: string;
  adminMemo?: string;
  bundleNumber?: string;
  memo?: string;
  insurance?: string;
  orderName?: string;
  claimItem?: StampBundleItemJson[];
};

export type StampDetailResult = {
  ok: boolean;
  runId?: string;
  status?: number;
  stampId: string;
  stamp?: StampBundleJson;
  message?: string;
};

const STAMP_TOUCH_DISABLED_MESSAGE =
  'スタンプ参照API（/touch）はサーバー側で無効化されています。ローカルスタンプを利用してください。';

const parseJson = async (response: Response): Promise<unknown> => {
  try {
    return await response.json();
  } catch {
    try {
      const text = await response.text();
      return text ? JSON.parse(text) : {};
    } catch {
      return {};
    }
  }
};

export async function fetchUserProfile(userName: string): Promise<UserProfileResult> {
  const runId = getObservabilityMeta().runId ?? generateRunId();
  updateObservabilityMeta({ runId });
  const response = await httpFetch(`/user/${encodeURIComponent(userName)}`);
  const json = (await parseJson(response)) as Record<string, unknown>;
  return {
    ok: response.ok,
    runId,
    status: response.status,
    id: typeof json.id === 'number' ? (json.id as number) : undefined,
    userId: typeof json.userId === 'string' ? (json.userId as string) : undefined,
    message: response.ok ? undefined : (json.message as string | undefined),
  };
}

export async function fetchStampTree(_userPk: number): Promise<StampTreeResult> {
  const runId = getObservabilityMeta().runId ?? generateRunId();
  updateObservabilityMeta({ runId });
  return {
    ok: false,
    runId,
    status: 404,
    trees: [],
    message: STAMP_TOUCH_DISABLED_MESSAGE,
  };
}

export async function fetchStampDetail(stampId: string): Promise<StampDetailResult> {
  const runId = getObservabilityMeta().runId ?? generateRunId();
  updateObservabilityMeta({ runId });
  return {
    ok: false,
    runId,
    status: 404,
    stampId,
    stamp: undefined,
    message: STAMP_TOUCH_DISABLED_MESSAGE,
  };
}
