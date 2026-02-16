import { useState } from 'react';

import type { OrcaXmlProxyEndpoint, OrcaXmlProxyResponse } from '../orcaXmlProxyApi';

import { AdminCard } from '../components/AdminCard';
import { AdminCodeBlock } from '../components/AdminCodeBlock';
import { AdminField } from '../components/AdminField';
import { AdminStatusPill } from '../components/AdminStatusPill';

type OrcaXmlProxyOption = {
  id: OrcaXmlProxyEndpoint;
  label: string;
  hint: string;
  supportsClass: boolean;
};

type OrcaXmlProxyFormState = {
  xml: string;
  classCode?: string;
  result?: OrcaXmlProxyResponse | null;
};

type OrcaXmlProxyCardProps = {
  isSystemAdmin: boolean;
  guardDetailsId?: string;
  options: OrcaXmlProxyOption[];
  target: OrcaXmlProxyEndpoint;
  currentOption: OrcaXmlProxyOption;
  currentState: OrcaXmlProxyFormState;
  result: OrcaXmlProxyResponse | null;
  statusTone: 'ok' | 'warn' | 'error' | 'pending' | 'idle';
  statusLabel: string;
  pending: boolean;
  onTargetChange: (value: OrcaXmlProxyEndpoint) => void;
  onClassChange: (value: string) => void;
  onXmlChange: (value: string) => void;
  onSubmit: () => void;
  onReset: () => void;
};

const prettyXml = (raw: string) => {
  const parser = new DOMParser();
  const doc = parser.parseFromString(raw, 'application/xml');
  if (doc.querySelector('parsererror')) {
    throw new Error('XML parse error');
  }
  return new XMLSerializer().serializeToString(doc).replace(/(>)(<)(\/*)/g, '$1\n$2$3');
};

const formatDateTime = (date?: string, time?: string) => {
  if (!date && !time) return '―';
  if (!time) return date ?? '―';
  if (!date) return time ?? '―';
  return `${date} ${time}`;
};

export function OrcaXmlProxyCard({
  isSystemAdmin,
  guardDetailsId,
  options,
  target,
  currentOption,
  currentState,
  result,
  statusTone,
  statusLabel,
  pending,
  onTargetChange,
  onClassChange,
  onXmlChange,
  onSubmit,
  onReset,
}: OrcaXmlProxyCardProps) {
  const readOnly = !isSystemAdmin;
  const [parseError, setParseError] = useState<string | null>(null);

  const handleFormatXml = () => {
    try {
      const formatted = prettyXml(currentState.xml);
      onXmlChange(formatted);
      setParseError(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'XML parse error';
      setParseError(message);
    }
  };

  return (
    <AdminCard
      title="ORCA公式XMLプロキシ"
      description="payload整形・parse error統一表示・rawレスポンス折りたたみを提供します。"
      status={<AdminStatusPill status={statusTone} value={statusLabel} />}
    >
      <div className="admin-inline-meta">
        <span>HTTP: {result?.status ?? '―'}</span>
        <span>Api_Result: {result?.apiResult ?? '―'}</span>
      </div>

      <AdminField label="エンドポイント" htmlFor="orca-xml-endpoint" hint={currentOption.hint}>
        <select
          id="orca-xml-endpoint"
          value={target}
          onChange={(event) => onTargetChange(event.target.value as OrcaXmlProxyEndpoint)}
          disabled={readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
        >
          {options.map((option) => (
            <option key={option.id} value={option.id}>
              {option.label}
            </option>
          ))}
        </select>
      </AdminField>

      {currentOption.supportsClass ? (
        <AdminField label="class" htmlFor="orca-xml-class">
          <input
            id="orca-xml-class"
            type="text"
            value={currentState.classCode ?? ''}
            onChange={(event) => onClassChange(event.target.value)}
            readOnly={readOnly}
            aria-readonly={readOnly}
            aria-describedby={readOnly ? guardDetailsId : undefined}
          />
        </AdminField>
      ) : null}

      <AdminField label="XML2 payload" htmlFor="orca-xml-payload" error={parseError ?? undefined}>
        <textarea
          id="orca-xml-payload"
          className="admin-textarea"
          value={currentState.xml}
          onChange={(event) => {
            onXmlChange(event.target.value);
            if (parseError) setParseError(null);
          }}
          readOnly={readOnly}
          aria-readonly={readOnly}
          aria-describedby={readOnly ? guardDetailsId : undefined}
          rows={8}
        />
      </AdminField>

      <div className="admin-actions">
        <button type="button" className="admin-button admin-button--secondary" onClick={handleFormatXml} disabled={readOnly}>
          XML整形
        </button>
        <button type="button" className="admin-button admin-button--primary" onClick={onSubmit} disabled={pending || readOnly}>
          送信
        </button>
        <button type="button" className="admin-button admin-button--secondary" onClick={onReset} disabled={pending || readOnly}>
          テンプレ再生成
        </button>
      </div>

      {result ? (
        <div className="admin-result admin-result--stack">
          <div>HTTP Status: {result.status}</div>
          <div>Api_Result: {result.apiResult ?? '―'}</div>
          <div>Message: {result.apiResultMessage ?? '―'}</div>
          <div>取得日時: {formatDateTime(result.informationDate, result.informationTime)}</div>
          {result.missingTags?.length ? <div>Missing tags: {result.missingTags.join(', ')}</div> : null}
          {result.error ? <div className="admin-error">error: {result.error}</div> : null}
        </div>
      ) : null}
      {result?.rawXml ? <AdminCodeBlock value={result.rawXml} language="xml" title="XML proxy raw" collapsedByDefault /> : null}
    </AdminCard>
  );
}
