import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const baseURL = process.env.QA_BASE_URL ?? 'http://localhost:5173';
const runId = process.env.RUN_ID;
const artifactDir = process.env.ARTIFACT_DIR;
if (!runId || !artifactDir) throw new Error('RUN_ID and ARTIFACT_DIR are required');
const screenshotDir = path.join(artifactDir, 'screenshots');
fs.mkdirSync(screenshotDir, { recursive: true });

const facilityId = '1.3.6.1.4.1.9414.72.103';
const patientId = process.env.QA_PATIENT_ID ?? '01415';

const auth = {
  facilityId,
  userId: 'doctor1',
  passwordMd5: '632080fabdb968f9ac4f31fb55104648',
  passwordPlain: 'doctor2025',
  clientUuid: `qa-${runId}`,
};

const session = {
  facilityId,
  userId: auth.userId,
  displayName: `QA ${runId}`,
  clientUuid: auth.clientUuid,
  runId,
  role: 'admin',
  roles: ['admin'],
};

const sanitize = (s) => String(s).replace(/[^a-zA-Z0-9_-]/g, '_');
const writeShot = async (page, name) => {
  const shot = path.join(screenshotDir, `${sanitize(name)}.png`);
  await page.screenshot({ path: shot, fullPage: true });
  return path.relative(artifactDir, shot);
};

const parseBodyTrace = (body) => {
  if (!body || typeof body !== 'string') return null;
  try {
    const json = JSON.parse(body);
    return json?.traceId ?? json?.payload?.traceId ?? json?.correlationId ?? null;
  } catch {
    return null;
  }
};

const parseBodyTotal = (body) => {
  if (!body || typeof body !== 'string') return null;
  try {
    const json = JSON.parse(body);
    if (typeof json?.totalCount === 'number') return json.totalCount;
    if (Array.isArray(json?.items)) return json.items.length;
    if (Array.isArray(json)) return json.length;
    return null;
  } catch {
    return null;
  }
};

const categories = [
  {
    key: 'prescription',
    quickAdd: '[data-test-id="order-dock-quick-add-prescription"]',
    panel: '[data-test-id="medOrder-edit-panel"]',
    input: '#medOrder-item-name-0',
    endpointPart: '/orca/master/generic-class',
    keyword: '中枢',
  },
  {
    key: 'injection',
    quickAdd: '[data-test-id="order-dock-quick-add-injection"]',
    panel: '[data-test-id="injectionOrder-edit-panel"]',
    input: '#injectionOrder-item-name-0',
    endpointPart: '/orca/master/generic-class',
    keyword: '中枢',
  },
  {
    key: 'procedure',
    quickAdd: '[data-test-id="order-dock-quick-add-treatment"]',
    panel: '[data-test-id="treatmentOrder-edit-panel"]',
    input: '#treatmentOrder-item-name-0',
    endpointPart: '/orca/master/material',
    keyword: '動脈',
    switchLabel: '処置材料',
  },
  {
    key: 'test',
    quickAdd: '[data-test-id="order-dock-quick-add-test"]',
    panel: '[data-test-id="testOrder-edit-panel"]',
    input: '#testOrder-item-name-0',
    endpointPart: '/orca/master/kensa-sort',
    keyword: '血',
    switchLabel: '検査区分',
  },
  {
    key: 'charge',
    quickAdd: '[data-test-id="order-dock-quick-add-charge"]',
    panel: '[data-test-id="baseChargeOrder-edit-panel"]',
    input: '#baseChargeOrder-item-name-0',
    endpointPart: '/orca/master/etensu',
    keyword: '初診',
  },
];

const run = async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });

  await context.addInitScript(([storedSession, credentials]) => {
    window.sessionStorage.setItem('opendolphin:web-client:auth', JSON.stringify(storedSession));
    window.sessionStorage.setItem('devFacilityId', credentials.facilityId);
    window.sessionStorage.setItem('devUserId', credentials.userId);
    window.sessionStorage.setItem('devPasswordMd5', credentials.passwordMd5);
    window.sessionStorage.setItem('devPasswordPlain', credentials.passwordPlain);
    window.sessionStorage.setItem('devClientUuid', credentials.clientUuid);
    window.localStorage.setItem('devFacilityId', credentials.facilityId);
    window.localStorage.setItem('devUserId', credentials.userId);
    window.localStorage.setItem('devPasswordMd5', credentials.passwordMd5);
    window.localStorage.setItem('devPasswordPlain', credentials.passwordPlain);
    window.localStorage.setItem('devClientUuid', credentials.clientUuid);
  }, [session, auth]);

  const page = await context.newPage();
  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.locator('.charts-page').waitFor({ timeout: 20000 });
  await page.waitForFunction(() => {
    const el = document.querySelector('.charts-patient-summary__safety-state');
    const text = (el?.textContent ?? '').trim();
    return text === 'OK';
  }, { timeout: 20000 }).catch(() => null);
  const safetyStateText = await page.locator('.charts-patient-summary__safety-state').first().textContent().catch(() => null);

  const results = [];
  for (const category of categories) {
    const result = {
      category: category.key,
      status: 'fail',
      endpoint: category.endpointPart,
      keyword: category.keyword,
      responseStatus: null,
      requestUrl: null,
      responseTraceId: null,
      bodyTraceId: null,
      totalCount: null,
      selected: false,
      reflected: false,
      reflectedValue: '',
      note: '',
      screenshot: null,
    };

    try {
      const quickAdd = page.locator(category.quickAdd);
      await quickAdd.waitFor({ state: 'visible', timeout: 15000 });
      await quickAdd.click({ timeout: 8000 });

      const panel = page.locator(category.panel);
      await panel.waitFor({ state: 'visible', timeout: 15000 });

      if (category.switchLabel) {
        await panel.getByRole('button', { name: category.switchLabel }).click({ timeout: 5000 });
      }

      const input = panel.locator(category.input);
      await input.waitFor({ state: 'visible', timeout: 10000 });

      const responsePromise = page.waitForResponse(
        (res) => res.url().includes(category.endpointPart) && res.request().method() === 'GET',
        { timeout: 20000 },
      );

      await input.fill(category.keyword);
      const response = await responsePromise;
      const body = await response.text().catch(() => '');
      const row = panel.locator('button.charts-side-panel__search-row').first();

      result.responseStatus = response.status();
      result.requestUrl = response.url();
      result.responseTraceId = response.headers()['x-trace-id'] ?? null;
      result.bodyTraceId = parseBodyTrace(body);
      result.totalCount = parseBodyTotal(body);

      await row.waitFor({ state: 'visible', timeout: 10000 });
      const before = await input.inputValue();
      await row.dispatchEvent('click');
      const after = await input.inputValue();
      result.selected = true;
      result.reflected = after.trim().length > 0 && after !== before;
      result.reflectedValue = after;

      result.status = result.responseStatus === 200 && result.selected && result.reflected ? 'pass' : 'partial';
      result.screenshot = await writeShot(page, `${category.key}_pass`);

      await page.getByRole('button', { name: '一覧へ' }).click({ timeout: 5000 });
      await page.locator(category.quickAdd).waitFor({ state: 'visible', timeout: 10000 });
    } catch (error) {
      result.note = String(error);
      result.screenshot = await writeShot(page, `${category.key}_error`).catch(() => null);
      try {
        await page.getByRole('button', { name: '一覧へ' }).click({ timeout: 1000 });
      } catch {}
    }

    results.push(result);
  }

  const summary = {
    runId,
    executedAt: new Date().toISOString(),
    baseURL,
    chartsUrl: page.url(),
    safetyStateText,
    results,
  };

  fs.writeFileSync(path.join(artifactDir, 'summary.json'), JSON.stringify(summary, null, 2));
  const md = [
    '# cmd_20260218_01_sub_6 major category recheck',
    '',
    `- RUN_ID: ${runId}`,
    `- Base URL: ${baseURL}`,
    `- Data state: ${safetyStateText ?? 'unknown'}`,
    '',
    '|category|status|HTTP|traceId(header)|traceId(body)|totalCount|selected|reflected|request|evidence|',
    '|---|---|---:|---|---|---:|---:|---:|---|---|',
    ...results.map((r) => `|${r.category}|${r.status}|${r.responseStatus ?? '-'}|${r.responseTraceId ?? '-'}|${r.bodyTraceId ?? '-'}|${r.totalCount ?? '-'}|${r.selected ? 'yes' : 'no'}|${r.reflected ? 'yes' : 'no'}|${r.requestUrl ?? '-'}|${r.screenshot ?? '-'}|`),
  ].join('\n');
  fs.writeFileSync(path.join(artifactDir, 'summary.md'), md);

  await context.close();
  await browser.close();
  console.log(JSON.stringify({ runId, artifactDir, summaryPath: path.join(artifactDir, 'summary.md') }));
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
