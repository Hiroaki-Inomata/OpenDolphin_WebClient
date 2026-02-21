import { useMemo } from 'react';

import { isSafeReturnTo } from '../../routes/appNavigation';
import { useNavigationGuard } from '../../routes/NavigationGuardProvider';
import { useAppNavigation } from '../../routes/useAppNavigation';

import './returnToBar.css';

type ReturnToBarProps = {
  scope: { facilityId: string | undefined; userId?: string };
  returnTo?: string | null;
  from?: string | null;
  fallbackUrl: string;
  showShortcuts?: boolean;
};

const RETURN_LABELS: Record<string, string> = {
  charts: 'カルテ',
  reception: '受付',
  patients: '患者管理',
  print: '印刷',
  orderSets: 'オーダーセット',
  mobileImages: '画像アップロード',
  admin: '管理画面',
  debug: 'デバッグ',
};

export function ReturnToBar({ scope, returnTo, from, fallbackUrl, showShortcuts = false }: ReturnToBarProps) {
  const { guardedNavigate } = useNavigationGuard();
  const nav = useAppNavigation(scope);

  const safeReturnTo = useMemo(() => {
    return isSafeReturnTo(returnTo ?? undefined, scope.facilityId) ? (returnTo ?? undefined) : undefined;
  }, [returnTo, scope.facilityId]);

  const label = RETURN_LABELS[String(from ?? '')] ?? '戻る';
  const backLabel = label === '戻る' ? '◀︎ 戻る' : `◀︎ ${label}へ戻る`;
  const hint = safeReturnTo ? `${label}に戻れます` : '戻り先がないため安全な画面へ戻ります';

  return (
    <div className="return-to-bar" role="region" aria-label="戻り導線">
      <div className="return-to-bar__main">
        <button
          type="button"
          className="return-to-bar__back"
          onClick={() => guardedNavigate(safeReturnTo ?? fallbackUrl)}
          title={safeReturnTo ?? fallbackUrl}
        >
          {backLabel}
        </button>
        <span className="return-to-bar__hint">{hint}</span>
      </div>

      {showShortcuts ? (
        <div className="return-to-bar__links" role="group" aria-label="主要画面へ">
          {nav.currentScreen !== 'reception' ? (
            <button type="button" className="return-to-bar__link" onClick={() => nav.openReception()}>
              受付へ
            </button>
          ) : null}
          {nav.currentScreen !== 'patients' ? (
            <button type="button" className="return-to-bar__link" onClick={() => nav.openPatients()}>
              患者管理へ
            </button>
          ) : null}
          {nav.currentScreen !== 'charts' ? (
            <button type="button" className="return-to-bar__link" onClick={() => nav.openCharts()}>
              カルテへ
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
