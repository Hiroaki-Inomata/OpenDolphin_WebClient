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

const masterResponses = [];

const parseTraceId = (body) => {
  if (!body || typeof body !== 'string') return null;
  try {
    const json = JSON.parse(body);
    if (json && typeof json === 'object') {
      return json.traceId ?? json.payload?.traceId ?? null;
    }
  } catch {
    const m = body.match(/"traceId"\s*:\s*"([^"]+)"/);
    if (m) return m[1];
  }
  return null;
};

const writeShot = async (page, name) => {
  const p = path.join(screenshotDir, `${name}.png`);
  await page.screenshot({ path: p, fullPage: true });
  return path.relative(artifactDir, p);
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

  const categoryResults = [];

  const verify = async ({ name, addButton, entity, masterType, keyword, endpointPart }) => {
    const out = { name, endpointPart, keyword, panel: entity, status: 'unknown', selected: false, itemFilled: false, traceId: null, responseStatus: null, screenshot: null, note: '' };
    try {
      await page.getByRole('button', { name: addButton }).first().click({ timeout: 15000 });
      const panel = page.locator(`[data-test-id="${entity}-edit-panel"]`);
      await panel.waitFor({ state: 'visible', timeout: 15000 });
      await panel.locator(`#${entity}-bundle-name`).fill(`${name}-${runId}`);
      const typeSelect = panel.locator(`#${entity}-master-type`);
      if (await typeSelect.count()) {
        await typeSelect.selectOption(masterType);
      }

      const responsePromise = page.waitForResponse((res) => {
        const u = res.url();
        return u.includes(endpointPart) && u.includes(`keyword=${encodeURIComponent(keyword)}`) && res.request().method() === 'GET';
      }, { timeout: 20000 }).catch(() => null);

      await panel.locator(`#${entity}-master-keyword`).fill(keyword);
      const response = await responsePromise;
      if (response) {
        out.responseStatus = response.status();
        let b = '';
        try { b = await response.text(); } catch {}
        out.traceId = parseTraceId(b);
      }

      const resultRow = panel.locator('button.charts-side-panel__search-row').first();
      const errorNotice = panel.locator('.charts-side-panel__notice--error').first();
      const emptyNotice = panel.locator('.charts-side-panel__empty').first();
      await Promise.race([
        resultRow.waitFor({ state: 'visible', timeout: 10000 }).catch(() => null),
        errorNotice.waitFor({ state: 'visible', timeout: 10000 }).catch(() => null),
        emptyNotice.waitFor({ state: 'visible', timeout: 10000 }).catch(() => null),
      ]);

      if (await resultRow.isVisible().catch(() => false)) {
        await resultRow.click();
        out.selected = true;
      }

      const itemInput = panel.locator(`#${entity}-item-name-0`);
      if (await itemInput.count()) {
        const v = await itemInput.inputValue().catch(() => '');
        out.itemFilled = Boolean(v.trim());
      }

      if (out.responseStatus === 200 && out.selected && out.itemFilled) {
        out.status = 'pass';
      } else if (out.responseStatus === 200) {
        out.status = 'partial';
      } else {
        out.status = 'fail';
      }

      out.screenshot = await writeShot(page, `${name.replace(/[^a-zA-Z0-9_-]/g, '_')}`);
      await page.keyboard.press('Escape').catch(() => null);
    } catch (error) {
      out.status = 'fail';
      out.note = String(error);
      out.screenshot = await writeShot(page, `${name.replace(/[^a-zA-Z0-9_-]/g, '_')}_error`).catch(() => null);
    }
    categoryResults.push(out);
  };

  await page.goto(`/f/${encodeURIComponent(facilityId)}/charts?patientId=${encodeURIComponent(patientId)}`, { waitUntil: 'domcontentloaded' });
  await page.locator('.charts-page').waitFor({ timeout: 20000 });
  await page.locator('[data-utility-action="order-set"]').click({ timeout: 10000 });

  await verify({ name: 'prescription', addButton: '+処方', entity: 'medOrder', masterType: 'generic-class', keyword: 'アム', endpointPart: '/orca/master/generic-class' });
  await verify({ name: 'injection', addButton: '+注射', entity: 'injectionOrder', masterType: 'generic-class', keyword: 'アム', endpointPart: '/orca/master/generic-class' });
  await verify({ name: 'procedure', addButton: '+処置', entity: 'treatmentOrder', masterType: 'material', keyword: 'ガーゼ', endpointPart: '/orca/master/material' });
  await verify({ name: 'test', addButton: '+検査', entity: 'testOrder', masterType: 'kensa-sort', keyword: '血液', endpointPart: '/orca/master/kensa-sort' });
  await verify({ name: 'charge', addButton: '+算定', entity: 'baseChargeOrder', masterType: 'etensu', keyword: '腹', endpointPart: '/orca/master/etensu' });

  const keywordRetest = await page.evaluate(async () => {
    const req = async (path) => {
      const res = await fetch(path, { headers: {
        userName: '1.3.6.1.4.1.9414.72.103:doctor1',
        password: '632080fabdb968f9ac4f31fb55104648',
      } });
      let body = {};
      try { body = await res.json(); } catch {}
      return { path, status: res.status, traceId: body?.traceId ?? body?.payload?.traceId ?? null };
    };
    const paths = [
      '/orca/master/generic-class?keyword=アム&page=1&size=50',
      '/orca/master/material?keyword=ガーゼ',
      '/orca/master/youhou?keyword=朝食',
      '/orca/master/kensa-sort?keyword=血液',
      '/orca/master/etensu?keyword=腹&category=2',
    ];
    const out = [];
    for (const p of paths) out.push(await req(p));
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

  fs.writeFileSync(path.join(artifactDir, 'summary.json'), JSON.stringify(summary, null, 2));
  const md = [
    `# cmd_20260217_01_sub_8 主要カテゴリ操作再検証（ashigaru7）`,
    '',
    `- RUN_ID: ${runId}`,
    `- Base URL: ${baseURL}`,
    `- 実施時刻: ${summary.executedAt}`,
    '',
    '## 主要カテゴリ操作（実画面）',
    '|カテゴリ|HTTP|選択|項目反映|判定|traceId|証跡|',
    '|---|---:|---:|---:|---|---|---|',
    ...categoryResults.map((r) => `|${r.name}|${r.responseStatus ?? '-'}|${r.selected ? 'yes' : 'no'}|${r.itemFilled ? 'yes' : 'no'}|${r.status}|${r.traceId ?? '-'}|${r.screenshot ?? '-'}|`),
    '',
    '## 前回失敗キーワード再試験（API）',
    '|path|status|traceId|',
    '|---|---:|---|',
    ...keywordRetest.map((r) => `|${r.path}|${r.status}|${r.traceId ?? '-'}|`),
    '',
    '## 備考',
    '- `summary.json` に master応答ログ全件を保存。',
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
