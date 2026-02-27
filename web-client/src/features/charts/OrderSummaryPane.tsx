import { useMemo } from 'react';

import type { OrderBundle, OrderBundleItem } from './orderBundleApi';
import {
  ORDER_GROUP_REGISTRY,
  resolveOrderDockCategoryLabel,
  resolveOrderEntity,
  resolveOrderGroupKeyByEntity,
  type OrderEntity,
  type OrderGroupKey,
} from './orderCategoryRegistry';
import { parseOrcaOrderItemMemo } from './orcaOrderItemMeta';
import { sortBundlesByLatestRule, type RightUtilityTool } from './RightUtilityDrawer';

type OrderSummaryPaneProps = {
  orderBundles?: OrderBundle[];
  prescriptionBundles?: OrderBundle[];
  orderBundlesLoading?: boolean;
  orderBundlesError?: string;
  activeTool?: RightUtilityTool;
  onBundleSelect?: (payload: { group: OrderGroupKey; entity: OrderEntity; bundle: OrderBundle }) => void;
  onDocumentSelect?: () => void;
};

type SummaryCategoryKey = OrderGroupKey | 'document';

type SummaryCategorySpec = {
  key: SummaryCategoryKey;
  label: string;
  groupKey?: OrderGroupKey;
};

type SummaryCategoryBundle = SummaryCategorySpec & {
  defaultEntity: OrderEntity | null;
  bundles: OrderBundle[];
};

const SUMMARY_CATEGORIES: SummaryCategorySpec[] = [
  { key: 'prescription', label: '処方', groupKey: 'prescription' },
  { key: 'injection', label: '点滴・注射', groupKey: 'injection' },
  { key: 'treatment', label: '処置', groupKey: 'treatment' },
  { key: 'test', label: '検査', groupKey: 'test' },
  { key: 'charge', label: '算定', groupKey: 'charge' },
  { key: 'document', label: '文書' },
];

const normalizeBundleEntity = (bundle: OrderBundle, fallback: OrderEntity): OrderEntity => {
  const raw = bundle.entity?.trim() ?? '';
  const resolved = resolveOrderEntity(raw);
  if (resolved) return resolved;
  return fallback;
};

const normalizeInline = (value?: string | null) => (value ?? '').replace(/\s+/g, ' ').trim();

const stripLeadingCode = (value?: string | null) => {
  const normalized = normalizeInline(value);
  if (!normalized) return '';
  const tokens = normalized.split(' ');
  if (tokens.length >= 2 && /^[A-Za-z0-9]{4,}$/.test(tokens[0] ?? '')) {
    return tokens.slice(1).join(' ');
  }
  return normalized;
};

const formatQuantityWithUnit = (quantity?: string, unit?: string) => {
  const q = normalizeInline(quantity);
  const u = normalizeInline(unit);
  if (!q && !u) return '';
  return `${q}${u}`;
};

const extractIngredientAmount = (item: OrderBundleItem) => {
  const source = item as unknown as Record<string, unknown>;
  const quantity = pickFirstString(source, [
    'ingredientQuantity',
    'componentQuantity',
    'contentQuantity',
    'activeIngredientQuantity',
  ]);
  const unit = pickFirstString(source, ['ingredientUnit', 'componentUnit', 'contentUnit', 'activeIngredientUnit']);
  return formatQuantityWithUnit(quantity, unit);
};

const formatDateTime = (raw?: string | null) => {
  const source = normalizeInline(raw);
  if (!source) return '日時不明';
  const parsed = Date.parse(source);
  if (Number.isNaN(parsed)) return source;
  const hasTime = /[T\s]\d{1,2}:\d{2}/.test(source);
  const date = new Intl.DateTimeFormat('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(parsed);
  if (!hasTime) return date;
  const time = new Intl.DateTimeFormat('ja-JP', { hour: '2-digit', minute: '2-digit', hour12: false }).format(parsed);
  return `${date} ${time}`;
};

const pickFirstString = (source: Record<string, unknown>, keys: string[]) => {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === 'string' && value.trim()) return value.trim();
  }
  return '';
};

const resolveOperatorLine = (bundle?: OrderBundle | null) => {
  if (!bundle) return '入力者不明 医師 日時不明';
  const source = bundle as Record<string, unknown>;
  const author = pickFirstString(source, [
    'enteredByName',
    'enteredName',
    'authorName',
    'inputByName',
    'createdByName',
    'userName',
  ]);
  const role = pickFirstString(source, ['enteredByRole', 'enteredRole', 'authorRole', 'inputByRole', 'createdByRole', 'role']);
  const datetimeRaw = pickFirstString(source, ['enteredAt', 'inputAt', 'authoredAt', 'createdAt', 'updatedAt', 'started']);
  return `${author || '入力者不明'} ${role || '医師'} ${formatDateTime(datetimeRaw)}`;
};

const toSafeMemoText = (memoText: string) => {
  return memoText
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('__'))
    .join(' / ');
};

const resolvePrescriptionDrugDetails = (bundle: OrderBundle) => {
  const usage = normalizeInline(bundle.admin);
  const count = normalizeInline(bundle.bundleNumber);
  return (bundle.items ?? [])
    .map((item) => {
      const parsed = parseOrcaOrderItemMemo(item.memo);
      const details: string[] = [];
      const quantity = formatQuantityWithUnit(item.quantity, item.unit);
      const ingredientAmount = extractIngredientAmount(item);
      if (quantity || ingredientAmount) {
        details.push(`薬剤量: ${quantity || '不明'}${ingredientAmount ? ` / 成分量: ${ingredientAmount}` : ''}`);
      }
      if (usage || count) {
        details.push(`用法: ${usage || '未設定'}${count ? ` / 回数: ${count}` : ''}`);
      }
      const userComment = normalizeInline(parsed.meta.userComment);
      if (userComment) details.push(`薬剤コメント: ${userComment}`);
      const memo = toSafeMemoText(parsed.memoText);
      if (memo) details.push(`レセプトコメント: ${memo}`);
      return {
        genericNote:
          parsed.meta.genericFlg === 'no'
            ? '【後発変更不可】'
            : parsed.meta.genericFlg === 'yes'
              ? '【後発変更可】'
              : '',
        name: stripLeadingCode(item.name) || '薬剤名未設定',
        details,
      };
    })
    .filter((item) => item.name);
};

const resolveListLines = (bundle: OrderBundle, includeQuantity: boolean) => {
  const items = (bundle.items ?? []).filter((item) => normalizeInline(item.name));
  if (items.length === 0) return ['項目情報なし'];
  return items.map((item) => {
    const name = stripLeadingCode(item.name) || '項目名未設定';
    if (!includeQuantity) return name;
    const qty = formatQuantityWithUnit(item.quantity, item.unit);
    return qty ? `${name} ${qty}` : name;
  });
};

const resolveBundleLabelForAria = (bundle: OrderBundle, fallback: string) => {
  const value = normalizeInline(bundle.bundleName) || normalizeInline(bundle.className);
  return value || fallback;
};

export function OrderSummaryPane({
  orderBundles,
  prescriptionBundles,
  orderBundlesLoading = false,
  orderBundlesError,
  activeTool,
  onBundleSelect,
  onDocumentSelect,
}: OrderSummaryPaneProps) {
  const groupedBundles = useMemo<SummaryCategoryBundle[]>(() => {
    const groupMap = new Map(ORDER_GROUP_REGISTRY.map((group) => [group.key, group]));
    return SUMMARY_CATEGORIES.map((category) => {
      if (category.key === 'document') {
        return {
          ...category,
          defaultEntity: null,
          bundles: [],
        };
      }
      const group = groupMap.get(category.groupKey ?? category.key);
      if (!group) {
        return {
          ...category,
          defaultEntity: null,
          bundles: [],
        };
      }
      const sourceBundles =
        group.key === 'prescription' && prescriptionBundles
          ? prescriptionBundles
          : (orderBundles ?? []).filter((bundle) => {
              const raw = bundle.entity?.trim() ?? '';
              return resolveOrderGroupKeyByEntity(raw) === group.key;
            });
      return {
        ...category,
        defaultEntity: group.defaultEntity,
        bundles: sortBundlesByLatestRule(sourceBundles),
      };
    });
  }, [orderBundles, prescriptionBundles]);

  const hasAnyOrderBundle = groupedBundles.some((group) => group.key !== 'document' && group.bundles.length > 0);

  const renderCardBody = (category: SummaryCategoryBundle, bundle: OrderBundle) => {
    if (category.key === 'prescription') {
      const drugs = resolvePrescriptionDrugDetails(bundle);
      const rp = normalizeInline(bundle.bundleNumber);
      return (
        <div className="soap-note__summary-body">
          <p className="soap-note__summary-detail soap-note__summary-detail--heading">{rp ? `RP${rp}` : 'RP'}</p>
          {drugs.length === 0 ? (
            <p className="soap-note__summary-detail">薬剤情報なし</p>
          ) : (
            <ul className="soap-note__summary-list">
              {drugs.map((drug, index) => (
                <li key={`summary-drug-${index}`} className="soap-note__summary-list-item">
                  {drug.genericNote ? <span className="soap-note__summary-item-sub">{drug.genericNote}</span> : null}
                  <span className="soap-note__summary-item-name">{drug.name}</span>
                  {drug.details.map((detail, detailIndex) => (
                    <span key={`summary-drug-detail-${index}-${detailIndex}`} className="soap-note__summary-item-sub">
                      {detail}
                    </span>
                  ))}
                </li>
              ))}
            </ul>
          )}
        </div>
      );
    }

    if (category.key === 'injection') {
      const lines = (bundle.items ?? []).filter((item) => normalizeInline(item.name)).map((item) => {
        const name = stripLeadingCode(item.name) || '薬剤名未設定';
        const qty = formatQuantityWithUnit(item.quantity, item.unit);
        return `${name}${qty ? ` 薬剤量: ${qty}` : ''}`;
      });
      const admin = normalizeInline(bundle.admin);
      const adminMemo = normalizeInline(bundle.adminMemo);
      const adminLine = [admin ? admin : null, adminMemo ? adminMemo : null].filter(Boolean).join(' ');
      return (
        <div className="soap-note__summary-body">
          <p className="soap-note__summary-detail soap-note__summary-detail--heading">点滴・注射</p>
          <ul className="soap-note__summary-list">
            {(lines.length > 0 ? lines : ['薬剤情報なし']).map((line, index) => (
              <li key={`summary-injection-${index}`} className="soap-note__summary-list-item">
                <span className="soap-note__summary-item-name">{line}</span>
              </li>
            ))}
          </ul>
          <p className="soap-note__summary-detail">{adminLine || '投与情報なし'}</p>
        </div>
      );
    }

    if (category.key === 'treatment') {
      const lines = resolveListLines(bundle, true);
      return (
        <div className="soap-note__summary-body">
          <p className="soap-note__summary-detail soap-note__summary-detail--heading">処置</p>
          <ul className="soap-note__summary-list">
            {lines.map((line, index) => (
              <li key={`summary-treatment-${index}`} className="soap-note__summary-list-item">
                <span className="soap-note__summary-item-name">{line}</span>
              </li>
            ))}
          </ul>
        </div>
      );
    }

    if (category.key === 'test') {
      const lines = resolveListLines(bundle, false);
      return (
        <div className="soap-note__summary-body">
          <ul className="soap-note__summary-list">
            {lines.map((line, index) => (
              <li key={`summary-test-${index}`} className="soap-note__summary-list-item">
                <span className="soap-note__summary-item-name">{line}</span>
              </li>
            ))}
          </ul>
        </div>
      );
    }

    const lines = (bundle.items ?? []).filter((item) => normalizeInline(item.name));
    const bundleMemo = normalizeInline(bundle.memo);
    return (
      <div className="soap-note__summary-body">
        <ul className="soap-note__summary-list">
          {lines.length === 0 ? (
            <li className="soap-note__summary-list-item">
              <span className="soap-note__summary-item-name">項目情報なし</span>
            </li>
          ) : (
            lines.map((item, index) => {
              const qty = formatQuantityWithUnit(item.quantity, item.unit);
              const itemMemo = normalizeInline(item.memo);
              return (
                <li key={`summary-charge-${index}`} className="soap-note__summary-list-item">
                  <span className="soap-note__summary-item-name">
                    {stripLeadingCode(item.name) || '項目名未設定'}
                    {qty ? ` ${qty}` : ''}
                  </span>
                  {itemMemo ? <span className="soap-note__summary-item-sub">メモ:{itemMemo}</span> : null}
                </li>
              );
            })
          )}
        </ul>
        {bundleMemo ? <p className="soap-note__summary-detail">メモ:{bundleMemo}</p> : null}
      </div>
    );
  };

  return (
    <aside
      className="soap-note__paper soap-note__center-panel-only"
      aria-label="オーダー概要"
      data-loading={orderBundlesLoading ? '1' : '0'}
      data-error={orderBundlesError ? '1' : '0'}
    >
      <header className="soap-note__paper-header">
        <div>
          <strong>オーダー概要</strong>
          <span className="soap-note__paper-meta">カテゴリ別詳細カード</span>
        </div>
      </header>

      {orderBundlesLoading ? <p className="soap-note__paper-empty">オーダー情報を取得しています...</p> : null}
      {orderBundlesError ? <p className="soap-note__paper-empty">オーダー情報の取得に失敗しました: {orderBundlesError}</p> : null}
      {!orderBundlesLoading && !orderBundlesError && !hasAnyOrderBundle ? (
        <p className="soap-note__paper-empty">当日のオーダーはありません。</p>
      ) : null}

      {!orderBundlesLoading && !orderBundlesError ? (
        <div className="soap-note__order-groups">
          {groupedBundles.map((category) => {
            const selected = activeTool === category.key;
            return (
              <section
                key={`summary-group-${category.key}`}
                className="soap-note__order-group"
                data-group={category.key}
                data-active={selected ? 'true' : 'false'}
              >
                <header className="soap-note__order-group-header">
                  <strong>{category.label}</strong>
                  <span className="soap-note__order-group-meta">
                    {category.key === 'document' ? '編集' : `${category.bundles.length}件`}
                  </span>
                </header>

                {category.key === 'document' ? (
                  <button
                    type="button"
                    className="order-dock__search-result soap-note__summary-card"
                    onClick={() => onDocumentSelect?.()}
                    aria-label="文書を編集"
                    title="文書を編集"
                  >
                    <p className="soap-note__summary-meta">{resolveOperatorLine(null)}</p>
                    <div className="soap-note__summary-body">
                      <p className="soap-note__summary-detail">文書名: 文書情報なし</p>
                      <p className="soap-note__summary-detail">本文情報なし</p>
                    </div>
                  </button>
                ) : category.bundles.length === 0 ? (
                  <article className="soap-note__summary-card soap-note__summary-card--empty">
                    <p className="soap-note__summary-meta">{resolveOperatorLine(null)}</p>
                    <p className="soap-note__order-group-submeta">該当オーダーなし</p>
                  </article>
                ) : (
                  <ul className="soap-note__order-list">
                    {category.bundles.map((bundle, index) => {
                      const fallbackEntity = category.defaultEntity;
                      if (!fallbackEntity) return null;
                      const groupKey = (category.groupKey ?? category.key) as OrderGroupKey;
                      const entity = normalizeBundleEntity(bundle, fallbackEntity);
                      const label = resolveBundleLabelForAria(
                        bundle,
                        resolveOrderDockCategoryLabel(groupKey) ?? category.label,
                      );
                      return (
                        <li
                          key={`summary-bundle-${category.key}-${bundle.documentId ?? 'doc'}-${bundle.moduleId ?? 'mod'}-${index}`}
                          className="soap-note__order-item"
                        >
                          <button
                            type="button"
                            className="order-dock__search-result soap-note__summary-card"
                            onClick={() => onBundleSelect?.({ group: groupKey, entity, bundle })}
                            aria-label={`${label}を編集`}
                            title={`${category.label}を編集`}
                          >
                            <p className="soap-note__summary-meta">{resolveOperatorLine(bundle)}</p>
                            {renderCardBody(category, bundle)}
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      ) : null}
    </aside>
  );
}
