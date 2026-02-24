import type { MedicalModV2Information } from './orcaClaimApi';
import { resolveOrderEntityDefaultClassMeta } from './orderCategoryRegistry';
import type { OrderBundle, OrderBundleItem } from './orderBundleApi';
import { parseOrcaOrderItemMemo } from './orcaOrderItemMeta';

const COMMENT_CODE_PATTERN = /^(008[1-6]|8[1-6]|098|099|98|99)/;
const DRUG_CODE_PATTERN = /^6\d{8}$/;

const isCommentMedicationCode = (code: string) => COMMENT_CODE_PATTERN.test(code.trim());

export type RpNormalizedMedication = {
  code: string;
  name?: string;
  number?: string;
  unit?: string;
  genericFlg?: 'yes' | 'no';
};

export type RpNormalizedRowSource =
  | { kind: 'bundle_item'; itemIndex: number }
  | { kind: 'usage' };

export type RpNormalizedRow = {
  medication: RpNormalizedMedication;
  source: RpNormalizedRowSource;
};

export type RpNormalizedHeader = {
  entity?: string;
  documentId?: number;
  moduleId?: number;
  bundleName?: string;
  medicalClass: string;
  medicalClassName?: string;
  medicalClassNumber: string;
};

export type RpNormalizedBundle = {
  header: RpNormalizedHeader;
  rows: RpNormalizedRow[];
};

const toRpNormalizedMedication = (item: OrderBundleItem): RpNormalizedMedication | null => {
  const code = item.code?.trim();
  if (!code) return null;
  const { meta } = parseOrcaOrderItemMemo(item.memo);
  const genericFlg = DRUG_CODE_PATTERN.test(code) ? meta.genericFlg : undefined;
  return {
    code,
    name: item.name?.trim() || undefined,
    number: item.quantity?.trim() || undefined,
    unit: item.unit?.trim() || undefined,
    genericFlg,
  };
};

const resolveMedicalClass = (bundle: OrderBundle) => {
  const explicit = bundle.classCode?.trim();
  if (explicit) return explicit;
  const classMeta = resolveOrderEntityDefaultClassMeta(bundle.entity?.trim());
  return classMeta?.classCode?.trim() || '';
};

const buildUsageRow = (bundle: OrderBundle, rows: RpNormalizedRow[]): RpNormalizedRow | null => {
  const isPrescription = (bundle.entity?.trim() ?? '') === 'medOrder';
  if (!isPrescription) return null;
  const usageCodeCandidate =
    bundle.adminMemo?.trim() || (bundle.admin?.trim() ? bundle.admin.trim().split(/\s+/)[0] : '');
  const usageCode = /^\d{4,}$/.test(usageCodeCandidate) ? usageCodeCandidate : '';
  if (!usageCode) return null;
  const hasUsageAlready = rows.some((row) => row.medication.code.trim() === usageCode);
  if (hasUsageAlready) return null;
  const usageName =
    bundle.admin?.trim()
      ? bundle.admin.trim().startsWith(`${usageCode} `)
        ? bundle.admin.trim().slice(usageCode.length).trim() || undefined
        : bundle.admin.trim()
      : undefined;
  return {
    medication: {
      code: usageCode,
      name: usageName,
      number: '',
      unit: undefined,
      genericFlg: undefined,
    },
    source: { kind: 'usage' },
  };
};

export const normalizeOrderBundleToRp = (bundle: OrderBundle): RpNormalizedBundle | null => {
  const bundleRows: RpNormalizedRow[] = (bundle.items ?? []).flatMap((item, itemIndex) => {
    const medication = toRpNormalizedMedication(item);
    if (!medication) return [];
    return [{ medication, source: { kind: 'bundle_item', itemIndex } }];
  });
  if (bundleRows.length === 0) return null;

  const medicalClass = resolveMedicalClass(bundle);
  if (!medicalClass) return null;

  const usageRow = buildUsageRow(bundle, bundleRows);
  const isPrescription = (bundle.entity?.trim() ?? '') === 'medOrder';
  const head = isPrescription ? bundleRows.filter((row) => !isCommentMedicationCode(row.medication.code)) : bundleRows;
  const tail = isPrescription ? bundleRows.filter((row) => isCommentMedicationCode(row.medication.code)) : [];
  const rows = isPrescription ? [...head, ...(usageRow ? [usageRow] : []), ...tail] : bundleRows;

  return {
    header: {
      entity: bundle.entity?.trim() || undefined,
      documentId: bundle.documentId,
      moduleId: bundle.moduleId,
      bundleName: bundle.bundleName?.trim() || undefined,
      medicalClass,
      medicalClassName: bundle.className?.trim() || undefined,
      medicalClassNumber: bundle.bundleNumber?.trim() || '1',
    },
    rows,
  };
};

export type MedicalModV2InformationSource = RpNormalizedHeader & {
  rows: RpNormalizedRow[];
};

export const toMedicalModV2InformationWithSource = (
  bundle: OrderBundle,
): { info: MedicalModV2Information; source: MedicalModV2InformationSource } | null => {
  const normalized = normalizeOrderBundleToRp(bundle);
  if (!normalized) return null;

  const info: MedicalModV2Information = {
    medicalClass: normalized.header.medicalClass,
    medicalClassName: normalized.header.medicalClassName,
    medicalClassNumber: normalized.header.medicalClassNumber,
    medications: normalized.rows.map((row) => row.medication),
  };
  const source: MedicalModV2InformationSource = {
    ...normalized.header,
    rows: normalized.rows,
  };
  return { info, source };
};
