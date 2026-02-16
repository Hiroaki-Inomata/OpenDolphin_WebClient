import { AdminStatusPill } from './AdminStatusPill';

type DirtyStateBarProps = {
  dirty: boolean;
  updatedAt?: string;
  className?: string;
};

const formatTimestamp = (iso?: string) => {
  if (!iso) return '―';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('ja-JP', { hour12: false });
};

export function DirtyStateBar({ dirty, updatedAt, className }: DirtyStateBarProps) {
  return (
    <div className={`admin-dirty-state${className ? ` ${className}` : ''}`} aria-live="polite">
      <AdminStatusPill status={dirty ? 'warn' : 'ok'} value={dirty ? '変更あり（未保存）' : '保存済み'} />
      <span className="admin-dirty-state__updated">最終保存: {formatTimestamp(updatedAt)}</span>
    </div>
  );
}
