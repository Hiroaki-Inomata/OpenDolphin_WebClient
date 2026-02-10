import { httpFetch } from '../../libs/http/httpClient';

export type AccessSex = 'M' | 'F' | 'O';

export type AccessManagedUser = {
  userPk: number;
  userId: string;
  loginId: string;
  displayName?: string;
  sirName?: string;
  givenName?: string;
  email?: string;
  roles: string[];
  factor2Auth?: string;
  sex?: AccessSex | null;
  staffRole?: string | null;
  registeredDate?: string | null;
  profileCreatedAt?: string | null;
  profileUpdatedAt?: string | null;
};

export type AccessUsersResponse = {
  runId?: string;
  facilityId?: string;
  users: AccessManagedUser[];
};

export type AccessUserUpsertPayload = {
  loginId?: string;
  password?: string;
  displayName?: string;
  sirName?: string;
  givenName?: string;
  email?: string;
  roles?: string[];
  sex?: AccessSex | '';
  staffRole?: string | '';
};

export type AccessPasswordResetPayload = {
  totpCode: string;
};

export type AccessPasswordResetResponse = {
  runId?: string;
  ok: boolean;
  userPk: number;
  loginId?: string;
  temporaryPassword?: string;
};

export type ApiFailure = Error & { status?: number; errorCode?: string };

const ACCESS_USERS_ENDPOINT = '/api/admin/access/users';

const safeJson = async (response: Response) => {
  const contentType = response.headers.get('content-type') ?? '';
  const text = await response.text();
  if (!contentType.includes('application/json')) {
    throw new Error(`unexpected content-type: ${contentType || 'unknown'} (body starts with: ${text.slice(0, 80)})`);
  }
  return JSON.parse(text) as unknown;
};

const readApiError = async (response: Response): Promise<ApiFailure> => {
  let json: any = null;
  try {
    json = await safeJson(response);
  } catch {
    // ignore
  }
  const errorCode = (json?.errorCode ?? json?.code ?? json?.error) as string | undefined;
  const message =
    (json?.message as string | undefined) ??
    (json?.errorMessage as string | undefined) ??
    `HTTP ${response.status} (${response.url || ACCESS_USERS_ENDPOINT})`;
  const err: ApiFailure = new Error(message);
  err.status = response.status;
  err.errorCode = errorCode;
  return err;
};

const requireOkJson = async <T>(response: Response): Promise<T> => {
  if (!response.ok) {
    throw await readApiError(response);
  }
  return (await safeJson(response)) as T;
};

export async function fetchAccessUsers(): Promise<AccessUsersResponse> {
  const response = await httpFetch(ACCESS_USERS_ENDPOINT, { method: 'GET', notifySessionExpired: false });
  return await requireOkJson<AccessUsersResponse>(response);
}

export async function createAccessUser(payload: AccessUserUpsertPayload): Promise<AccessManagedUser> {
  const response = await httpFetch(ACCESS_USERS_ENDPOINT, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
    notifySessionExpired: false,
  });
  const json = await requireOkJson<{ user?: AccessManagedUser } & Record<string, unknown>>(response);
  if (!json.user) {
    throw new Error('create user response is missing user');
  }
  return json.user;
}

export async function updateAccessUser(userPk: number, payload: AccessUserUpsertPayload): Promise<AccessManagedUser> {
  const response = await httpFetch(`${ACCESS_USERS_ENDPOINT}/${encodeURIComponent(String(userPk))}`, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
    notifySessionExpired: false,
  });
  const json = await requireOkJson<{ user?: AccessManagedUser } & Record<string, unknown>>(response);
  if (!json.user) {
    throw new Error('update user response is missing user');
  }
  return json.user;
}

export async function resetAccessUserPassword(
  userPk: number,
  payload: AccessPasswordResetPayload,
): Promise<AccessPasswordResetResponse> {
  const response = await httpFetch(`${ACCESS_USERS_ENDPOINT}/${encodeURIComponent(String(userPk))}/password-reset`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload ?? {}),
    notifySessionExpired: false,
  });
  return await requireOkJson<AccessPasswordResetResponse>(response);
}

