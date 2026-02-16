import { useEffect, useMemo, useState } from 'react';

import { copyTextToClipboard } from '../../../libs/observability/runIdCopy';
import { useAppToast } from '../../../libs/ui/appToast';

type AdminCodeBlockLanguage = 'json' | 'xml' | 'text';

type AdminCodeBlockProps = {
  value?: string;
  language?: AdminCodeBlockLanguage;
  title?: string;
  collapsedByDefault?: boolean;
  className?: string;
};

const formatXml = (raw: string) => {
  const parser = new DOMParser();
  const doc = parser.parseFromString(raw, 'application/xml');
  if (doc.querySelector('parsererror')) {
    throw new Error('XML parse error');
  }
  const serialized = new XMLSerializer().serializeToString(doc);
  const lines = serialized.replace(/(>)(<)(\/*)/g, '$1\n$2$3').split('\n');
  let indent = 0;
  const formatted = lines
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => {
      if (line.startsWith('</')) {
        indent = Math.max(0, indent - 1);
      }
      const prefix = '  '.repeat(indent);
      const next = `${prefix}${line}`;
      const opens = /^<[^!?][^>]*[^/]>$/.test(line);
      if (opens) {
        indent += 1;
      }
      return next;
    });
  return formatted.join('\n');
};

const prettyValue = (value: string, language: AdminCodeBlockLanguage) => {
  if (language === 'json') {
    return JSON.stringify(JSON.parse(value), null, 2);
  }
  if (language === 'xml') {
    return formatXml(value);
  }
  return value;
};

export function AdminCodeBlock({
  value,
  language = 'text',
  title = 'raw',
  collapsedByDefault = true,
  className,
}: AdminCodeBlockProps) {
  const [expanded, setExpanded] = useState(!collapsedByDefault);
  const [text, setText] = useState(value ?? '');
  const [formatError, setFormatError] = useState<string | null>(null);
  const { enqueue } = useAppToast();

  useEffect(() => {
    setText(value ?? '');
    setFormatError(null);
  }, [value]);

  const hasValue = text.trim().length > 0;
  const lineCount = useMemo(() => text.split('\n').length, [text]);

  const handleCopy = async () => {
    if (!hasValue) return;
    try {
      await copyTextToClipboard(text);
      enqueue({ tone: 'success', message: `${title} をコピーしました`, durationMs: 1800 });
    } catch {
      enqueue({ tone: 'error', message: `${title} のコピーに失敗しました` });
    }
  };

  const handleFormat = () => {
    if (!hasValue) return;
    try {
      const formatted = prettyValue(text, language);
      setText(formatted);
      setFormatError(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'format error';
      setFormatError(message);
    }
  };

  return (
    <div className={`admin-code-block${className ? ` ${className}` : ''}`}>
      <div className="admin-code-block__header">
        <button
          type="button"
          className="admin-button admin-button--secondary admin-code-block__toggle"
          onClick={() => setExpanded((prev) => !prev)}
          aria-expanded={expanded}
        >
          {expanded ? '折りたたむ' : `${title} を表示`}
        </button>
        <span className="admin-code-block__meta">{lineCount} lines</span>
        <button
          type="button"
          className="admin-button admin-button--secondary"
          onClick={handleFormat}
          disabled={!hasValue}
        >
          整形
        </button>
        <button
          type="button"
          className="admin-button admin-button--secondary"
          onClick={handleCopy}
          disabled={!hasValue}
        >
          コピー
        </button>
      </div>
      {formatError ? <p className="admin-field__error">整形に失敗しました: {formatError}</p> : null}
      {expanded ? (
        <pre className="admin-code-block__pre" aria-label={`${title} (${language})`}>
          <code>{text || '（データなし）'}</code>
        </pre>
      ) : null}
    </div>
  );
}
