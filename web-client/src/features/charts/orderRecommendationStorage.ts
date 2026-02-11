import type { OrderBundleItem } from './orderBundleApi';

export type OrderRecommendationSource = 'patient' | 'facility';

export type OrderRecommendationTemplate = {
  bundleName: string;
  admin: string;
  bundleNumber: string;
  adminMemo: string;
  memo: string;
  prescriptionLocation?: 'in' | 'out';
  prescriptionTiming?: 'regular' | 'tonyo' | 'temporal';
  items: OrderBundleItem[];
  materialItems: OrderBundleItem[];
  commentItems: OrderBundleItem[];
  bodyPart?: OrderBundleItem | null;
};

export type OrderRecommendationCandidate = {
  key: string;
  source: OrderRecommendationSource;
  count: number;
  lastUsedAt: string;
  template: OrderRecommendationTemplate;
};

type UsageAggregate = {
  key: string;
  entity: string;
  template: OrderRecommendationTemplate;
  count: number;
  lastUsedAt: string;
};

type UsageEntityBucket = Record<string, UsageAggregate>;

type FacilityUsageBucket = {
  entities: Record<string, UsageEntityBucket>;
  patients: Record<string, Record<string, UsageEntityBucket>>;
};

type UsageStorage = {
  version: 1;
  updatedAt: string;
  facilities: Record<string, FacilityUsageBucket>;
};

const STORAGE_KEY = 'opendolphin:web-client:charts:order-recommendations:v1';
const MAX_ENTRIES_PER_ENTITY = 240;

const normalizeText = (value?: string) => value?.trim() ?? '';

const normalizeItem = (item: OrderBundleItem): OrderBundleItem => ({
  code: normalizeText(item.code) || undefined,
  name: normalizeText(item.name),
  quantity: normalizeText(item.quantity) || undefined,
  unit: normalizeText(item.unit) || undefined,
  memo: normalizeText(item.memo) || undefined,
});

const normalizeItems = (items?: OrderBundleItem[]) =>
  (items ?? [])
    .map(normalizeItem)
    .filter((item) => Boolean(item.name || item.code || item.quantity || item.unit || item.memo));

const normalizeTemplate = (template: OrderRecommendationTemplate): OrderRecommendationTemplate => ({
  bundleName: normalizeText(template.bundleName),
  admin: normalizeText(template.admin),
  bundleNumber: normalizeText(template.bundleNumber) || '1',
  adminMemo: normalizeText(template.adminMemo),
  memo: normalizeText(template.memo),
  prescriptionLocation: template.prescriptionLocation,
  prescriptionTiming: template.prescriptionTiming,
  items: normalizeItems(template.items),
  materialItems: normalizeItems(template.materialItems),
  commentItems: normalizeItems(template.commentItems),
  bodyPart: template.bodyPart
    ? {
        code: normalizeText(template.bodyPart.code) || undefined,
        name: normalizeText(template.bodyPart.name),
        quantity: normalizeText(template.bodyPart.quantity) || undefined,
        unit: normalizeText(template.bodyPart.unit) || undefined,
        memo: normalizeText(template.bodyPart.memo) || undefined,
      }
    : null,
});

const buildTemplateKey = (entity: string, template: OrderRecommendationTemplate) => {
  const normalized = normalizeTemplate(template);
  return JSON.stringify({
    entity,
    bundleName: normalized.bundleName,
    admin: normalized.admin,
    bundleNumber: normalized.bundleNumber,
    adminMemo: normalized.adminMemo,
    memo: normalized.memo,
    prescriptionLocation: normalized.prescriptionLocation,
    prescriptionTiming: normalized.prescriptionTiming,
    items: normalized.items,
    materialItems: normalized.materialItems,
    commentItems: normalized.commentItems,
    bodyPart: normalized.bodyPart,
  });
};

const compareUsage = (left: UsageAggregate, right: UsageAggregate) => {
  if (left.count !== right.count) return right.count - left.count;
  return right.lastUsedAt.localeCompare(left.lastUsedAt);
};

const readStorage = (): UsageStorage => {
  if (typeof localStorage === 'undefined') {
    return {
      version: 1,
      updatedAt: new Date().toISOString(),
      facilities: {},
    };
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {
        version: 1,
        updatedAt: new Date().toISOString(),
        facilities: {},
      };
    }
    const parsed = JSON.parse(raw) as Partial<UsageStorage>;
    if (parsed?.version !== 1 || !parsed.facilities || typeof parsed.facilities !== 'object') {
      return {
        version: 1,
        updatedAt: new Date().toISOString(),
        facilities: {},
      };
    }
    return {
      version: 1,
      updatedAt: typeof parsed.updatedAt === 'string' ? parsed.updatedAt : new Date().toISOString(),
      facilities: parsed.facilities as Record<string, FacilityUsageBucket>,
    };
  } catch {
    return {
      version: 1,
      updatedAt: new Date().toISOString(),
      facilities: {},
    };
  }
};

const writeStorage = (storage: UsageStorage) => {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(storage));
  } catch {
    // ignore storage errors
  }
};

const ensureFacilityBucket = (storage: UsageStorage, facilityId: string): FacilityUsageBucket => {
  const existing = storage.facilities[facilityId];
  if (existing) return existing;
  const created: FacilityUsageBucket = { entities: {}, patients: {} };
  storage.facilities[facilityId] = created;
  return created;
};

const pruneEntityBucket = (bucket: UsageEntityBucket) => {
  const sorted = Object.values(bucket).sort(compareUsage);
  if (sorted.length <= MAX_ENTRIES_PER_ENTITY) return;
  const next = sorted.slice(0, MAX_ENTRIES_PER_ENTITY);
  Object.keys(bucket).forEach((key) => {
    delete bucket[key];
  });
  next.forEach((entry) => {
    bucket[entry.key] = entry;
  });
};

const upsertUsage = (
  entityBucketByEntity: Record<string, UsageEntityBucket>,
  entity: string,
  key: string,
  template: OrderRecommendationTemplate,
  usedAt: string,
) => {
  const byEntity = entityBucketByEntity[entity] ?? {};
  const current = byEntity[key];
  byEntity[key] = {
    key,
    entity,
    template,
    count: (current?.count ?? 0) + 1,
    lastUsedAt: usedAt,
  };
  pruneEntityBucket(byEntity);
  entityBucketByEntity[entity] = byEntity;
};

export const recordOrderRecommendationUsage = (params: {
  facilityId?: string;
  patientId?: string;
  entity: string;
  template: OrderRecommendationTemplate;
  usedAt?: string;
}) => {
  const facilityId = normalizeText(params.facilityId);
  const patientId = normalizeText(params.patientId);
  const entity = normalizeText(params.entity);
  if (!facilityId || !patientId || !entity) return;

  const normalizedTemplate = normalizeTemplate(params.template);
  const hasMeaningfulPayload =
    normalizedTemplate.items.length > 0 ||
    normalizedTemplate.materialItems.length > 0 ||
    normalizedTemplate.commentItems.length > 0 ||
    Boolean(normalizedTemplate.bodyPart?.name);
  if (!hasMeaningfulPayload) return;

  const usedAt = params.usedAt ?? new Date().toISOString();
  const key = buildTemplateKey(entity, normalizedTemplate);
  const storage = readStorage();
  const facilityBucket = ensureFacilityBucket(storage, facilityId);

  upsertUsage(facilityBucket.entities, entity, key, normalizedTemplate, usedAt);

  const patientBucket = facilityBucket.patients[patientId] ?? {};
  upsertUsage(patientBucket, entity, key, normalizedTemplate, usedAt);
  facilityBucket.patients[patientId] = patientBucket;

  storage.updatedAt = usedAt;
  writeStorage(storage);
};

export const listOrderRecommendations = (params: {
  facilityId?: string;
  patientId?: string;
  entity: string;
  limit?: number;
}): OrderRecommendationCandidate[] => {
  const facilityId = normalizeText(params.facilityId);
  const patientId = normalizeText(params.patientId);
  const entity = normalizeText(params.entity);
  const limit = Math.max(1, params.limit ?? 6);
  if (!facilityId || !patientId || !entity) return [];

  const storage = readStorage();
  const facilityBucket = storage.facilities[facilityId];
  if (!facilityBucket) return [];

  const patientRows = Object.values(facilityBucket.patients[patientId]?.[entity] ?? {}).sort(compareUsage);
  const facilityRows = Object.values(facilityBucket.entities[entity] ?? {}).sort(compareUsage);

  const usedKeys = new Set<string>();
  const result: OrderRecommendationCandidate[] = [];

  patientRows.forEach((row) => {
    if (result.length >= limit) return;
    usedKeys.add(row.key);
    result.push({
      key: row.key,
      source: 'patient',
      count: row.count,
      lastUsedAt: row.lastUsedAt,
      template: row.template,
    });
  });

  facilityRows.forEach((row) => {
    if (result.length >= limit) return;
    if (usedKeys.has(row.key)) return;
    usedKeys.add(row.key);
    result.push({
      key: row.key,
      source: 'facility',
      count: row.count,
      lastUsedAt: row.lastUsedAt,
      template: row.template,
    });
  });

  return result;
};

export const clearOrderRecommendationStorage = () => {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
};
