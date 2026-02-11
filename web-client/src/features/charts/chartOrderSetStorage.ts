import type { DiseaseEntry } from './diseaseApi';
import type { OrderBundle, OrderBundleItem } from './orderBundleApi';
import type { SoapDraft, SoapEntry } from './soapNote';
import type { ChartImageAttachment } from './documentImageAttach';

export type ChartOrderSetSnapshot = {
  sourcePatientId: string;
  sourceVisitDate: string;
  capturedAt: string;
  diagnoses: DiseaseEntry[];
  soapDraft: SoapDraft;
  soapHistory: SoapEntry[];
  orderBundles: OrderBundle[];
  imageAttachments: ChartImageAttachment[];
};

export type ChartOrderSetEntry = {
  id: string;
  facilityId: string;
  createdBy?: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  snapshot: ChartOrderSetSnapshot;
};

type ChartOrderSetStorage = {
  version: 1;
  updatedAt: string;
  items: ChartOrderSetEntry[];
};

const STORAGE_KEY = 'opendolphin:web-client:charts:order-sets:v1';
const MAX_ENTRIES_PER_FACILITY = 200;

const normalizeText = (value?: string | null) => value?.trim() ?? '';

const cloneOrderItem = (item: OrderBundleItem): OrderBundleItem => ({
  code: normalizeText(item.code) || undefined,
  name: normalizeText(item.name),
  quantity: normalizeText(item.quantity) || undefined,
  unit: normalizeText(item.unit) || undefined,
  memo: normalizeText(item.memo) || undefined,
});

const cloneOrderBundle = (bundle: OrderBundle): OrderBundle => ({
  documentId: bundle.documentId,
  moduleId: bundle.moduleId,
  entity: normalizeText(bundle.entity) || undefined,
  bundleName: normalizeText(bundle.bundleName) || undefined,
  bundleNumber: normalizeText(bundle.bundleNumber) || undefined,
  classCode: normalizeText(bundle.classCode) || undefined,
  classCodeSystem: normalizeText(bundle.classCodeSystem) || undefined,
  className: normalizeText(bundle.className) || undefined,
  admin: normalizeText(bundle.admin) || undefined,
  adminMemo: normalizeText(bundle.adminMemo) || undefined,
  memo: normalizeText(bundle.memo) || undefined,
  started: normalizeText(bundle.started) || undefined,
  items: (bundle.items ?? []).map(cloneOrderItem).filter((item) => Boolean(item.name || item.code)),
});

const cloneDisease = (entry: DiseaseEntry): DiseaseEntry => ({
  diagnosisId: entry.diagnosisId,
  diagnosisName: normalizeText(entry.diagnosisName) || undefined,
  diagnosisCode: normalizeText(entry.diagnosisCode) || undefined,
  departmentCode: normalizeText(entry.departmentCode) || undefined,
  insuranceCombinationNumber: normalizeText(entry.insuranceCombinationNumber) || undefined,
  startDate: normalizeText(entry.startDate) || undefined,
  endDate: normalizeText(entry.endDate) || undefined,
  outcome: normalizeText(entry.outcome) || undefined,
  category: normalizeText(entry.category) || undefined,
  suspectedFlag: normalizeText(entry.suspectedFlag) || undefined,
});

const cloneSoapEntry = (entry: SoapEntry): SoapEntry => ({
  id: normalizeText(entry.id),
  section: entry.section,
  body: entry.body ?? '',
  templateId: normalizeText(entry.templateId) || undefined,
  authoredAt: entry.authoredAt,
  authorRole: normalizeText(entry.authorRole),
  authorName: normalizeText(entry.authorName) || undefined,
  action: entry.action,
  patientId: normalizeText(entry.patientId) || undefined,
  appointmentId: normalizeText(entry.appointmentId) || undefined,
  receptionId: normalizeText(entry.receptionId) || undefined,
  visitDate: normalizeText(entry.visitDate) || undefined,
});

const cloneSoapDraft = (draft: SoapDraft): SoapDraft => ({
  free: draft.free ?? '',
  subjective: draft.subjective ?? '',
  objective: draft.objective ?? '',
  assessment: draft.assessment ?? '',
  plan: draft.plan ?? '',
});

const cloneAttachment = (item: ChartImageAttachment): ChartImageAttachment => ({
  id: item.id,
  title: normalizeText(item.title) || undefined,
  fileName: normalizeText(item.fileName) || undefined,
  contentType: normalizeText(item.contentType) || undefined,
  contentSize: item.contentSize,
  recordedAt: normalizeText(item.recordedAt) || undefined,
});

const sanitizeSnapshot = (snapshot: ChartOrderSetSnapshot): ChartOrderSetSnapshot => ({
  sourcePatientId: normalizeText(snapshot.sourcePatientId),
  sourceVisitDate: normalizeText(snapshot.sourceVisitDate),
  capturedAt: snapshot.capturedAt,
  diagnoses: (snapshot.diagnoses ?? []).map(cloneDisease).filter((item) => Boolean(item.diagnosisName)),
  soapDraft: cloneSoapDraft(snapshot.soapDraft),
  soapHistory: (snapshot.soapHistory ?? []).map(cloneSoapEntry).filter((item) => Boolean(item.body.trim())),
  orderBundles: (snapshot.orderBundles ?? []).map(cloneOrderBundle).filter((item) => item.items.length > 0),
  imageAttachments: (snapshot.imageAttachments ?? []).map(cloneAttachment),
});

const readStorage = (): ChartOrderSetStorage => {
  if (typeof localStorage === 'undefined') {
    return { version: 1, updatedAt: new Date().toISOString(), items: [] };
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { version: 1, updatedAt: new Date().toISOString(), items: [] };
    const parsed = JSON.parse(raw) as Partial<ChartOrderSetStorage>;
    if (parsed?.version !== 1 || !Array.isArray(parsed.items)) {
      return { version: 1, updatedAt: new Date().toISOString(), items: [] };
    }
    return {
      version: 1,
      updatedAt: typeof parsed.updatedAt === 'string' ? parsed.updatedAt : new Date().toISOString(),
      items: parsed.items,
    };
  } catch {
    return { version: 1, updatedAt: new Date().toISOString(), items: [] };
  }
};

const writeStorage = (storage: ChartOrderSetStorage) => {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(storage));
  } catch {
    // ignore storage errors
  }
};

const pruneFacilityItems = (items: ChartOrderSetEntry[], facilityId: string) => {
  const target = items.filter((item) => item.facilityId === facilityId);
  if (target.length <= MAX_ENTRIES_PER_FACILITY) return items;

  const sorted = target
    .slice()
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, MAX_ENTRIES_PER_FACILITY);
  const keepIds = new Set(sorted.map((item) => item.id));
  return items.filter((item) => item.facilityId !== facilityId || keepIds.has(item.id));
};

export const listChartOrderSets = (facilityId?: string): ChartOrderSetEntry[] => {
  const resolvedFacilityId = normalizeText(facilityId);
  if (!resolvedFacilityId) return [];
  const storage = readStorage();
  return storage.items
    .filter((item) => normalizeText(item.facilityId) === resolvedFacilityId)
    .map((item) => ({
      ...item,
      name: normalizeText(item.name) || '名称未設定',
      snapshot: sanitizeSnapshot(item.snapshot),
    }))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
};

export const getChartOrderSet = (params: { facilityId?: string; id?: string }): ChartOrderSetEntry | null => {
  const facilityId = normalizeText(params.facilityId);
  const id = normalizeText(params.id);
  if (!facilityId || !id) return null;
  const found = listChartOrderSets(facilityId).find((item) => item.id === id);
  return found ?? null;
};

export const saveChartOrderSet = (params: {
  facilityId?: string;
  userId?: string;
  name?: string;
  snapshot: ChartOrderSetSnapshot;
  id?: string;
}) => {
  const facilityId = normalizeText(params.facilityId);
  if (!facilityId) throw new Error('facilityId is required');
  const now = new Date().toISOString();
  const storage = readStorage();
  const id = normalizeText(params.id) || `order-set-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
  const normalizedName = normalizeText(params.name) || `${sanitizeSnapshot(params.snapshot).sourceVisitDate || now.slice(0, 10)} セット`;
  const snapshot = sanitizeSnapshot(params.snapshot);
  const existing = storage.items.find((item) => item.id === id && item.facilityId === facilityId);

  const nextEntry: ChartOrderSetEntry = {
    id,
    facilityId,
    createdBy: normalizeText(params.userId) || existing?.createdBy,
    name: normalizedName,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
    snapshot,
  };

  if (existing) {
    storage.items = storage.items.map((item) => (item.id === existing.id ? nextEntry : item));
  } else {
    storage.items = [nextEntry, ...storage.items];
  }

  storage.items = pruneFacilityItems(storage.items, facilityId);
  storage.updatedAt = now;
  writeStorage(storage);
  return nextEntry;
};

export const deleteChartOrderSet = (params: { facilityId?: string; id?: string }) => {
  const facilityId = normalizeText(params.facilityId);
  const id = normalizeText(params.id);
  if (!facilityId || !id) return false;
  const storage = readStorage();
  const before = storage.items.length;
  storage.items = storage.items.filter((item) => !(item.facilityId === facilityId && item.id === id));
  if (storage.items.length === before) return false;
  storage.updatedAt = new Date().toISOString();
  writeStorage(storage);
  return true;
};

export const clearChartOrderSetStorage = () => {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
};
