import { useCallback, useMemo } from 'react';
import { useLocation } from 'react-router-dom';

import { buildScopedStorageKey } from '../libs/session/storageScope';
import { useAuthService } from '../features/charts/authService';
import {
  buildChartsUrl,
  hasEncounterContext,
  normalizeRunId,
  normalizeVisitDate,
  parseChartsEncounterContext,
  type OutpatientEncounterContext,
  type ReceptionCarryoverParams,
} from '../features/charts/encounterContext';
import { useNavigationGuard } from './NavigationGuardProvider';
import {
  applyExternalParams,
  buildMobileImagesUrl,
  buildOrderSetUrl,
  buildPatientsUrl,
  buildPrintUrl,
  buildReceptionUrl,
  isSafeReturnTo,
  parseCarryover,
  pickExternalParams,
  type ExternalParams,
} from './appNavigation';
import { buildFacilityPath } from './facilityRoutes';

type AppNavigationScope = {
  facilityId: string | undefined;
  userId?: string;
};

type NavigateExtras = {
  replace?: boolean;
  state?: unknown;
};

const RETURN_TO_STORAGE_BASE = 'opendolphin:web-client:patients:returnTo';
const RETURN_TO_VERSION = 'v2';
const RETURN_TO_LEGACY_KEY = `${RETURN_TO_STORAGE_BASE}:v1`;

const hasAnyCarryover = (carryover: ReceptionCarryoverParams) =>
  Boolean(carryover.kw || carryover.dept || carryover.phys || carryover.pay || carryover.sort || carryover.date);

const mergeCarryover = (base: ReceptionCarryoverParams, override?: ReceptionCarryoverParams) => {
  if (!override) return base;
  const next: ReceptionCarryoverParams = { ...base };
  (Object.keys(override) as Array<keyof ReceptionCarryoverParams>).forEach((key) => {
    if (override[key] !== undefined) {
      next[key] = override[key];
    }
  });
  return next;
};

const mergeExternal = (base: ExternalParams, override?: ExternalParams) => {
  if (!override) return base;
  return { ...base, ...override };
};

const mergeEncounter = (base: OutpatientEncounterContext, override?: OutpatientEncounterContext) => {
  if (!override) return base;
  return {
    patientId: override.patientId ?? base.patientId,
    appointmentId: override.appointmentId ?? base.appointmentId,
    receptionId: override.receptionId ?? base.receptionId,
    visitDate: override.visitDate ?? base.visitDate,
  };
};

const parseSearchFromReturnTo = (returnTo: string): URLSearchParams => {
  try {
    const url = new URL(returnTo, 'https://app.invalid');
    return url.searchParams;
  } catch {
    return new URLSearchParams();
  }
};

const resolveCurrentScreen = (pathname: string): string => {
  if (pathname.includes('/charts/print/')) return 'print';
  if (pathname.endsWith('/charts/order-sets')) return 'orderSets';
  if (pathname.endsWith('/charts')) return 'charts';
  if (pathname.endsWith('/reception')) return 'reception';
  if (pathname.endsWith('/patients')) return 'patients';
  if (pathname.endsWith('/administration')) return 'admin';
  if (pathname.includes('/debug')) return 'debug';
  if (pathname.endsWith('/m/images') || pathname === '/m/images') return 'mobileImages';
  return 'unknown';
};

const readQueryParam = (search: string, key: string): string | undefined => {
  const params = new URLSearchParams(search.startsWith('?') ? search.slice(1) : search);
  const value = params.get(key);
  return value ?? undefined;
};

const readReturnToFromState = (state: unknown): string | undefined => {
  if (!state || typeof state !== 'object') return undefined;
  const obj = state as Record<string, unknown>;
  return typeof obj.returnTo === 'string' ? obj.returnTo : undefined;
};

const readFromFromState = (state: unknown): string | undefined => {
  if (!state || typeof state !== 'object') return undefined;
  const obj = state as Record<string, unknown>;
  return typeof obj.from === 'string' ? obj.from : undefined;
};

export function useAppNavigation(scope: AppNavigationScope) {
  const location = useLocation();
  const { flags } = useAuthService();
  const { guardedNavigate } = useNavigationGuard();

  const facilityId = scope.facilityId;
  const userId = scope.userId;

  const currentUrl = useMemo(() => `${location.pathname}${location.search}`, [location.pathname, location.search]);
  const currentScreen = useMemo(() => resolveCurrentScreen(location.pathname), [location.pathname]);

  const currentSearchParams = useMemo(
    () => new URLSearchParams(location.search.startsWith('?') ? location.search.slice(1) : location.search),
    [location.search],
  );

  const returnToInQuery = useMemo(() => readQueryParam(location.search, 'returnTo'), [location.search]);
  const returnToInState = useMemo(() => readReturnToFromState(location.state), [location.state]);
  const returnToCandidate = returnToInQuery ?? returnToInState;
  const safeReturnToCandidate = useMemo(
    () => (isSafeReturnTo(returnToCandidate, facilityId) ? returnToCandidate : undefined),
    [facilityId, returnToCandidate],
  );

  const carryoverFromUrl = useMemo(() => parseCarryover(currentSearchParams), [currentSearchParams]);
  const carryoverFromReturnTo = useMemo(
    () => (safeReturnToCandidate ? parseCarryover(parseSearchFromReturnTo(safeReturnToCandidate)) : {}),
    [safeReturnToCandidate],
  );
  const baseCarryover = useMemo(
    () => (hasAnyCarryover(carryoverFromUrl) ? carryoverFromUrl : carryoverFromReturnTo),
    [carryoverFromReturnTo, carryoverFromUrl],
  );

  const externalFromUrl = useMemo(() => pickExternalParams(currentSearchParams), [currentSearchParams]);
  const externalFromReturnTo = useMemo(
    () => (safeReturnToCandidate ? pickExternalParams(parseSearchFromReturnTo(safeReturnToCandidate)) : {}),
    [safeReturnToCandidate],
  );
  const baseExternal = useMemo(
    () => (Object.keys(externalFromUrl).length > 0 ? externalFromUrl : externalFromReturnTo),
    [externalFromReturnTo, externalFromUrl],
  );

  const encounterFromUrl = useMemo(() => parseChartsEncounterContext(location.search), [location.search]);
  const encounterFromReturnTo = useMemo(
    () => (safeReturnToCandidate ? parseChartsEncounterContext(new URL(safeReturnToCandidate, 'https://app.invalid').search) : {}),
    [safeReturnToCandidate],
  );
  const baseEncounter = useMemo(
    () => (hasEncounterContext(encounterFromUrl) ? encounterFromUrl : encounterFromReturnTo),
    [encounterFromReturnTo, encounterFromUrl],
  );

  const resolvedRunId = useMemo(() => {
    const fromUrl = readQueryParam(location.search, 'runId');
    return normalizeRunId(fromUrl ?? undefined) ?? normalizeRunId(flags.runId) ?? undefined;
  }, [flags.runId, location.search]);

  const openReception = useCallback(
    (opts?: {
      from?: string;
      returnTo?: string;
      runId?: string;
      carryover?: ReceptionCarryoverParams;
      visitDate?: string;
      section?: string;
      intent?: string;
      create?: boolean;
      external?: ExternalParams;
      navigate?: NavigateExtras;
    }) => {
      const from = opts?.from ?? currentScreen;
      const returnTo = opts?.returnTo ?? currentUrl;
      const encounter = mergeEncounter(baseEncounter, opts?.visitDate ? { visitDate: opts.visitDate } : undefined);
      const url = buildReceptionUrl({
        facilityId,
        from,
        returnTo,
        runId: opts?.runId ?? resolvedRunId,
        carryover: mergeCarryover(baseCarryover, opts?.carryover),
        visitDate: opts?.visitDate ?? normalizeVisitDate(encounter.visitDate),
        section: opts?.section,
        intent: opts?.intent,
        create: opts?.create,
        external: mergeExternal(baseExternal, opts?.external),
      });
      guardedNavigate(url, { replace: opts?.navigate?.replace, state: opts?.navigate?.state });
    },
    [baseCarryover, baseEncounter, baseExternal, currentScreen, currentUrl, facilityId, guardedNavigate, resolvedRunId],
  );

  const openPatients = useCallback(
    (opts?: {
      from?: string;
      returnTo?: string;
      runId?: string;
      carryover?: ReceptionCarryoverParams;
      encounter?: OutpatientEncounterContext;
      patientId?: string;
      intent?: string;
      external?: ExternalParams;
      navigate?: NavigateExtras;
    }) => {
      const from = opts?.from ?? currentScreen;
      const returnTo = opts?.returnTo ?? currentUrl;
      const encounter = mergeEncounter(baseEncounter, opts?.encounter);
      const patientId = opts?.patientId ?? encounter.patientId;
      const url = buildPatientsUrl({
        facilityId,
        from,
        returnTo,
        runId: opts?.runId ?? resolvedRunId ?? flags.runId,
        carryover: mergeCarryover(baseCarryover, opts?.carryover),
        patientId,
        appointmentId: encounter.appointmentId,
        receptionId: encounter.receptionId,
        visitDate: normalizeVisitDate(encounter.visitDate),
        intent: opts?.intent,
        external: mergeExternal(baseExternal, opts?.external),
      });

      if (from === 'charts' && isSafeReturnTo(returnTo, facilityId) && typeof sessionStorage !== 'undefined') {
        try {
          const scopedKey = buildScopedStorageKey(RETURN_TO_STORAGE_BASE, RETURN_TO_VERSION, { facilityId, userId }) ?? RETURN_TO_LEGACY_KEY;
          sessionStorage.setItem(scopedKey, returnTo);
          if (scopedKey !== RETURN_TO_LEGACY_KEY) {
            sessionStorage.removeItem(RETURN_TO_LEGACY_KEY);
          }
        } catch {
          // ignore storage errors
        }
      }

      guardedNavigate(url, { replace: opts?.navigate?.replace, state: opts?.navigate?.state });
    },
    [baseCarryover, baseEncounter, baseExternal, currentScreen, currentUrl, facilityId, flags.runId, guardedNavigate, resolvedRunId, userId],
  );

  const openCharts = useCallback(
    (opts?: {
      encounter?: OutpatientEncounterContext;
      carryover?: ReceptionCarryoverParams;
      runId?: string;
      external?: ExternalParams;
      navigate?: NavigateExtras;
    }) => {
      const encounter = mergeEncounter(baseEncounter, opts?.encounter);
      const chartsBasePath = buildFacilityPath(facilityId, '/charts');
      const runId = opts?.runId ?? resolvedRunId ?? flags.runId;
      const baseUrl = buildChartsUrl(encounter, mergeCarryover(baseCarryover, opts?.carryover), { runId }, chartsBasePath);
      const url = (() => {
        try {
          const parsed = new URL(baseUrl, 'https://app.invalid');
          applyExternalParams(parsed.searchParams, mergeExternal(baseExternal, opts?.external));
          const search = parsed.searchParams.toString();
          return `${parsed.pathname}${search ? `?${search}` : ''}`;
        } catch {
          return baseUrl;
        }
      })();
      guardedNavigate(url, { replace: opts?.navigate?.replace, state: opts?.navigate?.state });
    },
    [baseCarryover, baseEncounter, baseExternal, facilityId, flags.runId, guardedNavigate, resolvedRunId],
  );

  const openOrderSets = useCallback(
    (opts?: { from?: string; returnTo?: string; external?: ExternalParams; navigate?: NavigateExtras }) => {
      const from = opts?.from ?? currentScreen;
      const returnTo = opts?.returnTo ?? currentUrl;
      const url = buildOrderSetUrl({
        facilityId,
        from,
        returnTo,
        external: mergeExternal(baseExternal, opts?.external),
      });
      guardedNavigate(url, { replace: opts?.navigate?.replace, state: opts?.navigate?.state });
    },
    [baseExternal, currentScreen, currentUrl, facilityId, guardedNavigate],
  );

  const openPrintOutpatient = useCallback(
    (opts: { state: Record<string, unknown>; from?: string; returnTo?: string; external?: ExternalParams; navigate?: NavigateExtras }) => {
      const from = opts.from ?? currentScreen;
      const returnTo = opts.returnTo ?? currentUrl;
      const url = buildPrintUrl({
        facilityId,
        kind: 'outpatient',
        from,
        external: mergeExternal(baseExternal, opts.external),
      });
      guardedNavigate(url, {
        replace: opts.navigate?.replace,
        state: { ...opts.state, from, returnTo },
      });
    },
    [baseExternal, currentScreen, currentUrl, facilityId, guardedNavigate],
  );

  const openPrintDocument = useCallback(
    (opts: { state: Record<string, unknown>; from?: string; returnTo?: string; external?: ExternalParams; navigate?: NavigateExtras }) => {
      const from = opts.from ?? currentScreen;
      const returnTo = opts.returnTo ?? currentUrl;
      const url = buildPrintUrl({
        facilityId,
        kind: 'document',
        from,
        external: mergeExternal(baseExternal, opts.external),
      });
      guardedNavigate(url, {
        replace: opts.navigate?.replace,
        state: { ...opts.state, from, returnTo },
      });
    },
    [baseExternal, currentScreen, currentUrl, facilityId, guardedNavigate],
  );

  const openMobileImages = useCallback(
    (opts?: { from?: string; returnTo?: string; patientId?: string; external?: ExternalParams; navigate?: NavigateExtras }) => {
      const from = opts?.from ?? currentScreen;
      const returnTo = opts?.returnTo ?? currentUrl;
      const url = buildMobileImagesUrl({
        facilityId,
        from,
        returnTo,
        patientId: opts?.patientId,
        external: mergeExternal(baseExternal, opts?.external),
      });
      guardedNavigate(url, { replace: opts?.navigate?.replace, state: opts?.navigate?.state });
    },
    [baseExternal, currentScreen, currentUrl, facilityId, guardedNavigate],
  );

  const returnFromInState = useMemo(() => readFromFromState(location.state), [location.state]);
  const fromInQuery = useMemo(() => readQueryParam(location.search, 'from'), [location.search]);
  const fromCandidate = fromInQuery ?? returnFromInState;

  return {
    currentUrl,
    currentScreen,
    fromCandidate,
    returnToCandidate,
    safeReturnToCandidate,
    carryover: baseCarryover,
    external: baseExternal,
    encounter: baseEncounter,
    openReception,
    openPatients,
    openCharts,
    openOrderSets,
    openPrintOutpatient,
    openPrintDocument,
    openMobileImages,
  };
}

