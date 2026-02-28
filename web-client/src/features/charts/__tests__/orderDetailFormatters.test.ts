import { describe, expect, it } from 'vitest';

const ORDER_DETAIL_FORMATTERS_MODULE_PATH = '../orderDetailFormatters';

const loadOrderDetailFormattersModule = async (): Promise<Record<string, unknown>> => {
  try {
    return (await import(/* @vite-ignore */ ORDER_DETAIL_FORMATTERS_MODULE_PATH)) as Record<string, unknown>;
  } catch (error) {
    throw new Error(
      `orderDetailFormatters.ts の実装が未完了です。` +
        ` normalizeInline / stripLeadingCode / formatQuantityWithUnit / formatDateTime / memo整形関数を追加してください。 原因: ${String(error)}`,
    );
  }
};

const resolveRequiredFunction = <T extends (...args: any[]) => unknown>(
  module: Record<string, unknown>,
  names: string[],
  requirementLabel: string,
): T => {
  for (const name of names) {
    const candidate = module[name];
    if (typeof candidate === 'function') return candidate as T;
  }
  throw new Error(`${requirementLabel} を満たす公開関数が見つかりません。候補: ${names.join(', ')}`);
};

describe('orderDetailFormatters requirements', () => {
  it('共通フォーマッタを公開する', async () => {
    const module = await loadOrderDetailFormattersModule();

    expect(typeof module.normalizeInline).toBe('function');
    expect(typeof module.stripLeadingCode).toBe('function');
    expect(typeof module.formatQuantityWithUnit).toBe('function');
    expect(typeof module.formatDateTime).toBe('function');
  });

  it('normalizeInline / stripLeadingCode は表示崩れを抑止する', async () => {
    const module = await loadOrderDetailFormattersModule();
    const normalizeInline = resolveRequiredFunction<(value?: string | null) => string>(
      module,
      ['normalizeInline'],
      '空白正規化',
    );
    const stripLeadingCode = resolveRequiredFunction<(value?: string | null) => string>(
      module,
      ['stripLeadingCode'],
      '先頭コード除去',
    );

    expect(normalizeInline('  服薬 \n 指示\tメモ  ')).toBe('服薬 指示 メモ');
    expect(stripLeadingCode('620000001 メトホルミン')).toBe('メトホルミン');
    expect(stripLeadingCode('abc 薬剤名')).toBe('abc 薬剤名');
  });

  it('formatQuantityWithUnit / formatDateTime は欠損時フォールバックを統一する', async () => {
    const module = await loadOrderDetailFormattersModule();
    const formatQuantityWithUnit = resolveRequiredFunction<(q?: string | null, u?: string | null) => string>(
      module,
      ['formatQuantityWithUnit'],
      '数量+単位フォーマット',
    );
    const formatDateTime = resolveRequiredFunction<(value?: string | null) => string>(
      module,
      ['formatDateTime'],
      '日時フォーマット',
    );

    expect(formatQuantityWithUnit(' 1 ', ' 錠 ')).toBe('1錠');
    expect(formatQuantityWithUnit('', '')).toBe('');
    expect(formatDateTime('')).toBe('日時不明');
    expect(formatDateTime('  invalid-date-value  ')).toBe('invalid-date-value');
  });

  it('memo整形はメタ行と空行を除去し、表示用に1行化する', async () => {
    const module = await loadOrderDetailFormattersModule();
    const formatMemo = resolveRequiredFunction<(value?: string | null) => string>(
      module,
      ['formatMemoForDisplay', 'normalizeMemoForDisplay', 'toSafeMemoText'],
      'memo整形',
    );

    const source = '__orca_meta__:{"genericFlg":"no"}\n レセコメントA \n\n  レセコメントB  ';
    expect(formatMemo(source)).toBe('レセコメントA / レセコメントB');
  });
});
