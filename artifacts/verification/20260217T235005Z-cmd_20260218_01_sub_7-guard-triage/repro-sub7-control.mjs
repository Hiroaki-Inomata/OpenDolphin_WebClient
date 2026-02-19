import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const baseURL = 'http://localhost:5173';
const runId = '20260217T235005Z-cmd_20260218_01_sub_7-guard-triage';
const artifactDir = process.env.ARTIFACT_DIR;
const facilityId = '1.3.6.1.4.1.9414.72.103';
const patientId = '01415';

const auth = {
  facilityId,
  userId: 'doctor1',
  passwordMd5: '632080fabdb968f9ac4f31fb55104648',
  passwordPlain: 'doctor2025',
  clientUuid: `qa-${runId}-control`,
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

const parseTraceId = (body) => {
  if (!body || typeof body !== 'string') return null;
  try { return JSON.parse(body)?.traceId ?? null; } catch { return null; }
};

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
  const masterResponses = [];
  page.on('response', async (response) => {
    const url = response.url();
    if (!url.includes('/orca/master/')) return;
    let body = '';
    try { body = await response.text(); } catch {}
    masterResponses.push({ url, status: response.status(), traceId: parseTraceId(body) });
  });

  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, { waitUntil: 'domcontentloaded' });
  await page.locator('.charts-page').waitFor({ timeout: 25000 });
  await page.waitForTimeout(2500);

  const add = page.locator('button[data-test-id="order-dock-quick-add-prescription"]');
  await add.click({ timeout: 5000 });
  const input = page.locator('#medOrder-item-name-0');
  await input.waitFor({ state: 'visible', timeout: 10000 });
  await input.fill('アム');
  await page.waitForTimeout(3000);

  const status = await page.evaluate(() => {
    const input = document.querySelector('#medOrder-item-name-0');
    return {
      missingMaster: document.querySelector('.charts-patient-summary')?.getAttribute('data-missing-master') ?? null,
      inputValue: input instanceof HTMLInputElement ? input.value : null,
    };
  });

  const result = { status, masterResponses, masterResponseCount: masterResponses.length };
  fs.writeFileSync(path.join(artifactDir, 'control.json'), `${JSON.stringify(result, null, 2)}\n`);
  await browser.close();
};

run().catch((e) => { console.error(e); process.exit(1); });
