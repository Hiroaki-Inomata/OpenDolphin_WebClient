import type { SystemDailyResponse, SystemInfoResponse } from '../api';

import { AdminCard } from '../components/AdminCard';
import { AdminCodeBlock } from '../components/AdminCodeBlock';
import { AdminField } from '../components/AdminField';
import { AdminStatusPill } from '../components/AdminStatusPill';

type SystemHealthCardProps = {
  isSystemAdmin: boolean;
  guardDetailsId?: string;
  overallTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  overallLabel: string;
  infoTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  infoLabel: string;
  dailyTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  dailyLabel: string;
  systemInfoResult: SystemInfoResponse | null;
  systemDailyResult: SystemDailyResponse | null;
  systemBaseDate: string;
  onSystemBaseDateChange: (value: string) => void;
  onHealthCheck: () => void;
  healthCheckPending: boolean;
  abnormalSummary: string;
};

const formatDateTime = (date?: string, time?: string) => {
  if (!date && !time) return '―';
  if (!time) return date ?? '―';
  if (!date) return time ?? '―';
  return `${date} ${time}`;
};

export function SystemHealthCard({
  isSystemAdmin,
  guardDetailsId,
  overallTone,
  overallLabel,
  infoTone,
  infoLabel,
  dailyTone,
  dailyLabel,
  systemInfoResult,
  systemDailyResult,
  systemBaseDate,
  onSystemBaseDateChange,
  onHealthCheck,
  healthCheckPending,
  abnormalSummary,
}: SystemHealthCardProps) {
  const readOnly = !isSystemAdmin;

  return (
    <AdminCard
      title="システムヘルスチェック"
      description="systeminfv2/system01dailyv2 を統合表示し、異常要約を提示します。"
      status={<AdminStatusPill status={overallTone} value={`総合: ${overallLabel}`} />}
      actions={
        <button
          type="button"
          className="admin-button admin-button--secondary"
          onClick={onHealthCheck}
          disabled={healthCheckPending || readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        >
          ヘルスチェック実行
        </button>
      }
    >
      <div className="admin-inline-meta">
        <AdminStatusPill status={infoTone} value={`systeminfv2: ${infoLabel}`} />
        <AdminStatusPill status={dailyTone} value={`system01dailyv2: ${dailyLabel}`} />
      </div>
      <p className="admin-note">異常要約: {abnormalSummary}</p>

      <div className="admin-summary">
        <div className="admin-summary__row">
          <span className="admin-summary__label">JMA Receipt</span>
          <span>{systemInfoResult?.jmaReceiptVersion ?? '―'}</span>
        </div>
        <div className="admin-summary__row">
          <span className="admin-summary__label">DB(Local/New)</span>
          <span>
            {systemInfoResult?.databaseLocalVersion ?? '―'} / {systemInfoResult?.databaseNewVersion ?? '―'}
          </span>
        </div>
        <div className="admin-summary__row">
          <span className="admin-summary__label">Master更新日</span>
          <span>{systemInfoResult?.lastUpdateDate ?? '―'}</span>
        </div>
        <div className="admin-summary__row">
          <span className="admin-summary__label">取得日時</span>
          <span>
            {formatDateTime(systemInfoResult?.informationDate, systemInfoResult?.informationTime)} /{' '}
            {formatDateTime(systemDailyResult?.informationDate, systemDailyResult?.informationTime)}
          </span>
        </div>
      </div>

      <AdminField label="system01dailyv2 Base_Date" htmlFor="system-base-date">
        <input
          id="system-base-date"
          type="date"
          value={systemBaseDate}
          onChange={(event) => onSystemBaseDateChange(event.target.value)}
          readOnly={readOnly}
          aria-readonly={readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        />
      </AdminField>

      <div className="admin-scroll admin-scroll--sticky">
        <table className="admin-table" aria-label="systeminfv2 versions">
          <thead>
            <tr>
              <th>マスタ名</th>
              <th>Local</th>
              <th>New</th>
              <th>状態</th>
              <th>推奨アクション</th>
            </tr>
          </thead>
          <tbody>
            {systemInfoResult?.versions.length ? (
              systemInfoResult.versions.map((entry, index) => {
                const isDiff =
                  entry.localVersion && entry.newVersion && entry.localVersion !== entry.newVersion;
                return (
                  <tr key={`${entry.name ?? 'master'}-${index}`} className={isDiff ? 'admin-version--diff' : undefined}>
                    <td>{entry.name ?? '―'}</td>
                    <td>{entry.localVersion ?? '―'}</td>
                    <td>{entry.newVersion ?? '―'}</td>
                    <td>{isDiff ? '更新あり' : '一致'}</td>
                    <td>{isDiff ? 'マスタ同期を実行' : '対応不要'}</td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td colSpan={5}>バージョン情報は未取得です。</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {systemInfoResult?.rawXml ? (
        <AdminCodeBlock value={systemInfoResult.rawXml} language="xml" title="systeminfv2 rawXml" collapsedByDefault />
      ) : null}
      {systemDailyResult?.rawXml ? (
        <AdminCodeBlock
          value={systemDailyResult.rawXml}
          language="xml"
          title="system01dailyv2 rawXml"
          collapsedByDefault
        />
      ) : null}
    </AdminCard>
  );
}
