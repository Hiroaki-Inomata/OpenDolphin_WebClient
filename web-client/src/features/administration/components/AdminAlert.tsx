import { resolveAriaLive } from '../../../libs/observability/observability';

type AdminAlertTone = 'ok' | 'warn' | 'error' | 'info';

type AdminAlertProps = {
  tone: AdminAlertTone;
  message: string;
  detail?: string;
  className?: string;
};

export function AdminAlert({ tone, message, detail, className }: AdminAlertProps) {
  const role = tone === 'error' || tone === 'warn' ? 'alert' : 'status';
  const live = resolveAriaLive(tone === 'warn' ? 'warning' : tone === 'ok' ? 'success' : tone);

  return (
    <div
      className={`admin-alert admin-alert--${tone}${className ? ` ${className}` : ''}`}
      role={role}
      aria-live={live}
      aria-atomic="true"
    >
      <strong className="admin-alert__tone">{tone.toUpperCase()}</strong>
      <p className="admin-alert__message">
        {message}
        {detail ? ` ${detail}` : ''}
      </p>
    </div>
  );
}
