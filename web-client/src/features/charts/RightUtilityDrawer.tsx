import { cloneElement, isValidElement, useEffect, useMemo, type ReactElement, type ReactNode } from 'react';

import {
  OrderBundleEditPanel,
  type OrderBundleEditPanelMeta,
  type OrderBundleEditPanelRequest,
  type OrderBundleEditingContext,
} from './OrderBundleEditPanel';
import type { OrderBundle } from './orderBundleApi';
import {
  ORDER_GROUP_REGISTRY,
  isOrderEntity,
  resolveOrderEntityEditorMeta,
  resolveOrderEntityLabel,
  resolveOrderGroupKeyByEntity,
  type OrderEntity,
  type OrderGroupKey,
} from './orderCategoryRegistry';

export type RightUtilityTool = OrderGroupKey | 'document';

type RightUtilityDrawerProps = {
  open: boolean;
  activeTool: RightUtilityTool;
  patientId?: string;
  meta: OrderBundleEditPanelMeta;
  orderBundles?: OrderBundle[];
  orderBundlesLoading?: boolean;
  orderBundlesError?: string;
  activeOrderEntity?: OrderEntity | null;
  activeOrderRequest?: OrderBundleEditPanelRequest | null;
  onOrderRequestConsumed?: (requestId: string) => void;
  onOrderEditingContextChange?: (context: OrderBundleEditingContext) => void;
  onOrderEntitySwitch?: (entity: OrderEntity) => void;
  onOrderBundleSelect?: (entity: OrderEntity, bundle: OrderBundle) => void;
  onOrderBundleCreate?: (entity: OrderEntity) => void;
  onClose: () => void;
  documentPanel?: ReactNode;
  documentHistoryCopyRequest?: { requestId: string; letterId: number } | null;
  onDocumentHistoryCopyConsumed?: (requestId: string) => void;
};

type BundleSortMeta = {
  bundle: OrderBundle;
  index: number;
  startedTimestamp: number | null;
  documentId: number | null;
};

const parseStartedTimestamp = (bundle: OrderBundle): number | null => {
  const raw = bundle.started?.trim();
  if (!raw) return null;
  const parsed = Date.parse(raw);
  if (Number.isNaN(parsed)) return null;
  return parsed;
};

const parseDocumentId = (bundle: OrderBundle): number | null => {
  return typeof bundle.documentId === 'number' && Number.isFinite(bundle.documentId) ? bundle.documentId : null;
};

const compareBundleSortMeta = (left: BundleSortMeta, right: BundleSortMeta) => {
  const leftHasStarted = left.startedTimestamp !== null;
  const rightHasStarted = right.startedTimestamp !== null;
  if (leftHasStarted && rightHasStarted) {
    if (left.startedTimestamp !== right.startedTimestamp) {
      return (right.startedTimestamp ?? 0) - (left.startedTimestamp ?? 0);
    }
    if (left.documentId !== right.documentId) {
      return (right.documentId ?? Number.NEGATIVE_INFINITY) - (left.documentId ?? Number.NEGATIVE_INFINITY);
    }
    return right.index - left.index;
  }
  if (leftHasStarted !== rightHasStarted) return leftHasStarted ? -1 : 1;
  if (left.documentId !== right.documentId) {
    return (right.documentId ?? Number.NEGATIVE_INFINITY) - (left.documentId ?? Number.NEGATIVE_INFINITY);
  }
  return right.index - left.index;
};

export const sortBundlesByLatestRule = (bundles: OrderBundle[]): OrderBundle[] => {
  const metas = bundles.map<BundleSortMeta>((bundle, index) => ({
    bundle,
    index,
    startedTimestamp: parseStartedTimestamp(bundle),
    documentId: parseDocumentId(bundle),
  }));
  metas.sort(compareBundleSortMeta);
  return metas.map((meta) => meta.bundle);
};

export const resolveLatestBundle = (bundles: OrderBundle[]): OrderBundle | null => {
  if (bundles.length === 0) return null;
  return sortBundlesByLatestRule(bundles)[0] ?? bundles[bundles.length - 1] ?? null;
};

const resolveGroupByTool = (tool: RightUtilityTool) => {
  if (tool === 'document') return null;
  return ORDER_GROUP_REGISTRY.find((spec) => spec.key === tool) ?? null;
};

const normalizeBundleName = (bundle: OrderBundle) => {
  const value = bundle.bundleName?.trim() || bundle.className?.trim();
  return value || '名称未設定';
};

const normalizeBundleEntity = (bundle: OrderBundle, fallback: OrderEntity): OrderEntity => {
  const raw = bundle.entity?.trim() ?? '';
  if (isOrderEntity(raw)) return raw;
  return fallback;
};

const belongsToSelectionEntity = (bundleEntity: OrderEntity, selectedEntity: OrderEntity) => {
  if (selectedEntity === 'testOrder') {
    return bundleEntity === 'testOrder' || bundleEntity === 'laboTest';
  }
  return bundleEntity === selectedEntity;
};

const buildBundleItemsSummary = (bundle: OrderBundle) => {
  const names = (bundle.items ?? [])
    .map((item) => item.name?.trim())
    .filter((name): name is string => Boolean(name))
    .slice(0, 3);
  if (names.length === 0) return '項目なし';
  const suffix = (bundle.items?.length ?? 0) > names.length ? ' …' : '';
  return `${names.join(' / ')}${suffix}`;
};

const isOrderTool = (tool: RightUtilityTool): tool is OrderGroupKey => tool !== 'document';

const cloneDocumentPanelNode = (
  node: ReactNode,
  historyCopyRequest?: { requestId: string; letterId: number } | null,
  onHistoryCopyConsumed?: (requestId: string) => void,
): ReactNode => {
  if (!isValidElement(node)) return node;
  const panel = node as ReactElement<Record<string, unknown>>;
  return cloneElement(panel, {
    historyCopyRequest,
    onHistoryCopyConsumed,
  });
};

export function RightUtilityDrawer({
  open,
  activeTool,
  patientId,
  meta,
  orderBundles,
  orderBundlesLoading = false,
  orderBundlesError,
  activeOrderEntity,
  activeOrderRequest,
  onOrderRequestConsumed,
  onOrderEditingContextChange,
  onOrderEntitySwitch,
  onOrderBundleSelect,
  onOrderBundleCreate,
  onClose,
  documentPanel,
  documentHistoryCopyRequest,
  onDocumentHistoryCopyConsumed,
}: RightUtilityDrawerProps) {
  useEffect(() => {
    if (!open) return undefined;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose, open]);

  const groupSpec = useMemo(() => resolveGroupByTool(activeTool), [activeTool]);
  const isOrderPanel = isOrderTool(activeTool) && Boolean(groupSpec);

  const groupBundles = useMemo(() => {
    if (!groupSpec) return [];
    return (orderBundles ?? []).filter((bundle) => {
      const raw = bundle.entity?.trim() ?? '';
      if (!isOrderEntity(raw)) return false;
      return resolveOrderGroupKeyByEntity(raw) === groupSpec.key;
    });
  }, [groupSpec, orderBundles]);

  const sortedGroupBundles = useMemo(() => sortBundlesByLatestRule(groupBundles), [groupBundles]);

  const selectedEntity = useMemo<OrderEntity | null>(() => {
    if (!groupSpec) return null;
    if (activeOrderEntity && groupSpec.entities.includes(activeOrderEntity)) return activeOrderEntity;
    return groupSpec.defaultEntity;
  }, [activeOrderEntity, groupSpec]);

  const bundlesBySelectedEntity = useMemo(() => {
    if (!selectedEntity || !groupSpec) return [];
    return sortedGroupBundles.filter((bundle) => {
      const entity = normalizeBundleEntity(bundle, groupSpec.defaultEntity);
      return belongsToSelectionEntity(entity, selectedEntity);
    });
  }, [groupSpec, selectedEntity, sortedGroupBundles]);

  const selectedEntityMeta = useMemo(
    () => (selectedEntity ? resolveOrderEntityEditorMeta(selectedEntity) : null),
    [selectedEntity],
  );

  const activeEditBundle = useMemo(() => {
    if (!activeOrderRequest) return null;
    if (activeOrderRequest.kind === 'edit' || activeOrderRequest.kind === 'copy') {
      return activeOrderRequest.bundle;
    }
    return null;
  }, [activeOrderRequest]);

  const documentPanelNode = useMemo(() => {
    if (!documentPanel) {
      return <p className="order-dock__empty">文書パネルが未接続です。</p>;
    }
    return cloneDocumentPanelNode(documentPanel, documentHistoryCopyRequest, onDocumentHistoryCopyConsumed);
  }, [documentHistoryCopyRequest, documentPanel, onDocumentHistoryCopyConsumed]);

  return (
    <aside
      className="soap-note__right-drawer"
      data-open={open ? 'true' : 'false'}
      data-tool={activeTool}
      aria-hidden={!open}
      aria-label="右ユーティリティドロワー"
    >
      <header className="soap-note__right-drawer-header">
        <strong>{activeTool === 'document' ? '文書' : groupSpec?.label ?? 'オーダー'}</strong>
        <button type="button" className="order-dock__bundle-action" onClick={onClose} aria-label="右ドロワーを閉じる">
          閉じる
        </button>
      </header>

      <div className="soap-note__right-drawer-content">
        <section
          className="soap-note__right-drawer-panel soap-note__right-drawer-panel--document"
          data-active={activeTool === 'document' ? 'true' : 'false'}
          aria-hidden={activeTool === 'document' ? 'false' : 'true'}
        >
          {documentPanelNode}
        </section>

        <section
          className="soap-note__right-drawer-panel soap-note__right-drawer-panel--order"
          data-active={isOrderPanel && groupSpec && selectedEntity && selectedEntityMeta ? 'true' : 'false'}
          aria-hidden={isOrderPanel && groupSpec && selectedEntity && selectedEntityMeta ? 'false' : 'true'}
        >
          {isOrderPanel && groupSpec && selectedEntity && selectedEntityMeta ? (
            <div className="soap-note__right-drawer-order-layout">
              <div className="soap-note__right-drawer-order-editor">
                {groupSpec.entities.length > 1 ? (
                  <div className="order-dock__subtype-tabs" role="tablist" aria-label={`${groupSpec.label}サブカテゴリ`}>
                    {groupSpec.entities.map((entity) => {
                      const isActive = selectedEntity === entity;
                      return (
                        <button
                          key={`drawer-sub-${entity}`}
                          type="button"
                          className="order-dock__subtype-tab"
                          data-active={isActive ? 'true' : 'false'}
                          aria-pressed={isActive}
                          onClick={() => onOrderEntitySwitch?.(entity)}
                        >
                          {resolveOrderEntityLabel(entity)}
                        </button>
                      );
                    })}
                  </div>
                ) : null}

                {!patientId ? (
                  <p className="order-dock__empty">患者IDが未選択のためオーダー編集を開始できません。</p>
                ) : (
                  <OrderBundleEditPanel
                    patientId={patientId}
                    entity={selectedEntity}
                    title={selectedEntityMeta.title}
                    bundleLabel={selectedEntityMeta.bundleLabel}
                    itemQuantityLabel={selectedEntityMeta.itemQuantityLabel}
                    meta={meta}
                    variant="embedded"
                    bundlesOverride={bundlesBySelectedEntity}
                    request={activeOrderRequest}
                    onRequestConsumed={onOrderRequestConsumed}
                    onEditingContextChange={onOrderEditingContextChange}
                    onClose={onClose}
                  />
                )}
              </div>

              <aside className="soap-note__right-drawer-order-list" aria-label={`${groupSpec.label}既存一覧`}>
                <div className="soap-note__right-drawer-order-list-header">
                  <strong>既存オーダー</strong>
                  <button
                    type="button"
                    className="order-dock__bundle-action"
                    onClick={() => onOrderBundleCreate?.(selectedEntity)}
                  >
                    新規
                  </button>
                </div>

                {orderBundlesLoading ? <p className="order-dock__empty">読み込み中...</p> : null}
                {orderBundlesError ? <p className="order-dock__empty">取得失敗: {orderBundlesError}</p> : null}
                {!orderBundlesLoading && !orderBundlesError && sortedGroupBundles.length === 0 ? (
                  <p className="order-dock__empty">このカテゴリの既存オーダーはありません。</p>
                ) : null}
                {!orderBundlesLoading && !orderBundlesError ? (
                  <div className="soap-note__right-drawer-order-list-body order-dock__bundle-list" role="list">
                    {sortedGroupBundles.map((bundle, index) => {
                      const entity = normalizeBundleEntity(bundle, groupSpec.defaultEntity);
                      const isActive = Boolean(
                        activeEditBundle &&
                          activeEditBundle.documentId === bundle.documentId &&
                          activeEditBundle.moduleId === bundle.moduleId,
                      );
                      return (
                        <button
                          key={`drawer-bundle-${bundle.documentId ?? 'doc'}-${bundle.moduleId ?? 'mod'}-${index}`}
                          type="button"
                          role="listitem"
                          className="order-dock__search-result"
                          data-active={isActive ? 'true' : 'false'}
                          onClick={() => onOrderBundleSelect?.(entity, bundle)}
                          title={normalizeBundleName(bundle)}
                        >
                          <strong>{normalizeBundleName(bundle)}</strong>
                          <span>{resolveOrderEntityLabel(entity)}</span>
                          <span>{buildBundleItemsSummary(bundle)}</span>
                        </button>
                      );
                    })}
                  </div>
                ) : null}
              </aside>
            </div>
          ) : null}
        </section>
      </div>
    </aside>
  );
}
