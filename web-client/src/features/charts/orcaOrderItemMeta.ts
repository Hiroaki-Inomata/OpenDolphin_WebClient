export type OrcaOrderItemMeta = {
  // "yes"/"no" only. When omitted, ORCA uses its own default setting.
  genericFlg?: 'yes' | 'no';
};

const META_PREFIX = '__orca_meta__:';

const normalizeGenericFlg = (value: unknown): OrcaOrderItemMeta['genericFlg'] => {
  if (value === 'yes' || value === 'no') return value;
  return undefined;
};

const isEmptyMeta = (meta: OrcaOrderItemMeta) => !meta.genericFlg;

export function parseOrcaOrderItemMemo(memo?: string | null): { meta: OrcaOrderItemMeta; memoText: string } {
  const raw = typeof memo === 'string' ? memo : '';
  if (!raw || !raw.startsWith(META_PREFIX)) {
    return { meta: {}, memoText: raw };
  }
  const [firstLine, ...rest] = raw.split('\n');
  const jsonPart = firstLine.slice(META_PREFIX.length).trim();
  const memoText = rest.join('\n');
  if (!jsonPart) return { meta: {}, memoText };
  try {
    const parsed = JSON.parse(jsonPart) as Record<string, unknown>;
    return {
      meta: {
        genericFlg: normalizeGenericFlg(parsed.genericFlg),
      },
      memoText,
    };
  } catch {
    // If parsing fails, treat the whole memo as user text to avoid accidental data loss.
    return { meta: {}, memoText: raw };
  }
}

export function formatOrcaOrderItemMemo(meta: OrcaOrderItemMeta, memoText: string): string {
  const body = memoText ?? '';
  if (isEmptyMeta(meta)) return body;
  const metaLine = `${META_PREFIX}${JSON.stringify({ genericFlg: meta.genericFlg })}`;
  if (!body.trim()) return metaLine;
  return `${metaLine}\n${body}`;
}

export function updateOrcaOrderItemMeta(memo: string | undefined, patch: Partial<OrcaOrderItemMeta>): string {
  const { meta, memoText } = parseOrcaOrderItemMemo(memo);
  const next: OrcaOrderItemMeta = { ...meta, ...patch };
  if (!next.genericFlg) {
    delete next.genericFlg;
  }
  return formatOrcaOrderItemMemo(next, memoText);
}

