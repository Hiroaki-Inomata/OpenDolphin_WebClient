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

const writeShot = async (page, name) => {
  const shot = path.join(screenshotDir, `${sanitize(name)}.png`);
  await page.screenshot({ path: shot, fullPage: true });
  return path.relative(artifactDir, shot);
};

const resolveVisibleButton = async (page, label) => {
  const candidates = page.locator('button').filter({ hasText: label });
  const count = await candidates.count();
  for (let i = 0; i < count; i += 1) {
    const btn = candidates.nth(i);
    if (await btn.isVisible().catch(() => false)) return btn;
  }
  return candidates.first();
};

const clickCategoryButton = async (page, label) => {
  const btn = await resolveVisibleButton(page, label);
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

const domProbe = async (page, selectorMap) => {
  return page.evaluate((input) => {
    const out = {};
    Object.entries(input).forEach(([name, selector]) => {
      const el = document.querySelector(selector);
      out[name] = {
        selector,
        exists: Boolean(el),
        visible: Boolean(el && el instanceof HTMLElement && el.offsetParent !== null),
        text: el ? (el.textContent ?? '').trim().slice(0, 200) : null,
        html: el ? el.outerHTML.slice(0, 500) : null,
      };
    });
    return out;
  }, selectorMap);
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
  await page.locator('.charts-page').waitFor({ timeout: 20000 });
  let starterReady = false;
  for (let i = 0; i < 3; i += 1) {
    await page.locator('[data-utility-action="order-set"]').click({ timeout: 10000 }).catch(() => null);
    const starter = page.locator('button').filter({ hasText: '+処方' }).first();
    if (await starter.isVisible().catch(() => false)) {
      starterReady = true;
      break;
    }
    await page.waitForTimeout(1200);
  }
  const bootstrapReady = starterReady;

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
      blockedAt: null,
      blockedAction: null,
      dom: {},
      improvement: null,
    };

    try {
      out.blockedAction = `カテゴリボタン ${addButton} クリック`;
      await clickCategoryButton(page, addButton);

      const panelSelector = `[data-test-id="${entity}-edit-panel"]`;
      const panel = page.locator(panelSelector);
      out.blockedAction = `${panelSelector} 可視化待ち`;
      await panel.waitFor({ state: 'visible', timeout: 15000 });

      const inputSelector = `#${entity}-item-name-0`;
      const itemInput = panel.locator(inputSelector);
      out.blockedAction = `${inputSelector} 可視化待ち`;
      await itemInput.waitFor({ state: 'visible', timeout: 15000 });

      const responsePromise = page
        .waitForResponse(
          (res) => res.url().includes(endpointPart) && res.request().method() === 'GET',
          { timeout: 20000 },
        )
        .catch(() => null);

      out.blockedAction = `keyword入力(${keyword})と候補API待ち`;
      await itemInput.fill(keyword);
      const response = await responsePromise;

      if (response) {
        out.responseStatus = response.status();
        let body = '';
        try {
          body = await response.text();
        } catch {}
        out.traceId = parseTraceId(body);
        try {
          const parsed = JSON.parse(body);
          out.totalCount = parsed?.totalCount ?? parsed?.payload?.totalCount ?? (Array.isArray(parsed?.items) ? parsed.items.length : null);
        } catch {}
      }

      const rowSelector = `${panelSelector} button.charts-side-panel__search-row`;
      const row = panel.locator('button.charts-side-panel__search-row').first();
      if (await row.isVisible().catch(() => false)) {
        out.blockedAction = `${rowSelector} 選択クリック`;
        await row.click({ timeout: 5000, force: true });
        out.selected = true;
      }

      const itemVal = await itemInput.inputValue().catch(() => '');
      out.itemFilled = Boolean(itemVal.trim());

      out.dom = await domProbe(page, {
        categoryButton: `button:has-text("${addButton}")`,
        panel: panelSelector,
        itemInput: inputSelector,
        firstCandidateRow: rowSelector,
        utilityPanel: '.charts-side-panel__content',
      });

      if (out.responseStatus === 200 && out.selected && out.itemFilled) {
        out.status = 'pass';
      } else if (out.responseStatus === 200 && out.itemFilled) {
        out.status = 'partial';
      } else {
        out.status = 'fail';
      }

      if (out.status !== 'pass') {
        out.blockedAt = out.responseStatus ? '候補一覧/選択' : '候補API応答';
        out.improvement = 'カテゴリごとに data-test-id を追加（例: category-button / category-panel / category-first-row）し、失敗時の停止点を機械判定可能にする。';
      }

      out.screenshot = await writeShot(page, name);

      await panel.getByRole('button', { name: '閉じる' }).click({ timeout: 5000, force: true }).catch(async () => {
        await page.keyboard.press('Escape').catch(() => null);
      });
      await page.waitForTimeout(250);
    } catch (error) {
      out.status = 'fail';
      out.note = String(error);
      out.blockedAt = 'UI操作途中で例外';
      out.improvement = '失敗したUI要素に安定 selector（data-test-id）を付与し、表示可否をUIロガーへ出力する。';
      out.dom = await domProbe(page, {
        chartsPage: '.charts-page',
        orderSetTab: '[data-utility-action="order-set"]',
        utilityPanel: '.charts-side-panel__content',
      }).catch(() => ({}));
      out.screenshot = await writeShot(page, `${name}_error`).catch(() => null);
    }

    categoryResults.push(out);
  };

  await verify({ name: 'prescription', addButton: '+処方', entity: 'medOrder', endpointPart: '/orca/master/generic-class', keyword: 'アム' });
  await verify({ name: 'injection', addButton: '+注射', entity: 'injectionOrder', endpointPart: '/orca/master/generic-class', keyword: 'アム' });
  await verify({ name: 'test', addButton: '+検査', entity: 'testOrder', endpointPart: '/orca/master/kensa-sort', keyword: '血液' });
  await verify({ name: 'procedure', addButton: '+処置', entity: 'treatmentOrder', endpointPart: '/orca/master/material', keyword: 'ガーゼ' });
  await verify({ name: 'charge', addButton: '+算定', entity: 'baseChargeOrder', endpointPart: '/orca/master/etensu', keyword: '腹' });

  const summary = {
    runId,
    executedAt: new Date().toISOString(),
    baseURL,
    chartsUrl: page.url(),
    bootstrapReady,
    categoryResults,
    masterResponses,
  };

  fs.writeFileSync(path.join(artifactDir, 'summary.json'), JSON.stringify(summary, null, 2));

  const md = [
    '# cmd_20260217_01_sub_10 主要カテゴリ操作の実画面証明（ashigaru6）',
    '',
    `- RUN_ID: ${runId}`,
    `- Base URL: ${baseURL}`,
    `- 実施時刻: ${summary.executedAt}`,
    '',
    '## 主要カテゴリ操作（実画面）',
    '|カテゴリ|HTTP|totalCount|選択|項目反映|判定|traceId|停止点|証跡|',
    '|---|---:|---:|---:|---:|---|---|---|---|',
    ...categoryResults.map((r) => `|${r.name}|${r.responseStatus ?? '-'}|${r.totalCount ?? '-'}|${r.selected ? 'yes' : 'no'}|${r.itemFilled ? 'yes' : 'no'}|${r.status}|${r.traceId ?? '-'}|${r.blockedAt ?? '-'}|${r.screenshot ?? '-'}|`),
    '',
    '## 到達不可カテゴリのDOM情報と最小改善案',
    ...categoryResults
      .filter((r) => r.status !== 'pass')
      .flatMap((r) => [
        `### ${r.name}`,
        `- 停止操作: ${r.blockedAction ?? '-'}`,
        `- 停止点: ${r.blockedAt ?? '-'}`,
        `- 改善案: ${r.improvement ?? '-'}`,
        `- 主要DOM: \`${JSON.stringify(r.dom)}\``,
      ]),
    '',
    '## 備考',
    '- `summary.json` に master応答ログ全件とカテゴリ別詳細を保存。',
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
