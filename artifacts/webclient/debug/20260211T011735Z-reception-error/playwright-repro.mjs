import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const EVID_DIR = process.env.EVID_DIR ?? path.resolve(process.cwd());
const FACILITY_ID = process.env.FACILITY_ID ?? '1.3.6.1.4.1.9414.10.1';
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const RUN_ID = process.env.RUN_ID ?? '20260211T011735Z';
const PATIENT_ID = process.env.PATIENT_ID ?? '00001';
const VISIT_DATE = process.env.VISIT_DATE ?? '2000-01-01';

fs.mkdirSync(EVID_DIR, { recursive: true });

const outConsole = [];
const outRequests = [];
const outResponses = [];

const main = async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    recordHar: { path: path.join(EVID_DIR, 'playwright.har'), content: 'embed' },
    viewport: { width: 1366, height: 768 },
  });
  const page = await context.newPage();

  page.on('console', (msg) => {
    outConsole.push(`[console:${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', (err) => {
    outConsole.push(`[pageerror] ${String(err)}`);
  });
  page.on('requestfailed', (req) => {
    outRequests.push(
      JSON.stringify(
        { url: req.url(), method: req.method(), failure: req.failure()?.errorText ?? 'unknown' },
        null,
        2,
      ),
    );
  });
  page.on('response', (res) => {
    const status = res.status();
    if (status >= 400) {
      outResponses.push(JSON.stringify({ url: res.url(), status }, null, 2));
    }
  });

  const receptionUrl = `${BASE_URL}/f/${encodeURIComponent(FACILITY_ID)}/reception`;
  const chartsUrl = `${BASE_URL}/f/${encodeURIComponent(FACILITY_ID)}/charts?patientId=${encodeURIComponent(
    PATIENT_ID,
  )}&visitDate=${encodeURIComponent(VISIT_DATE)}&runId=${encodeURIComponent(RUN_ID)}`;

  await page.goto(receptionUrl, { waitUntil: 'domcontentloaded', timeout: 30_000 });
  await page.waitForTimeout(3_000);
  await page.screenshot({ path: path.join(EVID_DIR, 'playwright.reception.png'), fullPage: true });

  await page.goto(chartsUrl, { waitUntil: 'domcontentloaded', timeout: 30_000 });
  await page.waitForTimeout(5_000);
  await page.screenshot({ path: path.join(EVID_DIR, 'playwright.charts.png'), fullPage: true });

  await context.close();
  await browser.close();
};

await main().catch((err) => {
  outConsole.push(`[fatal] ${String(err)}`);
  process.exitCode = 1;
});

fs.writeFileSync(path.join(EVID_DIR, 'playwright.console.txt'), outConsole.join('\n') + '\n', 'utf8');
fs.writeFileSync(path.join(EVID_DIR, 'playwright.requestfailed.jsonl'), outRequests.join('\n') + '\n', 'utf8');
fs.writeFileSync(path.join(EVID_DIR, 'playwright.http-errors.jsonl'), outResponses.join('\n') + '\n', 'utf8');

