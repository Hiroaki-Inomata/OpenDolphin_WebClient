type DevAuthIdentity = {
  facilityId: string;
  userId: string;
};

type DevAuthPayload = DevAuthIdentity & {
  passwordPlain: string;
};

type DevAuthVolatileStore = {
  __opendolphinDevAuthVolatileStore__?: Map<string, string>;
};

const normalizeIdentityValue = (value: string) => value.trim();

const buildIdentityKey = ({ facilityId, userId }: DevAuthIdentity): string | null => {
  const normalizedFacilityId = normalizeIdentityValue(facilityId);
  const normalizedUserId = normalizeIdentityValue(userId);
  if (!normalizedFacilityId || !normalizedUserId) return null;
  return `${normalizedFacilityId}\u0000${normalizedUserId}`;
};

const resolveStore = (): Map<string, string> => {
  const target = globalThis as typeof globalThis & DevAuthVolatileStore;
  if (!target.__opendolphinDevAuthVolatileStore__) {
    target.__opendolphinDevAuthVolatileStore__ = new Map<string, string>();
  }
  return target.__opendolphinDevAuthVolatileStore__;
};

export const setDevVolatilePlainPassword = ({ facilityId, userId, passwordPlain }: DevAuthPayload): void => {
  const key = buildIdentityKey({ facilityId, userId });
  if (!key) return;
  const store = resolveStore();
  if (!passwordPlain) {
    store.delete(key);
    return;
  }
  store.set(key, passwordPlain);
};

export const getDevVolatilePlainPassword = ({ facilityId, userId }: DevAuthIdentity): string | undefined => {
  const key = buildIdentityKey({ facilityId, userId });
  if (!key) return undefined;
  return resolveStore().get(key);
};

export const clearDevVolatilePlainPassword = (): void => {
  resolveStore().clear();
};
