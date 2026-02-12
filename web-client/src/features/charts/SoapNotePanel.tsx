import { useCallback, useEffect, useMemo, useState } from 'react';

import type { DataSourceTransition } from './authService';
import { recordChartsAuditEvent } from './audit';
import type { DraftDirtySource } from './draftSources';
import {
  SOAP_SECTIONS,
  SOAP_SECTION_LABELS,
  SOAP_TEMPLATES,
  buildSoapDraftFromHistory,
  buildSoapEntryId,
  formatSoapAuthoredAt,
  getLatestSoapEntries,
  type SoapDraft,
  type SoapEntry,
  type SoapSectionKey,
} from './soapNote';
import { SubjectivesPanel } from './soap/SubjectivesPanel';
import { appendImageAttachmentPlaceholders, type ChartImageAttachment } from './documentImageAttach';
import { postChartSubjectiveEntry, type ChartSubjectiveEntryRequest } from './soap/subjectiveChartApi';
import { RevisionHistoryDrawer } from './revisions/RevisionHistoryDrawer';
import type { RpHistoryEntry } from './karteExtrasApi';
import type { OrderBundle } from './orderBundleApi';

export type SoapNoteMeta = {
  runId?: string;
  cacheHit?: boolean;
  missingMaster?: boolean;
  fallbackUsed?: boolean;
  dataSourceTransition?: DataSourceTransition;
  patientId?: string;
  appointmentId?: string;
  receptionId?: string;
  visitDate?: string;
};

export type SoapNoteAuthor = {
  role: string;
  displayName?: string;
  userId: string;
};

type SoapNotePanelProps = {
  history: SoapEntry[];
  meta: SoapNoteMeta;
  author: SoapNoteAuthor;
  readOnly?: boolean;
  readOnlyReason?: string;
  rpHistory?: RpHistoryEntry[];
  rpHistoryLoading?: boolean;
  rpHistoryError?: string;
  orderBundles?: OrderBundle[];
  orderBundlesLoading?: boolean;
  orderBundlesError?: string;
  onOpenPrescriptionEditor?: () => void;
  onOpenOrderEditor?: (entity: string) => void;
  onDraftSnapshot?: (draft: SoapDraft) => void;
  replaceDraftRequest?: { token: string; draft: SoapDraft; note?: string } | null;
  applyDraftPatch?: { token: string; section: SoapSectionKey; body: string; note?: string } | null;
  attachmentInsert?: { attachment: ChartImageAttachment; section: SoapSectionKey; token: string } | null;
  onAttachmentInserted?: () => void;
  onAppendHistory?: (entries: SoapEntry[]) => void;
  onDraftDirtyChange?: (next: {
    dirty: boolean;
    patientId?: string;
    appointmentId?: string;
    receptionId?: string;
    visitDate?: string;
    dirtySources?: DraftDirtySource[];
  }) => void;
  onClearHistory?: () => void;
  onAuditLogged?: () => void;
};

const resolveAuthorLabel = (author: SoapNoteAuthor) => {
  return author.displayName ?? author.userId ?? author.role;
};

const filterTemplatesForSection = (section: SoapSectionKey) =>
  SOAP_TEMPLATES.filter((template) => Boolean(template.sections[section]));

const resolveSoapCategory = (section: SoapSectionKey): 'S' | 'O' | 'A' | 'P' | null => {
  switch (section) {
    case 'subjective':
      return 'S';
    case 'objective':
      return 'O';
    case 'assessment':
      return 'A';
    case 'plan':
      return 'P';
    case 'free':
      return 'S';
    default:
      return null;
  }
};

export function SoapNotePanel({
  history,
  meta,
  author,
  readOnly,
  readOnlyReason,
  rpHistory,
  rpHistoryLoading = false,
  rpHistoryError,
  orderBundles,
  orderBundlesLoading = false,
  orderBundlesError,
  onOpenPrescriptionEditor,
  onOpenOrderEditor,
  onDraftSnapshot,
  replaceDraftRequest,
  applyDraftPatch,
  attachmentInsert,
  onAttachmentInserted,
  onAppendHistory,
  onDraftDirtyChange,
  onClearHistory,
  onAuditLogged,
}: SoapNotePanelProps) {
  const isRevisionHistoryEnabled = import.meta.env.VITE_CHARTS_REVISION_HISTORY === '1';
  type SoapNoteViewMode = 'both' | 'soap' | 'free';
  const SOAP_VIEW_MODE_STORAGE_KEY = 'opendolphin:web-client:charts:soap-view-mode:v1';
  const loadViewMode = (): SoapNoteViewMode => {
    if (typeof sessionStorage === 'undefined') return 'both';
    try {
      const raw = sessionStorage.getItem(SOAP_VIEW_MODE_STORAGE_KEY);
      return raw === 'soap' || raw === 'free' || raw === 'both' ? raw : 'both';
    } catch {
      return 'both';
    }
  };
  const [viewMode, setViewMode] = useState<SoapNoteViewMode>(() => loadViewMode());
  useEffect(() => {
    if (typeof sessionStorage === 'undefined') return;
    try {
      sessionStorage.setItem(SOAP_VIEW_MODE_STORAGE_KEY, viewMode);
    } catch {
      // ignore storage errors
    }
  }, [viewMode]);
  const SOAP_HISTORY_VIEW_STORAGE_KEY = 'opendolphin:web-client:charts:soap-history-view:v1';
  const loadHistoryView = (): boolean => {
    if (typeof sessionStorage === 'undefined') return false;
    try {
      const raw = sessionStorage.getItem(SOAP_HISTORY_VIEW_STORAGE_KEY);
      return raw === '1';
    } catch {
      return false;
    }
  };
  const [historyView, setHistoryView] = useState<boolean>(() => loadHistoryView());
  useEffect(() => {
    if (typeof sessionStorage === 'undefined') return;
    try {
      sessionStorage.setItem(SOAP_HISTORY_VIEW_STORAGE_KEY, historyView ? '1' : '0');
    } catch {
      // ignore storage errors
    }
  }, [historyView]);
  const [draft, setDraft] = useState<SoapDraft>(() => buildSoapDraftFromHistory(history));
  const [selectedTemplate, setSelectedTemplate] = useState<Partial<Record<SoapSectionKey, string>>>({});
  const [pendingTemplate, setPendingTemplate] = useState<Partial<Record<SoapSectionKey, string>>>({});
  const [feedback, setFeedback] = useState<string | null>(null);
  const [revisionDrawerOpen, setRevisionDrawerOpen] = useState(false);
  const [subjectivesOpen, setSubjectivesOpen] = useState(false);

  const latestBySection = useMemo(() => getLatestSoapEntries(history), [history]);
  const firstBySection = useMemo(() => {
    const map = new Map<SoapSectionKey, SoapEntry>();
    const timestampBySection = new Map<SoapSectionKey, number>();
    history.forEach((entry) => {
      const tsRaw = Date.parse(entry.authoredAt);
      const ts = Number.isNaN(tsRaw) ? Number.POSITIVE_INFINITY : tsRaw;
      const current = timestampBySection.get(entry.section);
      if (typeof current === 'number' && current <= ts) return;
      timestampBySection.set(entry.section, ts);
      map.set(entry.section, entry);
    });
    return map;
  }, [history]);
  const historyBySection = useMemo(() => {
    const map = new Map<SoapSectionKey, SoapEntry[]>();
    history.forEach((entry) => {
      const list = map.get(entry.section);
      if (list) {
        list.push(entry);
      } else {
        map.set(entry.section, [entry]);
      }
    });
    return map;
  }, [history]);
  const visibleSections = useMemo<SoapSectionKey[]>(() => {
    switch (viewMode) {
      case 'soap':
        return SOAP_SECTIONS.filter((section) => section !== 'free');
      case 'free':
        return ['free'];
      default:
        return SOAP_SECTIONS;
    }
  }, [viewMode]);
  const authoredMeta = useMemo(() => {
    if (history.length === 0) return { first: null as SoapEntry | null, last: null as SoapEntry | null };
    let first = history[0];
    let last = history[0];
    let firstTs = Date.parse(first.authoredAt);
    let lastTs = Date.parse(last.authoredAt);
    history.slice(1).forEach((entry) => {
      const ts = Date.parse(entry.authoredAt);
      if (!Number.isNaN(ts) && (Number.isNaN(firstTs) || ts < firstTs)) {
        first = entry;
        firstTs = ts;
      }
      if (!Number.isNaN(ts) && (Number.isNaN(lastTs) || ts > lastTs)) {
        last = entry;
        lastTs = ts;
      }
    });
    return { first, last };
  }, [history]);
  const latestPrescription = useMemo(() => {
    const entries = (rpHistory ?? []).filter(Boolean);
    if (entries.length === 0) return null;
    const sorted = entries.slice().sort((a, b) => (b.issuedDate ?? '').localeCompare(a.issuedDate ?? ''));
    return sorted[0] ?? null;
  }, [rpHistory]);
  const prescriptionDrugs = useMemo(() => latestPrescription?.rpList ?? [], [latestPrescription]);
  const prescriptionIssuedDate = latestPrescription?.issuedDate?.trim() ?? '';
  const prescriptionMemo = latestPrescription?.memo?.trim() ?? '';
  const orderVisitDate = meta.visitDate?.slice(0, 10) ?? '';

  const orderBundlesByEntity = useMemo(() => {
    const map = new Map<string, OrderBundle[]>();
    const list = (orderBundles ?? []).filter(Boolean);
    list.forEach((bundle) => {
      const started = bundle.started?.slice(0, 10);
      if (orderVisitDate && started && started !== orderVisitDate) return;
      const entity = bundle.entity?.trim() || 'unknown';
      const current = map.get(entity) ?? [];
      current.push(bundle);
      map.set(entity, current);
    });
    return map;
  }, [orderBundles, orderVisitDate]);

  const orderGroupSpecs = useMemo(
    () => [
      { key: 'prescription', label: '処方', entities: ['medOrder'], defaultEditorEntity: 'medOrder' },
      { key: 'injection', label: '注射', entities: ['injectionOrder'], defaultEditorEntity: 'injectionOrder' },
      { key: 'treatment', label: '処置', entities: ['treatmentOrder', 'generalOrder', 'surgeryOrder', 'otherOrder'], defaultEditorEntity: 'treatmentOrder' },
      { key: 'test', label: '検査', entities: ['testOrder', 'physiologyOrder', 'bacteriaOrder', 'radiologyOrder'], defaultEditorEntity: 'testOrder' },
      { key: 'charge', label: '算定', entities: ['baseChargeOrder', 'instractionChargeOrder'], defaultEditorEntity: 'baseChargeOrder' },
    ],
    [],
  );

  const orderGroups = useMemo(() => {
    return orderGroupSpecs.map((spec) => {
      const bundles = spec.entities.flatMap((entity) => orderBundlesByEntity.get(entity) ?? []);
      const entityCounts = spec.entities
        .map((entity) => ({ entity, count: (orderBundlesByEntity.get(entity) ?? []).length }))
        .filter((x) => x.count > 0);
      return { ...spec, bundles, entityCounts };
    });
  }, [orderBundlesByEntity, orderGroupSpecs]);
  const hasOrderBundles = orderGroups.some((group) => group.bundles.length > 0);

  const historySignature = useMemo(
    () => history.map((entry) => entry.id ?? entry.authoredAt ?? '').join('|'),
    [history],
  );

  useEffect(() => {
    setDraft(buildSoapDraftFromHistory(history));
    setSelectedTemplate({});
    setPendingTemplate({});
    setFeedback(null);
    setSubjectivesOpen(false);
  }, [historySignature]);

  useEffect(() => {
    onDraftSnapshot?.(draft);
  }, [draft, onDraftSnapshot]);

  useEffect(() => {
    if (!replaceDraftRequest) return;
    if (readOnly) {
      setFeedback(readOnlyReason ?? '読み取り専用のためセット反映できません。');
      return;
    }
    setDraft(replaceDraftRequest.draft);
    setFeedback(replaceDraftRequest.note ?? 'SOAPドラフトをオーダーセットから反映しました。');
    onDraftDirtyChange?.({
      dirty: true,
      patientId: meta.patientId,
      appointmentId: meta.appointmentId,
      receptionId: meta.receptionId,
      visitDate: meta.visitDate,
      dirtySources: ['soap'],
    });
  }, [
    meta.appointmentId,
    meta.patientId,
    meta.receptionId,
    meta.visitDate,
    onDraftDirtyChange,
    readOnly,
    readOnlyReason,
    replaceDraftRequest?.token,
  ]);

  useEffect(() => {
    if (!applyDraftPatch) return;
    if (readOnly) {
      setFeedback(readOnlyReason ?? '読み取り専用のため転記できません。');
      return;
    }
    setDraft((prev) => ({ ...prev, [applyDraftPatch.section]: applyDraftPatch.body }));
    setFeedback(applyDraftPatch.note ?? `${SOAP_SECTION_LABELS[applyDraftPatch.section]} を転記しました。`);
    onDraftDirtyChange?.({
      dirty: true,
      patientId: meta.patientId,
      appointmentId: meta.appointmentId,
      receptionId: meta.receptionId,
      visitDate: meta.visitDate,
      dirtySources: ['soap'],
    });
  }, [applyDraftPatch?.token, readOnly, readOnlyReason, onDraftDirtyChange, meta.patientId, meta.appointmentId, meta.receptionId, meta.visitDate]);

  useEffect(() => {
    if (!isRevisionHistoryEnabled) setRevisionDrawerOpen(false);
  }, [isRevisionHistoryEnabled]);

  useEffect(() => {
    if (!attachmentInsert) return;
    if (readOnly) {
      setFeedback(readOnlyReason ?? '読み取り専用のため挿入できません。');
      onAttachmentInserted?.();
      return;
    }
    const targetSection = attachmentInsert.section ?? 'free';
    setDraft((prev) => ({
      ...prev,
      [targetSection]: appendImageAttachmentPlaceholders(prev[targetSection], attachmentInsert.attachment),
    }));
    setFeedback(`画像リンクを ${SOAP_SECTION_LABELS[targetSection]} に挿入しました。`);
    onDraftDirtyChange?.({
      dirty: true,
      patientId: meta.patientId,
      appointmentId: meta.appointmentId,
      receptionId: meta.receptionId,
      visitDate: meta.visitDate,
      dirtySources: ['soap'],
    });
    onAttachmentInserted?.();
  }, [
    attachmentInsert?.token,
    attachmentInsert,
    meta.appointmentId,
    meta.patientId,
    meta.receptionId,
    meta.visitDate,
    onAttachmentInserted,
    onDraftDirtyChange,
    readOnly,
    readOnlyReason,
  ]);

  const updateDraft = useCallback(
    (section: SoapSectionKey, value: string) => {
      setDraft((prev) => ({ ...prev, [section]: value }));
      setFeedback(null);
      onDraftDirtyChange?.({
        dirty: true,
        patientId: meta.patientId,
        appointmentId: meta.appointmentId,
        receptionId: meta.receptionId,
        visitDate: meta.visitDate,
        dirtySources: ['soap'],
      });
    },
    [meta.appointmentId, meta.patientId, meta.receptionId, meta.visitDate, onDraftDirtyChange],
  );

  const handleTemplateInsert = useCallback(
    (section: SoapSectionKey) => {
      const templateId = selectedTemplate[section];
      if (!templateId) {
        setFeedback('テンプレートを選択してください。');
        return;
      }
      const template = SOAP_TEMPLATES.find((item) => item.id === templateId);
      const snippet = template?.sections?.[section];
      if (!snippet) {
        setFeedback('テンプレート本文が見つかりません。');
        return;
      }
      setDraft((prev) => {
        const current = prev[section];
        const next = current ? `${current}\n${snippet}` : snippet;
        return { ...prev, [section]: next };
      });
      setPendingTemplate((prev) => ({ ...prev, [section]: templateId }));
      setSelectedTemplate((prev) => ({ ...prev, [section]: '' }));
      const authoredAt = new Date().toISOString();
      recordChartsAuditEvent({
        action: 'SOAP_TEMPLATE_APPLY',
        outcome: 'success',
        subject: 'chart-soap-template',
        actor: resolveAuthorLabel(author),
        patientId: meta.patientId,
        appointmentId: meta.appointmentId,
        runId: meta.runId,
        cacheHit: meta.cacheHit,
        missingMaster: meta.missingMaster,
        fallbackUsed: meta.fallbackUsed,
        dataSourceTransition: meta.dataSourceTransition,
        note: `${SOAP_SECTION_LABELS[section]} テンプレ挿入`,
        details: {
          soapSection: section,
          templateId,
          authoredAt,
          authorRole: author.role,
          authorName: resolveAuthorLabel(author),
          receptionId: meta.receptionId,
          visitDate: meta.visitDate,
          soapLength: snippet.length,
        },
      });
      setFeedback(`テンプレート「${template?.label ?? templateId}」を挿入しました。`);
      onDraftDirtyChange?.({
        dirty: true,
        patientId: meta.patientId,
        appointmentId: meta.appointmentId,
        receptionId: meta.receptionId,
        visitDate: meta.visitDate,
        dirtySources: ['soap'],
      });
    },
    [author, meta.appointmentId, meta.cacheHit, meta.dataSourceTransition, meta.fallbackUsed, meta.missingMaster, meta.patientId, meta.receptionId, meta.runId, meta.visitDate, onDraftDirtyChange, selectedTemplate],
  );

  const handleSave = useCallback(async () => {
    const authoredAt = new Date().toISOString();
    const entries: SoapEntry[] = [];
    const emptyClears: SoapSectionKey[] = [];
    SOAP_SECTIONS.forEach((section) => {
      const bodyRaw = draft[section] ?? '';
      const body = bodyRaw.trim();
      const prior = latestBySection.get(section);
      const priorBody = (prior?.body ?? '').trim();

      if (!body) {
        if (priorBody.length > 0) {
          emptyClears.push(section);
        }
        return;
      }

      if (prior && body === priorBody && !pendingTemplate[section]) return;

      const action = prior ? 'update' : 'save';
      const templateId = pendingTemplate[section] ?? prior?.templateId ?? null;
      const authorLabel = resolveAuthorLabel(author);
      const soapLength = body.length;
      const entry: SoapEntry = {
        id: buildSoapEntryId(section, authoredAt),
        section,
        body,
        templateId: templateId ?? undefined,
        authoredAt,
        authorRole: author.role,
        authorName: authorLabel,
        action,
        patientId: meta.patientId,
        appointmentId: meta.appointmentId,
        receptionId: meta.receptionId,
        visitDate: meta.visitDate,
      };
      entries.push(entry);

      recordChartsAuditEvent({
        action: action === 'save' ? 'SOAP_NOTE_SAVE' : 'SOAP_NOTE_UPDATE',
        outcome: 'success',
        subject: 'chart-soap-note',
        actor: authorLabel,
        patientId: meta.patientId,
        appointmentId: meta.appointmentId,
        runId: meta.runId,
        cacheHit: meta.cacheHit,
        missingMaster: meta.missingMaster,
        fallbackUsed: meta.fallbackUsed,
        dataSourceTransition: meta.dataSourceTransition,
        note: `${SOAP_SECTION_LABELS[section]} 記載`,
        details: {
          soapSection: section,
          authoredAt,
          authorRole: author.role,
          authorName: authorLabel,
          templateId,
          soapLength,
          receptionId: meta.receptionId,
          visitDate: meta.visitDate,
        },
      });
    });

    if (entries.length === 0) {
      if (emptyClears.length > 0) {
        const targets = emptyClears.map((section) => SOAP_SECTION_LABELS[section]).join(', ');
        setFeedback(`空欄へのクリアは保存できません（未対応）: ${targets}`);
        return;
      }
      setFeedback('変更がないため保存できません。');
      return;
    }

    onAppendHistory?.(entries);
    onAuditLogged?.();
    setPendingTemplate({});
    if (emptyClears.length > 0) {
      const targets = emptyClears.map((section) => SOAP_SECTION_LABELS[section]).join(', ');
      setFeedback(`${entries.length} セクションを保存しました（空欄クリア未対応: ${targets}）`);
    } else {
      setFeedback(`${entries.length} セクションを保存しました。`);
    }
    onDraftDirtyChange?.({
      dirty: emptyClears.length > 0,
      patientId: meta.patientId,
      appointmentId: meta.appointmentId,
      receptionId: meta.receptionId,
      visitDate: meta.visitDate,
      dirtySources: emptyClears.length > 0 ? (['soap'] satisfies DraftDirtySource[]) : [],
    });

    if (!meta.patientId) {
      setFeedback((prev) => {
        const suffix = '患者未選択のため server 保存をスキップしました。';
        if (!prev) return suffix;
        return `${prev} / ${suffix}`;
      });
      return;
    }

    const performDate = meta.visitDate ?? new Date().toISOString().slice(0, 10);
    const requests = entries.reduce<ChartSubjectiveEntryRequest[]>((acc, entry) => {
      const soapCategory = resolveSoapCategory(entry.section);
      if (!soapCategory) return acc;
      acc.push({
        patientId: meta.patientId as string,
        performDate,
        soapCategory,
        body: entry.body,
      });
      return acc;
    }, []);

    if (requests.length === 0) {
      setFeedback('SOAP server 保存対象がありません。');
      return;
    }

    const results = await Promise.all(
      requests.map(async (payload) => {
        try {
          return await postChartSubjectiveEntry(payload);
        } catch (error) {
          return { ok: false, status: 0, apiResultMessage: String(error) };
        }
      }),
    );
    const failures = results.filter((result) => !result.ok || (result.apiResult && result.apiResult !== '00'));
    if (failures.length > 0) {
      const detail = failures[0]?.apiResultMessage ?? failures[0]?.apiResult ?? 'unknown';
      setFeedback(`SOAP server 保存に失敗/警告: ${detail}`);
      return;
    }
    setFeedback(`SOAP server 保存 OK（${results.length} 件）`);
  }, [
    author,
    draft,
    latestBySection,
    meta.appointmentId,
    meta.cacheHit,
    meta.dataSourceTransition,
    meta.fallbackUsed,
    meta.missingMaster,
    meta.patientId,
    meta.receptionId,
    meta.runId,
    meta.visitDate,
    onAppendHistory,
    onAuditLogged,
    onDraftDirtyChange,
    pendingTemplate,
  ]);

  const handleClear = useCallback(() => {
    setDraft({
      free: '',
      subjective: '',
      objective: '',
      assessment: '',
      plan: '',
    });
    setPendingTemplate({});
    setFeedback('入力内容をクリアしました。');
    onDraftDirtyChange?.({
      dirty: true,
      patientId: meta.patientId,
      appointmentId: meta.appointmentId,
      receptionId: meta.receptionId,
      visitDate: meta.visitDate,
      dirtySources: ['soap'],
    });
  }, [meta.appointmentId, meta.patientId, meta.receptionId, meta.visitDate, onDraftDirtyChange]);

  const handleClearHistory = useCallback(() => {
    if (!onClearHistory) return;
    const confirmed = typeof window === 'undefined' ? true : window.confirm('SOAP履歴をクリアしますか？');
    if (!confirmed) return;
    onClearHistory();
    setDraft({
      free: '',
      subjective: '',
      objective: '',
      assessment: '',
      plan: '',
    });
    setPendingTemplate({});
    setSelectedTemplate({});
    setFeedback('SOAP履歴をクリアしました。');
  }, [onClearHistory]);

  const cycleViewMode = useCallback(() => {
    setViewMode((prev) => {
      switch (prev) {
        case 'both':
          return 'soap';
        case 'soap':
          return 'free';
        default:
          return 'both';
      }
    });
  }, []);

  const viewModeLabel = useMemo(() => {
    switch (viewMode) {
      case 'soap':
        return 'SOAPのみ';
      case 'free':
        return 'FREEのみ';
      default:
        return '両方';
    }
  }, [viewMode]);

  const resolveEntryActor = (entry?: SoapEntry | null): string => {
    if (!entry) return '—';
    const raw = entry.authorName ?? entry.authorRole ?? '';
    const normalized = raw.trim();
    return normalized.length > 0 ? normalized : '不明';
  };

  const authoredFirst = authoredMeta.first;
  const authoredLast = authoredMeta.last;
  const authoredSummary =
    authoredFirst && authoredLast
      ? `初回: ${formatSoapAuthoredAt(authoredFirst.authoredAt)} / ${resolveEntryActor(authoredFirst)}  最終: ${formatSoapAuthoredAt(authoredLast.authoredAt)} / ${resolveEntryActor(authoredLast)}`
      : null;

  const freeHistoryEntries = historyBySection.get('free') ?? [];

  useEffect(() => {
    if (!historyView) return;
    setSubjectivesOpen(false);
  }, [historyView]);

  type HistoryDiff = { section: SoapSectionKey; removed: string[]; added: string[] };
  type HistoryStep = { key: string; authoredAt: string; actor: string; actionLabel: string; diffs: HistoryDiff[] };

  const historyTimeline = useMemo<HistoryStep[]>(() => {
    if (!history || history.length === 0) return [];
    const sorted = history
      .slice()
      .sort((a, b) => (a.authoredAt ?? '').localeCompare(b.authoredAt ?? ''))
      .filter(Boolean);

    const groupEntries = new Map<string, SoapEntry[]>();
    sorted.forEach((entry) => {
      const key = entry.authoredAt?.trim() ? entry.authoredAt.trim() : `unknown:${entry.section}:${entry.id ?? ''}`;
      const list = groupEntries.get(key) ?? [];
      list.push(entry);
      groupEntries.set(key, list);
    });

    const snapshot: Record<SoapSectionKey, string> = {
      free: '',
      subjective: '',
      objective: '',
      assessment: '',
      plan: '',
    };

    const diffLines = (before: string, after: string): { removed: string[]; added: string[] } => {
      const normalizeLine = (line: string) => line.trimEnd();
      const beforeLines = before
        .split('\n')
        .map(normalizeLine)
        .filter((line) => line.trim().length > 0);
      const afterLines = after
        .split('\n')
        .map(normalizeLine)
        .filter((line) => line.trim().length > 0);
      const afterSet = new Set(afterLines);
      const beforeSet = new Set(beforeLines);
      const removed = beforeLines.filter((line) => !afterSet.has(line));
      const added = afterLines.filter((line) => !beforeSet.has(line));
      return {
        removed: removed.slice(0, 12),
        added: added.slice(0, 12),
      };
    };

    const steps: HistoryStep[] = [];

    Array.from(groupEntries.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .forEach(([authoredAt, entries], index) => {
        const before = { ...snapshot };
        entries.forEach((entry) => {
          snapshot[entry.section] = entry.body ?? '';
        });

        const actorRaw = entries[0]?.authorName ?? entries[0]?.authorRole ?? '';
        const actor = actorRaw.trim() ? actorRaw.trim() : '不明';
        const actionLabel = (() => {
          const actions = new Set(entries.map((e) => e.action));
          if (actions.has('update')) return '更新';
          if (actions.has('save')) return '保存';
          return actions.size > 0 ? Array.from(actions.values()).join(',') : '—';
        })();
        const diffs: HistoryDiff[] = [];
        entries.forEach((entry) => {
          const { removed, added } = diffLines(before[entry.section] ?? '', snapshot[entry.section] ?? '');
          if (removed.length === 0 && added.length === 0) return;
          diffs.push({ section: entry.section, removed, added });
        });

        steps.push({
          key: `${authoredAt}-${index}`,
          authoredAt,
          actor,
          actionLabel,
          diffs,
        });
      });

    // Newest first.
    steps.reverse();
    return steps;
  }, [history]);

  return (
    <section className="soap-note" aria-label="SOAP 記載" data-run-id={meta.runId} data-view-mode={viewMode}>
      <header className="soap-note__header">
        <div>
          <h2>SOAP 記載</h2>
          <p className="soap-note__subtitle">
            記載者: {resolveAuthorLabel(author)} ／ role: {author.role} ／ 受付: {meta.receptionId ?? '—'}
          </p>
          {authoredSummary ? <p className="soap-note__subtitle soap-note__subtitle--meta">{authoredSummary}</p> : null}
        </div>
        <div className="soap-note__actions">
          <button
            type="button"
            onClick={() => setHistoryView((prev) => !prev)}
            className="soap-note__ghost"
            title={historyView ? 'SOAP入力へ戻ります。' : '訂正履歴を表示します（取り消し線で差分を可視化）。'}
          >
            {historyView ? '編集へ戻る' : '履歴表示'}
          </button>
          {!historyView ? (
          <button
            type="button"
            onClick={cycleViewMode}
            className="soap-note__ghost"
            title="表示モードを切り替えます（SOAPのみ / FREEのみ / 両方）"
          >
            表示:{viewModeLabel}
          </button>
          ) : null}
          {isRevisionHistoryEnabled ? (
            <button
              type="button"
              onClick={() => setRevisionDrawerOpen(true)}
              className="soap-note__ghost"
              aria-haspopup="dialog"
              aria-expanded={revisionDrawerOpen}
            >
              版履歴
            </button>
          ) : null}
          {!historyView ? (
          <button
            type="button"
            onClick={handleSave}
            disabled={readOnly}
            className="soap-note__primary"
            title={readOnly ? readOnlyReason ?? '読み取り専用のため保存できません。' : undefined}
          >
            {history.length === 0 ? '保存' : '更新'}
          </button>
          ) : null}
          {!historyView ? (
          <button
            type="button"
            onClick={handleClear}
            disabled={readOnly}
            className="soap-note__ghost"
            title={readOnly ? readOnlyReason ?? '読み取り専用のためクリアできません。' : undefined}
          >
            クリア
          </button>
          ) : null}
          {!historyView && onClearHistory ? (
            <button type="button" onClick={handleClearHistory} className="soap-note__ghost">
              履歴クリア
            </button>
          ) : null}
        </div>
      </header>
      {isRevisionHistoryEnabled ? (
        <RevisionHistoryDrawer
          open={revisionDrawerOpen}
          onClose={() => setRevisionDrawerOpen(false)}
          meta={{
            patientId: meta.patientId,
            appointmentId: meta.appointmentId,
            receptionId: meta.receptionId,
            visitDate: meta.visitDate,
          }}
          soapHistory={history}
        />
      ) : null}
      {readOnly ? (
        <p className="soap-note__guard">読み取り専用: {readOnlyReason ?? '編集はロック中です。'}</p>
      ) : null}
      {feedback ? <p className="soap-note__feedback" role="status">{feedback}</p> : null}
      <div className="soap-note__body">
        <div className="soap-note__editor">
          {historyView ? (
            <div className="soap-note__history-mode" aria-label="訂正履歴">
              <p className="soap-note__history-hint">
                訂正履歴を差分表示します（この端末の SOAP 履歴）。編集は「編集へ戻る」で切り替えます。
              </p>
              {historyTimeline.length === 0 ? (
                <p className="soap-note__history-empty" role="status">
                  履歴がありません。
                </p>
              ) : (
                <ol className="soap-note__history-timeline" aria-label="訂正履歴（新しい順）">
                  {historyTimeline.map((step) => (
                    <li key={step.key} className="soap-note__history-step">
                      <div className="soap-note__history-step-head">
                        <strong>{formatSoapAuthoredAt(step.authoredAt)}</strong>
                        <span>{step.actor}</span>
                        <span>{step.actionLabel}</span>
                      </div>
                      {step.diffs.length === 0 ? (
                        <p className="soap-note__history-nochange">差分はありません。</p>
                      ) : (
                        <div className="soap-note__history-diffs">
                          {step.diffs.map((diff) => (
                            <div key={`${step.key}-${diff.section}`} className="soap-note__history-diff" data-section={diff.section}>
                              <div className="soap-note__history-diff-title">{SOAP_SECTION_LABELS[diff.section]}</div>
                              {diff.removed.length > 0 ? (
                                <ul className="soap-note__history-lines soap-note__history-lines--removed" aria-label="削除">
                                  {diff.removed.map((line, idx) => (
                                    <li key={`${step.key}-${diff.section}-rm-${idx}`}>
                                      <del>{line}</del>
                                    </li>
                                  ))}
                                </ul>
                              ) : null}
                              {diff.added.length > 0 ? (
                                <ul className="soap-note__history-lines soap-note__history-lines--added" aria-label="追加">
                                  {diff.added.map((line, idx) => (
                                    <li key={`${step.key}-${diff.section}-add-${idx}`}>
                                      <span className="soap-note__history-added">+ {line}</span>
                                    </li>
                                  ))}
                                </ul>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </div>
          ) : (
            <>
              <div className="soap-note__grid">
                {visibleSections.map((section) => {
                  const latest = latestBySection.get(section);
                  const first = firstBySection.get(section);
                  const templateOptions = filterTemplatesForSection(section);
                  const templateLabel = latest?.templateId ? `template=${latest.templateId}` : 'templateなし';
                  const hasOrigin = Boolean(first && latest && first.id !== latest.id);
                  const textareaRows = (() => {
                    if (section === 'free') return viewMode === 'free' ? 6 : 4;
                    return viewMode === 'soap' ? 4 : 2;
                  })();
                  return (
                    <article key={section} className="soap-note__section" data-section={section}>
                      <div className="soap-note__section-header">
                        <strong>{SOAP_SECTION_LABELS[section]}</strong>
                        {latest ? (
                          <>
                            <span>
                              最終: {formatSoapAuthoredAt(latest.authoredAt)} ／ {resolveEntryActor(latest)} ／ {templateLabel}
                            </span>
                            {hasOrigin && first ? (
                              <span>
                                初回: {formatSoapAuthoredAt(first.authoredAt)} ／ {resolveEntryActor(first)}
                              </span>
                            ) : null}
                          </>
                        ) : (
                          <span>記載履歴なし</span>
                        )}
                      </div>
                      <textarea
                        id={`soap-note-${section}`}
                        name={`soapNote-${section}`}
                        value={draft[section]}
                        onChange={(event) => updateDraft(section, event.target.value)}
                        rows={textareaRows}
                        placeholder={`${SOAP_SECTION_LABELS[section]} を記載してください。`}
                        readOnly={readOnly}
                        aria-readonly={readOnly}
                      />
                      <div className="soap-note__section-actions">
                        <label>
                          テンプレ
                          <select
                            id={`soap-note-template-${section}`}
                            name={`soapNoteTemplate-${section}`}
                            value={selectedTemplate[section] ?? ''}
                            onChange={(event) => setSelectedTemplate((prev) => ({ ...prev, [section]: event.target.value }))}
                            disabled={readOnly}
                            title={readOnly ? readOnlyReason ?? '読み取り専用のため選択できません。' : undefined}
                          >
                            <option value="">選択してください</option>
                            {templateOptions.map((template) => (
                              <option key={template.id} value={template.id}>
                                {template.label}
                              </option>
                            ))}
                          </select>
                        </label>
                        <button
                          type="button"
                          onClick={() => handleTemplateInsert(section)}
                          className="soap-note__ghost"
                          disabled={readOnly}
                          title={readOnly ? readOnlyReason ?? '読み取り専用のため挿入できません。' : undefined}
                        >
                          テンプレ挿入
                        </button>
                        {section === 'free' ? (
                          <button
                            type="button"
                            onClick={() => updateDraft('free', '')}
                            className="soap-note__ghost"
                            disabled={readOnly}
                            title={readOnly ? readOnlyReason ?? '読み取り専用のため操作できません。' : 'Free を新規カードとして開始します'}
                          >
                            新規カード
                          </button>
                        ) : null}
                        {pendingTemplate[section] ? <span className="soap-note__template-tag">挿入中: {pendingTemplate[section]}</span> : null}
                      </div>
                      {section === 'free' && freeHistoryEntries.length > 0 ? (
                        <details className="soap-note__history" aria-label="Free 履歴">
                          <summary className="soap-note__history-summary">Free履歴（{freeHistoryEntries.length}）</summary>
                          <div className="soap-note__history-list" role="list">
                            {freeHistoryEntries
                              .slice()
                              .reverse()
                              .map((entry) => (
                                <div key={entry.id} className="soap-note__history-card" role="listitem">
                                  <div className="soap-note__history-meta">
                                    {formatSoapAuthoredAt(entry.authoredAt)} ／ {resolveEntryActor(entry)} ／ {entry.action}
                                  </div>
                                  <div className="soap-note__history-body">{entry.body}</div>
                                </div>
                              ))}
                          </div>
                        </details>
                      ) : null}
                    </article>
                  );
                })}
              </div>
              <details
                className="soap-note__subjectives-fold"
                open={subjectivesOpen}
                onToggle={(event) => {
                  setSubjectivesOpen(event.currentTarget.open);
                }}
              >
                <summary className="soap-note__subjectives-summary">症状詳記（ORCA）</summary>
                {subjectivesOpen ? (
                  <div className="soap-note__subjectives-content">
                    <SubjectivesPanel
                      patientId={meta.patientId}
                      visitDate={meta.visitDate}
                      runId={meta.runId}
                      readOnly={readOnly}
                      readOnlyReason={readOnlyReason}
                      suggestedText={draft.subjective}
                    />
                  </div>
                ) : null}
              </details>
            </>
          )}
        </div>
        <aside
          className="soap-note__paper"
          aria-label="オーダー情報"
          data-loading={orderBundlesLoading || rpHistoryLoading ? '1' : '0'}
          data-error={orderBundlesError || rpHistoryError ? '1' : '0'}
        >
          <header className="soap-note__paper-header">
            <div>
              <strong>オーダー情報</strong>
              <span className="soap-note__paper-meta">
                診療日:{orderVisitDate || '—'}
                {hasOrderBundles ? ` / bundles:${orderGroups.reduce((acc, group) => acc + group.bundles.length, 0)}` : ''}
              </span>
            </div>
          </header>

          {orderBundlesLoading ? (
            <p className="soap-note__paper-empty" role="status">
              オーダー情報を取得しています...
            </p>
          ) : orderBundlesError ? (
            <p className="soap-note__paper-empty" role="status">
              オーダー情報の取得に失敗しました: {orderBundlesError}
            </p>
          ) : hasOrderBundles ? (
            <div className="soap-note__order-groups" aria-label="当日オーダー一覧">
              {orderGroups
                .filter((group) => group.bundles.length > 0)
                .map((group) => {
                  const canEdit = Boolean(meta.patientId && (group.key === 'prescription' ? onOpenPrescriptionEditor : onOpenOrderEditor));
                  const editLabel = group.key === 'prescription' ? '処方編集' : `${group.label}編集`;
                  const handleEdit = () => {
                    if (!meta.patientId) return;
                    if (group.key === 'prescription') {
                      onOpenPrescriptionEditor?.();
                      return;
                    }
                    onOpenOrderEditor?.(group.defaultEditorEntity);
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
                  const submeta = group.entityCounts
                    .map((x) => `${resolveEntityLabel(x.entity)}:${x.count}`)
                    .slice(0, 6)
                    .join(' / ');
                  return (
                    <section key={group.key} className="soap-note__order-group" data-group={group.key}>
                      <header className="soap-note__order-group-header">
                        <div>
                          <strong>{group.label}</strong>
                          <span className="soap-note__order-group-meta">{group.bundles.length}件</span>
                        </div>
                        <button
                          type="button"
                          className="soap-note__paper-action"
                          onClick={handleEdit}
                          disabled={!canEdit}
                          title={!meta.patientId ? '患者未選択のため開けません。' : !canEdit ? '編集UIが未接続です。' : undefined}
                        >
                          {editLabel}
                        </button>
                      </header>
                      {submeta && group.entityCounts.length > 1 ? <p className="soap-note__order-group-submeta">内訳: {submeta}</p> : null}
                      <ul className="soap-note__order-list" aria-label={`${group.label}オーダー`}>
                        {group.bundles.slice(0, 8).map((bundle, index) => {
                          const items = (bundle.items ?? []).filter(Boolean);
                          const itemLabels = items
                            .map((item) => {
                              const name = item.name?.trim();
                              if (!name) return null;
                              const quantity = item.quantity?.trim();
                              const unit = item.unit?.trim();
                              const qty = [quantity, unit].filter(Boolean).join('');
                              return qty ? `${name}(${qty})` : name;
                            })
                            .filter((v): v is string => Boolean(v));
                          const itemInline = itemLabels.slice(0, 4).join(' / ');
                          const itemMore = itemLabels.length > 4 ? ` 他${itemLabels.length - 4}` : '';
                          const itemSummary = itemInline ? `${itemInline}${itemMore}` : '項目なし';
                          const title = itemLabels.length > 0 ? itemLabels.join(' / ') : undefined;
                          const bundleName = bundle.bundleName?.trim() || bundle.className?.trim() || '名称未設定';
                          return (
                            <li key={`${group.key}-${bundle.documentId ?? 'doc'}-${bundle.moduleId ?? 'mod'}-${index}`} className="soap-note__order-item">
                              <strong className="soap-note__order-bundle">{bundleName}</strong>
                              <span className="soap-note__order-items" title={title}>
                                {itemSummary}
                              </span>
                            </li>
                          );
                        })}
                      </ul>
                      {group.bundles.length > 8 ? <p className="soap-note__paper-empty">他 {group.bundles.length - 8} 件</p> : null}
                    </section>
                  );
                })}
            </div>
          ) : (
            <p className="soap-note__paper-empty" role="status">
              当日のオーダーはありません。
            </p>
          )}

          {rpHistoryLoading || rpHistoryError || prescriptionDrugs.length > 0 || prescriptionMemo ? (
            <div className="soap-note__rx-history" aria-label="処方履歴（直近）">
              <header className="soap-note__paper-header">
                <div>
                  <strong>処方履歴（直近）</strong>
                  <span className="soap-note__paper-meta">発行:{prescriptionIssuedDate || '—'}</span>
                </div>
                {onOpenPrescriptionEditor ? (
                  <button
                    type="button"
                    className="soap-note__paper-action"
                    onClick={onOpenPrescriptionEditor}
                    disabled={!meta.patientId}
                    title={!meta.patientId ? '患者未選択のため開けません。' : undefined}
                  >
                    処方編集
                  </button>
                ) : null}
              </header>
              {rpHistoryLoading ? (
                <p className="soap-note__paper-empty" role="status">
                  処方履歴を取得しています...
                </p>
              ) : rpHistoryError ? (
                <p className="soap-note__paper-empty" role="status">
                  処方履歴の取得に失敗しました: {rpHistoryError}
                </p>
              ) : prescriptionDrugs.length === 0 ? (
                <p className="soap-note__paper-empty" role="status">
                  直近の処方履歴はありません。
                </p>
              ) : (
                <ol className="soap-note__paper-list" aria-label="処方薬剤一覧">
                  {prescriptionDrugs.slice(0, 40).map((drug, index) => {
                    const name = drug.name?.trim() || '薬剤名不明';
                    const dose = drug.dose?.trim();
                    const amount = drug.amount?.trim();
                    const usage = drug.usage?.trim();
                    const days = drug.days?.trim();
                    const line = [dose, amount].filter(Boolean).join(' ');
                    const metaLine = [usage, days ? `日数:${days}` : null].filter(Boolean).join(' / ');
                    return (
                      <li key={`${name}-${index}`} className="soap-note__paper-item">
                        <strong className="soap-note__paper-drug">{name}</strong>
                        {line ? <span className="soap-note__paper-dose">{line}</span> : null}
                        {metaLine ? <span className="soap-note__paper-sub">{metaLine}</span> : null}
                      </li>
                    );
                  })}
                </ol>
              )}
              {prescriptionMemo ? <p className="soap-note__paper-memo">メモ: {prescriptionMemo}</p> : null}
            </div>
          ) : null}
        </aside>
      </div>
    </section>
  );
}
