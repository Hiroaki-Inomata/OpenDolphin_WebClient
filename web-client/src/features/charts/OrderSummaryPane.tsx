import { useMemo } from 'react';

import type { OrderBundle } from './orderBundleApi';
import {
  ORDER_GROUP_REGISTRY,
  resolveOrderEntity,
  resolveOrderEntityLabel,
  resolveOrderGroupKeyByEntity,
  type OrderEntity,
  type OrderGroupKey,
} from './orderCategoryRegistry';
import { sortBundlesByLatestRule, type RightUtilityTool } from './RightUtilityDrawer';

type OrderSummaryPaneProps = {
  orderBundles?: OrderBundle[];
  prescriptionBundles?: OrderBundle[];
  orderBundlesLoading?: boolean;
  orderBundlesError?: string;
  activeTool?: RightUtilityTool;
  onBundleSelect?: (payload: { group: OrderGroupKey; entity: OrderEntity; bundle: OrderBundle }) => void;
};

const normalizeBundleName = (bundle: OrderBundle) => {
  const value = bundle.bundleName?.trim() || bundle.className?.trim();
  return value || '名称未設定';
};

const normalizeBundleEntity = (bundle: OrderBundle, fallback: OrderEntity): OrderEntity => {
  const raw = bundle.entity?.trim() ?? '';
  const resolved = resolveOrderEntity(raw);
  if (resolved) return resolved;
  return fallback;
};

const buildBundleSummary = (bundle: OrderBundle) => {
  const labels = (bundle.items ?? [])
    .map((item) => item.name?.trim())
    .filter((name): name is string => Boolean(name))
    .slice(0, 3);
  if (labels.length === 0) return '項目なし';
  const suffix = (bundle.items?.length ?? 0) > labels.length ? ' …' : '';
  return `${labels.join(' / ')}${suffix}`;
};

export function OrderSummaryPane({
  orderBundles,
  prescriptionBundles,
  orderBundlesLoading = false,
  orderBundlesError,
  activeTool,
  onBundleSelect,
}: OrderSummaryPaneProps) {
  const groupedBundles = useMemo(() => {
    return ORDER_GROUP_REGISTRY.map((group) => {
      const sourceBundles =
        group.key === 'prescription' && prescriptionBundles
          ? prescriptionBundles
          : (orderBundles ?? []).filter((bundle) => {
          const raw = bundle.entity?.trim() ?? '';
          return resolveOrderGroupKeyByEntity(raw) === group.key;
          });
      const bundles = sortBundlesByLatestRule(sourceBundles);
      return { ...group, bundles };
    });
  }, [orderBundles, prescriptionBundles]);

  const hasAnyBundle = groupedBundles.some((group) => group.bundles.length > 0);

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
          <span className="soap-note__paper-meta">カテゴリ別の既存オーダーを表示</span>
        </div>
      </header>

      {orderBundlesLoading ? <p className="soap-note__paper-empty">オーダー情報を取得しています...</p> : null}
      {orderBundlesError ? <p className="soap-note__paper-empty">オーダー情報の取得に失敗しました: {orderBundlesError}</p> : null}
      {!orderBundlesLoading && !orderBundlesError && !hasAnyBundle ? (
        <p className="soap-note__paper-empty">当日のオーダーはありません。</p>
      ) : null}

      {!orderBundlesLoading && !orderBundlesError ? (
        <div className="soap-note__order-groups">
          {groupedBundles.map((group) => {
            const selected = activeTool === group.key;
            return (
              <section
                key={`summary-group-${group.key}`}
                className="soap-note__order-group"
                data-group={group.key}
                data-active={selected ? 'true' : 'false'}
              >
                <header className="soap-note__order-group-header">
                  <strong>{group.label}</strong>
                  <span className="soap-note__order-group-meta">{group.bundles.length}件</span>
                </header>
                {group.bundles.length === 0 ? (
                  <p className="soap-note__order-group-submeta">該当オーダーなし</p>
                ) : (
                  <ul className="soap-note__order-list">
                    {group.bundles.map((bundle, index) => {
                      const entity = normalizeBundleEntity(bundle, group.defaultEntity);
                      const name = normalizeBundleName(bundle);
                      return (
                        <li
                          key={`summary-bundle-${group.key}-${bundle.documentId ?? 'doc'}-${bundle.moduleId ?? 'mod'}-${index}`}
                          className="soap-note__order-item"
                        >
                          <button
                            type="button"
                            className="order-dock__search-result"
                            onClick={() => onBundleSelect?.({ group: group.key, entity, bundle })}
                            aria-label={`${name}を編集`}
                            title={`${name} / ${resolveOrderEntityLabel(entity)}`}
                          >
                            <strong>{name}</strong>
                            <span>{resolveOrderEntityLabel(entity)}</span>
                            <span>{buildBundleSummary(bundle)}</span>
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
