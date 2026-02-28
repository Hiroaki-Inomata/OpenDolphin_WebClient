import { cloneElement, isValidElement, useEffect, useMemo, type ReactElement, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

import {
  OrderBundleEditPanel,
  type OrderBundleEditPanelMeta,
  type OrderBundleEditPanelRequest,
  type OrderBundleEditingContext,
} from './OrderBundleEditPanel';
import { PrescriptionOrderEditorPanel } from './PrescriptionOrderEditorPanel';
import type { OrderBundle } from './orderBundleApi';
import {
  ORDER_GROUP_REGISTRY,
  resolveOrderEntity,
  resolveOrderEntityEditorMeta,
  resolveOrderEntityLabel,
  resolveOrderGroupKeyByEntity,
  type OrderEntity,
  type OrderGroupKey,
} from './orderCategoryRegistry';
import {
  buildOrderDetailDisplayRowsForGroup,
  resolveLatestBundle,
  sortBundlesByLatestRule,
} from './orderDetailDisplayViewModel';

export type RightUtilityTool = OrderGroupKey | 'document';

type RightUtilityDrawerProps = {
  open: boolean;
  activeTool: RightUtilityTool;
  patientId?: string;
  meta: OrderBundleEditPanelMeta;
  orderBundles?: OrderBundle[];
  orderBundlesLoading?: boolean;
  orderBundlesError?: string;
  prescriptionBundles?: OrderBundle[];
  prescriptionBundlesLoading?: boolean;
  prescriptionBundlesError?: string;
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

const resolveGroupByTool = (tool: RightUtilityTool) => {
  if (tool === 'document') return null;
  return ORDER_GROUP_REGISTRY.find((spec) => spec.key === tool) ?? null;
};

const normalizeBundleEntity = (bundle: OrderBundle, fallback: OrderEntity): OrderEntity => {
  const raw = bundle.entity?.trim() ?? '';
  const resolved = resolveOrderEntity(raw);
  if (resolved) return resolved;
  return fallback;
};

const belongsToSelectionEntity = (bundleEntity: OrderEntity, selectedEntity: OrderEntity) => {
  if (selectedEntity === 'testOrder') {
    return bundleEntity === 'testOrder' || bundleEntity === 'laboTest';
  }
  return bundleEntity === selectedEntity;
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

export { resolveLatestBundle, sortBundlesByLatestRule };

export function RightUtilityDrawer({
  open,
  activeTool,
  patientId,
  meta,
  orderBundles,
  orderBundlesLoading = false,
  orderBundlesError,
  prescriptionBundles,
  prescriptionBundlesLoading = false,
  prescriptionBundlesError,
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
    if (groupSpec.key === 'prescription' && prescriptionBundles) {
      return prescriptionBundles;
    }
    return (orderBundles ?? []).filter((bundle) => {
      const raw = bundle.entity?.trim() ?? '';
      const groupKey = resolveOrderGroupKeyByEntity(raw);
      return groupKey === groupSpec.key;
    });
  }, [groupSpec, orderBundles, prescriptionBundles]);

  const sortedGroupBundles = useMemo(() => sortBundlesByLatestRule(groupBundles), [groupBundles]);

  const existingOrderRows = useMemo(() => {
    if (!groupSpec) return [];
    return buildOrderDetailDisplayRowsForGroup({
      group: groupSpec.key,
      bundles: sortedGroupBundles,
      defaultEntity: groupSpec.defaultEntity,
    });
  }, [groupSpec, sortedGroupBundles]);

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

  const isDocumentPanelActive = activeTool === 'document';
  const activeOrderPanelContext = useMemo(() => {
    if (!isOrderPanel || !groupSpec || !selectedEntity || !selectedEntityMeta) return null;
    return {
      groupSpec,
      selectedEntity,
      selectedEntityMeta,
    };
  }, [groupSpec, isOrderPanel, selectedEntity, selectedEntityMeta]);
  const isDocumentPanelVisible = open && isDocumentPanelActive;
  const isOrderPanelVisible = open && Boolean(activeOrderPanelContext);
  const isPrescriptionPanel =
    Boolean(activeOrderPanelContext) && activeOrderPanelContext?.groupSpec.key === 'prescription';
  const resolvedPanelBundles =
    isPrescriptionPanel && prescriptionBundles ? prescriptionBundles : bundlesBySelectedEntity;
  const resolvedPanelLoading = isPrescriptionPanel ? prescriptionBundlesLoading : orderBundlesLoading;
  const resolvedPanelError = isPrescriptionPanel ? prescriptionBundlesError ?? orderBundlesError : orderBundlesError;

  const drawerNode = (
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
        {isDocumentPanelActive ? (
          <section
            key="drawer-document-panel"
            className="soap-note__right-drawer-panel soap-note__right-drawer-panel--document"
            data-active={isDocumentPanelVisible ? 'true' : 'false'}
            aria-hidden={isDocumentPanelVisible ? 'false' : 'true'}
          >
            <div className="soap-note__right-drawer-switch">{documentPanelNode}</div>
          </section>
        ) : null}

        {activeOrderPanelContext ? (
          <section
            key={`drawer-order-panel-${activeTool}-${activeOrderPanelContext.selectedEntity}`}
            className="soap-note__right-drawer-panel soap-note__right-drawer-panel--order"
            data-active={isOrderPanelVisible ? 'true' : 'false'}
            aria-hidden={isOrderPanelVisible ? 'false' : 'true'}
          >
            <div className="soap-note__right-drawer-switch soap-note__right-drawer-order-layout">
              <div className="soap-note__right-drawer-order-editor">
                {activeOrderPanelContext.groupSpec.entities.length > 1 ? (
                  <div
                    className="order-dock__subtype-tabs"
                    role="tablist"
                    aria-label={`${activeOrderPanelContext.groupSpec.label}サブカテゴリ`}
                  >
                    {activeOrderPanelContext.groupSpec.entities.map((entity) => {
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
                ) : isPrescriptionPanel ? (
                  <>
                    {resolvedPanelLoading ? <p className="order-dock__empty">処方情報を取得しています...</p> : null}
                    {resolvedPanelError ? <p className="order-dock__empty">処方情報の取得に失敗しました: {resolvedPanelError}</p> : null}
                    <PrescriptionOrderEditorPanel
                      patientId={patientId}
                      meta={meta}
                      variant="embedded"
                      bundlesOverride={resolvedPanelBundles}
                      request={activeOrderRequest}
                      onRequestConsumed={onOrderRequestConsumed}
                      onEditingContextChange={onOrderEditingContextChange}
                      onClose={onClose}
                      active={open && activeTool === 'prescription'}
                    />
                  </>
                ) : (
                  <OrderBundleEditPanel
                    patientId={patientId}
                    entity={activeOrderPanelContext.selectedEntity}
                    title={activeOrderPanelContext.selectedEntityMeta.title}
                    bundleLabel={activeOrderPanelContext.selectedEntityMeta.bundleLabel}
                    itemQuantityLabel={activeOrderPanelContext.selectedEntityMeta.itemQuantityLabel}
                    meta={meta}
                    variant="embedded"
                    bundlesOverride={resolvedPanelBundles}
                    request={activeOrderRequest}
                    onRequestConsumed={onOrderRequestConsumed}
                    onEditingContextChange={onOrderEditingContextChange}
                    onClose={onClose}
                  />
                )}
              </div>

              <section
                className="soap-note__right-drawer-order-preview"
                aria-label={`${activeOrderPanelContext.groupSpec.label}既存一覧`}
              >
                <div className="soap-note__right-drawer-order-preview-header">
                  <strong>既存オーダー</strong>
                  <button
                    type="button"
                    className="order-dock__bundle-action"
                    onClick={() => onOrderBundleCreate?.(activeOrderPanelContext.selectedEntity)}
                  >
                    新規
                  </button>
                </div>

                {resolvedPanelLoading ? <p className="order-dock__empty">読み込み中...</p> : null}
                {resolvedPanelError ? <p className="order-dock__empty">取得失敗: {resolvedPanelError}</p> : null}
                {!resolvedPanelLoading && !resolvedPanelError && existingOrderRows.length === 0 ? (
                  <p className="order-dock__empty">このカテゴリの既存オーダーはありません。</p>
                ) : null}
                {!resolvedPanelLoading && !resolvedPanelError ? (
                  <div className="soap-note__right-drawer-order-preview-list order-dock__bundle-list" role="list">
                    {existingOrderRows.map((row) => {
                      const isActive = Boolean(
                        activeEditBundle &&
                          activeEditBundle.documentId === row.bundle.documentId &&
                          activeEditBundle.moduleId === row.bundle.moduleId,
                      );
                      const previewInstanceKey = `preview-${row.id}`;
                      const previewRequest: OrderBundleEditPanelRequest = {
                        requestId: previewInstanceKey,
                        kind: 'edit',
                        bundle: row.bundle,
                      };
                      const previewMeta = resolveOrderEntityEditorMeta(row.entity);
                      if (!previewMeta) {
                        return null;
                      }
                      const previewPanel =
                        row.entity === 'medOrder' ? (
                          <PrescriptionOrderEditorPanel
                            patientId={patientId}
                            meta={meta}
                            variant="embedded"
                            bundlesOverride={[row.bundle]}
                            readOnlyPreview
                            instanceKey={previewInstanceKey}
                            active={open && activeTool === 'prescription'}
                          />
                        ) : (
                          <OrderBundleEditPanel
                            patientId={patientId}
                            entity={row.entity}
                            title={previewMeta.title}
                            bundleLabel={previewMeta.bundleLabel}
                            itemQuantityLabel={previewMeta.itemQuantityLabel}
                            meta={meta}
                            variant="embedded"
                            bundlesOverride={[row.bundle]}
                            request={previewRequest}
                            readOnlyPreview
                            instanceKey={previewInstanceKey}
                          />
                        );
                      return (
                        <article
                          key={`drawer-bundle-${row.id}`}
                          role="listitem"
                          className="soap-note__right-drawer-order-preview-item"
                          data-active={isActive ? 'true' : 'false'}
                        >
                          <header className="soap-note__right-drawer-order-preview-item-header">
                            <div>
                              <p className="soap-note__summary-meta">{row.operatorLine}</p>
                              <strong>{row.bundleLabel}</strong>
                            </div>
                            <button
                              type="button"
                              className="order-dock__bundle-action"
                              onClick={() => onOrderBundleSelect?.(row.entity, row.bundle)}
                              aria-label={`${row.bundleLabel}を編集`}
                            >
                              このセットを編集
                            </button>
                          </header>
                          <div className="soap-note__right-drawer-order-preview-item-body">{previewPanel}</div>
                        </article>
                      );
                    })}
                  </div>
                ) : null}
              </section>
            </div>
          </section>
        ) : null}
      </div>
    </aside>
  );

  if (typeof document === 'undefined') return drawerNode;
  return createPortal(drawerNode, document.body);
}
