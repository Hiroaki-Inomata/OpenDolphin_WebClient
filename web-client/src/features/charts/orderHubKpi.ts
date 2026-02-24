import { logUiState } from '../../libs/audit/auditLogger';
import { recordOutpatientFunnel, type DataSourceTransition } from '../../libs/telemetry/telemetryClient';

export type OrderHubKpiCategory = 'OUI-01' | 'OUI-02' | 'OUI-03' | 'OUI-04' | 'OUI-05';
export type OrderHubKpiSource = 'right-panel' | 'bottom-floating' | 'order-dock' | 'system';
export type OrderHubKpiResult =
  | 'started'
  | 'completed'
  | 'left'
  | 'blocked'
  | 'discarded'
  | 'recovered'
  | 'failed'
  | 'success';

export type OrderHubKpiMeta = {
  runId?: string;
  cacheHit?: boolean;
  missingMaster?: boolean;
  fallbackUsed?: boolean;
  dataSourceTransition?: DataSourceTransition;
  patientId?: string;
  appointmentId?: string;
};

export type OrderHubKpiRecord = {
  timestamp: string;
  category: OrderHubKpiCategory;
  source: OrderHubKpiSource;
  result: OrderHubKpiResult;
  eventId: string;
  note?: string;
  reason?: string;
  details?: Record<string, unknown>;
};

const orderHubKpiLog: OrderHubKpiRecord[] = [];

const toUiAction = (result: OrderHubKpiResult) => {
  switch (result) {
    case 'started':
      return 'start' as const;
    case 'completed':
    case 'success':
      return 'save' as const;
    case 'blocked':
    case 'failed':
      return 'cancel' as const;
    case 'discarded':
    case 'left':
      return 'draft' as const;
    case 'recovered':
      return 'navigate' as const;
    default:
      return 'navigate' as const;
  }
};

const toFunnelOutcome = (result: OrderHubKpiResult): 'started' | 'success' | 'blocked' | 'error' => {
  switch (result) {
    case 'started':
      return 'started';
    case 'blocked':
      return 'blocked';
    case 'failed':
      return 'error';
    default:
      return 'success';
  }
};

export const buildOrderHubEventId = () =>
  `order-hub-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;

export const getOrderHubKpiLog = () => [...orderHubKpiLog];

export const clearOrderHubKpiLog = () => {
  orderHubKpiLog.length = 0;
};

export const recordOrderHubKpi = (
  meta: OrderHubKpiMeta,
  payload: {
    category: OrderHubKpiCategory;
    source: OrderHubKpiSource;
    result: OrderHubKpiResult;
    eventId?: string;
    note?: string;
    reason?: string;
    details?: Record<string, unknown>;
  },
) => {
  const timestamp = new Date().toISOString();
  const eventId = payload.eventId ?? buildOrderHubEventId();
  const resolvedReason =
    payload.reason ??
    (payload.result === 'blocked' || payload.result === 'failed' ? payload.note ?? 'order_hub_operation' : undefined);
  const record: OrderHubKpiRecord = {
    timestamp,
    category: payload.category,
    source: payload.source,
    result: payload.result,
    eventId,
    note: payload.note,
    reason: resolvedReason,
    details: payload.details,
  };
  orderHubKpiLog.push(record);

  logUiState({
    action: toUiAction(record.result),
    screen: 'charts_order_hub',
    controlId: 'charts-order-hub',
    runId: meta.runId,
    patientId: meta.patientId,
    appointmentId: meta.appointmentId,
    cacheHit: meta.cacheHit,
    missingMaster: meta.missingMaster,
    fallbackUsed: meta.fallbackUsed,
    dataSourceTransition: meta.dataSourceTransition,
    details: {
      ...record.details,
      timestamp: record.timestamp,
      category: record.category,
      source: record.source,
      result: record.result,
      eventId: record.eventId,
      note: record.note,
      reason: record.reason,
    },
  });

  recordOutpatientFunnel('charts_action', {
    runId: meta.runId,
    cacheHit: meta.cacheHit ?? false,
    missingMaster: meta.missingMaster ?? false,
    fallbackUsed: meta.fallbackUsed ?? false,
    dataSourceTransition: meta.dataSourceTransition ?? 'server',
    action: `order_hub_${record.category.toLowerCase()}`,
    outcome: toFunnelOutcome(record.result),
    note: record.note,
    reason: record.reason,
  });

  return record;
};
