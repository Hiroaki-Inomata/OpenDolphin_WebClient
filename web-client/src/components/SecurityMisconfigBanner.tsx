import { readCsrfToken } from '../libs/security/csrf';

const BANNER_MESSAGE = 'セキュリティトークン未設定のため更新操作ができません。管理者に連絡してください。';

const shouldShowBanner = () => import.meta.env.PROD && !readCsrfToken();

export function SecurityMisconfigBanner() {
  if (!shouldShowBanner()) return null;

  return (
    <div className="app-shell__mock-banner status-message is-error" role="alert" data-test-id="csrf-misconfig-banner">
      <strong style={{ display: 'block', marginBottom: '0.25rem' }}>{BANNER_MESSAGE}</strong>
      <span style={{ display: 'block', opacity: 0.9 }}>更新系 API は CSRF トークン設定後に再開されます。</span>
    </div>
  );
}
