const HTTP_PROTOCOLS = new Set(['http:', 'https:']);

const hasDangerousSchemePrefix = (value: string): boolean => {
  return /^(?:javascript|data|vbscript|file|blob):/i.test(value);
};

const resolveBaseUrl = (baseUrl?: string): URL | null => {
  const candidate = baseUrl ?? (typeof window !== 'undefined' ? window.location.href : undefined);
  if (!candidate) return null;
  try {
    const parsed = new URL(candidate);
    return HTTP_PROTOCOLS.has(parsed.protocol.toLowerCase()) ? parsed : null;
  } catch {
    return null;
  }
};

export const safeSameOriginHttpUrl = (
  rawUrl: string | null | undefined,
  options?: { baseUrl?: string },
): string | undefined => {
  if (typeof rawUrl !== 'string') return undefined;
  const trimmed = rawUrl.trim();
  if (!trimmed || hasDangerousSchemePrefix(trimmed)) return undefined;

  const base = resolveBaseUrl(options?.baseUrl);
  if (!base) return undefined;

  let parsed: URL;
  try {
    parsed = new URL(trimmed, base);
  } catch {
    return undefined;
  }

  if (!HTTP_PROTOCOLS.has(parsed.protocol.toLowerCase())) return undefined;
  if (parsed.origin !== base.origin) return undefined;

  return `${parsed.pathname}${parsed.search}${parsed.hash}`;
};
