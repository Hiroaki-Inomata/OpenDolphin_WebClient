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
import { postChartSubjectiveEntry } from './soap/subjectiveChartApi';
import { RevisionHistoryDrawer } from './revisions/RevisionHistoryDrawer';
import type { RpHistoryEntry } from './karteExtrasApi';

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
  onOpenPrescriptionEditor?: () => void;
  onDraftSnapshot?: (draft: SoapDraft) => void;
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
  onOpenPrescriptionEditor,
  onDraftSnapshot,
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
    const requests = entries
      .map((entry) => {
        const soapCategory = resolveSoapCategory(entry.section);
        if (!soapCategory) return null;
        return {
          patientId: meta.patientId as string,
          performDate,
          soapCategory,
          physicianCode: undefined,
          body: entry.body,
        };
      })
      .filter((entry): entry is { patientId: string; performDate: string; soapCategory: 'S' | 'O' | 'A' | 'P'; physicianCode?: string; body: string } =>
        Boolean(entry),
      );

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
            onClick={cycleViewMode}
            className="soap-note__ghost"
            title="表示モードを切り替えます（SOAPのみ / FREEのみ / 両方）"
          >
            表示:{viewModeLabel}
          </button>
          {isRevisionHistoryEnabled ? (
            <button
              type="button"
              onClick={() => setRevisionDrawerOpen(true)}
              className="soap-note__ghost"
              aria-haspopup="dialog"
              aria-expanded={String(revisionDrawerOpen)}
            >
              版履歴
            </button>
          ) : null}
          <button
            type="button"
            onClick={handleSave}
            disabled={readOnly}
            className="soap-note__primary"
            title={readOnly ? readOnlyReason ?? '読み取り専用のため保存できません。' : undefined}
          >
            {history.length === 0 ? '保存' : '更新'}
          </button>
          <button
            type="button"
            onClick={handleClear}
            disabled={readOnly}
            className="soap-note__ghost"
            title={readOnly ? readOnlyReason ?? '読み取り専用のためクリアできません。' : undefined}
          >
            クリア
          </button>
          {onClearHistory ? (
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
          <div className="soap-note__grid">
            {visibleSections.map((section) => {
              const latest = latestBySection.get(section);
              const first = firstBySection.get(section);
              const templateOptions = filterTemplatesForSection(section);
              const templateLabel = latest?.templateId ? `template=${latest.templateId}` : 'templateなし';
              const hasOrigin = Boolean(first && latest && first.id !== latest.id);
              const textareaRows = (() => {
                if (section === 'free') return viewMode === 'free' ? 8 : 5;
                return viewMode === 'soap' ? 5 : 3;
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
        </div>
        <aside className="soap-note__paper" aria-label="処方情報（直近）" data-loading={rpHistoryLoading ? '1' : '0'} data-error={rpHistoryError ? '1' : '0'}>
          <header className="soap-note__paper-header">
            <div>
              <strong>処方（2号用紙）</strong>
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
        </aside>
      </div>
    </section>
  );
}
