import type { MasterLastUpdateResponse, MedicationModResponse } from '../api';

import { AdminCard } from '../components/AdminCard';
import { AdminCodeBlock } from '../components/AdminCodeBlock';
import { AdminField } from '../components/AdminField';
import { AdminStatusPill } from '../components/AdminStatusPill';

type OrcaMasterSyncCardProps = {
  isSystemAdmin: boolean;
  guardDetailsId?: string;
  masterStatusTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  masterStatusLabel: string;
  masterLastUpdateResult: MasterLastUpdateResponse | null;
  masterUpdateLabel: '初回取得' | '更新あり' | '更新なし';
  masterVersionDiffs: number;
  onMasterCheck: () => void;
  masterCheckPending: boolean;
  medicationSyncClass: string;
  onMedicationSyncClassChange: (value: string) => void;
  medicationSyncXml: string;
  onMedicationSyncXmlChange: (value: string) => void;
  medicationTemplateBaseDate: string;
  onMedicationTemplateBaseDateChange: (value: string) => void;
  onRegenerateMedicationTemplate: () => void;
  medicationStatusTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  medicationStatusLabel: string;
  medicationSyncResult: MedicationModResponse | null;
  onMedicationSync: () => void;
  medicationSyncPending: boolean;
};

const formatDateTime = (date?: string, time?: string) => {
  if (!date && !time) return '―';
  if (!time) return date ?? '―';
  if (!date) return time ?? '―';
  return `${date} ${time}`;
};

export function OrcaMasterSyncCard({
  isSystemAdmin,
  guardDetailsId,
  masterStatusTone,
  masterStatusLabel,
  masterLastUpdateResult,
  masterUpdateLabel,
  masterVersionDiffs,
  onMasterCheck,
  masterCheckPending,
  medicationSyncClass,
  onMedicationSyncClassChange,
  medicationSyncXml,
  onMedicationSyncXmlChange,
  medicationTemplateBaseDate,
  onMedicationTemplateBaseDateChange,
  onRegenerateMedicationTemplate,
  medicationStatusTone,
  medicationStatusLabel,
  medicationSyncResult,
  onMedicationSync,
  medicationSyncPending,
}: OrcaMasterSyncCardProps) {
  const readOnly = !isSystemAdmin;
  const hasUpdate = masterUpdateLabel === '更新あり';

  return (
    <AdminCard
      title="ORCAマスタ同期"
      description="masterlastupdatev3 の結果に応じて同期実行を制御します。"
      status={<AdminStatusPill status={masterStatusTone} value={masterStatusLabel} />}
      actions={
        <button
          type="button"
          className="admin-button admin-button--secondary"
          onClick={onMasterCheck}
          disabled={masterCheckPending || readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        >
          更新確認
        </button>
      }
    >
      <div className="admin-result admin-result--stack">
        <div className="admin-inline-meta">
          <span>更新判定:</span>
          <AdminStatusPill status={hasUpdate ? 'warn' : masterUpdateLabel === '更新なし' ? 'ok' : 'idle'} value={masterUpdateLabel} />
          <span>差分: {masterVersionDiffs}件</span>
        </div>
        <div>Api_Result: {masterLastUpdateResult?.apiResult ?? '―'}</div>
        <div>Message: {masterLastUpdateResult?.apiResultMessage ?? '―'}</div>
        <div>最終更新日: {masterLastUpdateResult?.lastUpdateDate ?? '―'}</div>
        <div>取得日時: {formatDateTime(masterLastUpdateResult?.informationDate, masterLastUpdateResult?.informationTime)}</div>
      </div>
      {masterLastUpdateResult?.rawXml ? (
        <AdminCodeBlock value={masterLastUpdateResult.rawXml} language="xml" title="masterlastupdatev3 rawXml" collapsedByDefault />
      ) : null}

      <div className="admin-divider" />

      <AdminField label="medicatonmodv2 class" htmlFor="medication-class" hint="例: 01（点数マスタ登録）">
        <input
          id="medication-class"
          type="text"
          value={medicationSyncClass}
          onChange={(event) => onMedicationSyncClassChange(event.target.value)}
          readOnly={readOnly}
          aria-readonly={readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        />
      </AdminField>
      <div className="admin-form__field-row">
        <AdminField label="テンプレBase_Date" htmlFor="medication-template-base-date">
          <input
            id="medication-template-base-date"
            type="date"
            value={medicationTemplateBaseDate}
            onChange={(event) => onMedicationTemplateBaseDateChange(event.target.value)}
            readOnly={readOnly}
            aria-readonly={readOnly}
            aria-describedby={readOnly ? guardDetailsId : undefined}
          />
        </AdminField>
      </div>
      <div className="admin-actions">
        <button type="button" className="admin-button admin-button--secondary" onClick={onRegenerateMedicationTemplate} disabled={readOnly}>
          XMLテンプレ再生成
        </button>
      </div>
      <AdminField label="medicatonmodv2 payload (XML)" htmlFor="medication-xml">
        <textarea
          id="medication-xml"
          className="admin-textarea"
          value={medicationSyncXml}
          onChange={(event) => onMedicationSyncXmlChange(event.target.value)}
          readOnly={readOnly}
          aria-readonly={readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
          rows={8}
        />
      </AdminField>

      {!hasUpdate ? (
        <p className="admin-note">更新なしのため同期は非推奨です。必要性を再確認してください。</p>
      ) : null}

      <div className="admin-actions">
        <button
          type="button"
          className={`admin-button ${hasUpdate ? 'admin-button--primary' : 'admin-button--secondary'}`}
          onClick={onMedicationSync}
          disabled={medicationSyncPending || readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        >
          点数マスタ同期
        </button>
      </div>

      {medicationSyncResult ? (
        <div className="admin-result admin-result--stack">
          <div className="admin-inline-meta">
            <AdminStatusPill status={medicationStatusTone} value={medicationStatusLabel} />
            <span>Api_Result: {medicationSyncResult.apiResult ?? '―'}</span>
          </div>
          <div>Message: {medicationSyncResult.apiResultMessage ?? '―'}</div>
          {medicationSyncResult.error ? <div className="admin-error">error: {medicationSyncResult.error}</div> : null}
        </div>
      ) : null}
      {medicationSyncResult?.rawXml ? (
        <AdminCodeBlock value={medicationSyncResult.rawXml} language="xml" title="medicatonmodv2 rawXml" collapsedByDefault />
      ) : null}
    </AdminCard>
  );
}
