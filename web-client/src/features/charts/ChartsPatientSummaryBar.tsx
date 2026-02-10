import { useEffect, useMemo, useState } from 'react';

import { resolveAriaLive } from '../../libs/observability/observability';
import type { DataSourceTransition } from '../../libs/observability/types';
import { RunIdBadge } from '../shared/RunIdBadge';
import { PatientMetaRow } from '../shared/PatientMetaRow';
import type { AllergyEntry } from './karteExtrasApi';

type PatientDisplay = {
  name: string;
  kana?: string;
  sex?: string;
  age?: string;
  birthDateEra?: string;
  birthDateIso?: string;
  note?: string;
  status?: string;
  department?: string;
  physician?: string;
  insurance?: string;
  visitDate?: string;
  appointmentTime?: string;
};

type ChartsPatientSummaryBarProps = {
  patientDisplay: PatientDisplay;
  patientId?: string;
  receptionId?: string;
  appointmentId?: string;
  runId?: string;
  allergies?: AllergyEntry[];
  allergiesError?: string;
  allergiesLoading?: boolean;
  missingMaster?: boolean;
  fallbackUsed?: boolean;
  cacheHit?: boolean;
  dataSourceTransition?: DataSourceTransition;
  recordsReturned?: number;
  fetchedAt?: string;
  approvalLabel?: string;
  approvalDetail?: string;
  lockStatus?: {
    label?: string;
    detail?: string;
  };
  onToggleSafetyDetail?: (open: boolean) => void;
  onOpenPatientPanel?: () => void;
};

const normalizeValue = (value?: string): string | undefined => {
  if (!value) return undefined;
  if (value.trim() === '' || value === '—') return undefined;
  return value;
};

const formatSexAge = (sex?: string, age?: string): string | undefined => {
  const safeSex = normalizeValue(sex);
  const safeAge = normalizeValue(age);
  if (safeSex && safeAge) return `${safeSex} / ${safeAge}`;
  return safeSex ?? safeAge;
};

const formatVisitDate = (date?: string, time?: string): string => {
  const safeDate = normalizeValue(date);
  const safeTime = normalizeValue(time);
  if (safeDate && safeTime) return `${safeDate} ${safeTime}`;
  return safeDate ?? safeTime ?? '—';
};

const normalizeMemo = (value?: string): string | undefined => {
  const safe = normalizeValue(value);
  if (!safe) return undefined;
  if (safe === 'メモなし') return undefined;
  return safe;
};

const truncate = (value: string, max: number): string => {
  if (value.length <= max) return value;
  return `${value.slice(0, max)}...`;
};

const resolveSafetyTone = (params: {
  missingMaster?: boolean;
  fallbackUsed?: boolean;
  cacheHit?: boolean;
  dataSourceTransition?: DataSourceTransition;
}): 'warning' | 'info' | 'neutral' => {
  if (params.missingMaster || params.fallbackUsed) return 'warning';
  if (params.dataSourceTransition && params.dataSourceTransition !== 'server') return 'info';
  if (params.cacheHit) return 'info';
  return 'neutral';
};

const resolveSafetyLabel = (params: {
  missingMaster?: boolean;
  fallbackUsed?: boolean;
  cacheHit?: boolean;
  dataSourceTransition?: DataSourceTransition;
}): string => {
  if (params.missingMaster) return 'master未同期';
  if (params.fallbackUsed) return 'fallback';
  if (params.cacheHit) return 'cache';
  if (params.dataSourceTransition && params.dataSourceTransition !== 'server') return params.dataSourceTransition;
  return 'OK';
};

export function ChartsPatientSummaryBar({
  patientDisplay,
  patientId,
  receptionId,
  appointmentId,
  runId,
  allergies,
  allergiesError,
  allergiesLoading,
  missingMaster,
  fallbackUsed,
  cacheHit,
  dataSourceTransition,
  recordsReturned,
  fetchedAt,
  approvalLabel,
  approvalDetail,
  lockStatus,
  onToggleSafetyDetail,
  onOpenPatientPanel,
}: ChartsPatientSummaryBarProps) {
  const [detailOpen, setDetailOpen] = useState(false);
  const [allergyOpen, setAllergyOpen] = useState(false);

  useEffect(() => {
    setDetailOpen(false);
    setAllergyOpen(false);
  }, [appointmentId, patientDisplay.name, patientId, receptionId]);

  const safetyTone = resolveSafetyTone({ missingMaster, fallbackUsed, cacheHit, dataSourceTransition });
  const safetyLabel = resolveSafetyLabel({ missingMaster, fallbackUsed, cacheHit, dataSourceTransition });
  const kana = normalizeValue(patientDisplay.kana);
  const sexAge = formatSexAge(patientDisplay.sex, patientDisplay.age);
  const birthEra = normalizeValue(patientDisplay.birthDateEra);
  const birthIso = normalizeValue(patientDisplay.birthDateIso);
  const birthLabel = useMemo(() => {
    if (birthEra && birthIso) return `${birthEra} / ${birthIso}`;
    return birthEra ?? birthIso;
  }, [birthEra, birthIso]);
  const memo = normalizeMemo(patientDisplay.note);
  const memoSnippet = memo ? truncate(memo, 26) : undefined;
  const hasAllergyHint = memo ? /アレル|allerg/i.test(memo) : false;
  const allergyItems = useMemo(() => (allergies ?? []).filter(Boolean), [allergies]);
  const allergyCount = allergyItems.length;

  const detailRows = useMemo(
    () =>
      [
        birthLabel ? { label: '生年月日', value: birthLabel } : undefined,
        typeof missingMaster === 'boolean' ? { label: 'missingMaster', value: String(missingMaster) } : undefined,
        typeof fallbackUsed === 'boolean' ? { label: 'fallbackUsed', value: String(fallbackUsed) } : undefined,
        typeof cacheHit === 'boolean' ? { label: 'cacheHit', value: String(cacheHit) } : undefined,
        dataSourceTransition ? { label: 'dataSourceTransition', value: dataSourceTransition } : undefined,
        typeof recordsReturned === 'number' ? { label: 'recordsReturned', value: String(recordsReturned) } : undefined,
        fetchedAt ? { label: 'fetchedAt', value: fetchedAt } : undefined,
        runId ? { label: 'runId', value: runId } : undefined,
      ].filter((item): item is { label: string; value: string } => Boolean(item)),
    [birthLabel, cacheHit, dataSourceTransition, fallbackUsed, fetchedAt, missingMaster, recordsReturned, runId],
  );

  const toggleDetail = () => {
    setDetailOpen((prev) => {
      const next = !prev;
      if (onToggleSafetyDetail) onToggleSafetyDetail(next);
      return next;
    });
  };

  const toggleLabel = detailOpen ? '詳細を閉じる' : '詳細を開く';
  const toggleIcon = detailOpen ? 'v' : '>';
  const ariaLive = resolveAriaLive(safetyTone === 'warning' ? 'warning' : 'info');

  return (
    <div
      className="charts-patient-summary"
      data-run-id={runId}
      data-missing-master={String(missingMaster ?? false)}
      data-cache-hit={String(cacheHit ?? false)}
      data-fallback-used={String(fallbackUsed ?? false)}
      data-source-transition={dataSourceTransition}
    >
      <div className="charts-patient-summary__top">
        <div className="charts-patient-summary__identity">
          <div className="charts-patient-summary__name-row">
            <h2 className="charts-patient-summary__name">{patientDisplay.name}</h2>
            {kana ? <span className="charts-patient-summary__kana">{kana}</span> : null}
          </div>
          <div className="charts-patient-summary__facts">
            <span className="charts-patient-summary__fact">{sexAge ?? '—'}</span>
            <span className="charts-patient-summary__fact" title={birthLabel ?? undefined}>
              生:{birthIso ?? '—'}
            </span>
            <span className="charts-patient-summary__fact">ID:{patientId ?? '—'}</span>
            <span className="charts-patient-summary__fact">
              診療日:{formatVisitDate(patientDisplay.visitDate, patientDisplay.appointmentTime)}
            </span>
            <button
              type="button"
              className="charts-patient-summary__fact-button"
              aria-expanded={allergyOpen}
              aria-controls="charts-patient-summary-allergies"
              disabled={Boolean(allergiesError) || allergyCount === 0}
              title={allergiesError ? `アレルギー取得失敗: ${allergiesError}` : allergyCount === 0 ? 'アレルギーなし' : undefined}
              onClick={() => setAllergyOpen((prev) => !prev)}
            >
              {allergiesLoading ? 'アレルギー…' : `アレルギー:${allergyCount}`}
            </button>
          </div>
          {memoSnippet ? (
            <div className="charts-patient-summary__memo" data-allergy={hasAllergyHint ? '1' : '0'}>
              <span className="charts-patient-summary__memo-label">{hasAllergyHint ? 'アレルギー/メモ' : 'メモ'}</span>
              <span className="charts-patient-summary__memo-text">{memoSnippet}</span>
            </div>
          ) : null}
          <div id="charts-patient-summary-allergies" className="charts-patient-summary__allergies" hidden={!allergyOpen}>
            {allergiesError ? (
              <p className="charts-patient-summary__allergies-empty">アレルギー取得に失敗しました。</p>
            ) : allergyItems.length === 0 ? (
              <p className="charts-patient-summary__allergies-empty">アレルギーは登録されていません。</p>
            ) : (
              <ul className="charts-patient-summary__allergies-list" aria-label="アレルギー一覧">
                {allergyItems.slice(0, 6).map((item, index) => {
                  const label = item.factor?.trim() || '要因未設定';
                  const severity = item.severity?.trim();
                  const date = item.identifiedDate?.trim();
                  const memoText = item.memo?.trim();
                  return (
                    <li key={`${label}-${date ?? 'none'}-${index}`} className="charts-patient-summary__allergy-item">
                      <span className="charts-patient-summary__allergy-factor">{label}</span>
                      {severity ? <span className="charts-patient-summary__allergy-severity">{severity}</span> : null}
                      {date ? <span className="charts-patient-summary__allergy-date">{date}</span> : null}
                      {memoText ? (
                        <span className="charts-patient-summary__allergy-memo" title={memoText}>
                          {truncate(memoText, 18)}
                        </span>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            )}
            {allergyCount > 6 ? <small className="charts-patient-summary__allergies-hint">他 {allergyCount - 6} 件</small> : null}
          </div>
        </div>

        <div className="charts-patient-summary__actions">
          {onOpenPatientPanel ? (
            <button type="button" className="charts-patient-summary__action" onClick={onOpenPatientPanel}>
              患者/受付
            </button>
          ) : null}
          <div
            className={`charts-patient-summary__safety-summary charts-patient-summary__safety-summary--${safetyTone}`}
            role="status"
            aria-live={ariaLive}
          >
            <span className="charts-patient-summary__safety-label">安全</span>
            <span className="charts-patient-summary__safety-state">{safetyLabel}</span>
          </div>
          <RunIdBadge runId={runId} className="charts-patient-summary__runid" />
          <button
            type="button"
            className="charts-patient-summary__safety-toggle"
            aria-expanded={detailOpen}
            aria-controls="charts-patient-summary-detail"
            onClick={toggleDetail}
          >
            <span className="charts-patient-summary__safety-toggle-icon" aria-hidden="true">
              {toggleIcon}
            </span>
            {toggleLabel}
          </button>
        </div>
      </div>

      <div id="charts-patient-summary-detail" className="charts-patient-summary__detail" hidden={!detailOpen}>
        <div className="charts-patient-summary__detail-grid">
          <div className="charts-patient-summary__detail-block" aria-label="ID">
            <PatientMetaRow
              patientId={patientId}
              receptionId={receptionId}
              appointmentId={appointmentId}
              showLabels
              showEmpty
              separator="none"
              runId={runId}
              className="charts-patient-summary__meta-row"
              itemClassName="charts-patient-summary__meta-item"
              labelClassName="charts-patient-summary__meta-label"
              valueClassName="charts-patient-summary__meta-value"
            />
          </div>
          <div className="charts-patient-summary__detail-block" aria-label="診療情報">
            <div className="charts-patient-summary__detail-row">
              <span className="charts-patient-summary__meta-label">診療ステータス</span>
              <strong className="charts-patient-summary__meta-value">{normalizeValue(patientDisplay.status) ?? '—'}</strong>
            </div>
            <div className="charts-patient-summary__detail-row">
              <span className="charts-patient-summary__meta-label">診療科</span>
              <strong className="charts-patient-summary__meta-value">{normalizeValue(patientDisplay.department) ?? '—'}</strong>
            </div>
            <div className="charts-patient-summary__detail-row">
              <span className="charts-patient-summary__meta-label">担当者</span>
              <strong className="charts-patient-summary__meta-value">{normalizeValue(patientDisplay.physician) ?? '—'}</strong>
            </div>
            <div className="charts-patient-summary__detail-row">
              <span className="charts-patient-summary__meta-label">保険/自費</span>
              <strong className="charts-patient-summary__meta-value">{normalizeValue(patientDisplay.insurance) ?? '—'}</strong>
            </div>
          </div>
          <div className="charts-patient-summary__detail-block" aria-label="承認/ロック">
            <div className="charts-patient-summary__detail-row">
              <span className="charts-patient-summary__meta-label">承認</span>
              <strong className="charts-patient-summary__meta-value">{approvalLabel ?? '—'}</strong>
              <span className="charts-patient-summary__meta-sub">{approvalDetail ?? '—'}</span>
            </div>
            <div className="charts-patient-summary__detail-row">
              <span className="charts-patient-summary__meta-label">ロック</span>
              <strong className="charts-patient-summary__meta-value">{lockStatus?.label ?? '—'}</strong>
              <span className="charts-patient-summary__meta-sub">{lockStatus?.detail ?? '—'}</span>
            </div>
          </div>
          <div className="charts-patient-summary__detail-block" aria-label="安全表示詳細">
            <div className="charts-patient-summary__safety-detail" aria-live={resolveAriaLive('info')}>
              {detailRows.length > 0 ? (
                detailRows.map((row) => (
                  <div key={row.label} className="charts-patient-summary__safety-item">
                    <span className="charts-patient-summary__safety-item-label">{row.label}</span>
                    <span className="charts-patient-summary__safety-item-value">{row.value}</span>
                  </div>
                ))
              ) : (
                <span className="charts-patient-summary__safety-empty">詳細データなし</span>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
