import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const baseURL = process.env.QA_BASE_URL ?? 'http://localhost:5173';
const runId = process.env.RUN_ID;
const artifactDir = process.env.ARTIFACT_DIR;
const screenshotDir = path.join(artifactDir, 'screenshots');
const logsDir = path.join(artifactDir, 'logs');

if (!runId || !artifactDir) throw new Error('RUN_ID and ARTIFACT_DIR are required');
fs.mkdirSync(screenshotDir, { recursive: true });
fs.mkdirSync(logsDir, { recursive: true });

const facilityId = '1.3.6.1.4.1.9414.72.103';
const patientId = process.env.QA_PATIENT_ID ?? '01415';

const auth = { facilityId, userId: 'doctor1', passwordMd5: '632080fabdb968f9ac4f31fb55104648', passwordPlain: 'doctor2025', clientUuid: `qa-${runId}` };
const session = { facilityId, userId: auth.userId, displayName: `QA ${runId}`, clientUuid: auth.clientUuid, runId, role: 'admin', roles: ['admin'] };

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const parseTraceId = (body) => { try { const j = JSON.parse(body); return j?.traceId ?? j?.payload?.traceId ?? j?.correlationId ?? null; } catch { const m = (body || '').match(/"traceId"\s*:\s*"([^"]+)"/); return m ? m[1] : null; } };
const parseTotalCount = (body) => { try { const j = JSON.parse(body); if (Array.isArray(j)) return j.length; if (typeof j?.totalCount === 'number') return j.totalCount; if (Array.isArray(j?.items)) return j.items.length; return null; } catch { return null; } };
const writeShot = async (page, file) => { const out = path.join(screenshotDir, file); await page.screenshot({ path: out, fullPage: true }); return path.relative(artifactDir, out); };

const CATEGORIES = [
  { order: 1, category: 'prescription', quickAddKey: 'prescription', entity: 'medOrder', endpointPart: '/orca/master/generic-class', keyword: '1' },
  { order: 2, category: 'injection', quickAddKey: 'injection', entity: 'injectionOrder', endpointPart: '/orca/master/generic-class', keyword: '1' },
  { order: 3, category: 'test', quickAddKey: 'test', entity: 'testOrder', endpointPart: '/orca/master/kensa-sort', keyword: '1' },
  { order: 4, category: 'procedure', quickAddKey: 'treatment', entity: 'treatmentOrder', endpointPart: '/orca/master/material', keyword: '1' },
  { order: 5, category: 'charge', quickAddKey: 'charge', entity: 'baseChargeOrder', endpointPart: '/orca/master/etensu', keyword: '1' },
];

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ baseURL, ignoreHTTPSErrors: true });
const consoleLogs = [];
const masterResponses = [];

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
page.on('console', (msg) => consoleLogs.push(`[${new Date().toISOString()}] [${msg.type()}] ${msg.text()}`));
page.on('requestfailed', (request) => { if (request.url().includes('/orca/master/')) masterResponses.push({ type: 'requestfailed', url: request.url(), method: request.method(), error: request.failure()?.errorText ?? null, at: new Date().toISOString() }); });
page.on('response', async (response) => {
  if (!response.url().includes('/orca/master/')) return;
  let body = '';
  try { body = await response.text(); } catch {}
  masterResponses.push({ type: 'response', url: response.url(), method: response.request().method(), status: response.status(), traceId: parseTraceId(body), totalCount: parseTotalCount(body), at: new Date().toISOString() });
});

const results = [];
for (const spec of CATEGORIES) {
  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, { waitUntil: 'domcontentloaded' });
  await page.locator('.charts-page').waitFor({ timeout: 20000 });
  await page.locator('[data-utility-action="order-set"]').click({ timeout: 10000 }).catch(() => null);

  const panelSelector = `[data-test-id="${spec.entity}-edit-panel"]`;
  const inputSelector = `${panelSelector} #${spec.entity}-item-name-0`;
  const out = { order: spec.order, category: spec.category, endpointPart: spec.endpointPart, keyword: spec.keyword, requestSeen: false, responseStatus: null, totalCount: null, traceId: null, candidateShown: false, selected: false, itemFilled: false, inputBefore: null, inputAfter: null, debounceMs: 320, queryEnabled: false, status: 'fail', blockedAt: null, error: null, screenshot: null };

  try {
    const quickAdd = page.locator(`[data-test-id="order-dock-quick-add-${spec.quickAddKey}"]`);
    await quickAdd.waitFor({ state: 'visible', timeout: 12000 });
    await page.evaluate((key) => {
      const el = document.querySelector(`[data-test-id="order-dock-quick-add-${key}"]`);
      if (!(el instanceof HTMLElement)) throw new Error(`quick-add not found: ${key}`);
      el.click();
    }, spec.quickAddKey);

    const panel = page.locator(panelSelector);
    await panel.waitFor({ state: 'visible', timeout: 15000 });
    const input = page.locator(inputSelector);
    await input.waitFor({ state: 'visible', timeout: 15000 });

    out.inputBefore = await input.inputValue().catch(() => null);
    const reqPromise = page.waitForRequest((req) => req.url().includes(spec.endpointPart) && req.method() === 'GET', { timeout: 15000 }).catch(() => null);
    const resPromise = page.waitForResponse((res) => res.url().includes(spec.endpointPart) && res.request().method() === 'GET', { timeout: 15000 }).catch(() => null);

    await input.fill(spec.keyword);
    out.queryEnabled = spec.keyword.trim().length > 0;
    await sleep(out.debounceMs + 80);
    out.inputAfter = await input.inputValue().catch(() => null);

    const req = await reqPromise;
    const res = await resPromise;
    out.requestSeen = Boolean(req);
    if (res) {
      out.responseStatus = res.status();
      let body = '';
      try { body = await res.text(); } catch {}
      out.traceId = parseTraceId(body);
      out.totalCount = parseTotalCount(body);
    }

    const candidate = panel.locator('button.charts-side-panel__search-row').first();
    out.candidateShown = await candidate.isVisible().catch(() => false);
    if (out.candidateShown) {
      await candidate.click({ timeout: 5000, force: true });
      out.selected = true;
    }
    out.itemFilled = Boolean((await input.inputValue().catch(() => '')).trim());

    if (out.responseStatus === 200 && out.requestSeen && out.totalCount !== null && out.totalCount > 0 && out.selected && out.itemFilled) out.status = 'pass';
    else { out.status = 'fail'; out.blockedAt = out.requestSeen ? 'candidate_selection' : 'master_api'; }

    out.screenshot = await writeShot(page, `${String(spec.order).padStart(2, '0')}-${spec.category}.png`);
  } catch (error) {
    out.error = String(error);
    out.status = 'fail';
    out.blockedAt = out.blockedAt ?? 'ui_operation';
    out.screenshot = await writeShot(page, `${String(spec.order).padStart(2, '0')}-${spec.category}-error.png`).catch(() => null);
  }

  results.push(out);
}

const passCount = results.filter((r) => r.status === 'pass').length;
const failCount = results.filter((r) => r.status === 'fail').length;
const summary = { runId, executedAt: new Date().toISOString(), baseURL, chartsUrl: page.url(), fixedOrder: CATEGORIES.map((c) => c.category), skipCount: 0, skipRule: 'SKIP must be 0. Any skip means incomplete run.', passCount, failCount, overall: failCount === 0 ? 'pass' : 'fail', categories: results };

fs.writeFileSync(path.join(artifactDir, 'summary.json'), JSON.stringify(summary, null, 2));
fs.writeFileSync(path.join(logsDir, 'failure-context.json'), JSON.stringify(results, null, 2));
fs.writeFileSync(path.join(logsDir, 'master-responses.ndjson'), masterResponses.map((e) => JSON.stringify(e)).join('\n') + (masterResponses.length ? '\n' : ''));
fs.writeFileSync(path.join(logsDir, 'console.log'), consoleLogs.join('\n') + (consoleLogs.length ? '\n' : ''));

const md = [
  '# major-category rerun checklist result', '',
  `- RUN_ID: ${runId}`,
  `- Base URL: ${baseURL}`,
  `- executedAt: ${summary.executedAt}`,
  `- fixedOrder: ${summary.fixedOrder.join(' -> ')}`,
  '- SKIP policy: SKIP must be 0. Any SKIP = FAIL (incomplete).',
  `- overall: ${summary.overall}`,
  `- passCount/failCount: ${passCount}/${failCount}`,
  '- failure context: logs/failure-context.json', '',
  '|order|category|HTTP|totalCount|request|queryEnabled|candidate|selected|reflected|status|traceId|blockedAt|screenshot|',
  '|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|',
  ...results.map((r) => `|${r.order}|${r.category}|${r.responseStatus ?? '-'}|${r.totalCount ?? '-'}|${r.requestSeen ? 'yes' : 'no'}|${r.queryEnabled ? 'yes' : 'no'}|${r.candidateShown ? 'yes' : 'no'}|${r.selected ? 'yes' : 'no'}|${r.itemFilled ? 'yes' : 'no'}|${r.status}|${r.traceId ?? '-'}|${r.blockedAt ?? '-'}|${r.screenshot ?? '-'}|`),
  '', '## event-chain diagnosis',
  '- single root cause (sub_26): verification scenario used keyword/observation conditions that did not align with live snapshot data, so non-selection was misread as API non-fire.',
  '- minimal fix (sub_28): use order-dock quick-add ids + request/response/requestfailed hooks + per-category fresh load to remove false negatives.',
  '', '## logs', '- logs/master-responses.ndjson', '- logs/console.log', '- logs/failure-context.json',
].join('\n');
fs.writeFileSync(path.join(artifactDir, 'summary.md'), md);

await context.close();
await browser.close();
console.log(JSON.stringify({ runId, artifactDir, passCount, failCount, overall: summary.overall }, null, 2));
