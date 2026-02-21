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
const CHARTS_SCREEN_KEY_PARAMS = ['patientId', 'appointmentId', 'receptionId', 'visitDate'] as const;

type ScreenKeyLocation = Pick<RouterLocation, 'pathname' | 'search'>;

const normalizePathname = (value?: string | null): string => {
  if (!value) return '/';
  const trimmed = value.trim();
  if (!trimmed) return '/';
  if (trimmed === '/') return '/';
  const normalized = trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
  return normalized.replace(/\/+$/, '');
};

const normalizeSearch = (value?: string | null): string => {
  if (!value) return '';
  return value.startsWith('?') ? value : `?${value}`;
};

const resolveChartsScreenKey = (search: string): string => {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const scoped = new URLSearchParams();
  CHARTS_SCREEN_KEY_PARAMS.forEach((key) => {
    const value = params.get(key);
    if (value !== null && value.trim() !== '') {
      scoped.set(key, value.trim());
    }
  });
  const scopedSearch = scoped.toString();
  return scopedSearch ? `/charts?${scopedSearch}` : '/charts';
};

export const resolveScreenKey = (target: ScreenKeyLocation): string => {
  const pathname = normalizePathname(target.pathname);
  if (pathname.endsWith('/charts')) {
    const scopedChartsKey = resolveChartsScreenKey(normalizeSearch(target.search));
    const scopedQuery = scopedChartsKey.startsWith('/charts') ? scopedChartsKey.slice('/charts'.length) : '';
    return `${pathname}${scopedQuery}`;
  }
  return pathname;
};

const resolveToLocation = (to: To, currentLocation: ScreenKeyLocation): ScreenKeyLocation | null => {
  const currentPathname = normalizePathname(currentLocation.pathname);
  const currentSearch = normalizeSearch(currentLocation.search);
  if (typeof to === 'string') {
    if (to.startsWith('#')) {
      return { pathname: currentPathname, search: currentSearch };
    }
    if (to.startsWith('?')) {
      return { pathname: currentPathname, search: normalizeSearch(to) };
    }
    try {
      const base = `https://app.invalid${currentPathname}${currentSearch}`;
      const parsed = new URL(to, base);
      return { pathname: normalizePathname(parsed.pathname), search: normalizeSearch(parsed.search) };
    } catch {
      return null;
    }
  }

  const pathname = to.pathname ?? currentPathname;
  const search = typeof to.search === 'string' ? to.search : to.pathname ? '' : currentSearch;
  return { pathname: normalizePathname(pathname), search: normalizeSearch(search) };
};

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
      const shouldBlock = dirtyNow && resolveScreenKey(currentLocation) !== resolveScreenKey(nextLocation);

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

  const guardedNavigate = useCallback(
    (to: To, options?: NavigateOptions) => {
      const dirtyNow = isDirtyRef.current;
      if (!dirtyNow) {
        navigate(to, options);
        return;
      }

      const currentLocation = locationRef.current;
      const targetLocation = resolveToLocation(to, currentLocation);
      if (targetLocation && resolveScreenKey(targetLocation) === resolveScreenKey(currentLocation)) {
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
    [blockedTx, enableBlocking, navigate],
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
