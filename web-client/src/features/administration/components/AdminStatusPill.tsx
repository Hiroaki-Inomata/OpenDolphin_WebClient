import { StatusPill } from '../../shared/StatusPill';

type AdminStatus = 'ok' | 'warn' | 'error' | 'pending' | 'idle';

type AdminStatusPillProps = {
  status: AdminStatus;
  label?: string;
  value: string;
  className?: string;
};

const toTone = (status: AdminStatus) => {
  if (status === 'ok') return 'success' as const;
  if (status === 'warn' || status === 'pending') return 'warning' as const;
  if (status === 'error') return 'error' as const;
  return 'neutral' as const;
};

export function AdminStatusPill({ status, label, value, className }: AdminStatusPillProps) {
  return <StatusPill className={className} label={label} value={value} tone={toTone(status)} size="sm" />;
}
