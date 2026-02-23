import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useOptionalSession } from '../../AppRouter';
import { FocusTrapDialog } from '../../components/modals/FocusTrapDialog';
import { mutateOrderBundles, type OrderBundle, type OrderBundleItem } from './orderBundleApi';
import { OrderBundleEditPanel, type OrderBundleEditPanelMeta, type OrderBundleEditPanelRequest } from './OrderBundleEditPanel';
import type { OrderRecommendationCandidate } from './orderRecommendationApi';
import { OrderRecommendationModal } from './OrderRecommendationModal';
import type { RpHistoryEntry } from './karteExtrasApi';
import { getOrcaClaimSendEntry, type OrcaMedicalWarningUi } from './orcaClaimSendCache';
import { parseOrcaOrderItemMemo } from './orcaOrderItemMeta';

type PastOrderEntity =
  | 'medOrder'
  | 'generalOrder'
  | 'injectionOrder'
  | 'treatmentOrder'
  | 'surgeryOrder'
  | 'otherOrder'
  | 'testOrder'
  | 'physiologyOrder'
  | 'bacteriaOrder'
  | 'radiologyOrder'
  | 'instractionChargeOrder'
  | 'baseChargeOrder';

type OrderGroupKey = 'prescription' | 'injection' | 'treatment' | 'test' | 'charge';
type TreatmentOrderEntity = 'treatmentOrder' | 'generalOrder' | 'surgeryOrder' | 'otherOrder';
type TestOrderEntity = 'testOrder' | 'physiologyOrder' | 'bacteriaOrder' | 'radiologyOrder';
type ChargeOrderEntity = 'baseChargeOrder' | 'instractionChargeOrder';

const isPastOrderEntity = (value: string): value is PastOrderEntity => {
  return (
    value === 'medOrder' ||
    value === 'generalOrder' ||
    value === 'injectionOrder' ||
    value === 'treatmentOrder' ||
    value === 'surgeryOrder' ||
    value === 'otherOrder' ||
    value === 'testOrder' ||
    value === 'physiologyOrder' ||
    value === 'bacteriaOrder' ||
    value === 'radiologyOrder' ||
    value === 'instractionChargeOrder' ||
    value === 'baseChargeOrder'
  );
};

const resolveEntityLabel = (entity: string): string => {
  switch (entity) {
    case 'treatmentOrder':
      return '処置';
    case 'generalOrder':
      return '一般';
    case 'surgeryOrder':
      return '手術';
    case 'otherOrder':
      return 'その他';
    case 'testOrder':
      return '検査';
    case 'physiologyOrder':
      return '生理';
    case 'bacteriaOrder':
      return '細菌';
    case 'radiologyOrder':
      return '放射線';
    case 'instractionChargeOrder':
      return '指導料';
    case 'baseChargeOrder':
      return '基本料';
    case 'injectionOrder':
      return '注射';
    case 'medOrder':
      return '処方';
    default:
      return entity;
  }
};

const resolveGroupKeyByEntity = (entity: string): OrderGroupKey | null => {
  switch (entity) {
    case 'medOrder':
      return 'prescription';
    case 'injectionOrder':
      return 'injection';
    case 'treatmentOrder':
    case 'generalOrder':
    case 'surgeryOrder':
    case 'otherOrder':
      return 'treatment';
    case 'testOrder':
    case 'physiologyOrder':
    case 'bacteriaOrder':
    case 'radiologyOrder':
      return 'test';
    case 'baseChargeOrder':
    case 'instractionChargeOrder':
      return 'charge';
    default:
      return null;
  }
};

const formatBundleName = (bundle: OrderBundle) => bundle.bundleName?.trim() || bundle.className?.trim() || '名称未設定';

const normalizeInline = (value: string) => value.replace(/\s+/g, ' ').trim();

const stripLeadingCode = (value: string) => {
  const normalized = normalizeInline(value);
  if (!normalized) return '';
  const tokens = normalized.split(' ');
  if (tokens.length >= 2 && /^[A-Za-z0-9]{4,}$/.test(tokens[0] ?? '')) {
    return tokens.slice(1).join(' ');
  }
  return normalized;
};

const formatBundleItemChip = (item: OrderBundleItem) => {
  const name = stripLeadingCode(item.name ?? '');
  if (!name) return '';
  const quantity = item.quantity?.trim();
  const unit = item.unit?.trim();
  const qty = [quantity, unit].filter(Boolean).join('');
  return qty ? `${name} ${qty}` : name;
};

const truncateChipText = (value: string, maxLength: number) => {
  if (value.length <= maxLength) return value;
  return `${value.slice(0, Math.max(1, maxLength - 1))}…`;
};

const resolveMedItemUserComment = (memo?: string | null): string => {
  const parsed = parseOrcaOrderItemMemo(memo);
  return normalizeInline(parsed.meta.userComment ?? '');
};

type BundleCardChip = {
  label: string;
  title: string;
  className?: string;
};

type BundleCardSummary = {
  metaLine?: string;
  chips: BundleCardChip[];
  moreLabel?: string;
};

const summarizeBundleForCard = (bundle: OrderBundle, entity: PastOrderEntity): BundleCardSummary => {
  const items = (bundle.items ?? []).filter((item) => Boolean(item?.name?.trim?.()));
  const chipsAll = items.reduce<BundleCardChip[]>((acc, item) => {
    const baseChip = formatBundleItemChip(item);
    if (!baseChip) return acc;
    const userComment = resolveMedItemUserComment(item.memo);
    if (!userComment) {
      acc.push({ label: baseChip, title: baseChip });
      return acc;
    }
    const shortComment = truncateChipText(userComment, 12);
    acc.push({
      label: `${baseChip} コメント:${shortComment}`,
      title: `${baseChip} コメント:${userComment}`,
      className: 'order-dock__chip--comment',
    });
    return acc;
  }, []);
  const usage = normalizeInline(bundle.admin ?? '');
  const bundleNumber = normalizeInline(bundle.bundleNumber ?? '');
  const memo = normalizeInline(bundle.memo ?? '');

  if (entity === 'medOrder') {
    const metaParts = [usage || null, bundleNumber ? `日数:${bundleNumber}` : null].filter(Boolean) as string[];
    return {
      metaLine: metaParts.length > 0 ? metaParts.join(' / ') : undefined,
      chips: chipsAll.slice(0, 6),
      moreLabel: chipsAll.length > 6 ? `他${chipsAll.length - 6}` : undefined,
    };
  }

  if (entity === 'baseChargeOrder' || entity === 'instractionChargeOrder') {
    const metaParts = [bundleNumber ? `回数:${bundleNumber}` : null, memo ? `メモ:${memo}` : null].filter(Boolean) as string[];
    return {
      metaLine: metaParts.length > 0 ? metaParts.join(' / ') : undefined,
      chips: chipsAll.slice(0, 4),
      moreLabel: chipsAll.length > 4 ? `他${chipsAll.length - 4}` : undefined,
    };
  }

  const metaParts = [usage || null, memo ? `メモ:${memo}` : null].filter(Boolean) as string[];
  return {
    metaLine: metaParts.length > 0 ? metaParts.join(' / ') : undefined,
    chips: chipsAll.slice(0, 5),
    moreLabel: chipsAll.length > 5 ? `他${chipsAll.length - 5}` : undefined,
  };
};

type BundleWarningBadge = { tone: 'warn' | 'contra'; label: string; count: number };
type SearchCandidate = {
  id: string;
  entity: PastOrderEntity;
  label: string;
  detail?: string;
  bundle?: OrderBundle;
};

const resolveWarningBadge = (warnings: OrcaMedicalWarningUi[]): BundleWarningBadge | null => {
  if (warnings.length === 0) return null;
  const combined = warnings
    .map((warning) =>
      [warning.medicalWarning, warning.message, warning.medicalClass, warning.code].filter(Boolean).join(' '),
    )
    .join(' ');
  const hasContra = /禁忌/.test(combined);
  return hasContra ? { tone: 'contra', label: '禁忌', count: warnings.length } : { tone: 'warn', label: '警告', count: warnings.length };
};

const buildRequestId = () => `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

const quickAddCategories = [
  { key: 'prescription', label: '+処方', entity: 'medOrder' as const },
  { key: 'injection', label: '+注射', entity: 'injectionOrder' as const },
  { key: 'treatment', label: '+処置', entity: null },
  { key: 'test', label: '+検査', entity: null },
  { key: 'charge', label: '+算定', entity: null },
] as const;

const formatSentAt = (value?: string) => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  const year = String(parsed.getFullYear());
  const month = String(parsed.getMonth() + 1).padStart(2, '0');
  const day = String(parsed.getDate()).padStart(2, '0');
  const hour = String(parsed.getHours()).padStart(2, '0');
  const minute = String(parsed.getMinutes()).padStart(2, '0');
  return `${year}/${month}/${day} ${hour}:${minute}`;
};

export function OrderDockPanel(props: {
  patientId?: string;
  meta: OrderBundleEditPanelMeta;
  visitDate?: string;
  orderBundles?: OrderBundle[];
  orderBundlesLoading?: boolean;
  orderBundlesError?: string;
  rpHistory?: RpHistoryEntry[];
  rpHistoryLoading?: boolean;
  rpHistoryError?: string;
  openRequest?: { requestId: string; entity: PastOrderEntity } | null;
  onOpenRequestConsumed?: (requestId: string) => void;
  historyCopyRequest?: { requestId: string; entity: PastOrderEntity; bundle: OrderBundle } | null;
  onHistoryCopyConsumed?: (requestId: string) => void;
}) {
  const {
    patientId,
    meta,
    visitDate,
    orderBundles,
    orderBundlesLoading,
    orderBundlesError,
    rpHistory,
    rpHistoryLoading,
    rpHistoryError,
    openRequest,
    onOpenRequestConsumed,
    historyCopyRequest,
    onHistoryCopyConsumed,
  } = props;
  const queryClient = useQueryClient();
  const session = useOptionalSession();
  const storageScope = useMemo(
    () => ({ facilityId: session?.facilityId, userId: session?.userId }),
    [session?.facilityId, session?.userId],
  );

  const orderVisitDate = (visitDate ?? meta.visitDate ?? '').slice(0, 10);
  const [orcaSendEntry, setOrcaSendEntry] = useState<ReturnType<typeof getOrcaClaimSendEntry> | null>(() =>
    getOrcaClaimSendEntry(storageScope, patientId),
  );
  useEffect(() => {
    setOrcaSendEntry(getOrcaClaimSendEntry(storageScope, patientId));
  }, [patientId, storageScope]);
  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<{ patientId?: string }>).detail;
      if (detail?.patientId && detail.patientId !== patientId) return;
      setOrcaSendEntry(getOrcaClaimSendEntry(storageScope, patientId));
    };
    window.addEventListener('orca-claim-send-cache-update', handler);
    return () => {
      window.removeEventListener('orca-claim-send-cache-update', handler);
    };
  }, [patientId, storageScope]);

  const orcaMedicalWarnings = useMemo<OrcaMedicalWarningUi[]>(() => {
    const warnings = orcaSendEntry?.medicalWarnings ?? [];
    const sentDate = orcaSendEntry?.performDate?.slice(0, 10);
    if (!sentDate || !orderVisitDate || sentDate !== orderVisitDate) return [];
    return warnings;
  }, [orcaSendEntry?.medicalWarnings, orcaSendEntry?.performDate, orderVisitDate]);

  const bundlesByEntity = useMemo(() => {
    const map = new Map<string, OrderBundle[]>();
    for (const bundle of (orderBundles ?? []).filter(Boolean)) {
      const started = bundle.started?.slice(0, 10);
      if (orderVisitDate && started && started !== orderVisitDate) continue;
      const entity = bundle.entity?.trim() || 'unknown';
      const list = map.get(entity) ?? [];
      list.push(bundle);
      map.set(entity, list);
    }
    return map;
  }, [orderBundles, orderVisitDate]);

  const groupSpecs = useMemo(
    () => [
      { key: 'prescription' as const, label: '処方', entities: ['medOrder'] as const },
      { key: 'injection' as const, label: '注射', entities: ['injectionOrder'] as const },
      {
        key: 'treatment' as const,
        label: '処置',
        entities: ['treatmentOrder', 'generalOrder', 'surgeryOrder', 'otherOrder'] as const,
      },
      {
        key: 'test' as const,
        label: '検査',
        entities: ['testOrder', 'physiologyOrder', 'bacteriaOrder', 'radiologyOrder'] as const,
      },
      { key: 'charge' as const, label: '算定', entities: ['baseChargeOrder', 'instractionChargeOrder'] as const },
    ],
    [],
  );

  const groupBundles = useMemo(() => {
    return groupSpecs.map((spec) => {
      const bundles = spec.entities.flatMap((entity) => bundlesByEntity.get(entity) ?? []);
      return { ...spec, bundles };
    });
  }, [bundlesByEntity, groupSpecs]);

  const hasAnyOrders = groupBundles.some((group) => group.bundles.length > 0);

  const [treatmentEntity, setTreatmentEntity] = useState<TreatmentOrderEntity>('treatmentOrder');
  const [testEntity, setTestEntity] = useState<TestOrderEntity>('testOrder');
  const [chargeEntity, setChargeEntity] = useState<ChargeOrderEntity>('baseChargeOrder');
  const [treatmentShowAll, setTreatmentShowAll] = useState(false);
  const [testShowAll, setTestShowAll] = useState(false);
  const [chargeShowAll, setChargeShowAll] = useState(false);
  const [quickSearch, setQuickSearch] = useState('');
  const [quickSearchGroup, setQuickSearchGroup] = useState<OrderGroupKey | 'all'>('all');

  const [activeEntity, setActiveEntity] = useState<PastOrderEntity | null>(null);
  const [activeRequest, setActiveRequest] = useState<OrderBundleEditPanelRequest | null>(null);
  const [quickAddGroupKey, setQuickAddGroupKey] = useState<OrderGroupKey | null>(null);
  const isQuickAddMode = quickAddGroupKey !== null;
  const quickAddModeSpec = useMemo(
    () => (quickAddGroupKey ? quickAddCategories.find((item) => item.key === quickAddGroupKey) ?? null : null),
    [quickAddGroupKey],
  );
  const quickAddEntityByGroup = useMemo<Record<OrderGroupKey, PastOrderEntity>>(
    () => ({
      prescription: 'medOrder',
      injection: 'injectionOrder',
      treatment: treatmentEntity,
      test: testEntity,
      charge: chargeEntity,
    }),
    [chargeEntity, testEntity, treatmentEntity],
  );

  const activeTitleMeta = useMemo(() => {
    if (!activeEntity) return null;
    switch (activeEntity) {
      case 'medOrder':
        return { title: '処方', bundleLabel: 'RP名', itemQuantityLabel: '用量' };
      case 'injectionOrder':
        return { title: '注射', bundleLabel: '注射名', itemQuantityLabel: '数量' };
      case 'treatmentOrder':
        return { title: '処置', bundleLabel: '処置名', itemQuantityLabel: '数量' };
      case 'generalOrder':
        return { title: '一般オーダー', bundleLabel: 'オーダー名', itemQuantityLabel: '数量' };
      case 'surgeryOrder':
        return { title: '手術', bundleLabel: '手技', itemQuantityLabel: '数量' };
      case 'otherOrder':
        return { title: 'その他', bundleLabel: '項目', itemQuantityLabel: '数量' };
      case 'testOrder':
        return { title: '検査', bundleLabel: '検査名', itemQuantityLabel: '数量' };
      case 'physiologyOrder':
        return { title: '生理検査', bundleLabel: '検査名', itemQuantityLabel: '数量' };
      case 'bacteriaOrder':
        return { title: '細菌検査', bundleLabel: '検査名', itemQuantityLabel: '数量' };
      case 'radiologyOrder':
        return { title: '放射線', bundleLabel: '検査名', itemQuantityLabel: '数量' };
      case 'baseChargeOrder':
        return { title: '基本料', bundleLabel: '算定', itemQuantityLabel: '数量' };
      case 'instractionChargeOrder':
        return { title: '指導料', bundleLabel: '算定', itemQuantityLabel: '数量' };
    }
  }, [activeEntity]);

  const canEdit = Boolean(patientId && !meta.readOnly && !meta.missingMaster && !meta.fallbackUsed);
  const editDisabledReason = !patientId
    ? '患者未選択のため操作できません。'
    : meta.readOnly
      ? meta.readOnlyReason ?? '閲覧専用のため操作できません。'
      : meta.missingMaster
        ? 'マスター未同期のため操作できません。'
        : meta.fallbackUsed
          ? 'フォールバックデータのため操作できません。'
          : undefined;

  const quickSearchCandidates = useMemo<SearchCandidate[]>(() => {
    const keyword = quickSearch.trim().toLowerCase();
    const matches = groupBundles.flatMap((group) => {
      if (quickSearchGroup !== 'all' && group.key !== quickSearchGroup) return [];
      return group.bundles.flatMap((bundle, index) => {
        const rawEntity = (bundle.entity?.trim() || group.entities[0]) as string;
        if (!isPastOrderEntity(rawEntity)) return [];
        const entity = rawEntity as PastOrderEntity;
        const summary = summarizeBundleForCard(bundle, entity);
        const label = formatBundleName(bundle);
        const detail = [resolveEntityLabel(entity), summary.metaLine].filter(Boolean).join(' / ');
        const haystack = [label, detail, summary.chips.map((chip) => chip.title).join(' ')].join(' ').toLowerCase();
        if (keyword && !haystack.includes(keyword)) return [];
        return [
          {
            id: `bundle-${group.key}-${bundle.documentId ?? 'doc'}-${bundle.moduleId ?? 'mod'}-${index}`,
            entity,
            label,
            detail,
            bundle,
          } satisfies SearchCandidate,
        ];
      });
    });
    if (matches.length > 0) return matches.slice(0, 8);

    if (!keyword) return [];
    const fallbackEntities: PastOrderEntity[] =
      quickSearchGroup === 'prescription'
        ? ['medOrder']
        : quickSearchGroup === 'injection'
          ? ['injectionOrder']
          : quickSearchGroup === 'treatment'
            ? ['treatmentOrder', 'generalOrder', 'surgeryOrder', 'otherOrder']
            : quickSearchGroup === 'test'
              ? ['testOrder', 'physiologyOrder', 'bacteriaOrder', 'radiologyOrder']
              : quickSearchGroup === 'charge'
                ? ['baseChargeOrder', 'instractionChargeOrder']
                : ['medOrder', 'injectionOrder', 'treatmentOrder', 'testOrder', 'baseChargeOrder'];
    const fallback = fallbackEntities
      .filter((entity) => resolveEntityLabel(entity).toLowerCase().includes(keyword))
      .map((entity) => ({
        id: `new-${entity}`,
        entity,
        label: `${resolveEntityLabel(entity)}を新規追加`,
        detail: '新規作成',
      }));
    return fallback.slice(0, 8);
  }, [groupBundles, quickSearch, quickSearchGroup]);

  const openEditor = useCallback(
    (entity: PastOrderEntity, request: OrderBundleEditPanelRequest) => {
      const groupKey = resolveGroupKeyByEntity(entity);
      if (groupKey === 'treatment') {
        setTreatmentEntity(entity as TreatmentOrderEntity);
      }
      if (groupKey === 'test') {
        setTestEntity(entity as TestOrderEntity);
      }
      if (groupKey === 'charge') {
        setChargeEntity(entity as ChargeOrderEntity);
      }
      if (quickAddGroupKey && groupKey !== quickAddGroupKey) {
        setQuickAddGroupKey(null);
      }
      setActiveEntity(entity);
      setActiveRequest(request);
    },
    [quickAddGroupKey],
  );

  const closeEditor = useCallback(() => {
    setActiveEntity(null);
    setActiveRequest(null);
  }, []);

  const lastOpenRequestIdRef = useRef<string | null>(null);
  useEffect(() => {
    if (!openRequest) return;
    if (openRequest.requestId === lastOpenRequestIdRef.current) return;
    lastOpenRequestIdRef.current = openRequest.requestId;
    setQuickAddGroupKey(null);
    openEditor(openRequest.entity, { requestId: openRequest.requestId, kind: 'new' });
    onOpenRequestConsumed?.(openRequest.requestId);
  }, [onOpenRequestConsumed, openEditor, openRequest]);

  const lastHistoryCopyRequestIdRef = useRef<string | null>(null);
  useEffect(() => {
    if (!historyCopyRequest) return;
    if (historyCopyRequest.requestId === lastHistoryCopyRequestIdRef.current) return;
    lastHistoryCopyRequestIdRef.current = historyCopyRequest.requestId;
    // Do not send a `new` request here: the embedded editor will consume `historyCopyRequest`
    // and would otherwise be overwritten by the reset handler (request effect runs after copy effect).
    const groupKey = resolveGroupKeyByEntity(historyCopyRequest.entity);
    if (groupKey === 'treatment') {
      setTreatmentEntity(historyCopyRequest.entity as TreatmentOrderEntity);
    }
    if (groupKey === 'test') {
      setTestEntity(historyCopyRequest.entity as TestOrderEntity);
    }
    if (groupKey === 'charge') {
      setChargeEntity(historyCopyRequest.entity as ChargeOrderEntity);
    }
    setQuickAddGroupKey(null);
    setActiveEntity(historyCopyRequest.entity);
    setActiveRequest(null);
  }, [historyCopyRequest]);

  useEffect(() => {
    if (typeof document === 'undefined') return;
    if (!activeEntity) return;
    const groupKey = resolveGroupKeyByEntity(activeEntity);
    if (!groupKey) return;
    requestAnimationFrame(() => {
      const section = document.querySelector(`section.order-dock__group[data-group="${groupKey}"]`);
      if (!section) return;
      const inlineEditor = section.querySelector('.order-dock__inline-editor');
      if (!(inlineEditor instanceof HTMLElement)) return;
      if (typeof inlineEditor.scrollIntoView !== 'function') return;
      inlineEditor.scrollIntoView({ block: 'nearest' });
    });
  }, [activeEntity]);

  const [notice, setNotice] = useState<{ tone: 'success' | 'error' | 'info'; message: string } | null>(null);
  const deleteMutation = useMutation({
    mutationFn: async (bundle: OrderBundle) => {
      if (!patientId) throw new Error('patientId is required');
      return mutateOrderBundles({
        patientId,
        operations: [
          {
            operation: 'delete',
            documentId: bundle.documentId,
            moduleId: bundle.moduleId,
            entity: bundle.entity,
          },
        ],
      });
    },
    onSuccess: (result) => {
      setNotice({ tone: result.ok ? 'success' : 'error', message: result.ok ? 'オーダーを削除しました。' : result.message ?? '削除に失敗しました。' });
      if (result.ok && patientId) {
        queryClient.invalidateQueries({ queryKey: ['charts-order-bundles', patientId] });
        queryClient.invalidateQueries({ queryKey: ['charts-order-recommendations', patientId] });
      }
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : String(error);
      setNotice({ tone: 'error', message: `削除に失敗しました: ${message}` });
    },
  });

  const [recommendModalOpen, setRecommendModalOpen] = useState(false);
  const [recommendModalEntity, setRecommendModalEntity] = useState<PastOrderEntity | ''>('');
  const [deleteTarget, setDeleteTarget] = useState<{ bundle: OrderBundle; label: string; groupLabel: string } | null>(null);
  const openRecommendationModal = useCallback((entity: PastOrderEntity) => {
    setRecommendModalEntity(entity);
    setRecommendModalOpen(true);
  }, []);

  const handleQuickAdd = useCallback(
    (groupKey: OrderGroupKey) => {
      setQuickAddGroupKey(groupKey);
      openEditor(quickAddEntityByGroup[groupKey], { requestId: buildRequestId(), kind: 'new' });
    },
    [openEditor, quickAddEntityByGroup],
  );

  const handleQuickAddExit = useCallback(() => {
    setQuickAddGroupKey(null);
  }, []);

  const handleQuickSearchApply = useCallback(
    (candidate: SearchCandidate) => {
      if (!canEdit) {
        setNotice({ tone: 'error', message: editDisabledReason ?? '編集不可のため追加できません。' });
        return;
      }
      setQuickAddGroupKey(null);
      const requestId = buildRequestId();
      if (candidate.bundle) {
        openEditor(candidate.entity, { requestId, kind: 'copy', bundle: candidate.bundle });
        setNotice({ tone: 'info', message: `「${candidate.label}」をコピーして編集を開始しました。` });
      } else {
        openEditor(candidate.entity, { requestId, kind: 'new' });
        setNotice({ tone: 'info', message: `${resolveEntityLabel(candidate.entity)}の新規入力を開始しました。` });
      }
      setQuickSearch('');
    },
    [canEdit, editDisabledReason, openEditor],
  );

  const handleApplyRecommendation = useCallback(
    (candidate: OrderRecommendationCandidate, entity: string) => {
      const resolved = entity.trim();
      if (!isPastOrderEntity(resolved)) return;
      setQuickAddGroupKey(null);
      openEditor(resolved, { requestId: buildRequestId(), kind: 'recommendation', candidate });
      setRecommendModalOpen(false);
    },
    [openEditor],
  );

  const latestPrescription = useMemo(() => {
    const entries = (rpHistory ?? []).filter(Boolean);
    if (entries.length === 0) return null;
    const sorted = entries.slice().sort((a, b) => (b.issuedDate ?? '').localeCompare(a.issuedDate ?? ''));
    return sorted[0] ?? null;
  }, [rpHistory]);
  const sendStatusLabel = orcaSendEntry?.sendStatus === 'success' ? '送信済み' : orcaSendEntry?.sendStatus === 'error' ? '送信失敗' : '未送信';
  const sendStatusTone = orcaSendEntry?.sendStatus === 'success' ? 'success' : orcaSendEntry?.sendStatus === 'error' ? 'error' : 'idle';
  const lastSentAt = formatSentAt(orcaSendEntry?.savedAt);
  const visibleGroupBundles = useMemo(
    () => (quickAddGroupKey ? groupBundles.filter((group) => group.key === quickAddGroupKey) : groupBundles),
    [quickAddGroupKey, groupBundles],
  );
  const hasVisibleOrders = visibleGroupBundles.some((group) => group.bundles.length > 0);
  const prescriptionDrugs = useMemo(() => latestPrescription?.rpList ?? [], [latestPrescription]);
  const prescriptionIssuedDate = latestPrescription?.issuedDate?.trim() ?? '';
  const prescriptionMemo = latestPrescription?.memo?.trim() ?? '';
  const latestPrescriptionBundle = useMemo<OrderBundle | null>(() => {
    const drugs = (latestPrescription?.rpList ?? []).filter(Boolean);
    if (drugs.length === 0) return null;
    const items = drugs.reduce<OrderBundleItem[]>((acc, drug) => {
      const code = normalizeInline(drug.srycd ?? '');
      const rawName = normalizeInline(drug.name ?? '');
      const name = normalizeInline([code, rawName].filter(Boolean).join(' '));
      if (!name) return acc;
      const item: OrderBundleItem = {
        name,
        quantity: normalizeInline(drug.dose ?? ''),
        unit: normalizeInline(drug.amount ?? ''),
        memo: normalizeInline(drug.memo ?? ''),
      };
      if (code) {
        item.code = code;
      }
      acc.push(item);
      return acc;
    }, []);
    if (items.length === 0) return null;
    const usage = normalizeInline(drugs[0]?.usage ?? '');
    const days = normalizeInline(drugs[0]?.days ?? '');
    return {
      entity: 'medOrder',
      bundleName: stripLeadingCode(items[0]?.name ?? '') || '前回処方',
      admin: usage,
      bundleNumber: days || '1',
      started: orderVisitDate,
      items,
    };
  }, [latestPrescription?.rpList, orderVisitDate]);

  const renderQuickAdds = () => {
    if (isQuickAddMode && quickAddModeSpec) {
      return (
        <div className="order-dock__quick-add" role="group" aria-label="オーダー追加">
          <span className="order-dock__quick-add-mode">クイック追加中: {quickAddModeSpec.label.replace('+', '')}</span>
          <button
            type="button"
            className="order-dock__mini-add"
            data-test-id={`order-dock-quick-add-${quickAddModeSpec.key}`}
            onClick={() => handleQuickAdd(quickAddModeSpec.key)}
            disabled={!canEdit}
            title={!canEdit ? editDisabledReason : undefined}
          >
            {quickAddModeSpec.label}
          </button>
          <button
            type="button"
            className="order-dock__mini-secondary"
            data-test-id="order-dock-quick-add-exit"
            onClick={handleQuickAddExit}
          >
            通常閲覧へ戻る
          </button>
        </div>
      );
    }

    return (
      <div className="order-dock__quick-add" role="group" aria-label="オーダー追加">
        {quickAddCategories.map((item) => (
          <button
            key={item.label}
            type="button"
            className="order-dock__mini-add"
            data-test-id={`order-dock-quick-add-${item.key}`}
            onClick={() => handleQuickAdd(item.key)}
            disabled={!canEdit}
            title={!canEdit ? editDisabledReason : undefined}
          >
            {item.label}
          </button>
        ))}
        <button
          type="button"
          className="order-dock__mini-secondary"
          onClick={() => openRecommendationModal('medOrder')}
          disabled={!patientId}
          title={!patientId ? '患者未選択のため開けません。' : undefined}
        >
          頻用
        </button>
      </div>
    );
  };

  const renderGroup = (group: (typeof groupBundles)[number]) => {
    const defaultEntity = (() => {
      switch (group.key) {
        case 'prescription':
          return 'medOrder' as const;
        case 'injection':
          return 'injectionOrder' as const;
        case 'treatment':
          return treatmentEntity;
        case 'test':
          return testEntity;
        case 'charge':
          return chargeEntity;
      }
    })();
    const canOpenEditor = canEdit;
    const canOpenRecommendation = Boolean(patientId);
    const selectionMeta = (() => {
      switch (group.key) {
        case 'treatment':
          return { showAll: treatmentShowAll, label: resolveEntityLabel(treatmentEntity) };
        case 'test':
          return { showAll: testShowAll, label: resolveEntityLabel(testEntity) };
        case 'charge':
          return { showAll: chargeShowAll, label: resolveEntityLabel(chargeEntity) };
        default:
          return { showAll: false, label: '' };
      }
    })();
    const isQuickAddTarget = quickAddGroupKey === group.key;
    const visibleBundles = (() => {
      if (group.bundles.length === 0) return [];
      if (isQuickAddMode && isQuickAddTarget) return group.bundles;
      if (group.key === 'treatment' && !treatmentShowAll) {
        return group.bundles.filter((bundle) => (bundle.entity?.trim() || defaultEntity) === treatmentEntity);
      }
      if (group.key === 'test' && !testShowAll) {
        return group.bundles.filter((bundle) => (bundle.entity?.trim() || defaultEntity) === testEntity);
      }
      if (group.key === 'charge' && !chargeShowAll) {
        return group.bundles.filter((bundle) => (bundle.entity?.trim() || defaultEntity) === chargeEntity);
      }
      return group.bundles;
    })();
    const showEntityBadge = Boolean(
      (isQuickAddMode && isQuickAddTarget) ||
      (group.key === 'treatment' && treatmentShowAll) ||
        (group.key === 'test' && testShowAll) ||
        (group.key === 'charge' && chargeShowAll),
    );
    const isInlineEditorVisible = Boolean(activeEntity && (group.entities as readonly string[]).includes(activeEntity));
    const inlineEntity = isInlineEditorVisible ? activeEntity : null;
    const inlineTitleMeta = isInlineEditorVisible ? activeTitleMeta : null;
    const inlineBundlesOverride = inlineEntity ? bundlesByEntity.get(inlineEntity) ?? [] : [];

    return (
      <section key={group.key} className="order-dock__group" data-group={group.key}>
        <header className="order-dock__group-header">
          <div className="order-dock__group-title">
            <strong>{group.label}</strong>
            {isQuickAddMode ? (
              <span className="order-dock__group-mode">クイック追加</span>
            ) : (
              <span className="order-dock__group-count">
                {visibleBundles.length}
                {visibleBundles.length !== group.bundles.length ? `/${group.bundles.length}` : ''}件
                {selectionMeta.label && !selectionMeta.showAll ? `(${selectionMeta.label})` : ''}
              </span>
            )}
          </div>
          <div className="order-dock__group-actions" role="group" aria-label={`${group.label}操作`}>
            <button
              type="button"
              className="order-dock__group-action order-dock__group-action--add"
              data-test-id={`order-dock-group-add-${group.key}`}
              onClick={() => openEditor(defaultEntity as PastOrderEntity, { requestId: buildRequestId(), kind: 'new' })}
              disabled={!canOpenEditor}
              title={!canOpenEditor ? editDisabledReason : undefined}
            >
              追加
            </button>
            <button
              type="button"
              className="order-dock__group-action"
              onClick={() => openRecommendationModal(defaultEntity)}
              disabled={!canOpenRecommendation}
              title={!canOpenRecommendation ? '患者未選択のため開けません。' : undefined}
            >
              頻用
            </button>
          </div>
        </header>

        {!isQuickAddMode && group.key === 'treatment' ? (
          <div className="order-dock__subtype-tabs" role="tablist" aria-label="処置種類">
            {(
              [
                { key: 'treatmentOrder' as const, label: '処置' },
                { key: 'generalOrder' as const, label: '一般' },
                { key: 'surgeryOrder' as const, label: '手術' },
                { key: 'otherOrder' as const, label: 'その他' },
              ] as const
            ).map((tab) => (
              <button
                key={`treatment-tab-${tab.key}`}
                type="button"
                className="order-dock__subtype-tab"
                data-active={!treatmentShowAll && treatmentEntity === tab.key ? 'true' : 'false'}
                aria-pressed={!treatmentShowAll && treatmentEntity === tab.key}
                onClick={() => {
                  setTreatmentEntity(tab.key);
                  setTreatmentShowAll(false);
                }}
              >
                {tab.label}
              </button>
            ))}
            <button
              type="button"
              className="order-dock__subtype-tab"
              data-active={treatmentShowAll ? 'true' : 'false'}
              aria-pressed={treatmentShowAll}
              onClick={() => setTreatmentShowAll(true)}
            >
              すべて
            </button>
          </div>
        ) : null}
        {!isQuickAddMode && group.key === 'test' ? (
          <div className="order-dock__subtype-tabs" role="tablist" aria-label="検査種類">
            {(
              [
                { key: 'testOrder' as const, label: '検査' },
                { key: 'physiologyOrder' as const, label: '生理' },
                { key: 'bacteriaOrder' as const, label: '細菌' },
                { key: 'radiologyOrder' as const, label: '放射線' },
              ] as const
            ).map((tab) => (
              <button
                key={`test-tab-${tab.key}`}
                type="button"
                className="order-dock__subtype-tab"
                data-active={!testShowAll && testEntity === tab.key ? 'true' : 'false'}
                aria-pressed={!testShowAll && testEntity === tab.key}
                onClick={() => {
                  setTestEntity(tab.key);
                  setTestShowAll(false);
                }}
              >
                {tab.label}
              </button>
            ))}
            <button
              type="button"
              className="order-dock__subtype-tab"
              data-active={testShowAll ? 'true' : 'false'}
              aria-pressed={testShowAll}
              onClick={() => setTestShowAll(true)}
            >
              すべて
            </button>
          </div>
        ) : null}
        {!isQuickAddMode && group.key === 'charge' ? (
          <div className="order-dock__subtype-tabs" role="tablist" aria-label="算定種類">
            {(
              [
                { key: 'baseChargeOrder' as const, label: '基本料' },
                { key: 'instractionChargeOrder' as const, label: '指導料' },
              ] as const
            ).map((tab) => (
              <button
                key={`charge-tab-${tab.key}`}
                type="button"
                className="order-dock__subtype-tab"
                data-active={!chargeShowAll && chargeEntity === tab.key ? 'true' : 'false'}
                aria-pressed={!chargeShowAll && chargeEntity === tab.key}
                onClick={() => {
                  setChargeEntity(tab.key);
                  setChargeShowAll(false);
                }}
              >
                {tab.label}
              </button>
            ))}
            <button
              type="button"
              className="order-dock__subtype-tab"
              data-active={chargeShowAll ? 'true' : 'false'}
              aria-pressed={chargeShowAll}
              onClick={() => setChargeShowAll(true)}
            >
              すべて
            </button>
          </div>
        ) : null}
        {inlineEntity && inlineTitleMeta ? (
          <div className="order-dock__inline-editor" aria-label={`${resolveEntityLabel(inlineEntity)}入力`}>
            <OrderBundleEditPanel
              patientId={patientId}
              entity={inlineEntity}
              title={inlineTitleMeta.title}
              bundleLabel={inlineTitleMeta.bundleLabel}
              itemQuantityLabel={inlineTitleMeta.itemQuantityLabel}
              meta={meta}
              variant="embedded"
              bundlesOverride={inlineBundlesOverride}
              request={activeRequest}
              onRequestConsumed={(requestId) => {
                setActiveRequest((prev) => (prev?.requestId === requestId ? null : prev));
              }}
              historyCopyRequest={
                historyCopyRequest?.entity === inlineEntity
                  ? { requestId: historyCopyRequest.requestId, bundle: historyCopyRequest.bundle }
                  : null
              }
              onHistoryCopyConsumed={(requestId) => onHistoryCopyConsumed?.(requestId)}
              onClose={closeEditor}
            />
          </div>
        ) : null}

        <div className="order-dock__bundle-list" role="list" aria-label={`${group.label}オーダー一覧`}>
          {group.bundles.length === 0 ? (
            isQuickAddMode ? null : <p className="order-dock__empty">まだありません。</p>
          ) : visibleBundles.length === 0 ? (
            isQuickAddMode ? null : <p className="order-dock__empty">この種類のオーダーはまだありません。</p>
          ) : (
            visibleBundles.map((bundle, index) => {
              const bundleEntity = (bundle.entity?.trim() || defaultEntity) as PastOrderEntity;
              const bundleLabel = formatBundleName(bundle);
              const summary = summarizeBundleForCard(bundle, bundleEntity);
              const warnings = bundle.documentId
                ? orcaMedicalWarnings.filter(
                    (warning) =>
                      warning.documentId === bundle.documentId && (warning.entity?.trim() ?? '') === bundleEntity,
                  )
                : [];
              const warningBadge = resolveWarningBadge(warnings);
              const canMutate = canEdit;
              return (
                <div
                  key={`${group.key}-${bundle.documentId ?? 'doc'}-${bundle.moduleId ?? 'mod'}-${index}`}
                  className="order-dock__bundle"
                  role="listitem"
                >
                  <div className="order-dock__bundle-main">
                    <div className="order-dock__bundle-head">
                      <strong className="order-dock__bundle-name">{bundleLabel}</strong>
                      <div className="order-dock__bundle-badges" role="group" aria-label="バッジ">
                        {showEntityBadge ? (
                          <span className="order-dock__badge order-dock__badge--entity">{resolveEntityLabel(bundleEntity)}</span>
                        ) : null}
                        {warningBadge ? (
                          <span className={`order-dock__badge order-dock__badge--${warningBadge.tone}`}>
                            {warningBadge.label}
                            {warningBadge.count > 1 ? `×${warningBadge.count}` : ''}
                          </span>
                        ) : null}
                      </div>
                    </div>
                    {summary.metaLine ? <span className="order-dock__bundle-meta">{summary.metaLine}</span> : null}
                    {summary.chips.length > 0 ? (
                      <div className="order-dock__chips" aria-label="項目">
                        {summary.chips.map((chip, chipIndex) => (
                          <span
                            key={`${chip.title}-${chipIndex}`}
                            className={`order-dock__chip${chip.className ? ` ${chip.className}` : ''}`}
                            title={chip.title}
                          >
                            {chip.label}
                          </span>
                        ))}
                        {summary.moreLabel ? (
                          <span className="order-dock__chip order-dock__chip--more">{summary.moreLabel}</span>
                        ) : null}
                      </div>
                    ) : (
                      <span className="order-dock__bundle-items">項目なし</span>
                    )}
                  </div>
                  <div className="order-dock__bundle-actions" role="group" aria-label={`${group.label}束操作`}>
                    <button
                      type="button"
                      className="order-dock__bundle-action"
                      onClick={() => openEditor(bundleEntity, { requestId: buildRequestId(), kind: 'edit', bundle })}
                      disabled={!canMutate}
                      title={!canMutate ? editDisabledReason : undefined}
                    >
                      編集
                    </button>
                    <button
                      type="button"
                      className="order-dock__bundle-action"
                      onClick={() => openEditor(bundleEntity, { requestId: buildRequestId(), kind: 'copy', bundle })}
                      disabled={!canMutate}
                      title={!canMutate ? editDisabledReason : undefined}
                    >
                      コピー
                    </button>
                    <button
                      type="button"
                      className="order-dock__bundle-action order-dock__bundle-action--danger"
                      onClick={() => {
                        if (!canMutate) return;
                        setDeleteTarget({ bundle, label: bundleLabel, groupLabel: group.label });
                      }}
                      disabled={!canMutate || deleteMutation.isPending}
                      title={!canMutate ? editDisabledReason : undefined}
                    >
                      削除
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </section>
    );
  };

  return (
    <div className="order-dock" data-has-orders={hasAnyOrders ? '1' : '0'}>
      <FocusTrapDialog
        open={Boolean(deleteTarget)}
        role="alertdialog"
        title="オーダーを削除しますか？"
        description="対象と影響範囲を確認して実行してください。"
        onClose={() => setDeleteTarget(null)}
        testId="order-dock-delete-dialog"
      >
        <section className="charts-tab-guard" aria-label="オーダー削除確認">
          <dl className="charts-actions__send-confirm-list">
            <div>
              <dt>対象名</dt>
              <dd>{deleteTarget?.label ?? '—'}</dd>
            </div>
            <div>
              <dt>患者ID</dt>
              <dd>{patientId ?? '—'}</dd>
            </div>
            <div>
              <dt>対象カテゴリ</dt>
              <dd>{deleteTarget?.groupLabel ?? '—'}</dd>
            </div>
            <div>
              <dt>影響範囲</dt>
              <dd>該当オーダー束が一覧から削除されます。</dd>
            </div>
          </dl>
          <div className="charts-tab-guard__actions" role="group" aria-label="オーダー削除操作">
            <button type="button" onClick={() => setDeleteTarget(null)}>
              キャンセル
            </button>
            <button
              type="button"
              className="charts-tab-guard__danger"
              onClick={() => {
                if (!deleteTarget) return;
                deleteMutation.mutate(deleteTarget.bundle, {
                  onSettled: () => setDeleteTarget(null),
                });
              }}
              disabled={deleteMutation.isPending}
            >
              削除する
            </button>
          </div>
        </section>
      </FocusTrapDialog>
      <header className="order-dock__header">
        <div className="order-dock__header-main">
          <span className="order-dock__step-label">1. 受診ヘッダ固定</span>
          <strong>オーダー入力</strong>
          <span className="order-dock__meta">診療日:{orderVisitDate || '—'}</span>
        </div>
        <div className="order-dock__header-actions" role="group" aria-label="頻用オーダー">
          <button
            type="button"
            className="order-dock__header-action"
            onClick={() => {
              setRecommendModalEntity('');
              setRecommendModalOpen(true);
            }}
            disabled={!patientId}
            title={!patientId ? '患者未選択のため開けません。' : '横断の頻用候補を表示します。'}
          >
            頻用
          </button>
        </div>
      </header>
      <section className="order-dock__stage" aria-label="カテゴリ入力">
        <p className="order-dock__stage-title">2. カテゴリ入力</p>
        {!orderBundlesLoading && !orderBundlesError ? renderQuickAdds() : null}
        {!isQuickAddMode ? (
          <div className="order-dock__search" aria-label="検索して追加">
            <div className="order-dock__search-row">
              <label htmlFor="order-dock-search-input">検索して追加</label>
              <input
                id="order-dock-search-input"
                type="search"
                value={quickSearch}
                onChange={(event) => setQuickSearch(event.target.value)}
                placeholder="オーダー名・薬剤名・コード"
                disabled={!patientId || !canEdit}
                aria-label="オーダー検索"
              />
              <select
                value={quickSearchGroup}
                onChange={(event) => setQuickSearchGroup(event.target.value as OrderGroupKey | 'all')}
                disabled={!patientId || !canEdit}
                aria-label="カテゴリ選択"
              >
                <option value="all">全カテゴリ</option>
                <option value="prescription">処方</option>
                <option value="injection">注射</option>
                <option value="treatment">処置</option>
                <option value="test">検査</option>
                <option value="charge">算定</option>
              </select>
            </div>
            {quickSearchCandidates.length > 0 ? (
              <ul className="order-dock__search-results" role="listbox" aria-label="検索候補">
                {quickSearchCandidates.map((candidate) => (
                  <li key={candidate.id}>
                    <button
                      type="button"
                      className="order-dock__search-result"
                      onClick={() => handleQuickSearchApply(candidate)}
                      disabled={!canEdit}
                      title={!canEdit ? editDisabledReason : candidate.detail}
                    >
                      <strong>{candidate.label}</strong>
                      <span>{candidate.detail ?? '候補'}</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : quickSearch.trim().length > 0 ? (
              <p className="order-dock__search-empty">候補が見つかりません。カテゴリを変えて検索してください。</p>
            ) : null}
          </div>
        ) : null}
      </section>
      <section className="order-dock__stage order-dock__stage--send" aria-label="送信/差分">
        <p className="order-dock__stage-title">3. 送信/差分</p>
        <div className="order-dock__send-summary" role="status" aria-live="polite">
          <span className={`order-dock__send-status order-dock__send-status--${sendStatusTone}`}>送信状態: {sendStatusLabel}</span>
          <span className="order-dock__send-last">最終送信: {lastSentAt}</span>
        </div>
      </section>

      {notice ? <div className={`order-dock__notice order-dock__notice--${notice.tone}`}>{notice.message}</div> : null}
      {orderBundlesLoading ? <p className="order-dock__empty">オーダー情報を取得しています...</p> : null}
      {orderBundlesError ? <p className="order-dock__empty">オーダー情報の取得に失敗しました: {orderBundlesError}</p> : null}
      {!orderBundlesLoading && !orderBundlesError && (hasVisibleOrders || activeEntity) ? (
        <div className="order-dock__groups">{visibleGroupBundles.map(renderGroup)}</div>
      ) : null}

      {rpHistoryLoading || rpHistoryError || prescriptionDrugs.length > 0 || prescriptionMemo ? (
        <details className="order-dock__rx" aria-label="処方履歴（直近）">
          <summary className="order-dock__rx-summary">処方履歴（直近）</summary>
          {rpHistoryLoading ? (
            <p className="order-dock__empty">処方履歴を取得しています...</p>
          ) : rpHistoryError ? (
            <p className="order-dock__empty">処方履歴の取得に失敗しました: {rpHistoryError}</p>
          ) : prescriptionDrugs.length === 0 ? (
            <p className="order-dock__empty">直近の処方履歴はありません。</p>
          ) : (
            <>
              <p className="order-dock__rx-meta">発行:{prescriptionIssuedDate || '—'}</p>
              <ol className="order-dock__rx-list" aria-label="処方薬剤一覧">
                {prescriptionDrugs.slice(0, 40).map((drug, index) => {
                  const name = drug.name?.trim() || '薬剤名不明';
                  const dose = drug.dose?.trim();
                  const amount = drug.amount?.trim();
                  const usage = drug.usage?.trim();
                  const days = drug.days?.trim();
                  const line = [dose, amount].filter(Boolean).join(' ');
                  const metaLine = [usage, days ? `日数:${days}` : null].filter(Boolean).join(' / ');
                  return (
                    <li key={`${name}-${index}`} className="order-dock__rx-item">
                      <strong>{name}</strong>
                      {line ? <span>{line}</span> : null}
                      {metaLine ? <span className="order-dock__rx-sub">{metaLine}</span> : null}
                    </li>
                  );
                })}
              </ol>
              {prescriptionMemo ? <p className="order-dock__rx-memo">メモ: {prescriptionMemo}</p> : null}
              <div className="order-dock__rx-actions" role="group" aria-label="処方取り込み">
                <button
                  type="button"
                  className="order-dock__rx-action"
                  onClick={() => openEditor('medOrder', { requestId: buildRequestId(), kind: 'new' })}
                  disabled={!canEdit}
                  title={!canEdit ? editDisabledReason : undefined}
                >
                  新規（空）
                </button>
                <button
                  type="button"
                  className="order-dock__rx-action"
                  onClick={() => {
                    if (!latestPrescriptionBundle) return;
                    openEditor('medOrder', { requestId: buildRequestId(), kind: 'copy', bundle: latestPrescriptionBundle });
                  }}
                  disabled={!canEdit || !latestPrescriptionBundle}
                  title={
                    !latestPrescriptionBundle
                      ? '直近処方がないためコピーできません。'
                      : !canEdit
                        ? editDisabledReason
                        : '直近処方をコピーして開始します。'
                  }
                >
                  直近処方をコピーして開始
                </button>
              </div>
            </>
          )}
        </details>
      ) : null}

      <OrderRecommendationModal
        open={recommendModalOpen}
        patientId={patientId}
        defaultEntity={recommendModalEntity || undefined}
        defaultScope={recommendModalEntity ? 'category' : 'all'}
        onClose={() => setRecommendModalOpen(false)}
        onApply={handleApplyRecommendation}
      />
    </div>
  );
}
