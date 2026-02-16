import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  UNSAFE_NavigationContext,
  useLocation,
  useNavigate,
  type Location as RouterLocation,
  type NavigateOptions,
  type To,
} from 'react-router-dom';

import { FocusTrapDialog } from '../components/modals/FocusTrapDialog';

type DirtySource = {
  reason?: string;
  updatedAt: number;
};

type BlockerTx = {
  location: RouterLocation;
  retry: () => void;
};

type NavigatorWithBlock = {
  block: (fn: (tx: BlockerTx) => void) => () => void;
};

type NavigationGuardContextValue = {
  registerDirty: (sourceKey: string, isDirty: boolean, reason?: string) => void;
  isDirty: boolean;
  dirtySources: Array<{ sourceKey: string; reason?: string }>;
  guardedNavigate: (to: To, options?: NavigateOptions) => void;
};

const NavigationGuardContext = createContext<NavigationGuardContextValue | null>(null);

export function NavigationGuardProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const navContext = useContext(UNSAFE_NavigationContext) as unknown as { navigator: Partial<NavigatorWithBlock> } | null;
  const [dirtyMap, setDirtyMap] = useState<Record<string, DirtySource>>({});
  const [blockedTx, setBlockedTx] = useState<BlockerTx | null>(null);
  const unblockRef = useRef<null | (() => void)>(null);
  const locationRef = useRef(location);
  const isDirtyRef = useRef(false);

  useEffect(() => {
    locationRef.current = location;
  }, [location]);

  const registerDirty = useCallback((sourceKey: string, isDirty: boolean, reason?: string) => {
    setDirtyMap((prev) => {
      if (isDirty) {
        const current = prev[sourceKey];
        const next: DirtySource = {
          reason: reason?.trim() || undefined,
          updatedAt: Date.now(),
        };
        if (current && current.reason === next.reason) return prev;
        return { ...prev, [sourceKey]: next };
      }
      if (!(sourceKey in prev)) return prev;
      const next = { ...prev };
      delete next[sourceKey];
      return next;
    });
  }, []);

  const dirtySources = useMemo(
    () =>
      Object.entries(dirtyMap)
        .map(([sourceKey, entry]) => ({ sourceKey, reason: entry.reason }))
        .sort((a, b) => a.sourceKey.localeCompare(b.sourceKey)),
    [dirtyMap],
  );
  const isDirty = dirtySources.length > 0;

  useEffect(() => {
    isDirtyRef.current = isDirty;
  }, [isDirty]);

  useEffect(() => {
    if (!isDirty) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [isDirty]);

  const enableBlocking = useCallback(() => {
    const navigator = navContext?.navigator;
    if (!navigator || typeof navigator.block !== 'function') return;
    if (unblockRef.current) return;

    unblockRef.current = navigator.block((tx) => {
      const currentLocation = locationRef.current;
      const nextLocation = tx.location;
      const dirtyNow = isDirtyRef.current;
      // Guard only screen-to-screen navigation. Query updates inside the same screen should be allowed.
      const shouldBlock = dirtyNow && currentLocation.pathname !== nextLocation.pathname;

      if (!shouldBlock) {
        const unblock = unblockRef.current;
        if (unblock) {
          unblock();
          unblockRef.current = null;
        }
        tx.retry();
        Promise.resolve().then(() => {
          if (isDirtyRef.current) enableBlocking();
        });
        return;
      }

      setBlockedTx({
        location: nextLocation,
        retry: () => {
          const unblock = unblockRef.current;
          if (unblock) {
            unblock();
            unblockRef.current = null;
          }
          tx.retry();
          Promise.resolve().then(() => {
            if (isDirtyRef.current) enableBlocking();
          });
        },
      });
    });
  }, [navContext?.navigator]);

  useEffect(() => {
    if (!isDirty) {
      const unblock = unblockRef.current;
      if (unblock) {
        unblock();
        unblockRef.current = null;
      }
      setBlockedTx(null);
      return;
    }

    enableBlocking();
    return () => {
      const unblock = unblockRef.current;
      if (unblock) {
        unblock();
        unblockRef.current = null;
      }
      setBlockedTx(null);
    };
  }, [enableBlocking, isDirty]);

  const dialogOpen = blockedTx !== null;

  const handleCancel = useCallback(() => {
    setBlockedTx(null);
  }, []);

  const handleDiscard = useCallback(() => {
    const tx = blockedTx;
    setBlockedTx(null);
    tx?.retry();
  }, [blockedTx]);

  const resolveToPathname = useCallback(
    (to: To): string | null => {
      const currentPathname = locationRef.current.pathname;
      if (typeof to === 'string') {
        if (to.startsWith('?') || to.startsWith('#')) return currentPathname;
        try {
          return new URL(to, 'https://app.invalid').pathname || currentPathname;
        } catch {
          return null;
        }
      }
      return to.pathname ?? currentPathname;
    },
    [],
  );

  const guardedNavigate = useCallback(
    (to: To, options?: NavigateOptions) => {
      const dirtyNow = isDirtyRef.current;
      if (!dirtyNow) {
        navigate(to, options);
        return;
      }

      // Guard only screen-to-screen navigation. Query updates inside the same screen should be allowed.
      const currentPathname = locationRef.current.pathname;
      const targetPathname = resolveToPathname(to);
      if (targetPathname && targetPathname === currentPathname) {
        navigate(to, options);
        return;
      }

      if (blockedTx) return;

      setBlockedTx({
        location: locationRef.current,
        retry: () => {
          const unblock = unblockRef.current;
          if (unblock) {
            unblock();
            unblockRef.current = null;
          }
          navigate(to, options);
          Promise.resolve().then(() => {
            if (isDirtyRef.current) enableBlocking();
          });
        },
      });
    },
    [blockedTx, enableBlocking, navigate, resolveToPathname],
  );

  return (
    <NavigationGuardContext.Provider value={{ registerDirty, isDirty, dirtySources, guardedNavigate }}>
      {children}
      <FocusTrapDialog
        open={dialogOpen}
        title="未保存の変更があります"
        description="画面遷移すると入力内容が失われる可能性があります。"
        role="alertdialog"
        onClose={handleCancel}
        testId="navigation-guard-dialog"
      >
        <div style={{ display: 'grid', gap: '0.75rem' }}>
          {dirtySources.length > 0 ? (
            <div>
              <p style={{ margin: 0, fontWeight: 700 }}>未保存の内容</p>
              <ul style={{ margin: '0.35rem 0 0', paddingLeft: '1.2rem' }}>
                {dirtySources.map((entry) => (
                  <li key={entry.sourceKey}>
                    {entry.reason ? entry.reason : entry.sourceKey}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button type="button" onClick={handleCancel}>
              キャンセル
            </button>
            <button
              type="button"
              onClick={handleDiscard}
              style={{
                background: '#b42318',
                color: 'white',
                border: 'none',
                padding: '0.5rem 0.75rem',
                borderRadius: 8,
              }}
            >
              破棄して移動
            </button>
          </div>
        </div>
      </FocusTrapDialog>
    </NavigationGuardContext.Provider>
  );
}

export function useNavigationGuard(): NavigationGuardContextValue {
  const value = useContext(NavigationGuardContext);
  if (!value) {
    throw new Error('useNavigationGuard must be used within NavigationGuardProvider');
  }
  return value;
}
