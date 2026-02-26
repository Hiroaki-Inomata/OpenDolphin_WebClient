import {
  fetchOrderBundlesWithPatientImportRecovery,
  mutateOrderBundles,
  type OrderBundle,
  type OrderBundleFetchResult,
  type OrderBundleItem,
  type OrderBundleMutationResult,
  type OrderBundleOperation,
} from './orderBundleApi';
import { formatOrcaOrderItemMemo, parseOrcaOrderItemMemo, type OrcaOrderItemMeta } from './orcaOrderItemMeta';

export type PrescriptionLocation = 'in' | 'out';
export type PrescriptionCategory = 'regular' | 'tonyo' | 'gaiyo';
export type PrescriptionRefillPattern = 'none' | 'standard' | 'alternate';

export type PrescriptionLowerFields = {
  lowerDrugCode?: string;
  lowerUsageCode?: string;
  lowerClaimCode?: string;
  lowerRouteCode?: string;
  lowerTimingCode?: string;
  lowerClassCode?: string;
};

export type PrescriptionClaimComment = PrescriptionLowerFields & {
  id: string;
  code?: string;
  name: string;
  note?: string;
};

export type PrescriptionDrug = PrescriptionLowerFields & {
  rowId: string;
  code?: string;
  name: string;
  quantity: string;
  unit: string;
  genericChangeAllowed: boolean;
  drugComment: string;
  claimComments: PrescriptionClaimComment[];
  patientRequest: boolean;
};

export type PrescriptionRp = PrescriptionLowerFields & {
  rpId: string;
  documentId?: number;
  moduleId?: number;
  name: string;
  location: PrescriptionLocation;
  category: PrescriptionCategory;
  usage: string;
  usageCode?: string;
  daysOrTimes: string;
  remark: string;
  refillCount?: 1 | 2 | 3;
  refillPattern: PrescriptionRefillPattern;
  doctorComment: string;
  started?: string;
  drugs: PrescriptionDrug[];
};

export type PrescriptionOrder = {
  patientId: string;
  doctorComment: string;
  rps: PrescriptionRp[];
  deletedDocumentIds: number[];
};

export type PrescriptionOrderFetchResult = Omit<OrderBundleFetchResult, 'bundles'> & {
  sourceBundles: OrderBundle[];
  order: PrescriptionOrder;
};

export type PrescriptionOrderSaveResult = OrderBundleMutationResult;

export type PrescriptionDoImportSource =
  | { type: 'bundle'; bundle: OrderBundle }
  | { type: 'rp'; rp: PrescriptionRp }
  | { type: 'order'; order: PrescriptionOrder };

type StoredRpMeta = {
  refillCount?: 1 | 2 | 3;
  refillPattern?: PrescriptionRefillPattern;
  doctorComment?: string;
  usageCode?: string;
  lowerFields?: PrescriptionLowerFields;
};

type StoredDrugMeta = {
  claimComments?: Array<{ code?: string; name?: string; note?: string; lowerFields?: PrescriptionLowerFields }>;
  patientRequest?: boolean;
  lowerFields?: PrescriptionLowerFields;
};

const COMMENT_CODE_PATTERN = /^(008[1-6]|8[1-6]|098|099|98|99)/;
const RX_RP_META_PREFIX = '__rx_rp_meta__:';
const RX_DRUG_META_PREFIX = '__rx_drug_meta__:';
const RX_CLAIM_LINK_PREFIX = '__rx_claim_target__:';
const PRESCRIPTION_CLASS_CODES: Record<PrescriptionCategory, Record<PrescriptionLocation, string>> = {
  regular: { in: '211', out: '212' },
  tonyo: { in: '221', out: '222' },
  gaiyo: { in: '231', out: '232' },
};

const createStableId = (prefix: string) =>
  `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;

const parseJsonLine = <T,>(line: string, prefix: string): T | null => {
  if (!line.startsWith(prefix)) return null;
  const raw = line.slice(prefix.length).trim();
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
};

const splitMetaText = <T,>(raw: string | undefined, prefix: string): { meta: T | null; text: string } => {
  if (!raw) return { meta: null, text: '' };
  const lines = raw.split('\n');
  let meta: T | null = null;
  const next: string[] = [];
  lines.forEach((line) => {
    const parsed = parseJsonLine<T>(line, prefix);
    if (parsed && !meta) {
      meta = parsed;
      return;
    }
    next.push(line);
  });
  return { meta, text: next.join('\n').trim() };
};

const withJsonMetaLine = (text: string, prefix: string, meta: unknown, keep: boolean) => {
  const cleaned = text.trim();
  if (!keep) return cleaned;
  const encoded = `${prefix}${JSON.stringify(meta)}`;
  return cleaned ? `${cleaned}\n${encoded}` : encoded;
};

const parsePrescriptionClassCode = (classCode?: string | null): { location: PrescriptionLocation; category: PrescriptionCategory } => {
  if (!classCode) return { location: 'out', category: 'regular' };
  const normalized = classCode.trim();
  const location: PrescriptionLocation = normalized.endsWith('2') ? 'out' : 'in';
  if (normalized.startsWith('22')) return { location, category: 'tonyo' };
  if (normalized.startsWith('23')) return { location, category: 'gaiyo' };
  return { location, category: 'regular' };
};

const resolvePrescriptionClassCode = (category: PrescriptionCategory, location: PrescriptionLocation) =>
  PRESCRIPTION_CLASS_CODES[category][location];

const isClaimCommentItem = (item: OrderBundleItem) => {
  const code = item.code?.trim() ?? '';
  return code.length > 0 && COMMENT_CODE_PATTERN.test(code);
};

const parseClaimTargetIndex = (memo?: string | null): number | null => {
  const raw = memo?.trim() ?? '';
  if (!raw.startsWith(RX_CLAIM_LINK_PREFIX)) return null;
  const index = Number(raw.slice(RX_CLAIM_LINK_PREFIX.length));
  if (!Number.isInteger(index) || index < 0) return null;
  return index;
};

const toClaimComment = (item: OrderBundleItem): PrescriptionClaimComment => ({
  id: createStableId('claim'),
  code: item.code?.trim() || undefined,
  name: item.name?.trim() || '',
  note: item.memo?.trim() || undefined,
});

const buildEmptyDrug = (): PrescriptionDrug => ({
  rowId: createStableId('drug'),
  code: undefined,
  name: '',
  quantity: '',
  unit: '',
  genericChangeAllowed: true,
  drugComment: '',
  claimComments: [],
  patientRequest: true,
});

const hasAnyLowerField = (fields?: PrescriptionLowerFields) => {
  if (!fields) return false;
  return Object.values(fields).some((value) => Boolean(value && value.trim()));
};

const normalizeClaimComments = (comments: PrescriptionClaimComment[]) => {
  const seen = new Set<string>();
  const next: PrescriptionClaimComment[] = [];
  comments.forEach((comment) => {
    const code = comment.code?.trim() ?? '';
    const name = comment.name.trim();
    if (!code && !name) return;
    const key = `${code}|${name}`;
    if (seen.has(key)) return;
    seen.add(key);
    next.push({
      ...comment,
      id: comment.id || createStableId('claim'),
      code: code || undefined,
      name,
    });
  });
  return next;
};

const toDrugFromItem = (item: OrderBundleItem): PrescriptionDrug => {
  const parsed = parseOrcaOrderItemMemo(item.memo);
  const drugMetaParsed = splitMetaText<StoredDrugMeta>(parsed.memoText, RX_DRUG_META_PREFIX);
  const drugMeta = drugMetaParsed.meta;
  const claimComments = normalizeClaimComments(
    (drugMeta?.claimComments ?? []).map((entry) => ({
      id: createStableId('claim'),
      code: entry.code?.trim() || undefined,
      name: entry.name?.trim() || '',
      note: entry.note?.trim() || undefined,
      ...(entry.lowerFields ?? {}),
    })),
  );

  return {
    rowId: createStableId('drug'),
    code: item.code?.trim() || undefined,
    name: item.name?.trim() || '',
    quantity: item.quantity?.trim() || '',
    unit: item.unit?.trim() || '',
    genericChangeAllowed: parsed.meta.genericFlg !== 'no',
    drugComment: parsed.meta.userComment?.trim() || '',
    claimComments,
    patientRequest: drugMeta?.patientRequest ?? true,
    ...(drugMeta?.lowerFields ?? {}),
  };
};

const toRpFromBundle = (bundle: OrderBundle): PrescriptionRp => {
  const classParsed = parsePrescriptionClassCode(bundle.classCode);
  const memoParsed = splitMetaText<StoredRpMeta>(bundle.memo, RX_RP_META_PREFIX);
  const rpMeta = memoParsed.meta;
  const drugs: PrescriptionDrug[] = [];
  const orphanComments: PrescriptionClaimComment[] = [];

  bundle.items.forEach((item) => {
    if (isClaimCommentItem(item)) {
      const comment = toClaimComment(item);
      const targetIndex = parseClaimTargetIndex(item.memo);
      if (targetIndex !== null && drugs[targetIndex]) {
        drugs[targetIndex].claimComments = normalizeClaimComments([
          ...drugs[targetIndex].claimComments,
          comment,
        ]);
        return;
      }
      if (drugs.length > 0) {
        const last = drugs[drugs.length - 1];
        last.claimComments = normalizeClaimComments([...last.claimComments, comment]);
      } else {
        orphanComments.push(comment);
      }
      return;
    }
    drugs.push(toDrugFromItem(item));
  });

  if (drugs.length === 0) {
    drugs.push(buildEmptyDrug());
  }
  if (orphanComments.length > 0) {
    drugs[0].claimComments = normalizeClaimComments([...drugs[0].claimComments, ...orphanComments]);
  }

  const refillCount = rpMeta?.refillCount;

  return {
    rpId: createStableId('rp'),
    documentId: bundle.documentId,
    moduleId: bundle.moduleId,
    name: bundle.bundleName?.trim() || '',
    location: classParsed.location,
    category: classParsed.category,
    usage: bundle.admin?.trim() || '',
    usageCode: rpMeta?.usageCode ?? (bundle.adminMemo?.trim() || undefined),
    daysOrTimes: bundle.bundleNumber?.trim() || '1',
    remark: memoParsed.text,
    refillCount: refillCount === 1 || refillCount === 2 || refillCount === 3 ? refillCount : undefined,
    refillPattern: rpMeta?.refillPattern ?? 'none',
    doctorComment: rpMeta?.doctorComment?.trim() || '',
    started: bundle.started,
    drugs,
    ...(rpMeta?.lowerFields ?? {}),
  };
};

export const buildEmptyPrescriptionRp = (started?: string): PrescriptionRp => ({
  rpId: createStableId('rp'),
  name: '',
  location: 'out',
  category: 'regular',
  usage: '',
  usageCode: undefined,
  daysOrTimes: '1',
  remark: '',
  refillCount: undefined,
  refillPattern: 'none',
  doctorComment: '',
  started,
  drugs: [buildEmptyDrug()],
});

export const buildEmptyPrescriptionOrder = (patientId: string, started?: string): PrescriptionOrder => ({
  patientId,
  doctorComment: '',
  rps: [buildEmptyPrescriptionRp(started)],
  deletedDocumentIds: [],
});

export const toPrescriptionOrder = (sourceBundles: OrderBundle[], patientId: string): PrescriptionOrder => {
  const medBundles = sourceBundles.filter((bundle) => (bundle.entity?.trim() ?? '') === 'medOrder');
  if (medBundles.length === 0) {
    return buildEmptyPrescriptionOrder(patientId);
  }
  const rps = medBundles.map(toRpFromBundle);
  return {
    patientId,
    doctorComment: rps.find((rp) => rp.doctorComment.trim())?.doctorComment ?? '',
    rps,
    deletedDocumentIds: [],
  };
};

const normalizeRpMeta = (rp: PrescriptionRp, doctorComment: string): StoredRpMeta => {
  const lowerFields = hasAnyLowerField({
    lowerDrugCode: rp.lowerDrugCode,
    lowerUsageCode: rp.lowerUsageCode,
    lowerClaimCode: rp.lowerClaimCode,
    lowerRouteCode: rp.lowerRouteCode,
    lowerTimingCode: rp.lowerTimingCode,
    lowerClassCode: rp.lowerClassCode,
  })
    ? {
        lowerDrugCode: rp.lowerDrugCode,
        lowerUsageCode: rp.lowerUsageCode,
        lowerClaimCode: rp.lowerClaimCode,
        lowerRouteCode: rp.lowerRouteCode,
        lowerTimingCode: rp.lowerTimingCode,
        lowerClassCode: rp.lowerClassCode,
      }
    : undefined;
  const trimmedDoctorComment = doctorComment.trim() || rp.doctorComment.trim();
  return {
    refillCount: rp.refillCount,
    refillPattern: rp.refillPattern,
    doctorComment: trimmedDoctorComment || undefined,
    usageCode: rp.usageCode?.trim() || undefined,
    lowerFields,
  };
};

const normalizeDrugMeta = (drug: PrescriptionDrug): StoredDrugMeta => {
  const lowerFields = hasAnyLowerField({
    lowerDrugCode: drug.lowerDrugCode,
    lowerUsageCode: drug.lowerUsageCode,
    lowerClaimCode: drug.lowerClaimCode,
    lowerRouteCode: drug.lowerRouteCode,
    lowerTimingCode: drug.lowerTimingCode,
    lowerClassCode: drug.lowerClassCode,
  })
    ? {
        lowerDrugCode: drug.lowerDrugCode,
        lowerUsageCode: drug.lowerUsageCode,
        lowerClaimCode: drug.lowerClaimCode,
        lowerRouteCode: drug.lowerRouteCode,
        lowerTimingCode: drug.lowerTimingCode,
        lowerClassCode: drug.lowerClassCode,
      }
    : undefined;

  return {
    patientRequest: drug.patientRequest,
    claimComments: normalizeClaimComments(drug.claimComments).map((comment) => ({
      code: comment.code,
      name: comment.name,
      note: comment.note,
      lowerFields: hasAnyLowerField({
        lowerDrugCode: comment.lowerDrugCode,
        lowerUsageCode: comment.lowerUsageCode,
        lowerClaimCode: comment.lowerClaimCode,
        lowerRouteCode: comment.lowerRouteCode,
        lowerTimingCode: comment.lowerTimingCode,
        lowerClassCode: comment.lowerClassCode,
      })
        ? {
            lowerDrugCode: comment.lowerDrugCode,
            lowerUsageCode: comment.lowerUsageCode,
            lowerClaimCode: comment.lowerClaimCode,
            lowerRouteCode: comment.lowerRouteCode,
            lowerTimingCode: comment.lowerTimingCode,
            lowerClassCode: comment.lowerClassCode,
          }
        : undefined,
    })),
    lowerFields,
  };
};

const toOrderBundleItems = (rp: PrescriptionRp): OrderBundleItem[] => {
  const items: OrderBundleItem[] = [];
  rp.drugs.forEach((drug, drugIndex) => {
    const code = drug.code?.trim() || undefined;
    const name = drug.name.trim();
    if (!name && !code) return;

    const orcaMeta: OrcaOrderItemMeta = {
      genericFlg: drug.genericChangeAllowed ? 'yes' : 'no',
      userComment: drug.drugComment.trim() || undefined,
    };

    const drugMeta = normalizeDrugMeta(drug);
    const memoText = withJsonMetaLine('', RX_DRUG_META_PREFIX, drugMeta, Boolean(
      drugMeta.patientRequest !== undefined ||
        (drugMeta.claimComments && drugMeta.claimComments.length > 0) ||
        hasAnyLowerField(drugMeta.lowerFields),
    ));

    items.push({
      code,
      name,
      quantity: drug.quantity.trim() || '',
      unit: drug.unit.trim() || '',
      memo: formatOrcaOrderItemMemo(orcaMeta, memoText),
    });

    normalizeClaimComments(drug.claimComments).forEach((comment) => {
      items.push({
        code: comment.code?.trim() || undefined,
        name: comment.name.trim(),
        quantity: '',
        unit: '',
        memo: `${RX_CLAIM_LINK_PREFIX}${drugIndex}`,
      });
    });
  });
  return items;
};

export const buildPrescriptionMutationOperations = (order: PrescriptionOrder): OrderBundleOperation[] => {
  const operations: OrderBundleOperation[] = [];
  order.rps.forEach((rp) => {
    const classCode = resolvePrescriptionClassCode(rp.category, rp.location);
    const rpMeta = normalizeRpMeta(rp, order.doctorComment);
    const memo = withJsonMetaLine(
      rp.remark.trim(),
      RX_RP_META_PREFIX,
      rpMeta,
      Boolean(
        rpMeta.refillCount ||
          (rpMeta.refillPattern && rpMeta.refillPattern !== 'none') ||
          (rpMeta.doctorComment && rpMeta.doctorComment.trim()) ||
          (rpMeta.usageCode && rpMeta.usageCode.trim()) ||
          hasAnyLowerField(rpMeta.lowerFields),
      ),
    );

    operations.push({
      operation: rp.documentId ? 'update' : 'create',
      documentId: rp.documentId,
      moduleId: rp.moduleId,
      entity: 'medOrder',
      bundleName: rp.name.trim() || '処方RP',
      bundleNumber: rp.daysOrTimes.trim() || '1',
      classCode,
      classCodeSystem: 'Claim007',
      className: undefined,
      admin: rp.usage.trim(),
      adminMemo: rp.usageCode?.trim() || '',
      memo,
      startDate: rp.started,
      items: toOrderBundleItems(rp),
    });
  });

  const deleted = Array.from(
    new Set(
      order.deletedDocumentIds
        .filter((id) => Number.isInteger(id) && id > 0)
        .map((id) => Number(id)),
    ),
  );
  deleted.forEach((documentId) => {
    operations.push({
      operation: 'delete',
      documentId,
      entity: 'medOrder',
    });
  });

  return operations;
};

export async function fetchPrescriptionOrder(params: {
  patientId: string;
  from?: string;
}): Promise<PrescriptionOrderFetchResult> {
  const fetched = await fetchOrderBundlesWithPatientImportRecovery({
    patientId: params.patientId,
    entity: 'medOrder',
    from: params.from,
  });
  const sourceBundles = fetched.bundles.filter((bundle) => (bundle.entity?.trim() ?? '') === 'medOrder');
  const order = fetched.ok ? toPrescriptionOrder(sourceBundles, params.patientId) : buildEmptyPrescriptionOrder(params.patientId);

  return {
    ...fetched,
    sourceBundles,
    order,
  };
}

export async function savePrescriptionOrder(params: {
  patientId: string;
  order: PrescriptionOrder;
}): Promise<PrescriptionOrderSaveResult> {
  const operations = buildPrescriptionMutationOperations(params.order);
  if (operations.length === 0) {
    return {
      ok: true,
      message: '保存対象がありません。',
    };
  }
  return mutateOrderBundles({
    patientId: params.patientId,
    operations,
  });
}

export const fetchPrescriptionOrderBundlesWithPatientImportRecovery = async (params: {
  patientId: string;
  from?: string;
}): Promise<OrderBundleFetchResult> => {
  const result = await fetchPrescriptionOrder(params);
  return {
    ...result,
    bundles: result.sourceBundles,
  };
};

export const mutatePrescriptionOrderBundles = async (params: {
  patientId: string;
  operations: OrderBundleOperation[];
}): Promise<OrderBundleMutationResult> => {
  const operations = params.operations.map((operation) => ({
    ...operation,
    entity: 'medOrder',
  }));
  return mutateOrderBundles({
    patientId: params.patientId,
    operations,
  });
};

const cloneClaimComment = (comment: PrescriptionClaimComment): PrescriptionClaimComment => ({
  ...comment,
  id: comment.id || createStableId('claim'),
  code: comment.code?.trim() || undefined,
  name: comment.name.trim(),
});

const cloneDrug = (drug: PrescriptionDrug): PrescriptionDrug => ({
  ...drug,
  rowId: drug.rowId || createStableId('drug'),
  code: drug.code?.trim() || undefined,
  name: drug.name,
  claimComments: normalizeClaimComments(drug.claimComments).map(cloneClaimComment),
});

const cloneRp = (rp: PrescriptionRp): PrescriptionRp => ({
  ...rp,
  rpId: rp.rpId || createStableId('rp'),
  usageCode: rp.usageCode?.trim() || undefined,
  drugs: rp.drugs.map(cloneDrug),
});

export const importPrescriptionDoInput = (
  baseOrder: PrescriptionOrder,
  source: PrescriptionDoImportSource,
): PrescriptionOrder => {
  const nextBase: PrescriptionOrder = {
    ...baseOrder,
    deletedDocumentIds: [...baseOrder.deletedDocumentIds],
    rps: baseOrder.rps.map(cloneRp),
  };

  if (source.type === 'bundle') {
    const importedRp = toRpFromBundle(source.bundle);
    nextBase.rps = [...nextBase.rps, importedRp];
    if (!nextBase.doctorComment.trim() && importedRp.doctorComment.trim()) {
      nextBase.doctorComment = importedRp.doctorComment;
    }
    return nextBase;
  }

  if (source.type === 'rp') {
    const importedRp = cloneRp(source.rp);
    nextBase.rps = [...nextBase.rps, importedRp];
    if (!nextBase.doctorComment.trim() && importedRp.doctorComment.trim()) {
      nextBase.doctorComment = importedRp.doctorComment;
    }
    return nextBase;
  }

  const incoming = source.order;
  const importedRps = incoming.rps.map(cloneRp);
  const importedDeletes = incoming.deletedDocumentIds.filter((id) => Number.isInteger(id) && id > 0);
  nextBase.rps = [...nextBase.rps, ...importedRps];
  nextBase.deletedDocumentIds = Array.from(new Set([...nextBase.deletedDocumentIds, ...importedDeletes]));
  if (!nextBase.doctorComment.trim() && incoming.doctorComment.trim()) {
    nextBase.doctorComment = incoming.doctorComment;
  }
  return nextBase;
};
