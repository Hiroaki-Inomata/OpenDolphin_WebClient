import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import type { MedicalSetResponse, MedicalSetSearchPayload } from '../api';
import { AdminCard } from '../components/AdminCard';
import { AdminField } from '../components/AdminField';
import { AdminStatusPill } from '../components/AdminStatusPill';

type MedicalSetSearchCardProps = {
  isSystemAdmin: boolean;
  guardDetailsId?: string;
  query: MedicalSetSearchPayload;
  onQueryChange: (patch: Partial<MedicalSetSearchPayload>) => void;
  result: MedicalSetResponse | null;
  statusTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  statusLabel: string;
  searchPending: boolean;
  onSearch: () => void;
  chartsPath: string;
};

export function MedicalSetSearchCard({
  isSystemAdmin,
  guardDetailsId,
  query,
  onQueryChange,
  result,
  statusTone,
  statusLabel,
  searchPending,
  onSearch,
  chartsPath,
}: MedicalSetSearchCardProps) {
  const readOnly = !isSystemAdmin;
  const [selectedSetCode, setSelectedSetCode] = useState<string | undefined>(undefined);
  const hasRangeError = useMemo(() => {
    if (!query.startDate || !query.endDate) return false;
    return query.startDate > query.endDate;
  }, [query.endDate, query.startDate]);

  return (
    <AdminCard
      title="診療セット検索"
      description="ORCA項目名ベースで検索条件を統一し、Charts導線へ接続します。"
      status={<AdminStatusPill status={statusTone} value={`${statusLabel} / ${result?.entries.length ?? 0}件`} />}
      actions={
        <button
          type="button"
          className="admin-button admin-button--secondary"
          onClick={onSearch}
          disabled={searchPending || readOnly || hasRangeError}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        >
          セット検索
        </button>
      }
    >
      <div className="admin-form">
        <AdminField label="Base_Date（基準日）" htmlFor="medicalset-base-date">
          <input
            id="medicalset-base-date"
            type="date"
            value={query.baseDate}
            onChange={(event) => onQueryChange({ baseDate: event.target.value })}
            readOnly={readOnly}
            aria-readonly={readOnly}
            aria-describedby={readOnly ? guardDetailsId : undefined}
          />
        </AdminField>
        <AdminField label="Set_Code（セットコード）" htmlFor="medicalset-code">
          <input
            id="medicalset-code"
            type="text"
            value={query.setCode ?? ''}
            onChange={(event) => onQueryChange({ setCode: event.target.value })}
            readOnly={readOnly}
            aria-readonly={readOnly}
            aria-describedby={readOnly ? guardDetailsId : undefined}
          />
        </AdminField>
        <AdminField label="Set_Code_Name（セット名）" htmlFor="medicalset-name">
          <input
            id="medicalset-name"
            type="text"
            value={query.setName ?? ''}
            onChange={(event) => onQueryChange({ setName: event.target.value })}
            readOnly={readOnly}
            aria-readonly={readOnly}
            aria-describedby={readOnly ? guardDetailsId : undefined}
          />
        </AdminField>
        <div className="admin-form__field-row">
          <AdminField label="Start_Date（有効開始）" htmlFor="medicalset-start-date">
            <input
              id="medicalset-start-date"
              type="date"
              value={query.startDate ?? ''}
              onChange={(event) => onQueryChange({ startDate: event.target.value })}
              readOnly={readOnly}
              aria-readonly={readOnly}
              aria-describedby={readOnly ? guardDetailsId : undefined}
            />
          </AdminField>
          <AdminField
            label="End_Date（有効終了）"
            htmlFor="medicalset-end-date"
            error={hasRangeError ? 'Start_Date が End_Date を超えています。' : undefined}
          >
            <input
              id="medicalset-end-date"
              type="date"
              value={query.endDate ?? ''}
              onChange={(event) => onQueryChange({ endDate: event.target.value })}
              readOnly={readOnly}
              aria-readonly={readOnly}
              aria-describedby={readOnly ? guardDetailsId : undefined}
            />
          </AdminField>
        </div>
        <AdminField label="InOut（外来/入院）" htmlFor="medicalset-inout">
          <select
            id="medicalset-inout"
            value={query.inOut ?? ''}
            onChange={(event) => onQueryChange({ inOut: event.target.value })}
            disabled={readOnly}
            aria-describedby={readOnly ? guardDetailsId : undefined}
          >
            <option value="">指定なし</option>
            <option value="O">O（外来）</option>
            <option value="I">I（入院）</option>
          </select>
        </AdminField>
      </div>

      {hasRangeError ? <p className="admin-error">Start_Date &gt; End_Date のため検索できません。</p> : null}

      <div className="admin-scroll">
        <table className="admin-table" aria-label="診療セット検索結果">
          <thead>
            <tr>
              <th>Set_Code</th>
              <th>セット名</th>
              <th>期間</th>
              <th>InOut</th>
              <th>内容</th>
            </tr>
          </thead>
          <tbody>
            {result?.entries.length ? (
              result.entries.map((entry, index) => {
                const isSelected = selectedSetCode === entry.setCode;
                return (
                  <tr
                    key={`${entry.setCode ?? 'set'}-${index}`}
                    className={isSelected ? 'admin-row--selected' : undefined}
                    onClick={() => setSelectedSetCode(entry.setCode)}
                  >
                    <td>{entry.setCode ?? '―'}</td>
                    <td>{entry.setName ?? '―'}</td>
                    <td>
                      {entry.startDate ?? '―'} ~ {entry.endDate ?? '―'}
                    </td>
                    <td>{entry.inOut ?? '―'}</td>
                    <td>{entry.medicationSummary ?? '―'}</td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td colSpan={5}>検索結果はまだありません。</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="admin-actions">
        <Link
          to={selectedSetCode ? `${chartsPath}?setCode=${encodeURIComponent(selectedSetCode)}` : chartsPath}
          className="admin-link admin-link--button"
        >
          Chartsで利用{selectedSetCode ? `: ${selectedSetCode}` : ''}
        </Link>
      </div>
    </AdminCard>
  );
}
