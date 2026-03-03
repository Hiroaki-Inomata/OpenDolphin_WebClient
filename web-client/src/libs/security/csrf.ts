export const readCsrfToken = (): string | undefined => {
  if (typeof document === 'undefined') return undefined;
  const content = document.querySelector("meta[name='csrf-token']")?.getAttribute('content');
  if (typeof content !== 'string') return undefined;
  const token = content.trim();
  return token.length > 0 ? token : undefined;
};
