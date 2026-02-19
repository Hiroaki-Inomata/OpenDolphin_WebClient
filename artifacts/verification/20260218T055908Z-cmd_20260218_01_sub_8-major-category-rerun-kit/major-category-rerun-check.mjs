import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const baseURL = process.env.QA_BASE_URL ?? 'http://localhost:5173';
const runId = process.env.RUN_ID ?? new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
const artifactDir = process.env.ARTIFACT_DIR ?? path.join(process.cwd(), 'artifacts', 'verification', `${runId}-major-category-rerun`);
const screenshotDir = path.join(artifactDir, 'screenshots');
const logDir = path.join(artifactDir, 'logs');
const progressLog = path.join(logDir, 'progress.log');

fs.mkdirSync(screenshotDir, { recursive: true });
fs.mkdirSync(logDir, { recursive: true });
fs.writeFileSync(progressLog, '');

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
    selectValue: 'prescription',
    inputLabel: '処方入力',
    endpointPart: '/orca/master/generic-class',
    keyword: 'アム',
  },
  {
    order: 2,
    key: 'injection',
    selectValue: 'injection',
    inputLabel: '注射入力',
    endpointPart: '/orca/master/generic-class',
    keyword: 'アム',
  },
  {
    order: 3,
    key: 'test',
    selectValue: 'test',
    inputLabel: '検査入力',
    endpointPart: '/orca/master/kensa-sort',
    keyword: '血液',
  },
  {
    order: 4,
    key: 'procedure',
    selectValue: 'treatment',
    inputLabel: '処置入力',
    endpointPart: '/orca/master/material',
    keyword: 'ガーゼ',
  },
  {
    order: 5,
    key: 'charge',
    selectValue: 'charge',
    inputLabel: '基本料入力',
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

const readResponseBody = async (response) => {
  try {
    return await response.text();
  } catch {
    return '';
  }
};

const nowIso = () => new Date().toISOString();
const logProgress = (message) => {
  fs.appendFileSync(progressLog, `[${nowIso()}] ${message}\n`);
};
const withTimeout = async (promise, timeoutMs, fallback) => {
  let timer;
  try {
    return await Promise.race([
      promise,
      new Promise((resolve) => {
        timer = setTimeout(() => resolve(fallback), timeoutMs);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
};

const classifyFailureReason = (failureText) => {
  const text = String(failureText ?? '').toLowerCase();
  if (!text) return null;
  if (text.includes('not enabled') || text.includes('disabled')) return 'ui_disabled';
  if (text.includes('timed out') || text.includes('timeout')) return 'timeout';
  if (text.includes('net::err') || text.includes('connection')) return 'network_error';
  if (text.includes('aborted') || text.includes('cancelled')) return 'aborted';
  return 'request_failed';
};

const toRetryDecision = ({ responseStatus, failureReason }) => {
  if (failureReason === 'timeout' || failureReason === 'network_error') return 'retry_recommended';
  if (typeof responseStatus === 'number' && responseStatus >= 500) return 'retry_recommended';
  if (typeof responseStatus === 'number' && responseStatus === 429) return 'retry_recommended';
  if (responseStatus == null) return 'retry_recommended';
  return 'retry_not_recommended';
};

const inferCause = ({ responseStatus, candidateShown, selected, itemFilled, failureReason }) => {
  if (typeof failureReason === 'string' && failureReason.startsWith('ui_')) return 'ui';
  if (responseStatus !== 200 || responseStatus == null) return 'environment';
  if (!candidateShown || !selected || !itemFilled) return 'ui';
  return 'none';
};

const clickWithFallback = async (locator) => {
  try {
    await locator.click({ timeout: 8000, force: true });
  } catch {
    await locator.evaluate((el) => el.click());
  }
};

const waitUntilEditable = async (page) => {
  const canEditProbe = page.locator('button').filter({ hasText: '+処方' }).first();
  const retryButton = page.getByRole('button', { name: '再取得' }).first();
  for (let i = 0; i < 15; i += 1) {
    const visible = await withTimeout(canEditProbe.isVisible().catch(() => false), 1200, false);
    const disabled = await withTimeout(canEditProbe.isDisabled().catch(() => true), 1200, true);
    if (visible && !disabled) return true;
    const retryVisible = await withTimeout(retryButton.isVisible().catch(() => false), 1200, false);
    if (retryVisible) {
      await clickWithFallback(retryButton).catch(() => null);
    }
    await page.waitForTimeout(700);
  }
  return false;
};

const writeFailureContext = (records) => {
  const file = path.join(logDir, 'failure-context.json');
  fs.writeFileSync(file, `${JSON.stringify(records, null, 2)}\n`);
  return path.relative(artifactDir, file);
};

const run = async () => {
  logProgress('run:start');
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
  const page = await context.newPage();
  logProgress('browser:launched');

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
  const masterDiagnostics = [];
  const requestMetaMap = new WeakMap();

  page.on('console', (msg) => {
    consoleLogs.push(`[${nowIso()}] [${msg.type()}] ${msg.text()}`);
  });

  page.on('request', (request) => {
    const url = request.url();
    if (!url.includes('/orca/master/')) return;
    const meta = {
      startedAt: nowIso(),
      startedAtMs: Date.now(),
      method: request.method(),
      url,
    };
    requestMetaMap.set(request, meta);
  });

  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/orca/master/')) return;

    const body = await readResponseBody(response);
    const request = response.request();
    const requestMeta = requestMetaMap.get(request);
    const endedAtMs = Date.now();
    const startedAtMs = requestMeta?.startedAtMs ?? endedAtMs;
    const durationMs = Math.max(0, endedAtMs - startedAtMs);
    const responseStatus = response.status();
    const retryDecision = toRetryDecision({ responseStatus, failureReason: null });

    masterResponses.push({
      at: nowIso(),
      atMs: Date.now(),
      method: request.method(),
      status: responseStatus,
      url,
      traceId: parseTraceId(body),
      bodySize: body.length,
    });

    masterDiagnostics.push({
      startedAt: requestMeta?.startedAt ?? nowIso(),
      endedAt: nowIso(),
      durationMs,
      method: request.method(),
      url,
      status: responseStatus,
      failureReason: null,
      retryDecision,
      traceId: parseTraceId(body),
    });
  });

  page.on('requestfailed', (request) => {
    const url = request.url();
    if (!url.includes('/orca/master/')) return;
    const requestMeta = requestMetaMap.get(request);
    const endedAtMs = Date.now();
    const startedAtMs = requestMeta?.startedAtMs ?? endedAtMs;
    const durationMs = Math.max(0, endedAtMs - startedAtMs);
    const failureReason = classifyFailureReason(request.failure()?.errorText);
    const retryDecision = toRetryDecision({ responseStatus: null, failureReason });

    masterDiagnostics.push({
      startedAt: requestMeta?.startedAt ?? nowIso(),
      endedAt: nowIso(),
      durationMs,
      method: request.method(),
      url,
      status: null,
      failureReason,
      retryDecision,
      traceId: null,
    });
  });

  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, {
    waitUntil: 'domcontentloaded',
  });
  logProgress('charts:goto_done');
  await page.locator('.charts-page').waitFor({ timeout: 25000 });
  logProgress('charts:page_ready');
  const searchInput = page.getByRole('searchbox', { name: 'オーダー検索' });
  const categorySelect = page.getByRole('combobox', { name: 'カテゴリ選択' });
  await searchInput.waitFor({ state: 'visible', timeout: 15000 });
  await categorySelect.waitFor({ state: 'visible', timeout: 15000 });

  const fixedOrder = categories.map((c) => c.key);
  const results = [];

  for (const category of categories) {
    logProgress(`category:${category.key}:start`);
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
      requestStartedAt: null,
      requestEndedAt: null,
      requestDurationMs: null,
      failureReason: null,
      retryDecision: 'retry_not_recommended',
      suspectedCause: 'none',
      status: 'fail',
      blockedAt: null,
      error: null,
      screenshot: null,
    };

    try {
      const actionStartedAt = Date.now();
      await waitUntilEditable(page);
      logProgress(`category:${category.key}:editable_ready`);
      await categorySelect.selectOption(category.selectValue);

      const requestPromise = page
        .waitForRequest(
          (req) => req.url().includes(category.endpointPart) && req.method() === 'GET',
          { timeout: 20000 },
        )
        .catch(() => null);
      const responsePromise = page
        .waitForResponse(
          (res) => res.url().includes(category.endpointPart) && res.request().method() === 'GET',
          { timeout: 20000 },
        )
        .catch(() => null);

      await searchInput.fill('');
      await page.waitForTimeout(80);
      await searchInput.fill(category.keyword);
      record.requestStartedAt = nowIso();
      logProgress(`category:${category.key}:keyword_input`);
      const request = await requestPromise;
      const response = (await responsePromise) ?? (request ? await request.response().catch(() => null) : null);
      record.requestEndedAt = nowIso();
      logProgress(`category:${category.key}:request_cycle_done`);
      if (record.requestStartedAt && record.requestEndedAt) {
        const startMs = Date.parse(record.requestStartedAt);
        const endMs = Date.parse(record.requestEndedAt);
        record.requestDurationMs = Number.isFinite(startMs) && Number.isFinite(endMs) ? Math.max(0, endMs - startMs) : null;
      }

      if (response && response.url().includes(category.endpointPart)) {
        record.responseStatus = response.status();
        const body = await readResponseBody(response);
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

      if (record.responseStatus == null) {
        const fallback = [...masterResponses]
          .reverse()
          .find((entry) => entry.url.includes(category.endpointPart) && entry.atMs >= actionStartedAt);
        if (fallback) {
          record.responseStatus = fallback.status;
          record.traceId = fallback.traceId ?? null;
        }
      }
      if (record.responseStatus == null) {
        record.failureReason = 'timeout';
      } else if (record.responseStatus >= 500) {
        record.failureReason = 'http_5xx';
      } else if (record.responseStatus === 429) {
        record.failureReason = 'rate_limited';
      }

      const firstRow = page.locator('[aria-label="検索候補"] button').first();
      record.candidateShown = await firstRow.isVisible().catch(() => false);

      if (record.candidateShown) {
        await clickWithFallback(firstRow);
        record.selected = true;
        const editPanel = page.locator(`[aria-label="${category.inputLabel}"]`).first();
        await editPanel.waitFor({ state: 'visible', timeout: 15000 });
        const itemInput = editPanel.locator('input[id$="-item-name-0"]').first();
        await itemInput.waitFor({ state: 'visible', timeout: 15000 });
        const valueAfter = await itemInput.inputValue().catch(() => '');
        record.itemFilled = valueAfter.trim().length > 0;
      }

      record.status =
        record.responseStatus === 200 && record.candidateShown && record.selected && record.itemFilled ? 'pass' : 'fail';
      record.suspectedCause = inferCause(record);
      record.retryDecision = toRetryDecision({ responseStatus: record.responseStatus, failureReason: record.failureReason });

      if (record.status !== 'pass') {
        record.blockedAt = record.responseStatus === 200 ? 'candidate_or_reflection' : 'master_api';
      }

      const shot = path.join(screenshotDir, `${String(category.order).padStart(2, '0')}-${category.key}.png`);
      await page.screenshot({ path: shot, fullPage: true });
      record.screenshot = path.relative(artifactDir, shot);
      logProgress(`category:${category.key}:screenshot_done`);

      await page.getByRole('button', { name: '一覧へ' }).click({ timeout: 5000, force: true }).catch(async () => {
        await page.keyboard.press('Escape').catch(() => null);
      });
      await page.waitForTimeout(200);
      logProgress(`category:${category.key}:end status=${record.status}`);
    } catch (error) {
      record.error = String(error);
      record.blockedAt = record.blockedAt ?? 'ui_operation';
      record.failureReason = record.failureReason ?? classifyFailureReason(record.error) ?? 'ui_operation_failed';
      record.retryDecision = toRetryDecision({ responseStatus: record.responseStatus, failureReason: record.failureReason });
      record.suspectedCause = inferCause(record);
      const shot = path.join(screenshotDir, `${String(category.order).padStart(2, '0')}-${category.key}-error.png`);
      await page.screenshot({ path: shot, fullPage: true }).catch(() => null);
      record.screenshot = path.relative(artifactDir, shot);
      logProgress(`category:${category.key}:error ${record.error}`);
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

  const endpointDiagnostics = categories.reduce((acc, category) => {
    const traces = masterDiagnostics.filter((entry) => entry.url.includes(category.endpointPart));
    const failures = traces.filter((entry) => entry.failureReason != null || (typeof entry.status === 'number' && entry.status >= 400));
    const retryRecommended = traces.filter((entry) => entry.retryDecision === 'retry_recommended').length;
    const durations = traces.map((entry) => entry.durationMs).filter((v) => Number.isFinite(v));
    const slowThresholdMs = 3000;
    const slowCount = durations.filter((ms) => ms >= slowThresholdMs).length;

    acc[category.endpointPart] = {
      requestCount: traces.length,
      failureCount: failures.length,
      slowThresholdMs,
      slowCount,
      retryRecommendedCount: retryRecommended,
      firstStartedAt: traces[0]?.startedAt ?? null,
      lastEndedAt: traces[traces.length - 1]?.endedAt ?? null,
      lastFailureReason: failures[failures.length - 1]?.failureReason ?? null,
    };
    return acc;
  }, {});
  summary.endpointDiagnostics = endpointDiagnostics;

  const summaryJson = path.join(artifactDir, 'summary.json');
  fs.writeFileSync(summaryJson, `${JSON.stringify(summary, null, 2)}\n`);

  const masterLog = path.join(logDir, 'master-responses.ndjson');
  fs.writeFileSync(masterLog, masterResponses.map((r) => JSON.stringify(r)).join('\n') + (masterResponses.length ? '\n' : ''));
  const masterDiagnosticsLog = path.join(logDir, 'master-diagnostics.ndjson');
  fs.writeFileSync(
    masterDiagnosticsLog,
    masterDiagnostics.map((r) => JSON.stringify(r)).join('\n') + (masterDiagnostics.length ? '\n' : ''),
  );

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
    '## triage (environment vs ui)',
    '',
    '|category|suspectedCause|failureReason|retryDecision|requestStartedAt|requestEndedAt|durationMs|',
    '|---|---|---|---|---|---|---:|',
    ...results.map(
      (r) =>
        `|${r.category}|${r.suspectedCause}|${r.failureReason ?? '-'}|${r.retryDecision}|${r.requestStartedAt ?? '-'}|${
          r.requestEndedAt ?? '-'
        }|${r.requestDurationMs ?? '-'}|`,
    ),
    '',
    '## fixed failure logs',
    `- ${path.relative(artifactDir, masterLog)}`,
    `- ${path.relative(artifactDir, masterDiagnosticsLog)}`,
    `- ${path.relative(artifactDir, consoleLog)}`,
    `- ${failureContextPath}`,
  ];

  fs.writeFileSync(path.join(artifactDir, 'summary.md'), `${mdLines.join('\n')}\n`);
  logProgress('run:summary_written');

  await context.close();
  await browser.close();
  logProgress('run:closed');

  if (summary.overall !== 'pass') {
    process.exitCode = 1;
  }
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
