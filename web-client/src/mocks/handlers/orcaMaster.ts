import { http, HttpResponse, passthrough } from 'msw';

const generateRunId = () => new Date().toISOString().slice(0, 19).replace(/[-:]/g, '') + 'Z';

const generateTraceId = () => {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `trace-${Date.now()}`;
};

const resolveAuditHeaders = (request: Request) => {
  const runId = request.headers.get('x-run-id') ?? generateRunId();
  const traceId = request.headers.get('x-trace-id') ?? generateTraceId();
  return { runId, traceId };
};

const shouldBypass = (request: Request): boolean => {
  // When the app explicitly requests server-backed data (e.g. E2E with
  // X-DataSource-Transition=server), do not override master queries.
  const transition = request.headers.get('x-datasource-transition');
  return transition != null && transition.trim().toLowerCase() === 'server';
};

type TensuItem = {
  tensuCode?: string;
  name?: string;
  unit?: string;
  category?: string;
  points?: number;
  noticeDate?: string;
  startDate?: string;
  endDate?: string;
};

type DrugMasterItem = {
  code?: string;
  name?: string;
  unit?: string;
  category?: string;
  note?: string;
  validFrom?: string;
  validTo?: string;
};

const ETENSU_ITEMS: TensuItem[] = [
  { tensuCode: '111000110', name: '初診料', unit: '', category: '110', points: 291, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '130003510', name: '静脈内注射', unit: '', category: '320', points: 37, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '140000610', name: '創傷処置（１００ｃｍ２未満）', unit: '', category: '400', points: 52, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '150129210', name: 'カテーテル心臓手術', unit: '', category: '500', points: 1000, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '160000110', name: '血液学的検査判断料', unit: '', category: '600', points: 125, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '170017510', name: 'ＣＴ撮影', unit: '', category: '700', points: 500, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '180000210', name: '薬剤情報提供料', unit: '', category: '800', points: 10, noticeDate: '20240101', startDate: '20240101' },
];

const BODY_PART_ITEMS: TensuItem[] = [
  { tensuCode: '820181220', name: '撮影部位（単純撮影）：胸部（肩を除く。）', unit: '部位', category: '820', points: 0, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '820181300', name: '撮影部位（単純撮影）：腹部', unit: '部位', category: '820', points: 0, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '820183500', name: '撮影部位（ＭＲＩ撮影）：膝', unit: '部位', category: '820', points: 0, noticeDate: '20240101', startDate: '20240101' },
];

const COMMENT_ITEMS: TensuItem[] = [
  { tensuCode: '820000001', name: '別途コメントあり', unit: '回', category: '820', points: 0, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '820181300', name: '撮影部位（単純撮影）：腹部', unit: '部位', category: '820', points: 0, noticeDate: '20240101', startDate: '20240101' },
  { tensuCode: '810000001', name: '混合', unit: '回', category: '810', points: 0, noticeDate: '20240101', startDate: '20240101' },
];

const GENERIC_CLASS_ITEMS: DrugMasterItem[] = [
  {
    code: 'A100',
    name: 'アムロジピン',
    unit: '錠',
    category: '降圧薬',
    note: 'MSW',
    validFrom: '20240101',
    validTo: '99999999',
  },
  {
    code: 'A200',
    name: 'ロサルタン',
    unit: '錠',
    category: '降圧薬',
    note: 'MSW',
    validFrom: '20240101',
    validTo: '99999999',
  },
];

const MATERIAL_ITEMS: DrugMasterItem[] = [
  {
    code: 'M001',
    name: '処置材料A',
    unit: '個',
    category: '処置',
    note: 'MSW',
    validFrom: '20240101',
    validTo: '99999999',
  },
];

const YOUHOU_ITEMS: DrugMasterItem[] = [
  {
    code: 'Y100',
    name: '1日1回 朝食後',
    unit: '回',
    category: '用法',
    note: 'MSW',
    validFrom: '20240101',
    validTo: '99999999',
  },
];

const KENSA_SORT_ITEMS: DrugMasterItem[] = [
  {
    code: 'K01',
    name: '血液検査',
    unit: '回',
    category: '検査区分',
    note: 'MSW',
    validFrom: '20240101',
    validTo: '99999999',
  },
];

const filterByKeyword = (items: TensuItem[], keyword: string) => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return items;
  return items.filter((item) => {
    const name = item.name?.toLowerCase() ?? '';
    const code = item.tensuCode?.toLowerCase() ?? '';
    return name.includes(normalized) || code.includes(normalized);
  });
};

const handleEtensuRequest = (request: Request) => {
  const { runId, traceId } = resolveAuditHeaders(request);
  const url = new URL(request.url);
  const category = url.searchParams.get('category');
  const keyword = url.searchParams.get('keyword') ?? '';
  const filteredByKeyword = filterByKeyword(ETENSU_ITEMS, keyword);
  const items =
    category && category.trim().length > 0
      ? filteredByKeyword.filter((item) => {
          const value = item.category?.trim() ?? '';
          return value.startsWith(category.trim());
        })
      : filteredByKeyword;
  return HttpResponse.json(
    { items, totalCount: items.length, runId, traceId },
    { headers: { 'x-run-id': runId, 'x-trace-id': traceId } },
  );
};

const handleBodypartRequest = (request: Request) => {
  const { runId, traceId } = resolveAuditHeaders(request);
  const url = new URL(request.url);
  const keyword = url.searchParams.get('keyword') ?? '';
  const items = filterByKeyword(BODY_PART_ITEMS, keyword);
  return HttpResponse.json(
    { items, totalCount: items.length, runId, traceId },
    { headers: { 'x-run-id': runId, 'x-trace-id': traceId } },
  );
};

const handleCommentRequest = (request: Request) => {
  const { runId, traceId } = resolveAuditHeaders(request);
  const url = new URL(request.url);
  const keyword = url.searchParams.get('keyword') ?? '';
  const items = filterByKeyword(COMMENT_ITEMS, keyword);
  return HttpResponse.json(
    { items, totalCount: items.length, runId, traceId },
    { headers: { 'x-run-id': runId, 'x-trace-id': traceId } },
  );
};

const filterDrugItems = (items: DrugMasterItem[], keyword: string) => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return items;
  return items.filter((item) => {
    const name = item.name?.toLowerCase() ?? '';
    const code = item.code?.toLowerCase() ?? '';
    return name.includes(normalized) || code.includes(normalized);
  });
};

const handleDrugMasterRequest = (request: Request, items: DrugMasterItem[]) => {
  const { runId, traceId } = resolveAuditHeaders(request);
  const url = new URL(request.url);
  const keyword = url.searchParams.get('keyword') ?? '';
  const filtered = filterDrugItems(items, keyword);
  return HttpResponse.json(
    { items: filtered, totalCount: filtered.length, runId, traceId },
    { headers: { 'x-run-id': runId, 'x-trace-id': traceId } },
  );
};

export const orcaMasterHandlers = [
  http.get('/orca/tensu/etensu', ({ request }) => (shouldBypass(request) ? passthrough() : handleEtensuRequest(request))),
  http.get('/orca/master/etensu', ({ request }) => (shouldBypass(request) ? passthrough() : handleEtensuRequest(request))),
  http.get('/orca/master/bodypart', ({ request }) => (shouldBypass(request) ? passthrough() : handleBodypartRequest(request))),
  http.get('/orca/master/comment', ({ request }) => (shouldBypass(request) ? passthrough() : handleCommentRequest(request))),
  http.get('/orca/master/generic-class', ({ request }) => (shouldBypass(request) ? passthrough() : handleDrugMasterRequest(request, GENERIC_CLASS_ITEMS))),
  http.get('/orca/master/material', ({ request }) => (shouldBypass(request) ? passthrough() : handleDrugMasterRequest(request, MATERIAL_ITEMS))),
  http.get('/orca/master/youhou', ({ request }) => (shouldBypass(request) ? passthrough() : handleDrugMasterRequest(request, YOUHOU_ITEMS))),
  http.get('/orca/master/kensa-sort', ({ request }) => (shouldBypass(request) ? passthrough() : handleDrugMasterRequest(request, KENSA_SORT_ITEMS))),
];
