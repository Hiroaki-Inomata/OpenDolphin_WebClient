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

const writeShot = async (page, name) => {
  const shot = path.join(screenshotDir, `${name}.png`);
  await page.screenshot({ path: shot, fullPage: true });
  return path.relative(artifactDir, shot);
};

const clickCategoryButton = async (page, label) => {
  const btn = page.getByRole('button', { name: label }).first();
  await btn.waitFor({ state: 'visible', timeout: 15000 });
  for (let i = 0; i < 3; i += 1) {
    try {
      await btn.click({ timeout: 8000 });
      return;
    } catch {
      await btn.scrollIntoViewIfNeeded().catch(() => null);
      await btn.click({ timeout: 8000, force: true }).catch(() => null);
    }
  }
  await btn.click({ timeout: 8000, force: true });
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
    masterResponses.push({
      url,
      status: response.status(),
      method: response.request().method(),
      traceId: parseTraceId(body),
    });
  });

  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, { waitUntil: 'domcontentloaded' });
  await page.locator('.charts-page').waitFor({ timeout: 20000 });
  await page.locator('[data-utility-action="order-set"]').click({ timeout: 10000 });

  const categoryResults = [];
  const verify = async ({ name, addButton, entity, endpointPart, keyword }) => {
    const out = {
      name,
      endpointPart,
      keyword,
      panel: entity,
      status: 'unknown',
      selected: false,
      itemFilled: false,
      traceId: null,
      responseStatus: null,
      totalCount: null,
      screenshot: null,
      note: '',
    };

    try {
      await clickCategoryButton(page, addButton);
      const panel = page.locator(`[data-test-id="${entity}-edit-panel"]`);
      await panel.waitFor({ state: 'visible', timeout: 15000 });

      const itemInput = panel.locator(`#${entity}-item-name-0`);
      await itemInput.waitFor({ state: 'visible', timeout: 15000 });

      const responsePromise = page.waitForResponse((res) => {
        const u = res.url();
        return u.includes(endpointPart) && res.request().method() === 'GET';
      }, { timeout: 20000 }).catch(() => null);

      await itemInput.fill(keyword);
      const response = await responsePromise;

      if (response) {
        out.responseStatus = response.status();
        let body = '';
        try { body = await response.text(); } catch {}
        out.traceId = parseTraceId(body);
        try {
          const parsed = JSON.parse(body);
          out.totalCount = parsed?.totalCount ?? parsed?.payload?.totalCount ?? (Array.isArray(parsed?.items) ? parsed.items.length : null);
        } catch {}
      }

      const row = panel.locator('button.charts-side-panel__search-row').first();
      if (await row.isVisible().catch(() => false)) {
        await row.click({ timeout: 5000, force: true });
        out.selected = true;
      }

      const itemVal = await itemInput.inputValue().catch(() => '');
      out.itemFilled = Boolean(itemVal.trim());

      if (out.responseStatus === 200 && out.selected && out.itemFilled) out.status = 'pass';
      else if (out.responseStatus === 200 && out.itemFilled) out.status = 'partial';
      else out.status = 'fail';

      out.screenshot = await writeShot(page, name.replace(/[^a-zA-Z0-9_-]/g, '_'));

      await panel.getByRole('button', { name: '閉じる' }).click({ timeout: 5000, force: true }).catch(async () => {
        await page.keyboard.press('Escape').catch(() => null);
      });
      await page.waitForTimeout(250);
    } catch (error) {
      out.status = 'fail';
      out.note = String(error);
      out.screenshot = await writeShot(page, `${name.replace(/[^a-zA-Z0-9_-]/g, '_')}_error`).catch(() => null);
    }

    categoryResults.push(out);
  };

  await verify({ name: 'prescription', addButton: '+処方', entity: 'medOrder', endpointPart: '/orca/master/generic-class', keyword: 'アム' });
  await verify({ name: 'injection', addButton: '+注射', entity: 'injectionOrder', endpointPart: '/orca/master/generic-class', keyword: 'アム' });
  await verify({ name: 'procedure', addButton: '+処置', entity: 'treatmentOrder', endpointPart: '/orca/master/material', keyword: 'ガーゼ' });
  await verify({ name: 'test', addButton: '+検査', entity: 'testOrder', endpointPart: '/orca/master/kensa-sort', keyword: '血液' });
  await verify({ name: 'charge', addButton: '+算定', entity: 'baseChargeOrder', endpointPart: '/orca/master/etensu', keyword: '腹' });

  const keywordRetest = await page.evaluate(async () => {
    const paths = [
      '/orca/master/generic-class?keyword=アム&page=1&size=50',
      '/orca/master/material?keyword=ガーゼ',
      '/orca/master/youhou?keyword=朝食',
      '/orca/master/kensa-sort?keyword=血液',
      '/orca/master/etensu?keyword=腹&category=2',
    ];
    const out = [];
    for (const p of paths) {
      const res = await fetch(p);
      let body = {};
      try { body = await res.json(); } catch {}
      out.push({ path: p, status: res.status, traceId: body?.traceId ?? body?.payload?.traceId ?? null });
    }
    return out;
  });

  const summary = {
    runId,
    executedAt: new Date().toISOString(),
    baseURL,
    chartsUrl: page.url(),
    categoryResults,
    keywordRetest,
    masterResponses,
  };

  fs.writeFileSync(path.join(artifactDir, 'summary-v2.json'), JSON.stringify(summary, null, 2));

  const md = [
    '# cmd_20260217_01_sub_8 主要カテゴリ操作再々検証（ashigaru7）',
    '',
    `- RUN_ID: ${runId}`,
    `- Base URL: ${baseURL}`,
    `- 実施時刻: ${summary.executedAt}`,
    '',
    '## 主要カテゴリ操作（実画面）',
    '|カテゴリ|HTTP|totalCount|選択|項目反映|判定|traceId|証跡|',
    '|---|---:|---:|---:|---:|---|---|---|',
    ...categoryResults.map((r) => `|${r.name}|${r.responseStatus ?? '-'}|${r.totalCount ?? '-'}|${r.selected ? 'yes' : 'no'}|${r.itemFilled ? 'yes' : 'no'}|${r.status}|${r.traceId ?? '-'}|${r.screenshot ?? '-'}|`),
    '',
    '## 前回失敗キーワード再試験（API）',
    '|path|status|traceId|',
    '|---|---:|---|',
    ...keywordRetest.map((r) => `|${r.path}|${r.status}|${r.traceId ?? '-'}|`),
    '',
    '## 備考',
    '- `summary-v2.json` に master応答ログ全件とカテゴリ別詳細を保存。',
  ].join('\n');

  fs.writeFileSync(path.join(artifactDir, 'summary-v2.md'), md);

  await context.close();
  await browser.close();
  console.log(JSON.stringify({ runId, artifactDir, summaryPath: path.join(artifactDir, 'summary-v2.md') }));
};

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
