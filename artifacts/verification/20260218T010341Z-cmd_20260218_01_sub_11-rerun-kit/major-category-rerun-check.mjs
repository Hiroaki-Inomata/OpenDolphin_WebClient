import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const baseURL = process.env.QA_BASE_URL ?? 'http://localhost:5173';
const runId = process.env.RUN_ID ?? new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
const artifactDir = process.env.ARTIFACT_DIR ?? path.join(process.cwd(), 'artifacts', 'verification', `${runId}-major-category-rerun`);
const screenshotDir = path.join(artifactDir, 'screenshots');
const logDir = path.join(artifactDir, 'logs');

fs.mkdirSync(screenshotDir, { recursive: true });
fs.mkdirSync(logDir, { recursive: true });

const facilityId = process.env.QA_FACILITY_ID ?? '1.3.6.1.4.1.9414.72.103';
const patientId = process.env.QA_PATIENT_ID ?? '01415';

const auth = {
  facilityId,
  userId: process.env.QA_USER_ID ?? 'doctor1',
  passwordMd5: process.env.QA_PASSWORD_MD5 ?? '632080fabdb968f9ac4f31fb55104648',
  passwordPlain: process.env.QA_PASSWORD_PLAIN ?? 'doctor2025',
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

const categories = [
  {
    order: 1,
    key: 'prescription',
    addButton: '+処方',
    panelTestId: 'medOrder-edit-panel',
    inputSelector: '#medOrder-item-name-0',
    endpointPart: '/orca/master/generic-class',
    keyword: 'アム',
  },
  {
    order: 2,
    key: 'injection',
    addButton: '+注射',
    panelTestId: 'injectionOrder-edit-panel',
    inputSelector: '#injectionOrder-item-name-0',
    endpointPart: '/orca/master/generic-class',
    keyword: 'アム',
  },
  {
    order: 3,
    key: 'test',
    addButton: '+検査',
    panelTestId: 'testOrder-edit-panel',
    inputSelector: '#testOrder-item-name-0',
    endpointPart: '/orca/master/kensa-sort',
    keyword: '血液',
  },
  {
    order: 4,
    key: 'procedure',
    addButton: '+処置',
    panelTestId: 'treatmentOrder-edit-panel',
    inputSelector: '#treatmentOrder-item-name-0',
    endpointPart: '/orca/master/material',
    keyword: 'ガーゼ',
  },
  {
    order: 5,
    key: 'charge',
    addButton: '+算定',
    panelTestId: 'baseChargeOrder-edit-panel',
    inputSelector: '#baseChargeOrder-item-name-0',
    endpointPart: '/orca/master/etensu',
    keyword: '腹',
  },
];

const parseTraceId = (body) => {
  if (!body || typeof body !== 'string') return null;
  try {
    const json = JSON.parse(body);
    return json?.traceId ?? json?.payload?.traceId ?? null;
  } catch {
    const m = body.match(/"traceId"\s*:\s*"([^"]+)"/);
    return m ? m[1] : null;
  }
};

const nowIso = () => new Date().toISOString();

const resolveVisibleButton = async (page, label) => {
  const candidates = page.locator('button').filter({ hasText: label });
  const count = await candidates.count();
  for (let i = 0; i < count; i += 1) {
    const button = candidates.nth(i);
    if (await button.isVisible().catch(() => false)) return button;
  }
  return candidates.first();
};

const ensureOrderSetReady = async (page) => {
  const tab = page.locator('[data-utility-action="order-set"]');
  const plusPrescription = page.locator('button').filter({ hasText: '+処方' }).first();
  for (let i = 0; i < 3; i += 1) {
    await tab.click({ timeout: 10000 }).catch(() => null);
    if (await plusPrescription.isVisible().catch(() => false)) return true;
    await page.waitForTimeout(700);
  }
  return plusPrescription.isVisible().catch(() => false);
};

const writeFailureContext = (records) => {
  const file = path.join(logDir, 'failure-context.json');
  fs.writeFileSync(file, `${JSON.stringify(records, null, 2)}\n`);
  return path.relative(artifactDir, file);
};

const run = async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
  const page = await context.newPage();

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

  const consoleLogs = [];
  const masterResponses = [];

  page.on('console', (msg) => {
    consoleLogs.push(`[${nowIso()}] [${msg.type()}] ${msg.text()}`);
  });

  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/orca/master/')) return;

    let body = '';
    try {
      body = await response.text();
    } catch {
      body = '';
    }

    masterResponses.push({
      at: nowIso(),
      method: response.request().method(),
      status: response.status(),
      url,
      traceId: parseTraceId(body),
      bodySize: body.length,
    });
  });

  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.locator('.charts-page').waitFor({ timeout: 25000 });

  const fixedOrder = categories.map((c) => c.key);
  const results = [];

  for (const category of categories) {
    const record = {
      order: category.order,
      category: category.key,
      endpointPart: category.endpointPart,
      keyword: category.keyword,
      responseStatus: null,
      totalCount: null,
      traceId: null,
      candidateShown: false,
      selected: false,
      itemFilled: false,
      status: 'fail',
      blockedAt: null,
      error: null,
      screenshot: null,
    };

    try {
      await ensureOrderSetReady(page);

      const addButton = await resolveVisibleButton(page, category.addButton);
      await addButton.waitFor({ state: 'visible', timeout: 15000 });
      await addButton.click({ timeout: 8000, force: true });

      const panelSelector = `[data-test-id="${category.panelTestId}"]`;
      const panel = page.locator(panelSelector);
      await panel.waitFor({ state: 'visible', timeout: 15000 });

      const itemInput = panel.locator(category.inputSelector);
      await itemInput.waitFor({ state: 'visible', timeout: 15000 });

      const responsePromise = page
        .waitForResponse(
          (res) => res.url().includes(category.endpointPart) && res.request().method() === 'GET',
          { timeout: 20000 },
        )
        .catch(() => null);

      await itemInput.fill(category.keyword);
      const response = await responsePromise;

      if (response) {
        record.responseStatus = response.status();
        let body = '';
        try {
          body = await response.text();
        } catch {
          body = '';
        }
        record.traceId = parseTraceId(body);
        try {
          const parsed = JSON.parse(body);
          record.totalCount =
            parsed?.totalCount ??
            parsed?.payload?.totalCount ??
            (Array.isArray(parsed?.items) ? parsed.items.length : null);
        } catch {
          record.totalCount = null;
        }
      }

      const firstRow = panel.locator('button.charts-side-panel__search-row').first();
      record.candidateShown = await firstRow.isVisible().catch(() => false);

      if (record.candidateShown) {
        await firstRow.click({ timeout: 5000, force: true });
        record.selected = true;
      }

      const valueAfter = await itemInput.inputValue().catch(() => '');
      record.itemFilled = valueAfter.trim().length > 0;

      record.status =
        record.responseStatus === 200 && record.candidateShown && record.selected && record.itemFilled ? 'pass' : 'fail';

      if (record.status !== 'pass') {
        record.blockedAt = record.responseStatus === 200 ? 'candidate_or_reflection' : 'master_api';
      }

      const shot = path.join(screenshotDir, `${String(category.order).padStart(2, '0')}-${category.key}.png`);
      await page.screenshot({ path: shot, fullPage: true });
      record.screenshot = path.relative(artifactDir, shot);

      await panel.getByRole('button', { name: '閉じる' }).click({ timeout: 5000, force: true }).catch(async () => {
        await page.keyboard.press('Escape').catch(() => null);
      });
      await page.waitForTimeout(200);
    } catch (error) {
      record.error = String(error);
      record.blockedAt = record.blockedAt ?? 'ui_operation';
      const shot = path.join(screenshotDir, `${String(category.order).padStart(2, '0')}-${category.key}-error.png`);
      await page.screenshot({ path: shot, fullPage: true }).catch(() => null);
      record.screenshot = path.relative(artifactDir, shot);
    }

    results.push(record);
  }

  const failureDetails = results.filter((r) => r.status !== 'pass');
  const summary = {
    runId,
    executedAt: nowIso(),
    baseURL,
    chartsUrl: `${baseURL}/f/${facilityId}/charts?patientId=${patientId}`,
    fixedOrder,
    skipCount: 0,
    skipRule: 'SKIP must be 0. Any skip means incomplete run.',
    passCount: results.filter((r) => r.status === 'pass').length,
    failCount: failureDetails.length,
    overall: failureDetails.length === 0 ? 'pass' : 'fail',
    categories: results,
  };

  const summaryJson = path.join(artifactDir, 'summary.json');
  fs.writeFileSync(summaryJson, `${JSON.stringify(summary, null, 2)}\n`);

  const masterLog = path.join(logDir, 'master-responses.ndjson');
  fs.writeFileSync(masterLog, masterResponses.map((r) => JSON.stringify(r)).join('\n') + (masterResponses.length ? '\n' : ''));

  const consoleLog = path.join(logDir, 'console.log');
  fs.writeFileSync(consoleLog, `${consoleLogs.join('\n')}\n`);

  const failureContextPath = writeFailureContext(failureDetails);

  const mdLines = [
    '# major-category rerun checklist result',
    '',
    `- RUN_ID: ${runId}`,
    `- Base URL: ${baseURL}`,
    `- executedAt: ${summary.executedAt}`,
    `- fixedOrder: ${fixedOrder.join(' -> ')}`,
    '- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).',
    `- overall: ${summary.overall}`,
    `- passCount/failCount: ${summary.passCount}/${summary.failCount}`,
    `- failure context: ${failureContextPath}`,
    '',
    '|order|category|HTTP|candidate|selected|reflected|status|traceId|blockedAt|screenshot|',
    '|---:|---|---:|---:|---:|---:|---|---|---|---|',
    ...results.map(
      (r) =>
        `|${r.order}|${r.category}|${r.responseStatus ?? '-'}|${r.candidateShown ? 'yes' : 'no'}|${r.selected ? 'yes' : 'no'}|${
          r.itemFilled ? 'yes' : 'no'
        }|${r.status}|${r.traceId ?? '-'}|${r.blockedAt ?? '-'}|${r.screenshot ?? '-'}|`,
    ),
    '',
    '## fixed failure logs',
    `- ${path.relative(artifactDir, masterLog)}`,
    `- ${path.relative(artifactDir, consoleLog)}`,
    `- ${failureContextPath}`,
  ];

  fs.writeFileSync(path.join(artifactDir, 'summary.md'), `${mdLines.join('\n')}\n`);

  await context.close();
  await browser.close();

  if (summary.overall !== 'pass') {
    process.exitCode = 1;
  }
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
