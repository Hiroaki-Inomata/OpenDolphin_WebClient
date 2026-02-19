import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const baseURL = process.env.QA_BASE_URL ?? 'http://localhost:5173';
const runId = process.env.RUN_ID;
const artifactDir = process.env.ARTIFACT_DIR;
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

const sanitize = (s) => String(s).replace(/[^a-zA-Z0-9_-]/g, '_');

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
    try {
      body = await response.text();
    } catch {}
    masterResponses.push({
      url,
      status: response.status(),
      method: response.request().method(),
      traceId: parseTraceId(body),
      at: new Date().toISOString(),
    });
  });

  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, {
    waitUntil: 'domcontentloaded',
  });
  await page.locator('.charts-page').waitFor({ timeout: 25000 });
  await page.waitForTimeout(2500);

  const summary = await page.evaluate(() => {
    const quickButtons = Array.from(document.querySelectorAll('button[data-test-id^="order-dock-quick-add-"]'));
    const quick = quickButtons.map((btn) => {
      const el = btn;
      return {
        testId: el.getAttribute('data-test-id'),
        text: (el.textContent || '').trim(),
        disabled: el.hasAttribute('disabled'),
        title: el.getAttribute('title'),
        visible: el instanceof HTMLElement ? el.offsetParent !== null : false,
      };
    });

    const charts = document.querySelector('.charts-page');
    const stateLabel = Array.from(document.querySelectorAll('*'))
      .find((el) => (el.textContent || '').includes('データ状態'))
      ?.textContent?.replace(/\s+/g, ' ')
      .slice(0, 220);

    const probes = {
      runId: charts?.getAttribute('data-run-id') ?? null,
      traceId: charts?.getAttribute('data-trace-id') ?? null,
      patientSummaryMissingMasterAttr:
        document.querySelector('.charts-patient-summary')?.getAttribute('data-missing-master') ?? null,
      patientSummaryTransitionAttr:
        document.querySelector('.charts-patient-summary')?.getAttribute('data-source-transition') ?? null,
      orderDockExists: Boolean(document.querySelector('.order-dock')),
      orderDockHeader: document.querySelector('.order-dock__header strong')?.textContent ?? null,
      orderPaneAria: document.querySelector('#charts-order-pane')?.getAttribute('aria-label') ?? null,
      stateLabel,
      quickButtons: quick,
    };
    return probes;
  });

  let clickAttempt = { clicked: false, blocked: null };
  const plusPrescription = page.locator('button[data-test-id="order-dock-quick-add-prescription"]');
  if (await plusPrescription.count()) {
    const disabled = await plusPrescription.isDisabled().catch(() => true);
    if (disabled) {
      const title = await plusPrescription.getAttribute('title');
      clickAttempt = { clicked: false, blocked: title ?? 'disabled=true' };
    } else {
      await plusPrescription.click({ timeout: 5000 });
      clickAttempt = { clicked: true, blocked: null };
    }
  } else {
    clickAttempt = { clicked: false, blocked: 'button-not-found' };
  }

  await page.waitForTimeout(2000);
  const shot = path.join(screenshotDir, `${sanitize('sub7_guard_probe')}.png`);
  await page.screenshot({ path: shot, fullPage: true });

  const out = {
    runId,
    executedAt: new Date().toISOString(),
    baseURL,
    chartsUrl: `${baseURL}/f/${facilityId}/charts?patientId=${patientId}`,
    probes: summary,
    clickAttempt,
    masterResponses,
    masterResponseCount: masterResponses.length,
    screenshot: path.relative(artifactDir, shot),
  };

  fs.writeFileSync(path.join(artifactDir, 'summary.json'), `${JSON.stringify(out, null, 2)}\n`);

  const lines = [];
  lines.push('# cmd_20260218_01_sub_7 guard triage');
  lines.push('');
  lines.push(`- RUN_ID: ${out.runId}`);
  lines.push(`- executedAt: ${out.executedAt}`);
  lines.push(`- patientSummary data-missing-master: ${out.probes.patientSummaryMissingMasterAttr}`);
  lines.push(`- patientSummary data-source-transition: ${out.probes.patientSummaryTransitionAttr}`);
  lines.push(`- quickAddCount: ${out.probes.quickButtons.length}`);
  lines.push(`- clickAttempt: ${out.clickAttempt.clicked ? 'clicked' : `blocked (${out.clickAttempt.blocked})`}`);
  lines.push(`- /orca/master responses: ${out.masterResponseCount}`);
  lines.push(`- screenshot: ${out.screenshot}`);
  lines.push('');
  lines.push('## quick-add buttons');
  for (const item of out.probes.quickButtons) {
    lines.push(`- ${item.text} (${item.testId}) visible=${item.visible} disabled=${item.disabled} title=${item.title ?? '-'}`);
  }

  fs.writeFileSync(path.join(artifactDir, 'summary.md'), `${lines.join('\n')}\n`);

  await browser.close();
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
