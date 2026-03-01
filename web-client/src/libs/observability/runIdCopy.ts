export type ClipboardCopyMethod = 'clipboard' | 'prompt';

export async function copyTextToClipboard(value: string): Promise<ClipboardCopyMethod> {
  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return 'clipboard';
  }

  if (typeof window !== 'undefined') {
    // Fallback for browsers where Clipboard API is unavailable.
    return 'prompt';
  }

  throw new Error('clipboard_unavailable');
}

export async function copyRunIdToClipboard(runId: string): Promise<ClipboardCopyMethod> {
  return copyTextToClipboard(runId);
}
