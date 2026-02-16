import type { ReactNode } from 'react';

type AdminFieldProps = {
  label: string;
  htmlFor?: string;
  hint?: ReactNode;
  error?: string;
  required?: boolean;
  children: ReactNode;
  className?: string;
};

export function AdminField({ label, htmlFor, hint, error, required, children, className }: AdminFieldProps) {
  return (
    <div className={`admin-form__field admin-field${className ? ` ${className}` : ''}`}>
      <label htmlFor={htmlFor}>
        {label}
        {required ? <span className="admin-field__required">*</span> : null}
      </label>
      {children}
      {hint ? <p className="admin-quiet">{hint}</p> : null}
      {error ? (
        <p className="admin-field__error" role="alert" aria-live="assertive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
