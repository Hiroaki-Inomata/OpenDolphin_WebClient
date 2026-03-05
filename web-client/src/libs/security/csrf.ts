const CSRF_PLACEHOLDER = '__CSRF_TOKEN__';

export const readCsrfToken = (): string | undefined => {
  if (typeof document === 'undefined') return undefined;
  const content = document.querySelector("meta[name='csrf-token']")?.getAttribute('content');
  if (typeof content !== 'string') return undefined;
  const token = content.trim();
  if (!token || token === CSRF_PLACEHOLDER) return undefined;
  return token;
};
