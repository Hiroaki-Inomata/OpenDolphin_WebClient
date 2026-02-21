import type { ReactNode } from 'react';

import type { LiveRegionAria } from '../../libs/observability/types';
import { resolveRunId } from '../../libs/observability/observability';

export type StatusPillTone = 'neutral' | 'info' | 'success' | 'warning' | 'error';
export type StatusPillSize = 'xs' | 'sm' | 'md';

export interface StatusPillProps {
  label?: string;
  value?: ReactNode;
  tone?: StatusPillTone;
  size?: StatusPillSize;
  ariaLive?: LiveRegionAria;
  runId?: string;
  className?: string;
  ariaLabel?: string;
  children?: ReactNode;
}

const buildAriaText = (label?: string, value?: ReactNode): string | undefined => {
  const valueText = typeof value === 'string' || typeof value === 'number' ? String(value) : undefined;
  if (!label && !valueText) return undefined;
  if (label && valueText) return `${label}: ${valueText}`;
  return label ?? valueText;
};

export function StatusPill({
  label,
  value,
  tone = 'neutral',
  size = 'sm',
  ariaLive,
  runId,
  className,
  ariaLabel,
  children,
}: StatusPillProps) {
  const resolvedRunId = resolveRunId(runId);
  const live = ariaLive ?? 'off';
  const liveProps =
    live === 'off'
      ? {}
      : {
          role: 'status' as const,
          'aria-live': live,
          'aria-atomic': 'true' as const,
        };
  const childrenText = typeof children === 'string' || typeof children === 'number' ? String(children) : undefined;
  const ariaText = ariaLabel ?? childrenText ?? buildAriaText(label, value);

  const content = children ?? (
    <>
      {label ? <span className="status-pill__label">{label}</span> : null}
      {value !== undefined ? <span className="status-pill__value">{value}</span> : null}
    </>
  );

  return (
    <span
      className={`status-pill status-pill--${tone} status-pill--${size}${className ? ` ${className}` : ''}`}
      {...liveProps}
      aria-label={ariaText}
      data-run-id={resolvedRunId}
    >
      {content}
    </span>
  );
}
