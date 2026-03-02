type DevVolatilePlain = {
  facilityId: string;
  userId: string;
  passwordPlain: string;
};

let current: DevVolatilePlain | null = null;

export const setDevVolatilePlainPassword = (input: DevVolatilePlain) => {
  if (!import.meta.env.DEV) return;
  current = { ...input };
};

export const getDevVolatilePlainPassword = (match: { facilityId: string; userId: string }) => {
  if (!import.meta.env.DEV) return undefined;
  if (!current) return undefined;
  if (current.facilityId !== match.facilityId) return undefined;
  if (current.userId !== match.userId) return undefined;
  return current.passwordPlain;
};

export const clearDevVolatilePlainPassword = () => {
  current = null;
};
