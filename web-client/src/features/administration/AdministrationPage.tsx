import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getAuditEventLog, logAuditEvent, logUiState } from '../../libs/audit/auditLogger';
import { resolveAriaLive, resolveRunId } from '../../libs/observability/observability';
import { copyTextToClipboard } from '../../libs/observability/runIdCopy';
import { persistHeaderFlags, resolveHeaderFlags } from '../../libs/http/header-flags';
import { isSystemAdminRole } from '../../libs/auth/roles';
import { useAppToast } from '../../libs/ui/appToast';
import { ToneBanner } from '../reception/components/ToneBanner';
import { useSession } from '../../AppRouter';
import { buildFacilityPath } from '../../routes/facilityRoutes';
import { applyAuthServicePatch, useAuthService, type AuthServiceFlags } from '../charts/authService';
import {
  ORCA_QUEUE_STALL_THRESHOLD_MS,
  buildOrcaQueueWarningSummary,
  isOrcaQueueWarningEntry,
} from '../outpatient/orcaQueueStatus';
import {
  discardOrcaQueue,
  fetchEffectiveAdminConfig,
  fetchMasterLastUpdate,
  fetchMedicalSet,
  fetchOrcaQueue,
  fetchSystemDaily,
  fetchSystemInfo,
  retryOrcaQueue,
  saveAdminConfig,
  syncMedicationMod,
  type AdminConfigPayload,
  type ChartsMasterSourcePolicy,
  type MasterLastUpdateResponse,
  type MedicalSetResponse,
  type MedicalSetSearchPayload,
  type MedicationModResponse,
  type OrcaQueueEntry,
  type SystemDailyResponse,
  type SystemInfoResponse,
} from './api';
import {
  buildAcceptListRequestXml,
  buildInsuranceProviderRequestXml,
  buildManageUsersRequestXml,
  buildSystemListRequestXml,
  postOrcaXmlProxy,
  type OrcaXmlProxyEndpoint,
  type OrcaXmlProxyResponse,
} from './orcaXmlProxyApi';
import {
  fetchOrcaConnectionConfig,
  saveOrcaConnectionConfig,
  testOrcaConnection,
  type OrcaConnectionTestResponse,
} from './orcaConnectionApi';
import {
  postBirthDelivery,
  postMedicalRecords,
  postMedicalSets,
  postPatientMutation,
  postSubjectiveEntry,
  postTensuSync,
  type MedicalPatientSummary,
  type MedicalRecordEntry,
  type OrcaInternalWrapperBase,
} from './orcaInternalWrapperApi';
import { LegacyRestPanel } from './LegacyRestPanel';
import { TouchAdmPhrPanel } from './TouchAdmPhrPanel';
import { AccessManagementPanel } from './AccessManagementPanel';
import { OrcaUserManagementPanel } from './OrcaUserManagementPanel';
import { MasterUpdatesPanel } from './MasterUpdatesPanel';
import './administration.css';
import {
  publishAdminBroadcast,
  type AdminDeliveryFlagState,
  type AdminDeliveryStatus,
} from '../../libs/admin/broadcast';
import { AuditSummaryInline } from '../shared/AuditSummaryInline';
import { RunIdBadge } from '../shared/RunIdBadge';
import { ConfirmDialog } from './components/ConfirmDialog';
import { AdminStatusPill } from './components/AdminStatusPill';
import { DeliverySubNav } from './delivery/DeliverySubNav';
import { DeliveryDashboard } from './delivery/DeliveryDashboard';
import { WebOrcaConnectionCard } from './delivery/WebOrcaConnectionCard';
import { AdminDeliveryConfigCard } from './delivery/AdminDeliveryConfigCard';
import { AdminDeliveryStatusCard } from './delivery/AdminDeliveryStatusCard';
import { OrcaMasterSyncCard } from './delivery/OrcaMasterSyncCard';
import { SystemHealthCard } from './delivery/SystemHealthCard';
import { MedicalSetSearchCard } from './delivery/MedicalSetSearchCard';
import { OrcaXmlProxyCard } from './delivery/OrcaXmlProxyCard';
import { OrcaInternalWrapperCard } from './delivery/OrcaInternalWrapperCard';
import { OrcaQueueCard } from './delivery/OrcaQueueCard';
import { DELIVERY_SECTION_ITEMS, type DeliverySection } from './delivery/types';

type AdministrationPageProps = {
  runId: string;
  role?: string;
};

type AdministrationTab = 'delivery' | 'orca-users' | 'master-updates';

type Feedback = { tone: 'success' | 'warning' | 'error' | 'info'; message: string };
type OrcaXmlProxyFormState = {
  xml: string;
  classCode?: string;
  result?: OrcaXmlProxyResponse | null;
};
type OrcaInternalWrapperEndpoint =
  | 'medical-sets'
  | 'tensu-sync'
  | 'birth-delivery'
  | 'medical-records'
  | 'patient-mutation'
  | 'chart-subjectives';
type OrcaInternalWrapperResult = OrcaInternalWrapperBase & {
  generatedAt?: string;
  patient?: MedicalPatientSummary;
  records?: MedicalRecordEntry[];
  warnings?: string[];
  recordedAt?: string;
  patientDbId?: number;
  patientId?: string;
};
type OrcaInternalWrapperFormState = {
  payload: string;
  result?: OrcaInternalWrapperResult | null;
  parseError?: string;
};
type OrcaConnectionFormState = {
  useWeborca: boolean;
  serverUrl: string;
  port: string;
  username: string;
  password: string;
  passwordConfigured: boolean;
  passwordUpdatedAt?: string;
  clientAuthEnabled: boolean;
  clientCertificateFile: File | null;
  clientCertificateConfigured: boolean;
  clientCertificateFileName?: string;
  clientCertificateUploadedAt?: string;
  clientCertificatePassphrase: string;
  clientCertificatePassphraseConfigured: boolean;
  clientCertificatePassphraseUpdatedAt?: string;
  caCertificateFile: File | null;
  caCertificateConfigured: boolean;
  caCertificateFileName?: string;
  caCertificateUploadedAt?: string;
  updatedAt?: string;
  auditSummary?: string;
};
type OrcaConnectionTestState = OrcaConnectionTestResponse | null;
type GuardAction =
  | 'access'
  | 'edit'
  | 'save'
  | 'retry'
  | 'discard'
  | 'master-check'
  | 'master-sync'
  | 'system-check'
  | 'medicalset-search'
  | 'orca-xml-proxy'
  | 'orca-internal-wrapper'
  | 'orca-connection'
  | 'legacy-rest'
  | 'touch-adm-phr';

const deliveryFlagStateLabel = (state: AdminDeliveryFlagState) => {
  if (state === 'applied') return '配信済み';
  if (state === 'pending') return '未反映';
  return '不明';
};

const DEFAULT_ORCA_ENDPOINT =
  (import.meta.env as Record<string, string | undefined>).VITE_ORCA_ENDPOINT ?? 'https://localhost:9080/openDolphin/resources';
const DEFAULT_FORM: AdminConfigPayload = {
  orcaEndpoint: DEFAULT_ORCA_ENDPOINT,
  mswEnabled: import.meta.env.VITE_DISABLE_MSW !== '1',
  useMockOrcaQueue: resolveHeaderFlags().useMockOrcaQueue,
  verifyAdminDelivery: resolveHeaderFlags().verifyAdminDelivery,
  chartsDisplayEnabled: true,
  chartsSendEnabled: true,
  chartsMasterSource: 'auto',
};

const DEFAULT_ORCA_CONNECTION_FORM: OrcaConnectionFormState = {
  useWeborca: true,
  serverUrl: '',
  port: '443',
  username: '',
  password: '',
  passwordConfigured: false,
  clientAuthEnabled: false,
  clientCertificateFile: null,
  clientCertificateConfigured: false,
  clientCertificatePassphrase: '',
  clientCertificatePassphraseConfigured: false,
  caCertificateFile: null,
  caCertificateConfigured: false,
};

const ORCA_XML_PROXY_OPTIONS: Array<{
  id: OrcaXmlProxyEndpoint;
  label: string;
  hint: string;
  supportsClass: boolean;
  defaultClass?: string;
}> = [
  {
    id: 'acceptlstv2',
    label: 'acceptlstv2（受付一覧）',
    hint: 'class=01/02 で受付一覧を取得',
    supportsClass: true,
    defaultClass: '01',
  },
  {
    id: 'system01lstv2',
    label: 'system01lstv2（システム管理一覧）',
    hint: 'class=02 が標準',
    supportsClass: true,
    defaultClass: '02',
  },
  {
    id: 'manageusersv2',
    label: 'manageusersv2（ユーザー管理）',
    hint: 'ユーザー管理の原本取得',
    supportsClass: false,
  },
  {
    id: 'insprogetv2',
    label: 'insprogetv2（保険者マスタ）',
    hint: '保険者マスタの原本取得',
    supportsClass: false,
  },
];

type OrcaInternalWrapperOption = {
  id: OrcaInternalWrapperEndpoint;
  label: string;
  hint: string;
  stubFixed?: boolean;
  defaultPayload: Record<string, unknown>;
};

const buildInternalWrapperOptions = (today: string): OrcaInternalWrapperOption[] => [
  {
    id: 'medical-sets',
    label: '/orca/medical-sets（診療セット）',
    hint: 'Trial 閉鎖のため stub 応答固定（Api_Result=79）',
    stubFixed: true,
    defaultPayload: {
      requestNumber: '01',
      patientId: '00002',
      sets: [
        {
          medicalClass: '120',
          medicationCode: '112007410',
          medicationName: 'テスト処方',
          quantity: '1',
          note: 'stub',
        },
      ],
    },
  },
  {
    id: 'tensu-sync',
    label: '/orca/tensu/sync（点数マスタ同期）',
    hint: 'Trial 未開放のため stub 応答固定（Api_Result=79）',
    stubFixed: true,
    defaultPayload: {
      requestNumber: '01',
      medications: [
        {
          medicationCode: '112007410',
          medicationName: 'テスト薬',
          kanaName: 'テストヤク',
          unit: '錠',
          point: '10',
          startDate: today,
          endDate: '',
        },
      ],
    },
  },
  {
    id: 'birth-delivery',
    label: '/orca/birth-delivery（出産育児一時金）',
    hint: 'Trial 閉鎖のため stub 応答固定（Api_Result=79）',
    stubFixed: true,
    defaultPayload: {
      requestNumber: '01',
      patientId: '00002',
      insuranceCombinationNumber: '0001',
      performDate: today,
      note: '出産育児一時金',
    },
  },
  {
    id: 'medical-records',
    label: '/orca/medical/records（診療記録取得）',
    hint: 'feature flag により stub/実データが切り替わります',
    defaultPayload: {
      patientId: '00002',
      fromDate: '',
      toDate: today,
      performMonths: 12,
      departmentCode: '01',
      sequentialNumber: '',
      insuranceCombinationNumber: '0001',
      includeVisitStatus: false,
    },
  },
  {
    id: 'patient-mutation',
    label: '/orca/patient/mutation（患者作成/更新/削除）',
    hint: 'delete は Trial 閉鎖のため stub 応答',
    defaultPayload: {
      operation: 'create',
      patient: {
        patientId: '00002',
        wholeName: 'テスト 太郎',
        wholeNameKana: 'テスト タロウ',
        birthDate: '1980-01-01',
        sex: '1',
        telephone: '',
        mobilePhone: '',
        zipCode: '',
        addressLine: '',
      },
    },
  },
  {
    id: 'chart-subjectives',
    label: '/orca/chart/subjectives（主訴登録）',
    hint: 'feature flag により stub/実データが切り替わります',
    defaultPayload: {
      patientId: '00002',
      performDate: today,
      soapCategory: 'S',
      physicianCode: '10001',
      body: '主訴テスト',
    },
  },
];

const buildInternalWrapperState = (options: OrcaInternalWrapperOption[]) =>
  options.reduce<Record<OrcaInternalWrapperEndpoint, OrcaInternalWrapperFormState>>((acc, option) => {
    acc[option.id] = {
      payload: JSON.stringify(option.defaultPayload, null, 2),
      result: null,
    };
    return acc;
  }, {} as Record<OrcaInternalWrapperEndpoint, OrcaInternalWrapperFormState>);

const resolveXmlProxyOption = (endpoint: OrcaXmlProxyEndpoint) =>
  ORCA_XML_PROXY_OPTIONS.find((option) => option.id === endpoint) ?? ORCA_XML_PROXY_OPTIONS[0];
const resolveInternalWrapperOption = (
  options: OrcaInternalWrapperOption[],
  endpoint: OrcaInternalWrapperEndpoint,
) => options.find((option) => option.id === endpoint) ?? options[0];

const formatDateInput = (date: Date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const formatTimeAgo = (iso?: string) => {
  if (!iso) return '―';
  const delta = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(delta / 60000);
  if (minutes < 1) return '1分以内';
  return `${minutes}分前`;
};

const formatTimestamp = (iso?: string) => {
  if (!iso) return '―';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString('ja-JP', { hour12: false });
};

const formatTimestampWithAgo = (iso?: string) => {
  if (!iso) return '―';
  return `${formatTimestamp(iso)}（${formatTimeAgo(iso)}）`;
};

const formatDateTime = (date?: string, time?: string) => {
  if (!date && !time) return '―';
  if (!time) return date ?? '―';
  if (!date) return time ?? '―';
  return `${date} ${time}`;
};

const QUEUE_DELAY_WARNING_MS = ORCA_QUEUE_STALL_THRESHOLD_MS;

const getStringValue = (value: unknown) => (typeof value === 'string' ? value : undefined);

const extractPatientIdFromPayload = (
  endpoint: OrcaInternalWrapperEndpoint,
  payload?: Record<string, unknown>,
) => {
  if (!payload) return undefined;
  if (endpoint === 'patient-mutation') {
    const patient = (payload.patient ?? {}) as Record<string, unknown>;
    return getStringValue(patient.patientId);
  }
  return getStringValue(payload.patientId);
};

const extractOperationFromPayload = (payload?: Record<string, unknown>) =>
  payload ? getStringValue(payload.operation) : undefined;

const toStatusClass = (status: string) => {
  if (status === 'delivered') return 'admin-queue__status admin-queue__status--delivered';
  if (status === 'failed') return 'admin-queue__status admin-queue__status--failed';
  return 'admin-queue__status admin-queue__status--pending';
};

const normalizeEnvironmentLabel = (raw?: string) => {
  if (!raw) return undefined;
  const value = raw.toLowerCase();
  if (value.includes('stage')) return 'stage';
  if (value.includes('dev')) return 'dev';
  if (value.includes('prod')) return 'prod';
  if (value.includes('preview')) return 'preview';
  return raw;
};

const resolveDeliveryFlagState = (
  configValue: boolean | string | undefined,
  deliveryValue: boolean | string | undefined,
): AdminDeliveryFlagState => {
  if (deliveryValue === undefined && configValue === undefined) return 'unknown';
  if (deliveryValue === undefined) return 'pending';
  if (configValue === undefined) return 'applied';
  return deliveryValue === configValue ? 'applied' : 'pending';
};

const buildChartsDeliveryStatus = (
  config?: Partial<AdminConfigPayload>,
  delivery?: Partial<AdminConfigPayload>,
): AdminDeliveryStatus => ({
  chartsDisplayEnabled: resolveDeliveryFlagState(config?.chartsDisplayEnabled, delivery?.chartsDisplayEnabled),
  chartsSendEnabled: resolveDeliveryFlagState(config?.chartsSendEnabled, delivery?.chartsSendEnabled),
  chartsMasterSource: resolveDeliveryFlagState(config?.chartsMasterSource, delivery?.chartsMasterSource),
});

const summarizeDeliveryStatus = (status: AdminDeliveryStatus) => {
  const states = Object.values(status).filter(Boolean) as AdminDeliveryFlagState[];
  const hasPending = states.some((state) => state === 'pending');
  const hasApplied = states.some((state) => state === 'applied');
  return {
    hasPending,
    summary: hasPending ? '次回リロード' : hasApplied ? '即時反映' : '不明',
  };
};

const formatDeliveryValue = (value: boolean | string | undefined) => (value === undefined ? '―' : String(value));
const DEFAULT_DELIVERY_SECTION: DeliverySection = 'dashboard';
const isDeliverySection = (value: string | null): value is DeliverySection =>
  DELIVERY_SECTION_ITEMS.some((item) => item.id === value);

const buildMedicationTemplateXml = (baseDate: string) =>
  [
    '<data>',
    '  <medicatonmodreq type="record">',
    '    <Request_Number type="string">01</Request_Number>',
    `    <Base_Date type="string">${baseDate}</Base_Date>`,
    '  </medicatonmodreq>',
    '</data>',
  ].join('\n');

export function AdministrationPage({ runId, role }: AdministrationPageProps) {
  const isSystemAdmin = isSystemAdminRole(role);
  const session = useSession();
  const { enqueue } = useAppToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = (() => {
    const tab = searchParams.get('tab');
    if (tab === 'access') return 'orca-users';
    if (tab === 'orca-users' || tab === 'master-updates') return tab;
    return 'delivery';
  })() as AdministrationTab;
  const activeDeliverySection = (() => {
    const section = searchParams.get('section');
    if (isDeliverySection(section)) return section;
    return DEFAULT_DELIVERY_SECTION;
  })();
  const handleTabChange = (next: AdministrationTab) => {
    setSearchParams(
      (prev) => {
        const updated = new URLSearchParams(prev);
        if (next === 'delivery') {
          updated.delete('tab');
          if (!isDeliverySection(updated.get('section'))) {
            updated.set('section', DEFAULT_DELIVERY_SECTION);
          }
        } else {
          updated.set('tab', next);
          updated.delete('section');
        }
        return updated;
      },
      { replace: false },
    );
  };
  const handleDeliverySectionChange = (next: DeliverySection) => {
    setSearchParams(
      (prev) => {
        const updated = new URLSearchParams(prev);
        updated.delete('tab');
        updated.set('section', next);
        return updated;
      },
      { replace: false },
    );
  };
  const appliedMeta = useRef<Partial<AuthServiceFlags>>({});
  const guardLogRef = useRef<{ runId?: string; role?: string }>({});
  const forbiddenLogRef = useRef<{ runId?: string; noted?: boolean }>({});
  const { flags, bumpRunId, setCacheHit, setMissingMaster, setDataSourceTransition, setFallbackUsed } = useAuthService();
  const today = useMemo(() => formatDateInput(new Date()), []);
  const internalWrapperOptions = useMemo(() => buildInternalWrapperOptions(today), [today]);
  const [form, setForm] = useState<AdminConfigPayload>(DEFAULT_FORM);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [orcaConnectionForm, setOrcaConnectionForm] = useState<OrcaConnectionFormState>(DEFAULT_ORCA_CONNECTION_FORM);
  const [orcaConnectionSavedSnapshot, setOrcaConnectionSavedSnapshot] = useState<OrcaConnectionFormState | null>(null);
  const [orcaConnectionFieldErrors, setOrcaConnectionFieldErrors] = useState<{
    serverUrl?: string;
    port?: string;
    username?: string;
    password?: string;
    clientCertificate?: string;
    clientCertificatePassphrase?: string;
  }>({});
  const [orcaConnectionFeedback, setOrcaConnectionFeedback] = useState<Feedback | null>(null);
  const [orcaConnectionTestResult, setOrcaConnectionTestResult] = useState<OrcaConnectionTestState>(null);
  const [saveConfirmOpen, setSaveConfirmOpen] = useState(false);
  const [discardConfirmTarget, setDiscardConfirmTarget] = useState<OrcaQueueEntry | null>(null);
  const [connectivitySummary, setConnectivitySummary] = useState<{
    testedAt: string;
    success: number;
    failure: number;
    details: string[];
  } | null>(null);
  const [masterLastUpdateResult, setMasterLastUpdateResult] = useState<MasterLastUpdateResponse | null>(null);
  const [medicationSyncResult, setMedicationSyncResult] = useState<MedicationModResponse | null>(null);
  const [medicationSyncClass, setMedicationSyncClass] = useState('01');
  const [medicationTemplateBaseDate, setMedicationTemplateBaseDate] = useState(() => today);
  const [medicationSyncXml, setMedicationSyncXml] = useState(() => buildMedicationTemplateXml(today));
  const [systemInfoResult, setSystemInfoResult] = useState<SystemInfoResponse | null>(null);
  const [systemDailyResult, setSystemDailyResult] = useState<SystemDailyResponse | null>(null);
  const [systemBaseDate, setSystemBaseDate] = useState(() => today);
  const [medicalSetQuery, setMedicalSetQuery] = useState<MedicalSetSearchPayload>(() => ({
    baseDate: today,
    setCode: '',
    setName: '',
    startDate: today,
    endDate: '',
    inOut: 'O',
  }));
  const [medicalSetResult, setMedicalSetResult] = useState<MedicalSetResponse | null>(null);
  const [orcaXmlProxyTarget, setOrcaXmlProxyTarget] = useState<OrcaXmlProxyEndpoint>('acceptlstv2');
  const [orcaXmlProxyState, setOrcaXmlProxyState] = useState<Record<OrcaXmlProxyEndpoint, OrcaXmlProxyFormState>>(
    () => ({
      acceptlstv2: {
        xml: buildAcceptListRequestXml(),
        classCode: resolveXmlProxyOption('acceptlstv2').defaultClass ?? '01',
        result: null,
      },
      system01lstv2: {
        xml: buildSystemListRequestXml(resolveXmlProxyOption('system01lstv2').defaultClass ?? '02'),
        classCode: resolveXmlProxyOption('system01lstv2').defaultClass ?? '02',
        result: null,
      },
      manageusersv2: {
        xml: buildManageUsersRequestXml(),
        result: null,
      },
      insprogetv2: {
        xml: buildInsuranceProviderRequestXml(),
        result: null,
      },
    }),
  );
  const [orcaInternalWrapperTarget, setOrcaInternalWrapperTarget] = useState<OrcaInternalWrapperEndpoint>('medical-sets');
  const [orcaInternalWrapperState, setOrcaInternalWrapperState] = useState<
    Record<OrcaInternalWrapperEndpoint, OrcaInternalWrapperFormState>
  >(() => buildInternalWrapperState(internalWrapperOptions));
  const [masterUpdateLabel, setMasterUpdateLabel] = useState<'初回取得' | '更新あり' | '更新なし'>('初回取得');
  const lastMasterSignatureRef = useRef<string | undefined>(undefined);
  const queryClient = useQueryClient();

  const configQuery = useQuery({
    queryKey: ['admin-config'],
    queryFn: fetchEffectiveAdminConfig,
    staleTime: 60_000,
  });

  const orcaConnectionQuery = useQuery({
    queryKey: ['admin-orca-connection'],
    queryFn: fetchOrcaConnectionConfig,
    staleTime: 60_000,
    enabled: isSystemAdmin && activeTab === 'delivery',
  });
  const orcaConnectionAuthStatus = orcaConnectionQuery.data?.status;
  const orcaConnectionAccessVerified =
    isSystemAdmin && activeTab === 'delivery' && orcaConnectionAuthStatus === 200;
  const orcaConnectionAuthBlocked =
    isSystemAdmin &&
    activeTab === 'delivery' &&
    (orcaConnectionAuthStatus === 401 || orcaConnectionAuthStatus === 403);

  const queueQuery = useQuery({
    queryKey: ['orca-queue'],
    queryFn: () => fetchOrcaQueue(),
    refetchInterval: 60_000,
  });

  const latestRunId = configQuery.data?.runId ?? queueQuery.data?.runId ?? runId;
  const resolvedRunId = resolveRunId(latestRunId ?? flags.runId);
  const panelRunId = resolvedRunId ?? runId;
  const infoLive = resolveAriaLive('info');
  const envFallback = normalizeEnvironmentLabel(
    (import.meta.env as Record<string, string | undefined>).VITE_ENVIRONMENT ??
      (import.meta.env as Record<string, string | undefined>).VITE_DEPLOY_ENV ??
      (import.meta.env.MODE === 'development' ? 'dev' : import.meta.env.MODE),
  );
  const environmentLabel = normalizeEnvironmentLabel(configQuery.data?.environment) ?? envFallback ?? 'unknown';
  const warningThresholdMinutes = Math.round(QUEUE_DELAY_WARNING_MS / 60000);
  const rawConfig = configQuery.data?.rawConfig ?? configQuery.data;
  const rawDelivery = configQuery.data?.rawDelivery;
  const latestAuditEvent = useMemo(() => {
    const snapshot = getAuditEventLog();
    const latest = snapshot[snapshot.length - 1];
    return (latest?.payload as Record<string, unknown> | undefined) ?? undefined;
  }, [configQuery.data?.runId, feedback?.message, queueQuery.data?.runId, resolvedRunId]);
  const guardMessageId = 'admin-guard-message';
  const guardDetailsId = 'admin-guard-details';
  const actorId = `${session.facilityId}:${session.userId}`;
  const showAdminDebugToggles = import.meta.env.VITE_ENABLE_ADMIN_DEBUG === '1' && isSystemAdmin;
  const toErrorMessage = (error: unknown) => (error instanceof Error ? error.message : String(error));
  const handleCopyValue = useCallback(
    async (value: string, label: string) => {
      try {
        await copyTextToClipboard(value);
        enqueue({ tone: 'success', message: `${label} をコピーしました`, detail: value, durationMs: 1800 });
      } catch {
        enqueue({ tone: 'error', message: `${label} のコピーに失敗しました` });
      }
    },
    [enqueue],
  );
  const requestTemplate = useMemo(
    () =>
      [
        '【system_admin 権限依頼テンプレート】',
        `施設ID: ${session.facilityId}`,
        `環境: ${environmentLabel}`,
        '作業内容: Administration の設定変更/配信',
        '影響範囲: WebORCA接続設定・配信設定・ORCA queue 操作',
      ].join('\n'),
    [environmentLabel, session.facilityId],
  );
  const buildConnectionSnapshot = useCallback(
    (formState: OrcaConnectionFormState): OrcaConnectionFormState => ({
      ...formState,
      password: '',
      clientCertificateFile: null,
      clientCertificatePassphrase: '',
      caCertificateFile: null,
    }),
    [],
  );
  const orcaConnectionDirty = useMemo(() => {
    if (!orcaConnectionSavedSnapshot) return false;
    return JSON.stringify(buildConnectionSnapshot(orcaConnectionForm)) !== JSON.stringify(orcaConnectionSavedSnapshot);
  }, [buildConnectionSnapshot, orcaConnectionForm, orcaConnectionSavedSnapshot]);
  const configDirty = useMemo(() => {
    if (!rawConfig) return false;
    return (
      rawConfig.orcaEndpoint !== form.orcaEndpoint ||
      rawConfig.mswEnabled !== form.mswEnabled ||
      rawConfig.useMockOrcaQueue !== form.useMockOrcaQueue ||
      rawConfig.verifyAdminDelivery !== form.verifyAdminDelivery ||
      rawConfig.chartsDisplayEnabled !== form.chartsDisplayEnabled ||
      rawConfig.chartsSendEnabled !== form.chartsSendEnabled ||
      rawConfig.chartsMasterSource !== form.chartsMasterSource
    );
  }, [form, rawConfig]);
  const configDiffRows = useMemo(
    () =>
      [
        { key: 'orcaEndpoint', label: 'orcaEndpoint', before: rawConfig?.orcaEndpoint, after: form.orcaEndpoint },
        { key: 'mswEnabled', label: 'mswEnabled', before: rawConfig?.mswEnabled, after: form.mswEnabled },
        {
          key: 'useMockOrcaQueue',
          label: 'useMockOrcaQueue',
          before: rawConfig?.useMockOrcaQueue,
          after: form.useMockOrcaQueue,
        },
        {
          key: 'verifyAdminDelivery',
          label: 'verifyAdminDelivery',
          before: rawConfig?.verifyAdminDelivery,
          after: form.verifyAdminDelivery,
        },
        {
          key: 'chartsDisplayEnabled',
          label: 'chartsDisplayEnabled',
          before: rawConfig?.chartsDisplayEnabled,
          after: form.chartsDisplayEnabled,
        },
        {
          key: 'chartsSendEnabled',
          label: 'chartsSendEnabled',
          before: rawConfig?.chartsSendEnabled,
          after: form.chartsSendEnabled,
        },
        {
          key: 'chartsMasterSource',
          label: 'chartsMasterSource',
          before: rawConfig?.chartsMasterSource,
          after: form.chartsMasterSource,
        },
      ].filter((row) => row.before !== row.after),
    [form, rawConfig],
  );
  const countVersionDiffs = (versions?: Array<{ localVersion?: string; newVersion?: string }>) =>
    versions?.filter((entry) => entry.localVersion && entry.newVersion && entry.localVersion !== entry.newVersion).length ?? 0;
  const resolveStatusTone = (result: { ok: boolean } | null, isPending: boolean) => {
    if (isPending) return 'pending';
    if (!result) return 'idle';
    return result.ok ? 'ok' : 'error';
  };
  const resolveStatusLabel = (result: { ok: boolean; apiResult?: string } | null, isPending: boolean) => {
    if (isPending) return '実行中';
    if (!result) return '未実行';
    if (result.ok) return `OK${result.apiResult ? ` (${result.apiResult})` : ''}`;
    return 'NG';
  };
  const isApiResultOk = (apiResult?: string) => (apiResult ? apiResult.startsWith('00') : false);
  const resolveHealthTone = (
    info: SystemInfoResponse | null,
    daily: SystemDailyResponse | null,
    isPending: boolean,
  ) => {
    if (isPending) return 'pending';
    if (!info && !daily) return 'idle';
    if (info && !info.ok) return 'error';
    if (daily && !daily.ok) return 'error';
    if (info && info.apiResult && !isApiResultOk(info.apiResult)) return 'warn';
    if (daily && daily.apiResult && !isApiResultOk(daily.apiResult)) return 'warn';
    return 'ok';
  };
  const resolveHealthLabel = (
    info: SystemInfoResponse | null,
    daily: SystemDailyResponse | null,
    isPending: boolean,
  ) => {
    const tone = resolveHealthTone(info, daily, isPending);
    if (tone === 'pending') return '実行中';
    if (tone === 'idle') return '未実行';
    if (tone === 'error') return 'NG';
    if (tone === 'warn') return 'Warn';
    return 'OK';
  };
  const buildMasterSignature = (result: MasterLastUpdateResponse | null) => {
    if (!result) return undefined;
    const versions = result.versions
      .map((entry) => `${entry.name ?? ''}:${entry.localVersion ?? ''}:${entry.newVersion ?? ''}`)
      .join('|');
    return `${result.lastUpdateDate ?? ''}|${versions}`;
  };
  const updateOrcaXmlProxyState = useCallback(
    (endpoint: OrcaXmlProxyEndpoint, patch: Partial<OrcaXmlProxyFormState>) => {
      setOrcaXmlProxyState((prev) => ({
        ...prev,
        [endpoint]: {
          ...prev[endpoint],
          ...patch,
        },
      }));
    },
    [],
  );
  const updateOrcaInternalWrapperState = useCallback(
    (endpoint: OrcaInternalWrapperEndpoint, patch: Partial<OrcaInternalWrapperFormState>) => {
      setOrcaInternalWrapperState((prev) => ({
        ...prev,
        [endpoint]: {
          ...prev[endpoint],
          ...patch,
        },
      }));
    },
    [],
  );
  const buildXmlProxyTemplate = useCallback((endpoint: OrcaXmlProxyEndpoint, classCode?: string) => {
    switch (endpoint) {
      case 'acceptlstv2':
        return buildAcceptListRequestXml();
      case 'system01lstv2':
        return buildSystemListRequestXml(classCode);
      case 'manageusersv2':
        return buildManageUsersRequestXml();
      case 'insprogetv2':
        return buildInsuranceProviderRequestXml();
      default:
        return '<data></data>';
    }
  }, []);
  const xmlProxyOption = resolveXmlProxyOption(orcaXmlProxyTarget);
  const currentXmlProxy = orcaXmlProxyState[orcaXmlProxyTarget];
  const xmlProxyResult = orcaXmlProxyState[orcaXmlProxyTarget]?.result ?? null;
  const internalWrapperOption = resolveInternalWrapperOption(internalWrapperOptions, orcaInternalWrapperTarget);
  const currentInternalWrapper = orcaInternalWrapperState[orcaInternalWrapperTarget];
  const internalWrapperResult = currentInternalWrapper?.result ?? null;

  const logGuardEvent = useCallback(
    (action: GuardAction, detail?: string) => {
      logAuditEvent({
        runId: resolvedRunId,
        source: 'admin/guard',
        note: action === 'access' ? 'admin access restricted' : 'admin action blocked',
        payload: {
          operation: action,
          actor: actorId,
          role,
          requiredRole: 'system_admin',
          environment: environmentLabel,
          detail,
          fallback: ['再ログイン', '管理者へ依頼', 'Receptionで確認'],
        },
      });
      logUiState({
        action: 'navigate',
        screen: 'administration',
        controlId: 'admin-guard',
        runId: resolvedRunId,
        details: { operation: action, role, detail, requiredRole: 'system_admin' },
      });
    },
    [actorId, environmentLabel, resolvedRunId, role],
  );

  const reportGuardedAction = useCallback(
    (action: GuardAction, detail?: string) => {
      setFeedback({ tone: 'warning', message: '権限がないため操作をブロックしました。管理者へ依頼してください。' });
      logGuardEvent(action, detail);
    },
    [logGuardEvent],
  );

  useEffect(() => {
    if (isSystemAdmin) return;
    if (guardLogRef.current.runId === resolvedRunId && guardLogRef.current.role === role) return;
    guardLogRef.current = { runId: resolvedRunId, role };
    logGuardEvent('access', 'read-only view');
  }, [isSystemAdmin, logGuardEvent, resolvedRunId, role]);

  useEffect(() => {
    const data = configQuery.data;
    if (!data) return;
    setForm((prev) => ({
      ...prev,
      orcaEndpoint: data.orcaEndpoint || prev.orcaEndpoint,
      mswEnabled: data.mswEnabled ?? prev.mswEnabled,
      useMockOrcaQueue: data.useMockOrcaQueue ?? prev.useMockOrcaQueue,
      verifyAdminDelivery: data.verifyAdminDelivery ?? prev.verifyAdminDelivery,
      chartsDisplayEnabled: data.chartsDisplayEnabled ?? prev.chartsDisplayEnabled,
      chartsSendEnabled: data.chartsSendEnabled ?? prev.chartsSendEnabled,
      chartsMasterSource: data.chartsMasterSource ?? prev.chartsMasterSource,
    }));
    persistHeaderFlags({
      useMockOrcaQueue: data.useMockOrcaQueue,
      verifyAdminDelivery: data.verifyAdminDelivery,
    });
    appliedMeta.current = applyAuthServicePatch(
      { runId: data.runId },
      appliedMeta.current,
      { bumpRunId, setCacheHit, setMissingMaster, setDataSourceTransition, setFallbackUsed },
    );
  }, [bumpRunId, configQuery.data, setCacheHit, setDataSourceTransition, setFallbackUsed, setMissingMaster]);

  useEffect(() => {
    const data = orcaConnectionQuery.data;
    if (!data) return;
    if (!data.ok) {
      if (data.error) {
        setOrcaConnectionFeedback({ tone: 'warning', message: `WebORCA 接続設定の取得に失敗しました: ${data.error}` });
      }
      return;
    }
    const next = buildConnectionSnapshot({
      ...orcaConnectionForm,
      useWeborca: data.useWeborca ?? orcaConnectionForm.useWeborca,
      serverUrl: data.serverUrl ?? orcaConnectionForm.serverUrl,
      port: data.port !== undefined ? String(data.port) : orcaConnectionForm.port,
      username: data.username ?? orcaConnectionForm.username,
      password: '',
      passwordConfigured: Boolean(data.passwordConfigured),
      passwordUpdatedAt: data.passwordUpdatedAt,
      clientAuthEnabled: Boolean(data.clientAuthEnabled),
      clientCertificateFile: null,
      clientCertificateConfigured: Boolean(data.clientCertificateConfigured),
      clientCertificateFileName: data.clientCertificateFileName,
      clientCertificateUploadedAt: data.clientCertificateUploadedAt,
      clientCertificatePassphrase: '',
      clientCertificatePassphraseConfigured: Boolean(data.clientCertificatePassphraseConfigured),
      clientCertificatePassphraseUpdatedAt: data.clientCertificatePassphraseUpdatedAt,
      caCertificateFile: null,
      caCertificateConfigured: Boolean(data.caCertificateConfigured),
      caCertificateFileName: data.caCertificateFileName,
      caCertificateUploadedAt: data.caCertificateUploadedAt,
      updatedAt: data.updatedAt,
      auditSummary: data.auditSummary,
    });
    setOrcaConnectionForm(next);
    setOrcaConnectionSavedSnapshot(next);
    setOrcaConnectionFieldErrors({});
    setOrcaConnectionFeedback(null);
  }, [buildConnectionSnapshot, orcaConnectionForm, orcaConnectionQuery.data]);

  const configMutation = useMutation({
    mutationFn: saveAdminConfig,
    onSuccess: (data) => {
      setSaveConfirmOpen(false);
      setFeedback({ tone: 'success', message: '設定を保存し、配信をブロードキャストしました。' });
      persistHeaderFlags({
        useMockOrcaQueue: data.useMockOrcaQueue,
        verifyAdminDelivery: data.verifyAdminDelivery,
      });
      const nextChartsFlags = {
        chartsDisplayEnabled: data.chartsDisplayEnabled ?? form.chartsDisplayEnabled,
        chartsSendEnabled: data.chartsSendEnabled ?? form.chartsSendEnabled,
        chartsMasterSource: data.chartsMasterSource ?? form.chartsMasterSource,
      };
      const nextDeliveryStatus = buildChartsDeliveryStatus(nextChartsFlags, rawDelivery);
      const deliveredAt = data.deliveredAt ?? rawDelivery?.deliveredAt;
      const resolvedEnvironment = normalizeEnvironmentLabel(data.environment) ?? environmentLabel;
      const broadcast = publishAdminBroadcast({
        runId: data.runId ?? runId,
        facilityId: session.facilityId,
        userId: session.userId,
        action: 'config',
        deliveryId: data.deliveryId,
        deliveryVersion: data.deliveryVersion,
        deliveryEtag: data.deliveryEtag ?? data.deliveryVersion,
        deliveredAt,
        queueMode: data.useMockOrcaQueue ? 'mock' : 'live',
        verifyAdminDelivery: data.verifyAdminDelivery,
        chartsDisplayEnabled: nextChartsFlags.chartsDisplayEnabled,
        chartsSendEnabled: nextChartsFlags.chartsSendEnabled,
        chartsMasterSource: nextChartsFlags.chartsMasterSource,
        environment: resolvedEnvironment,
        deliveryStatus: nextDeliveryStatus,
        note: data.note,
        source: data.source,
      });
      logAuditEvent({
        runId: data.runId ?? runId,
        source: 'admin/delivery',
        note: data.note ?? 'admin delivery saved',
        payload: {
          operation: 'save',
          actor: `${session.facilityId}:${session.userId}`,
          role: session.role,
          environment: resolvedEnvironment,
          delivery: {
            deliveryId: data.deliveryId,
            deliveryVersion: data.deliveryVersion,
            deliveryEtag: data.deliveryEtag ?? data.deliveryVersion,
            deliveredAt,
            deliveryMode: data.deliveryMode ?? configQuery.data?.deliveryMode,
            source: data.source,
            verified: data.verified,
          },
          flags: {
            ...form,
            ...nextChartsFlags,
          },
          broadcast,
          raw: {
            config: rawConfig,
            delivery: rawDelivery,
          },
        },
      });
      logUiState({
        action: 'config_delivery',
        screen: 'administration',
        controlId: 'save-config',
        runId: data.runId ?? runId,
        dataSourceTransition: undefined,
      });
    },
    onError: () => {
      setSaveConfirmOpen(false);
      setFeedback({ tone: 'error', message: '保存に失敗しました。再度お試しください。' });
    },
  });

  const orcaConnectionSaveMutation = useMutation({
    mutationFn: saveOrcaConnectionConfig,
    onSuccess: (data) => {
      if (!data.ok) {
        setOrcaConnectionFeedback({ tone: 'error', message: data.error ?? 'WebORCA 接続設定の保存に失敗しました。' });
        return;
      }
      setOrcaConnectionFeedback({ tone: 'success', message: 'WebORCA 接続設定を保存しました。' });
      const next = buildConnectionSnapshot({
        ...orcaConnectionForm,
        useWeborca: data.useWeborca ?? orcaConnectionForm.useWeborca,
        serverUrl: data.serverUrl ?? orcaConnectionForm.serverUrl,
        port: data.port !== undefined ? String(data.port) : orcaConnectionForm.port,
        username: data.username ?? orcaConnectionForm.username,
        password: '',
        passwordConfigured: Boolean(data.passwordConfigured),
        passwordUpdatedAt: data.passwordUpdatedAt,
        clientAuthEnabled: Boolean(data.clientAuthEnabled),
        clientCertificateFile: null,
        clientCertificateConfigured: Boolean(data.clientCertificateConfigured),
        clientCertificateFileName: data.clientCertificateFileName,
        clientCertificateUploadedAt: data.clientCertificateUploadedAt,
        clientCertificatePassphrase: '',
        clientCertificatePassphraseConfigured: Boolean(data.clientCertificatePassphraseConfigured),
        clientCertificatePassphraseUpdatedAt: data.clientCertificatePassphraseUpdatedAt,
        caCertificateFile: null,
        caCertificateConfigured: Boolean(data.caCertificateConfigured),
        caCertificateFileName: data.caCertificateFileName,
        caCertificateUploadedAt: data.caCertificateUploadedAt,
        updatedAt: data.updatedAt,
        auditSummary: data.auditSummary,
      });
      setOrcaConnectionForm(next);
      setOrcaConnectionSavedSnapshot(next);
      setOrcaConnectionFieldErrors({});
      queryClient.invalidateQueries({ queryKey: ['admin-orca-connection'] });
    },
    onError: (error) => {
      setOrcaConnectionFeedback({
        tone: 'error',
        message: `WebORCA 接続設定の保存に失敗しました: ${toErrorMessage(error)}`,
      });
    },
  });

  const orcaConnectionTestMutation = useMutation({
    mutationFn: testOrcaConnection,
    onSuccess: (data) => {
      setOrcaConnectionTestResult(data);
      if (data.ok) {
        setOrcaConnectionFeedback({
          tone: 'success',
          message: `接続テストに成功しました（HTTP ${data.orcaHttpStatus ?? '―'} / Api_Result=${data.apiResult ?? '―'}）。`,
        });
      } else {
        setOrcaConnectionFeedback({
          tone: 'error',
          message: data.error
            ? `接続テストに失敗しました: ${data.error}`
            : '接続テストに失敗しました。接続先・認証・証明書を確認してください。',
        });
      }
    },
    onError: (error) => {
      setOrcaConnectionTestResult({
        ok: false,
        status: 0,
        errorCategory: 'unknown',
        error: toErrorMessage(error),
      });
      setOrcaConnectionFeedback({
        tone: 'error',
        message: `接続テストに失敗しました: ${toErrorMessage(error)}`,
      });
    },
  });

  const queueMutation = useMutation({
    mutationFn: (params: { kind: 'retry' | 'discard'; patientId: string }) => {
      if (params.kind === 'retry') return retryOrcaQueue(params.patientId);
      return discardOrcaQueue(params.patientId);
    },
    onSuccess: (data, variables) => {
      queryClient.setQueryData(['orca-queue'], data);
      const queueOperation = variables.kind;
      const queueSummary = buildOrcaQueueWarningSummary(data.queue);
      publishAdminBroadcast({
        runId: data.runId ?? runId,
        facilityId: session.facilityId,
        userId: session.userId,
        action: 'queue',
        queueOperation,
        queueResult: 'success',
        queuePatientId: variables.patientId,
        queueStatus: queueSummary,
        deliveryId: variables.patientId,
        deliveryVersion: data.source,
        deliveredAt: new Date().toISOString(),
        queueMode: data.source,
        verifyAdminDelivery: data.verifyAdminDelivery,
        environment: environmentLabel,
        note: queueOperation === 'retry' ? '再送完了' : '破棄完了',
      });
      logAuditEvent({
        runId: data.runId ?? runId,
        source: 'admin/delivery',
        note: `orca queue ${queueOperation}`,
        payload: {
          operation: queueOperation,
          result: 'success',
          patientId: variables.patientId,
          environment: environmentLabel,
          queueMode: data.source,
          queue: data.queue,
          queueSnapshot: queueSummary,
          warningThresholdMs: QUEUE_DELAY_WARNING_MS,
        },
      });
      logAuditEvent({
        runId: data.runId ?? runId,
        source: 'orca/queue',
        note: queueOperation,
        patientId: variables.patientId,
        payload: {
          patientId: variables.patientId,
          queue: data.queue,
          operation: queueOperation,
          result: 'success',
          queueSnapshot: queueSummary,
          warningThresholdMs: QUEUE_DELAY_WARNING_MS,
        },
      });
      setFeedback({
        tone: 'info',
        message: queueOperation === 'retry' ? '再送リクエストを送信しました。' : 'キューエントリを破棄しました。',
      });
    },
    onError: (error, variables) => {
      const queueSnapshotEntries = queueQuery.data?.queue ?? [];
      const queueSummary = buildOrcaQueueWarningSummary(queueSnapshotEntries);
      const errorMessage = error instanceof Error ? error.message : String(error);
      const queueOperation = variables.kind;
      publishAdminBroadcast({
        runId: resolvedRunId,
        facilityId: session.facilityId,
        userId: session.userId,
        action: 'queue',
        queueOperation,
        queueResult: 'failure',
        queuePatientId: variables.patientId,
        queueStatus: queueSummary,
        deliveredAt: new Date().toISOString(),
        queueMode: queueQuery.data?.source ?? (form.useMockOrcaQueue ? 'mock' : 'live'),
        verifyAdminDelivery: queueQuery.data?.verifyAdminDelivery ?? form.verifyAdminDelivery,
        environment: environmentLabel,
        note: queueOperation === 'retry' ? '再送失敗' : '破棄失敗',
      });
      logAuditEvent({
        runId: resolvedRunId,
        source: 'admin/delivery',
        note: `orca queue ${queueOperation} failed`,
        payload: {
          operation: queueOperation,
          result: 'failure',
          patientId: variables.patientId,
          environment: environmentLabel,
          queueMode: queueQuery.data?.source ?? (form.useMockOrcaQueue ? 'mock' : 'live'),
          error: errorMessage,
          queueSnapshot: queueSummary,
          warningThresholdMs: QUEUE_DELAY_WARNING_MS,
        },
      });
      logAuditEvent({
        runId: resolvedRunId,
        source: 'orca/queue',
        note: `${queueOperation} failed`,
        patientId: variables.patientId,
        payload: {
          patientId: variables.patientId,
          operation: queueOperation,
          result: 'failure',
          error: errorMessage,
          queueSnapshot: queueSummary,
          warningThresholdMs: QUEUE_DELAY_WARNING_MS,
        },
      });
      setFeedback({ tone: 'error', message: 'キュー操作に失敗しました。' });
    },
  });

  const masterLastUpdateMutation = useMutation({
    mutationFn: fetchMasterLastUpdate,
    onSuccess: (data) => {
      const currentSignature = buildMasterSignature(data);
      const nextLabel =
        !lastMasterSignatureRef.current
          ? '初回取得'
          : lastMasterSignatureRef.current === currentSignature
            ? '更新なし'
            : '更新あり';
      setMasterUpdateLabel(nextLabel);
      lastMasterSignatureRef.current = currentSignature;
      setMasterLastUpdateResult(data);
      logAuditEvent({
        runId: data.runId ?? resolvedRunId,
        source: 'admin/master',
        note: 'master last update checked',
        payload: {
          operation: 'masterlastupdatev3',
          actor: actorId,
          role,
          apiResult: data.apiResult,
          apiResultMessage: data.apiResultMessage,
          lastUpdateDate: data.lastUpdateDate,
          versionDiffs: countVersionDiffs(data.versions),
          updateLabel: nextLabel,
        },
      });
      logUiState({
        action: 'master_check',
        screen: 'administration',
        controlId: 'masterlastupdatev3',
        runId: data.runId ?? resolvedRunId,
      });
    },
    onError: (error) => {
      setMasterLastUpdateResult({
        ok: false,
        status: 0,
        apiResultMessage: undefined,
        apiResult: undefined,
        informationDate: undefined,
        informationTime: undefined,
        lastUpdateDate: undefined,
        versions: [],
        rawXml: '',
        error: toErrorMessage(error),
        runId: resolvedRunId,
      });
    },
  });

  const medicationModMutation = useMutation({
    mutationFn: (payload: { classCode: string; xml: string }) => syncMedicationMod(payload),
    onSuccess: (data) => {
      setMedicationSyncResult(data);
      logAuditEvent({
        runId: data.runId ?? resolvedRunId,
        source: 'admin/master',
        note: 'medication master sync requested',
        payload: {
          operation: 'medicatonmodv2',
          actor: actorId,
          role,
          apiResult: data.apiResult,
          apiResultMessage: data.apiResultMessage,
          classCode: medicationSyncClass,
        },
      });
      logUiState({
        action: 'master_sync',
        screen: 'administration',
        controlId: 'medicatonmodv2',
        runId: data.runId ?? resolvedRunId,
      });
    },
    onError: (error) => {
      setMedicationSyncResult({
        ok: false,
        status: 0,
        apiResultMessage: undefined,
        apiResult: undefined,
        rawXml: '',
        error: toErrorMessage(error),
        runId: resolvedRunId,
      });
    },
  });

  const systemHealthMutation = useMutation({
    mutationFn: async (params: { baseDate: string }) => {
      const [info, daily] = await Promise.all([fetchSystemInfo(), fetchSystemDaily(params.baseDate)]);
      return { info, daily };
    },
    onSuccess: ({ info, daily }) => {
      setSystemInfoResult(info);
      setSystemDailyResult(daily);
      logAuditEvent({
        runId: info.runId ?? daily.runId ?? resolvedRunId,
        source: 'admin/system',
        note: 'system health check',
        payload: {
          operation: 'system_health',
          actor: actorId,
          role,
          info: {
            apiResult: info.apiResult,
            apiResultMessage: info.apiResultMessage,
            jmaReceiptVersion: info.jmaReceiptVersion,
            databaseLocalVersion: info.databaseLocalVersion,
            databaseNewVersion: info.databaseNewVersion,
            versionDiffs: countVersionDiffs(info.versions),
          },
          daily: {
            apiResult: daily.apiResult,
            apiResultMessage: daily.apiResultMessage,
            baseDate: daily.baseDate,
          },
        },
      });
      logUiState({
        action: 'system_health',
        screen: 'administration',
        controlId: 'system-health',
        runId: info.runId ?? daily.runId ?? resolvedRunId,
      });
    },
    onError: (error) => {
      const message = toErrorMessage(error);
      setSystemInfoResult({
        ok: false,
        status: 0,
        apiResult: undefined,
        apiResultMessage: undefined,
        informationDate: undefined,
        informationTime: undefined,
        jmaReceiptVersion: undefined,
        databaseLocalVersion: undefined,
        databaseNewVersion: undefined,
        lastUpdateDate: undefined,
        versions: [],
        rawXml: '',
        error: message,
        runId: resolvedRunId,
      });
      setSystemDailyResult({
        ok: false,
        status: 0,
        apiResult: undefined,
        apiResultMessage: undefined,
        informationDate: undefined,
        informationTime: undefined,
        baseDate: systemBaseDate,
        rawXml: '',
        error: message,
        runId: resolvedRunId,
      });
    },
  });

  const medicalSetMutation = useMutation({
    mutationFn: (payload: MedicalSetSearchPayload) => fetchMedicalSet(payload),
    onSuccess: (data) => {
      setMedicalSetResult(data);
      logAuditEvent({
        runId: data.runId ?? resolvedRunId,
        source: 'admin/medical-set',
        note: 'medical set search',
        payload: {
          operation: 'medicalsetv2',
          actor: actorId,
          role,
          apiResult: data.apiResult,
          apiResultMessage: data.apiResultMessage,
          query: medicalSetQuery,
          results: data.entries.length,
        },
      });
      logUiState({
        action: 'medicalset_search',
        screen: 'administration',
        controlId: 'medicalsetv2',
        runId: data.runId ?? resolvedRunId,
      });
    },
    onError: (error) => {
      setMedicalSetResult({
        ok: false,
        status: 0,
        apiResult: undefined,
        apiResultMessage: undefined,
        baseDate: medicalSetQuery.baseDate,
        entries: [],
        rawXml: '',
        error: toErrorMessage(error),
        runId: resolvedRunId,
      });
    },
  });

  const xmlProxyMutation = useMutation({
    mutationFn: (payload: { endpoint: OrcaXmlProxyEndpoint; xml: string; classCode?: string }) =>
      postOrcaXmlProxy(payload),
    onSuccess: (result, variables) => {
      updateOrcaXmlProxyState(variables.endpoint, { result });
      logAuditEvent({
        runId: result.runId ?? resolvedRunId,
        source: 'admin/orca-xml-proxy',
        note: 'orca xml proxy request',
        payload: {
          operation: result.endpoint,
          actor: actorId,
          role,
          apiResult: result.apiResult,
          apiResultMessage: result.apiResultMessage,
          status: result.status,
          classCode: variables.classCode,
          missingTags: result.missingTags,
        },
      });
      logUiState({
        action: 'orca_xml_proxy',
        screen: 'administration',
        controlId: `orca-xml-proxy:${result.endpoint}`,
        runId: result.runId ?? resolvedRunId,
        details: {
          endpoint: result.endpoint,
          status: result.status,
          apiResult: result.apiResult,
          apiResultMessage: result.apiResultMessage,
        },
      });
    },
    onError: (error, variables) => {
      const message = toErrorMessage(error);
      const fallback: OrcaXmlProxyResponse = {
        ok: false,
        status: 0,
        endpoint: variables.endpoint,
        rawXml: '',
        error: message,
        runId: resolvedRunId,
      };
      updateOrcaXmlProxyState(variables.endpoint, { result: fallback });
    },
  });

  const internalWrapperMutation = useMutation({
    mutationFn: async (params: { endpoint: OrcaInternalWrapperEndpoint; payload: Record<string, unknown> }) => {
      try {
        switch (params.endpoint) {
          case 'medical-sets':
            return postMedicalSets(params.payload);
          case 'tensu-sync':
            return postTensuSync(params.payload);
          case 'birth-delivery':
            return postBirthDelivery(params.payload);
          case 'medical-records':
            return postMedicalRecords(params.payload);
          case 'patient-mutation':
            return postPatientMutation(params.payload);
          case 'chart-subjectives':
            return postSubjectiveEntry(params.payload);
          default:
            return {
              ok: false,
              status: 0,
              error: 'unsupported endpoint',
              runId: resolvedRunId,
              raw: {},
            } as OrcaInternalWrapperResult;
        }
      } catch (error) {
        return {
          ok: false,
          status: 0,
          error: toErrorMessage(error),
          runId: resolvedRunId,
          raw: {},
        } as OrcaInternalWrapperResult;
      }
    },
    onSuccess: (result, variables) => {
      updateOrcaInternalWrapperState(variables.endpoint, { result, parseError: undefined });
      const patientId = extractPatientIdFromPayload(variables.endpoint, variables.payload);
      const operation = extractOperationFromPayload(variables.payload);
      logAuditEvent({
        runId: result.runId ?? resolvedRunId,
        traceId: result.traceId,
        source: 'admin/orca-internal-wrapper',
        note: 'orca internal wrapper request',
        payload: {
          operation: variables.endpoint,
          actor: actorId,
          role,
          patientId,
          operationType: operation,
          apiResult: result.apiResult,
          apiResultMessage: result.apiResultMessage,
          status: result.status,
          stub: result.stub,
          missingMaster: result.missingMaster,
          fallbackUsed: result.fallbackUsed,
          messageDetail: result.messageDetail,
          warningMessage: result.warningMessage,
        },
      });
      logUiState({
        action: 'send',
        screen: 'administration',
        controlId: `orca-internal:${variables.endpoint}`,
        runId: result.runId ?? resolvedRunId,
        traceId: result.traceId,
        missingMaster: result.missingMaster,
        fallbackUsed: result.fallbackUsed,
        details: {
          endpoint: variables.endpoint,
          apiResult: result.apiResult,
          apiResultMessage: result.apiResultMessage,
          status: result.status,
          stub: result.stub,
          patientId,
          operation,
        },
      });
    },
  });

  const xmlProxyStatusTone = (() => {
    if (xmlProxyMutation.isPending) return 'pending';
    if (!xmlProxyResult) return 'idle';
    if (!xmlProxyResult.ok) return 'error';
    if (xmlProxyResult.apiResult && !isApiResultOk(xmlProxyResult.apiResult)) return 'warn';
    return 'ok';
  })();
  const internalWrapperStatusTone = (() => {
    if (internalWrapperMutation.isPending) return 'pending';
    if (!internalWrapperResult) return 'idle';
    if (!internalWrapperResult.ok) return 'error';
    if (internalWrapperResult.stub) return 'warn';
    if (internalWrapperResult.apiResult && !isApiResultOk(internalWrapperResult.apiResult)) return 'warn';
    return 'ok';
  })();
  const internalWrapperStatusLabel = resolveStatusLabel(
    internalWrapperResult ?? null,
    internalWrapperMutation.isPending,
  );
  const internalWrapperStubFixed = internalWrapperOption?.stubFixed ?? false;
  const internalWrapperStubLabel = internalWrapperResult
    ? internalWrapperResult.stub
      ? 'stub'
      : internalWrapperResult.ok
        ? 'real'
        : 'error'
    : '―';
  const internalWrapperGuidance = (() => {
    if (currentInternalWrapper?.parseError) {
      return 'JSON payload を修正してください。';
    }
    if (!internalWrapperResult) return undefined;
    if (!internalWrapperResult.ok) {
      return 'payload の必須項目（patientId/operation 等）を再確認し、Trial 未開放の API は stub 固定です。';
    }
    if (internalWrapperResult.stub || internalWrapperStubFixed) {
      return 'Trial 未開放のため stub 応答固定です。実データ検証は本番環境で再実施してください。';
    }
    return undefined;
  })();

  const queueEntries: OrcaQueueEntry[] = useMemo(
    () => queueQuery.data?.queue ?? [],
    [queueQuery.data?.queue],
  );

  useEffect(() => {
    const runIdFromQueue = queueQuery.data?.runId ?? configQuery.data?.runId;
    if (!runIdFromQueue) return;
    appliedMeta.current = applyAuthServicePatch(
      { runId: runIdFromQueue },
      appliedMeta.current,
      { bumpRunId, setCacheHit, setMissingMaster, setDataSourceTransition, setFallbackUsed },
    );
  }, [bumpRunId, configQuery.data?.runId, queueQuery.data?.runId, setCacheHit, setDataSourceTransition, setFallbackUsed, setMissingMaster]);
  const warningEntries = useMemo(() => {
    const nowMs = Date.now();
    return queueEntries.filter((entry) => isOrcaQueueWarningEntry(entry, nowMs).isWarning);
  }, [queueEntries]);

  const requireOrcaConnectionAdminAuth = useCallback(() => {
    if (!isSystemAdmin) {
      reportGuardedAction('orca-connection');
      return false;
    }
    if (!orcaConnectionAccessVerified) {
      setOrcaConnectionFeedback({
        tone: 'warning',
        message:
          'WebORCA 接続設定は、管理者アカウントで認証済みのセッションでのみ表示・編集できます。再ログイン後に再取得してください。',
      });
      reportGuardedAction('orca-connection', 'admin authentication required');
      return false;
    }
    return true;
  }, [isSystemAdmin, orcaConnectionAccessVerified, reportGuardedAction]);

  const patchOrcaConnectionForm = useCallback(
    (patch: Partial<OrcaConnectionFormState>) => {
      if (!requireOrcaConnectionAdminAuth()) {
        return;
      }
      setOrcaConnectionForm((prev) => ({ ...prev, ...patch }));
      setOrcaConnectionFieldErrors((prev) => ({
        ...prev,
        serverUrl: patch.serverUrl !== undefined ? undefined : prev.serverUrl,
        port: patch.port !== undefined ? undefined : prev.port,
        username: patch.username !== undefined ? undefined : prev.username,
        password: patch.password !== undefined ? undefined : prev.password,
        clientCertificate: patch.clientCertificateFile !== undefined ? undefined : prev.clientCertificate,
        clientCertificatePassphrase:
          patch.clientCertificatePassphrase !== undefined ? undefined : prev.clientCertificatePassphrase,
      }));
    },
    [requireOrcaConnectionAdminAuth],
  );

  const handleOrcaConnectionWeborcaToggle = (next: boolean) => {
    const currentPort = Number(orcaConnectionForm.port);
    const shouldAutoSwitchPort = !Number.isFinite(currentPort) || currentPort === 443 || currentPort === 8000;
    patchOrcaConnectionForm({
      useWeborca: next,
      port: shouldAutoSwitchPort ? String(next ? 443 : 8000) : orcaConnectionForm.port,
    });
  };

  const handleOrcaConnectionSave = () => {
    if (!requireOrcaConnectionAdminAuth()) {
      return;
    }
    const serverUrl = orcaConnectionForm.serverUrl.trim();
    const port = Number(orcaConnectionForm.port);
    const username = orcaConnectionForm.username.trim();
    const password = orcaConnectionForm.password.trim();
    const passphrase = orcaConnectionForm.clientCertificatePassphrase.trim();
    const fieldErrors: {
      serverUrl?: string;
      port?: string;
      username?: string;
      password?: string;
      clientCertificate?: string;
      clientCertificatePassphrase?: string;
    } = {};

    if (!serverUrl) {
      fieldErrors.serverUrl = 'サーバURLは必須です。';
    }
    if (!Number.isFinite(port) || port <= 0 || port > 65535) {
      fieldErrors.port = 'ポートは 1〜65535 で入力してください。';
    }
    if (!username) {
      fieldErrors.username = 'ユーザー名は必須です。';
    }
    if (!orcaConnectionForm.passwordConfigured && !password) {
      fieldErrors.password = 'パスワードまたは API キーは必須です。';
    }
    if (orcaConnectionForm.clientAuthEnabled) {
      const hasP12 = orcaConnectionForm.clientCertificateConfigured || Boolean(orcaConnectionForm.clientCertificateFile);
      if (!hasP12) {
        fieldErrors.clientCertificate = 'mTLS 有効時はクライアント証明書（.p12）が必須です。';
      }
      const passphraseRequired = !orcaConnectionForm.clientCertificatePassphraseConfigured;
      if (!passphrase && passphraseRequired) {
        fieldErrors.clientCertificatePassphrase = 'mTLS 有効時は証明書パスフレーズが必須です。';
      }
    }
    setOrcaConnectionFieldErrors(fieldErrors);
    if (Object.keys(fieldErrors).length > 0) {
      setOrcaConnectionFeedback({ tone: 'error', message: '入力エラーを修正してください。' });
      return;
    }

    orcaConnectionSaveMutation.mutate({
      useWeborca: orcaConnectionForm.useWeborca,
      serverUrl,
      port,
      username,
      password: password || undefined,
      clientAuthEnabled: orcaConnectionForm.clientAuthEnabled,
      clientCertificatePassphrase: passphrase || undefined,
      clientCertificateFile: orcaConnectionForm.clientCertificateFile,
      caCertificateFile: orcaConnectionForm.caCertificateFile,
    });
  };

  const handleCopyRequestTemplate = useCallback(async () => {
    await handleCopyValue(requestTemplate, '依頼テンプレ');
  }, [handleCopyValue, requestTemplate]);

  const handleOrcaConnectionTest = () => {
    if (!requireOrcaConnectionAdminAuth()) {
      return;
    }
    orcaConnectionTestMutation.mutate();
  };

  const handleInputChange = (key: keyof AdminConfigPayload, value: string | boolean) => {
    if (!isSystemAdmin) {
      reportGuardedAction('edit', `field:${key}`);
      return;
    }
    setForm((prev) => ({ ...prev, [key]: value }));
    if (key === 'useMockOrcaQueue' && typeof value === 'boolean') {
      persistHeaderFlags({ useMockOrcaQueue: value });
    }
    if (key === 'verifyAdminDelivery' && typeof value === 'boolean') {
      persistHeaderFlags({ verifyAdminDelivery: value });
    }
  };

  const handleChartsMasterSourceChange = (value: string) => {
    const next: ChartsMasterSourcePolicy =
      value === 'auto' || value === 'server' || value === 'mock' || value === 'snapshot' || value === 'fallback'
        ? value
        : 'auto';
    handleInputChange('chartsMasterSource', next);
  };

  const handleSave = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('save');
      return;
    }
    setSaveConfirmOpen(true);
  };

  const handleConfirmSave = () => {
    configMutation.mutate(form);
  };

  const handleRetry = (patientId: string) => {
    if (!isSystemAdmin) {
      reportGuardedAction('retry', `patient:${patientId}`);
      return;
    }
    queueMutation.mutate({ kind: 'retry', patientId });
  };

  const handleDiscardRequest = (entry: OrcaQueueEntry) => {
    if (!isSystemAdmin) {
      reportGuardedAction('discard', `patient:${entry.patientId}`);
      return;
    }
    setDiscardConfirmTarget(entry);
  };

  const handleConfirmDiscard = () => {
    if (!discardConfirmTarget) return;
    queueMutation.mutate({ kind: 'discard', patientId: discardConfirmTarget.patientId });
    setDiscardConfirmTarget(null);
  };

  const handleMasterCheck = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('master-check');
      return;
    }
    masterLastUpdateMutation.mutate();
  };

  const handleRegenerateMedicationTemplate = () => {
    setMedicationSyncXml(buildMedicationTemplateXml(medicationTemplateBaseDate));
  };

  const handleMedicationSync = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('master-sync');
      return;
    }
    medicationModMutation.mutate({ classCode: medicationSyncClass, xml: medicationSyncXml });
  };

  const handleSystemHealthCheck = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('system-check');
      return;
    }
    systemHealthMutation.mutate({ baseDate: systemBaseDate });
  };

  const handleMedicalSetSearch = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('medicalset-search');
      return;
    }
    medicalSetMutation.mutate(medicalSetQuery);
  };

  const handleXmlProxySubmit = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('orca-xml-proxy');
      return;
    }
    if (!currentXmlProxy) return;
    xmlProxyMutation.mutate({
      endpoint: orcaXmlProxyTarget,
      xml: currentXmlProxy.xml,
      classCode: currentXmlProxy.classCode,
    });
  };

  const handleXmlProxyReset = () => {
    if (!currentXmlProxy) return;
    const nextXml = buildXmlProxyTemplate(orcaXmlProxyTarget, currentXmlProxy.classCode);
    updateOrcaXmlProxyState(orcaXmlProxyTarget, { xml: nextXml });
  };

  const handleXmlProxyClassChange = (value: string) => {
    updateOrcaXmlProxyState(orcaXmlProxyTarget, { classCode: value });
  };

  const handleXmlProxyXmlChange = (value: string) => {
    updateOrcaXmlProxyState(orcaXmlProxyTarget, { xml: value });
  };

  const handleInternalWrapperPayloadChange = (value: string) => {
    updateOrcaInternalWrapperState(orcaInternalWrapperTarget, { payload: value });
  };

  const handleInternalWrapperSubmit = () => {
    if (!isSystemAdmin) {
      reportGuardedAction('orca-internal-wrapper');
      return;
    }
    const rawPayload = currentInternalWrapper?.payload ?? '';
    try {
      const parsed = rawPayload ? (JSON.parse(rawPayload) as Record<string, unknown>) : {};
      updateOrcaInternalWrapperState(orcaInternalWrapperTarget, { parseError: undefined });
      internalWrapperMutation.mutate({ endpoint: orcaInternalWrapperTarget, payload: parsed });
    } catch (error) {
      updateOrcaInternalWrapperState(orcaInternalWrapperTarget, {
        parseError: error instanceof Error ? error.message : 'JSON の解析に失敗しました。',
      });
    }
  };

  const handleInternalWrapperReset = () => {
    const defaultPayload = internalWrapperOption?.defaultPayload ?? {};
    updateOrcaInternalWrapperState(orcaInternalWrapperTarget, {
      payload: JSON.stringify(defaultPayload, null, 2),
      parseError: undefined,
    });
  };

  const handleRunConnectivityGroup = async () => {
    if (!isSystemAdmin) {
      reportGuardedAction('orca-xml-proxy', 'connectivity-group');
      return;
    }
    const checks: Array<Promise<{ label: string; ok: boolean; detail: string }>> = [
      postOrcaXmlProxy({
        endpoint: 'acceptlstv2',
        xml: buildAcceptListRequestXml(),
        classCode: '01',
      })
        .then((result) => ({
          label: 'XML proxy acceptlstv2',
          ok: Boolean(result.ok),
          detail: `HTTP ${result.status} / Api_Result=${result.apiResult ?? '―'}`,
        }))
        .catch((error) => ({
          label: 'XML proxy acceptlstv2',
          ok: false,
          detail: toErrorMessage(error),
        })),
      postMedicalRecords(resolveInternalWrapperOption(internalWrapperOptions, 'medical-records').defaultPayload)
        .then((result) => ({
          label: 'internal wrapper medical-records',
          ok: Boolean(result.ok),
          detail: `HTTP ${result.status} / Api_Result=${result.apiResult ?? '―'} / source=${result.stub ? 'stub' : 'real'}`,
        }))
        .catch((error) => ({
          label: 'internal wrapper medical-records',
          ok: false,
          detail: toErrorMessage(error),
        })),
    ];

    if (orcaConnectionAccessVerified) {
      checks.push(
        testOrcaConnection()
          .then((result) => ({
            label: 'WebORCA connection test',
            ok: Boolean(result.ok),
            detail: `HTTP ${result.orcaHttpStatus ?? result.status} / Api_Result=${result.apiResult ?? '―'}`,
          }))
          .catch((error) => ({
            label: 'WebORCA connection test',
            ok: false,
            detail: toErrorMessage(error),
          })),
      );
    }

    const results = await Promise.all(checks);
    const success = results.filter((entry) => entry.ok).length;
    const failure = results.length - success;
    const details = results.map((entry) => `${entry.ok ? 'OK' : 'NG'} ${entry.label}: ${entry.detail}`);
    setConnectivitySummary({ testedAt: new Date().toISOString(), success, failure, details });
  };

  const syncMismatch = configQuery.data?.syncMismatch;
  const syncMismatchFields = configQuery.data?.syncMismatchFields?.length ? configQuery.data.syncMismatchFields.join(', ') : undefined;
  const isForbidden =
    configQuery.data?.status === 403 ||
    rawConfig?.status === 403 ||
    rawDelivery?.status === 403;
  useEffect(() => {
    if (!isForbidden) return;
    if (forbiddenLogRef.current.runId === resolvedRunId && forbiddenLogRef.current.noted) return;
    forbiddenLogRef.current = { runId: resolvedRunId, noted: true };
    setFeedback({ tone: 'warning', message: '管理APIが権限不足 (403) のため読み取り専用で表示しています。' });
    logAuditEvent({
      runId: resolvedRunId,
      source: 'admin/guard',
      note: 'admin api forbidden',
      payload: {
        operation: 'access',
        actor: actorId,
        role,
        requiredRole: 'system_admin',
        status: 403,
        detail: 'admin config/delivery 403 forbidden',
      },
    });
  }, [actorId, isForbidden, resolvedRunId, role]);
  const deliveryMode = configQuery.data?.deliveryMode ?? rawDelivery?.deliveryMode ?? rawConfig?.deliveryMode;
  const effectiveDeliveryEtag = configQuery.data?.deliveryEtag ?? configQuery.data?.deliveryVersion;
  const deliveryStatus = buildChartsDeliveryStatus(rawConfig, rawDelivery);
  const deliverySummary = summarizeDeliveryStatus(deliveryStatus);
  const lastDeliveredAt = rawDelivery?.deliveredAt ?? configQuery.data?.deliveredAt;
  const deliveryPriorityLabel = rawDelivery ? 'delivery → config' : 'config（delivery未取得）';
  const deliveryFlagRows = [
    {
      key: 'chartsDisplayEnabled',
      label: 'Charts表示',
      configValue: rawConfig?.chartsDisplayEnabled,
      deliveryValue: rawDelivery?.chartsDisplayEnabled,
      state: deliveryStatus.chartsDisplayEnabled ?? 'unknown',
    },
    {
      key: 'chartsSendEnabled',
      label: 'Charts送信',
      configValue: rawConfig?.chartsSendEnabled,
      deliveryValue: rawDelivery?.chartsSendEnabled,
      state: deliveryStatus.chartsSendEnabled ?? 'unknown',
    },
    {
      key: 'chartsMasterSource',
      label: 'Charts master',
      configValue: rawConfig?.chartsMasterSource,
      deliveryValue: rawDelivery?.chartsMasterSource,
      state: deliveryStatus.chartsMasterSource ?? 'unknown',
    },
  ];
  const masterVersionDiffs = countVersionDiffs(masterLastUpdateResult?.versions);
  const systemVersionDiffs = countVersionDiffs(systemInfoResult?.versions);
  const masterStatusTone = resolveStatusTone(masterLastUpdateResult, masterLastUpdateMutation.isPending);
  const medicationStatusTone = resolveStatusTone(medicationSyncResult, medicationModMutation.isPending);
  const systemInfoStatusTone = resolveStatusTone(systemInfoResult, systemHealthMutation.isPending);
  const systemDailyStatusTone = resolveStatusTone(systemDailyResult, systemHealthMutation.isPending);
  const medicalSetStatusTone = resolveStatusTone(medicalSetResult, medicalSetMutation.isPending);
  const orcaConnectionStatusTone = resolveStatusTone(orcaConnectionTestResult, orcaConnectionTestMutation.isPending);
  const orcaConnectionStatusLabel = resolveStatusLabel(orcaConnectionTestResult, orcaConnectionTestMutation.isPending);
  const xmlProxyStatusLabel = resolveStatusLabel(xmlProxyResult, xmlProxyMutation.isPending);
  const isMasterUpdateDetected = masterUpdateLabel === '更新あり';
  const masterUpdateHeadline = isMasterUpdateDetected ? '更新検知: 同期推奨' : `更新検知: ${masterUpdateLabel}`;
  const traceId = queueQuery.data?.traceId ?? orcaConnectionTestResult?.traceId;
  const deliveryMode = configQuery.data?.deliveryMode ?? rawDelivery?.deliveryMode ?? rawConfig?.deliveryMode;
  const effectiveDeliveryEtag = configQuery.data?.deliveryEtag ?? configQuery.data?.deliveryVersion;
  const deliveryStatus = buildChartsDeliveryStatus(rawConfig, rawDelivery);
  const deliverySummary = summarizeDeliveryStatus(deliveryStatus);
  const lastDeliveredAt = rawDelivery?.deliveredAt ?? configQuery.data?.deliveredAt;
  const deliveryFlagRows = [
    {
      key: 'chartsDisplayEnabled',
      label: 'Charts表示',
      configValue: rawConfig?.chartsDisplayEnabled,
      deliveryValue: rawDelivery?.chartsDisplayEnabled,
      state: deliveryStatus.chartsDisplayEnabled ?? 'unknown',
    },
    {
      key: 'chartsSendEnabled',
      label: 'Charts送信',
      configValue: rawConfig?.chartsSendEnabled,
      deliveryValue: rawDelivery?.chartsSendEnabled,
      state: deliveryStatus.chartsSendEnabled ?? 'unknown',
    },
    {
      key: 'chartsMasterSource',
      label: 'Charts masterSource',
      configValue: rawConfig?.chartsMasterSource,
      deliveryValue: rawDelivery?.chartsMasterSource,
      state: deliveryStatus.chartsMasterSource ?? 'unknown',
    },
  ];
  const queueSummary = useMemo(() => {
    let pending = 0;
    let failed = 0;
    let delivered = 0;
    let delayed = 0;
    const now = Date.now();
    for (const entry of queueEntries) {
      if (entry.status === 'pending') pending += 1;
      if (entry.status === 'failed') failed += 1;
      if (entry.status === 'delivered') delivered += 1;
      if (entry.status === 'pending' && entry.lastDispatchAt) {
        const delta = now - new Date(entry.lastDispatchAt).getTime();
        if (delta > QUEUE_DELAY_WARNING_MS) delayed += 1;
      }
    }
    return { pending, failed, delivered, delayed };
  }, [queueEntries]);
  const webOrcaConnectionLabel = orcaConnectionTestResult
    ? orcaConnectionTestResult.ok
      ? '接続OK'
      : '接続NG'
    : orcaConnectionAccessVerified
      ? '認証済み（未テスト）'
      : orcaConnectionAuthBlocked
        ? '認証要確認'
        : '未確認';
  const masterVersionDiffs = countVersionDiffs(masterLastUpdateResult?.versions);
  const masterStatusTone = resolveStatusTone(masterLastUpdateResult, masterLastUpdateMutation.isPending);
  const masterStatusLabel = resolveStatusLabel(masterLastUpdateResult, masterLastUpdateMutation.isPending);
  const medicationStatusTone = resolveStatusTone(medicationSyncResult, medicationModMutation.isPending);
  const medicationStatusLabel = resolveStatusLabel(medicationSyncResult, medicationModMutation.isPending);
  const systemInfoStatusTone = resolveStatusTone(systemInfoResult, systemHealthMutation.isPending);
  const systemDailyStatusTone = resolveStatusTone(systemDailyResult, systemHealthMutation.isPending);
  const medicalSetStatusTone = resolveStatusTone(medicalSetResult, medicalSetMutation.isPending);
  const orcaConnectionStatusTone = resolveStatusTone(orcaConnectionTestResult, orcaConnectionTestMutation.isPending);
  const orcaConnectionStatusLabel = resolveStatusLabel(orcaConnectionTestResult, orcaConnectionTestMutation.isPending);
  const abnormalSummary = (() => {
    const fragments: string[] = [];
    const dbDiffs = countVersionDiffs(systemInfoResult?.versions);
    if (dbDiffs > 0) fragments.push(`DB New ≠ Local が ${dbDiffs}件`);
    if (systemInfoResult && !systemInfoResult.ok) fragments.push('systeminfv2 が NG');
    if (systemDailyResult && !systemDailyResult.ok) fragments.push('system01dailyv2 が NG');
    if (queueSummary.failed > 0) fragments.push(`queue failed ${queueSummary.failed}件`);
    return fragments.length > 0 ? fragments.join(' / ') : '異常なし';
  })();

  return (
    <>
      <a className="skip-link" href="#administration-main">
        本文へスキップ
      </a>
      <main
        className="administration-page"
        data-test-id="administration-page"
        data-run-id={resolvedRunId}
        id="administration-main"
        tabIndex={-1}
      >
        <div className="administration-page__header">
          <h1>Administration</h1>
          {activeTab === 'delivery' ? (
            <p className="administration-page__lead" role="status" aria-live={infoLive}>
              設定配信の運用導線と診断導線を分離し、誤操作を防止します。
            </p>
          ) : activeTab === 'master-updates' ? (
            <p className="administration-page__lead" role="status" aria-live={infoLive}>
              ORCA/外部マスタの参照データ更新を管理します（自動・手動・アップロード・ロールバック）。
            </p>
          ) : (
            <p className="administration-page__lead" role="status" aria-live={infoLive}>
              ORCA職員マスタ連携と、連携済みユーザーへの電子カルテ権限付与を管理します。
            </p>
          )}

          <div className="admin-header-blocks">
            <section className="admin-header-block">
              <h2>運用KPI</h2>
              <div className="administration-page__meta" aria-live={infoLive}>
                <span className="administration-page__pill">配信状態: {deliverySummary.summary}</span>
                <span className="administration-page__pill">最終配信: {formatTimestampWithAgo(lastDeliveredAt)}</span>
                <span className="administration-page__pill">WebORCA: {webOrcaConnectionLabel}</span>
                <span className="administration-page__pill">
                  queue警告: pending {queueSummary.pending} / failed {queueSummary.failed} / 遅延 {queueSummary.delayed}
                </span>
                <span className="administration-page__pill">環境: {environmentLabel}</span>
                {syncMismatch ? (
                  <button
                    type="button"
                    className="administration-page__pill administration-page__pill--warn"
                    onClick={() => handleDeliverySectionChange('config')}
                  >
                    不整合あり
                  </button>
                ) : (
                  <span className="administration-page__pill">不整合なし</span>
                )}
              </div>
            </section>

            <section className="admin-header-block">
              <h2>識別子</h2>
              <div className="administration-page__meta">
                <RunIdBadge runId={resolvedRunId} />
                <AuditSummaryInline
                  auditEvent={latestAuditEvent}
                  className="administration-page__pill"
                  variant="inline"
                  runId={resolvedRunId}
                />
                <span className="administration-page__pill">
                  施設ID: {session.facilityId}
                  <button type="button" className="admin-pill-copy" onClick={() => handleCopyValue(session.facilityId, '施設ID')}>
                    コピー
                  </button>
                </span>
                <span className="administration-page__pill">role: {role ?? 'unknown'}</span>
                <span className="administration-page__pill">
                  traceId: {traceId ?? '―'}
                  {traceId ? (
                    <button type="button" className="admin-pill-copy" onClick={() => handleCopyValue(traceId, 'traceId')}>
                      コピー
                    </button>
                  ) : null}
                </span>
              </div>
            </section>

            <section className="admin-header-block">
              <h2>詳細フラグ</h2>
              <details>
                <summary>詳細を表示</summary>
                <div className="administration-page__meta">
                  <span className="administration-page__pill">配信元: {configQuery.data?.source ?? 'live'}</span>
                  <span className="administration-page__pill">deliveryMode: {deliveryMode ?? '―'}</span>
                  <span className="administration-page__pill">ETag: {effectiveDeliveryEtag ?? '―'}</span>
                  <span className="administration-page__pill">verifyAdminDelivery: {String(form.verifyAdminDelivery)}</span>
                  <span className="administration-page__pill">useMockOrcaQueue: {String(form.useMockOrcaQueue)}</span>
                  <span className="administration-page__pill">chartsMasterSource: {form.chartsMasterSource}</span>
                  <span className="administration-page__pill">mismatchFields: {syncMismatchFields ?? '―'}</span>
                </div>
              </details>
            </section>
          </div>

          <div className="administration-tabs" role="tablist" aria-label="Administration tabs">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'delivery'}
              className={`administration-tab${activeTab === 'delivery' ? ' is-active' : ''}`}
              onClick={() => handleTabChange('delivery')}
            >
              設定配信
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'orca-users'}
              className={`administration-tab${activeTab === 'orca-users' ? ' is-active' : ''}`}
              onClick={() => handleTabChange('orca-users')}
            >
              ORCAユーザー連携・権限
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'master-updates'}
              className={`administration-tab${activeTab === 'master-updates' ? ' is-active' : ''}`}
              onClick={() => handleTabChange('master-updates')}
            >
              マスタ更新
            </button>
          </div>

          {activeTab === 'delivery' ? (
            <DeliverySubNav activeSection={activeDeliverySection} onChange={handleDeliverySectionChange} />
          ) : null}

          {isForbidden && activeTab === 'delivery' ? (
            <ToneBanner
              tone="error"
              message="管理APIが 403 Forbidden を返しました。権限付与後に再ログインするか、システム管理者へ依頼してください。"
              destination="Administration"
              runId={resolvedRunId}
              nextAction="権限確認 / 再ログイン"
            />
          ) : null}

          {!isSystemAdmin ? (
            <div className="admin-guard" role="alert" aria-live={resolveAriaLive('warning')} id={guardMessageId}>
              <div className="admin-guard__header">
                <span className="admin-guard__title">操作ガード中</span>
                <span className="admin-guard__badge">system_adminのみ</span>
              </div>
              <p className="admin-guard__message">
                現在のロール（{role ?? 'unknown'}）では Administration の破壊操作はできません。入力欄は readOnly でコピー可能です。
              </p>
              <div className="admin-request-template">
                <textarea value={requestTemplate} readOnly rows={6} id={guardDetailsId} />
                <button type="button" className="admin-button admin-button--secondary" onClick={handleCopyRequestTemplate}>
                  依頼テンプレをコピー
                </button>
              </div>
              <ul className="admin-guard__next">
                <li>system_admin で再ログインしてください。</li>
                <li>権限保持者へ作業依頼を行ってください。</li>
                <li>
                  <Link to={buildFacilityPath(session.facilityId, '/reception')} className="admin-guard__link">
                    Reception へ戻って受付状況を確認
                  </Link>
                </li>
              </ul>
            </div>
          ) : null}
        </div>

        {activeTab === 'orca-users' ? (
          <div className="administration-grid administration-grid--wide">
            <OrcaUserManagementPanel runId={panelRunId} role={role} />
            <AccessManagementPanel runId={panelRunId} role={role} mode="linked-only" />
          </div>
        ) : activeTab === 'master-updates' ? (
          <div className="administration-grid administration-grid--wide">
            <MasterUpdatesPanel runId={panelRunId} role={role} />
          </div>
        ) : (
          <>
            {warningEntries.length > 0 ? (
              <ToneBanner
                tone="warning"
                message={`未配信・失敗バンドルが ${warningEntries.length} 件あります（遅延判定:${warningThresholdMinutes}分）。再送または破棄を実施してください。`}
                destination="ORCA queue"
                runId={resolvedRunId}
                nextAction="再送/破棄・再取得"
              />
            ) : syncMismatch ? (
              <ToneBanner
                tone="warning"
                message={`config/delivery の不一致を検知しました。fields: ${syncMismatchFields ?? 'unknown'}`}
                destination="Administration"
                runId={resolvedRunId}
                nextAction="再取得 / 再配信で解消"
              />
            ) : null}

            {activeDeliverySection === 'dashboard' ? (
              <DeliveryDashboard
                deliverySummary={deliverySummary.summary}
                deliveryMode={deliveryMode}
                lastDeliveredAt={formatTimestampWithAgo(lastDeliveredAt)}
                webOrcaConnection={webOrcaConnectionLabel}
                queueSummary={queueSummary}
                environmentLabel={environmentLabel}
                syncMismatch={syncMismatch}
                syncMismatchFields={syncMismatchFields}
                warningThresholdMinutes={warningThresholdMinutes}
                onNavigate={handleDeliverySectionChange}
              />
            ) : null}

            {activeDeliverySection === 'connection' ? (
              <WebOrcaConnectionCard
                form={orcaConnectionForm}
                fieldErrors={orcaConnectionFieldErrors}
                isSystemAdmin={isSystemAdmin}
                accessVerified={orcaConnectionAccessVerified}
                authBlocked={orcaConnectionAuthBlocked}
                dirty={orcaConnectionDirty}
                feedback={orcaConnectionFeedback}
                statusTone={orcaConnectionStatusTone}
                statusLabel={orcaConnectionStatusLabel}
                testSummary={orcaConnectionTestResult}
                savePending={orcaConnectionSaveMutation.isPending}
                testPending={orcaConnectionTestMutation.isPending}
                refetchPending={orcaConnectionQuery.isFetching}
                onPatch={patchOrcaConnectionForm}
                onToggleWeborca={handleOrcaConnectionWeborcaToggle}
                onSave={handleOrcaConnectionSave}
                onTest={handleOrcaConnectionTest}
                onRefetch={() => orcaConnectionQuery.refetch()}
                onCopyRequestTemplate={handleCopyRequestTemplate}
                requestTemplate={requestTemplate}
                guardDetailsId={guardDetailsId}
              />
            ) : null}

            {activeDeliverySection === 'config' ? (
              <div className="administration-grid">
                <AdminDeliveryConfigCard
                  form={form}
                  isSystemAdmin={isSystemAdmin}
                  showAdminDebugToggles={showAdminDebugToggles}
                  dirty={configDirty}
                  updatedAt={rawDelivery?.deliveredAt ?? rawConfig?.deliveredAt}
                  feedback={feedback}
                  note={configQuery.data?.note}
                  guardDetailsId={guardDetailsId}
                  saving={configMutation.isPending}
                  refetching={configQuery.isFetching}
                  onFieldChange={handleInputChange}
                  onChartsMasterSourceChange={handleChartsMasterSourceChange}
                  onSaveRequest={handleSave}
                  onRefetch={() => configQuery.refetch()}
                />
                <AdminDeliveryStatusCard
                  deliveryId={configQuery.data?.deliveryId}
                  deliveryVersion={configQuery.data?.deliveryVersion}
                  deliveryEtag={effectiveDeliveryEtag}
                  deliveredAt={rawDelivery?.deliveredAt ?? configQuery.data?.deliveredAt}
                  environmentLabel={environmentLabel}
                  deliveryMode={deliveryMode}
                  verified={configQuery.data?.verifyAdminDelivery}
                  rows={deliveryFlagRows}
                  onCopy={handleCopyValue}
                />
              </div>
            ) : null}

            {activeDeliverySection === 'queue' ? (
              <OrcaQueueCard
                entries={queueEntries}
                isSystemAdmin={isSystemAdmin}
                guardDetailsId={guardDetailsId}
                pending={queueMutation.isPending}
                warningThresholdMs={QUEUE_DELAY_WARNING_MS}
                onRetry={handleRetry}
                onDiscardRequest={handleDiscardRequest}
              />
            ) : null}

            {activeDeliverySection === 'master-health' ? (
              <div className="administration-grid administration-grid--wide">
                <OrcaMasterSyncCard
                  isSystemAdmin={isSystemAdmin}
                  guardDetailsId={guardDetailsId}
                  masterStatusTone={masterStatusTone}
                  masterStatusLabel={masterStatusLabel}
                  masterLastUpdateResult={masterLastUpdateResult}
                  masterUpdateLabel={masterUpdateLabel}
                  masterVersionDiffs={masterVersionDiffs}
                  onMasterCheck={handleMasterCheck}
                  masterCheckPending={masterLastUpdateMutation.isPending}
                  medicationSyncClass={medicationSyncClass}
                  onMedicationSyncClassChange={setMedicationSyncClass}
                  medicationSyncXml={medicationSyncXml}
                  onMedicationSyncXmlChange={setMedicationSyncXml}
                  medicationTemplateBaseDate={medicationTemplateBaseDate}
                  onMedicationTemplateBaseDateChange={setMedicationTemplateBaseDate}
                  onRegenerateMedicationTemplate={handleRegenerateMedicationTemplate}
                  medicationStatusTone={medicationStatusTone}
                  medicationStatusLabel={medicationStatusLabel}
                  medicationSyncResult={medicationSyncResult}
                  onMedicationSync={handleMedicationSync}
                  medicationSyncPending={medicationModMutation.isPending}
                />
                <SystemHealthCard
                  isSystemAdmin={isSystemAdmin}
                  guardDetailsId={guardDetailsId}
                  overallTone={resolveHealthTone(systemInfoResult, systemDailyResult, systemHealthMutation.isPending)}
                  overallLabel={resolveHealthLabel(systemInfoResult, systemDailyResult, systemHealthMutation.isPending)}
                  infoTone={systemInfoStatusTone}
                  infoLabel={resolveStatusLabel(systemInfoResult, systemHealthMutation.isPending)}
                  dailyTone={systemDailyStatusTone}
                  dailyLabel={resolveStatusLabel(systemDailyResult, systemHealthMutation.isPending)}
                  systemInfoResult={systemInfoResult}
                  systemDailyResult={systemDailyResult}
                  systemBaseDate={systemBaseDate}
                  onSystemBaseDateChange={setSystemBaseDate}
                  onHealthCheck={handleSystemHealthCheck}
                  healthCheckPending={systemHealthMutation.isPending}
                  abnormalSummary={abnormalSummary}
                />
              </div>
            ) : null}

            {activeDeliverySection === 'medicalset' ? (
              <MedicalSetSearchCard
                isSystemAdmin={isSystemAdmin}
                guardDetailsId={guardDetailsId}
                query={medicalSetQuery}
                onQueryChange={(patch) => setMedicalSetQuery((prev) => ({ ...prev, ...patch }))}
                result={medicalSetResult}
                statusTone={medicalSetStatusTone}
                statusLabel={resolveStatusLabel(medicalSetResult, medicalSetMutation.isPending)}
                searchPending={medicalSetMutation.isPending}
                onSearch={handleMedicalSetSearch}
                chartsPath={buildFacilityPath(session.facilityId, '/charts')}
              />
            ) : null}

            {activeDeliverySection === 'debug' ? (
              <>
                <section className="administration-card" aria-label="診断一括疎通">
                  <h2 className="administration-card__title">診断/デバッグ</h2>
                  <p className="admin-note">
                    このセクションは運用設定から隔離されています。診断用途のみで使用してください。
                  </p>
                  <div className="admin-actions">
                    <button
                      type="button"
                      className="admin-button admin-button--secondary"
                      onClick={handleRunConnectivityGroup}
                      disabled={!isSystemAdmin}
                    >
                      一括疎通（グループ）
                    </button>
                  </div>
                  {connectivitySummary ? (
                    <div className="admin-result admin-result--stack">
                      <div>testedAt: {formatTimestamp(connectivitySummary.testedAt)}</div>
                      <div>
                        success: {connectivitySummary.success} / failure: {connectivitySummary.failure}
                      </div>
                      <ul className="placeholder-page__list">
                        {connectivitySummary.details.map((detail) => (
                          <li key={detail}>{detail}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </section>
                <div className="administration-grid administration-grid--wide">
                  <OrcaXmlProxyCard
                    isSystemAdmin={isSystemAdmin}
                    guardDetailsId={guardDetailsId}
                    options={ORCA_XML_PROXY_OPTIONS}
                    target={orcaXmlProxyTarget}
                    currentOption={xmlProxyOption}
                    currentState={currentXmlProxy}
                    result={xmlProxyResult}
                    statusTone={xmlProxyStatusTone}
                    statusLabel={xmlProxyStatusLabel}
                    pending={xmlProxyMutation.isPending}
                    onTargetChange={setOrcaXmlProxyTarget}
                    onClassChange={handleXmlProxyClassChange}
                    onXmlChange={handleXmlProxyXmlChange}
                    onSubmit={handleXmlProxySubmit}
                    onReset={handleXmlProxyReset}
                  />
                  <OrcaInternalWrapperCard
                    isSystemAdmin={isSystemAdmin}
                    guardDetailsId={guardDetailsId}
                    options={internalWrapperOptions}
                    target={orcaInternalWrapperTarget}
                    currentOption={internalWrapperOption}
                    currentState={currentInternalWrapper}
                    result={internalWrapperResult}
                    statusTone={internalWrapperStatusTone}
                    statusLabel={internalWrapperStatusLabel}
                    pending={internalWrapperMutation.isPending}
                    onTargetChange={setOrcaInternalWrapperTarget}
                    onPayloadChange={handleInternalWrapperPayloadChange}
                    onSubmit={handleInternalWrapperSubmit}
                    onReset={handleInternalWrapperReset}
                  />
                </div>
                <LegacyRestPanel
                  runId={resolvedRunId ?? session.runId ?? 'RUN-UNSET'}
                  role={role}
                  actorId={actorId}
                  environmentLabel={environmentLabel}
                  isSystemAdmin={isSystemAdmin}
                  onGuarded={(detail) => reportGuardedAction('legacy-rest', detail)}
                />
                <TouchAdmPhrPanel
                  runId={resolvedRunId ?? session.runId ?? 'RUN-UNSET'}
                  role={role}
                  actorId={actorId}
                  environmentLabel={environmentLabel}
                  isSystemAdmin={isSystemAdmin}
                  facilityId={session.facilityId}
                  userId={session.userId}
                  onGuarded={(detail) => reportGuardedAction('touch-adm-phr', detail)}
                />
              </>
            ) : null}
          </>
        )}
      </main>

      <ConfirmDialog
        open={saveConfirmOpen}
        title="設定を保存して配信しますか？"
        description="差分内容と影響範囲を確認してください。"
        confirmLabel="保存して配信"
        tone="danger"
        pending={configMutation.isPending}
        onConfirm={handleConfirmSave}
        onCancel={() => setSaveConfirmOpen(false)}
      >
        <div className="admin-result admin-result--stack">
          <div>対象環境: {environmentLabel}</div>
          <div>施設ID: {session.facilityId}</div>
          <div>RUN_ID: {resolvedRunId ?? '―'}</div>
        </div>
        <table className="admin-table admin-table--compact">
          <thead>
            <tr>
              <th>項目</th>
              <th>変更前</th>
              <th>変更後</th>
            </tr>
          </thead>
          <tbody>
            {configDiffRows.length ? (
              configDiffRows.map((row) => (
                <tr key={row.key}>
                  <td>{row.label}</td>
                  <td>{String(row.before ?? '―')}</td>
                  <td>{String(row.after ?? '―')}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={3}>差分はありません。</td>
              </tr>
            )}
          </tbody>
        </table>
      </ConfirmDialog>

      <ConfirmDialog
        open={Boolean(discardConfirmTarget)}
        title="キュー破棄を実行しますか？"
        description="破棄後は再送できない可能性があります。"
        confirmLabel="破棄する"
        tone="danger"
        pending={queueMutation.isPending}
        onConfirm={handleConfirmDiscard}
        onCancel={() => setDiscardConfirmTarget(null)}
      >
        <div className="admin-result admin-result--stack">
          <div>patientId: {discardConfirmTarget?.patientId ?? '―'}</div>
          <div>status: {discardConfirmTarget?.status ?? '―'}</div>
          <div>影響: このエントリは再送不可となる場合があります。</div>
        </div>
      </ConfirmDialog>
    </>
  );
}
