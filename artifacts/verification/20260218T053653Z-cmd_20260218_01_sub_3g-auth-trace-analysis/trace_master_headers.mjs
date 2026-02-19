import { chromium } from 'playwright';

const baseURL = process.env.QA_BASE_URL ?? 'http://localhost:5173';
const facilityId = '1.3.6.1.4.1.9414.72.103';
const runId = `gunshi-${Date.now()}`;
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
let captured = false;
page.on('request', (req) => {
  if (captured) return;
  const url = req.url();
  if (!url.includes('/orca/master/')) return;
  captured = true;
  const h = req.headers();
  const out = {
    url,
    method: req.method(),
    headers: {
      authorization: h['authorization'] ?? null,
      userName: h['username'] ?? h['userName'] ?? null,
      password: h['password'] ?? null,
      xFacilityId: h['x-facility-id'] ?? null,
      xTraceId: h['x-trace-id'] ?? null,
      xRunId: h['x-run-id'] ?? null,
    },
  };
  console.log(JSON.stringify(out, null, 2));
});

await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=01415`, { waitUntil: 'domcontentloaded' });
await page.locator('.charts-page').waitFor({ timeout: 20000 });
await page.locator('[data-utility-action="order-set"]').click({ timeout: 10000 });
const btn = page.getByRole('button', { name: '+処方' }).first();
await btn.waitFor({ state: 'visible', timeout: 15000 });
await btn.click({ timeout: 8000 });
const panel = page.locator('[data-test-id="medOrder-edit-panel"]');
await panel.waitFor({ state: 'visible', timeout: 15000 });
const input = panel.locator('#medOrder-item-name-0');
await input.waitFor({ state: 'visible', timeout: 15000 });
await input.fill('アム');
await page.waitForTimeout(2500);

await context.close();
await browser.close();
