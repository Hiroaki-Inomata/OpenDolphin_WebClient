import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const RUN_ID = process.env.RUN_ID ?? '20260211T034536Z';
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const FACILITY_ID = process.env.FACILITY_ID ?? '1.3.6.1.4.1.9414.72.103';
const USER_ID = process.env.USER_ID ?? 'doctor1';
const PASSWORD = process.env.PASSWORD ?? 'doctor2025';
const OUT_DIR = process.env.OUT_DIR ?? path.resolve('artifacts', 'webclient', 'debug', `${RUN_ID}-fullflow`);

fs.mkdirSync(OUT_DIR, { recursive: true });

const logs = [];
const consoleLogs = [];
const requestFailedLogs = [];
const httpErrorLogs = [];
const keyResponses = [];
const stepResults = [];

const nowIso = () => new Date().toISOString();

const log = (message) => {
  const line = `[${nowIso()}] ${message}`;
  logs.push(line);
  console.log(line);
};

const sanitize = (value) =>
  String(value)
    .replace(/[^0-9A-Za-z_-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');

const writeFile = (fileName, body) => {
  fs.writeFileSync(path.join(OUT_DIR, fileName), body, 'utf8');
};

const saveJson = (fileName, value) => {
  writeFile(fileName, `${JSON.stringify(value, null, 2)}\n`);
};

const isVisible = async (locator, timeout = 0) => {
  try {
    return await locator.isVisible({ timeout });
  } catch {
    return false;
  }
};

const fillIfVisible = async (page, selector, value) => {
  const locator = page.locator(selector).first();
  if (await isVisible(locator, 1000)) {
    await locator.fill('');
    await locator.fill(value);
    return true;
  }
  return false;
};

const clickIfVisible = async (page, selector) => {
  const locator = page.locator(selector).first();
  if (await isVisible(locator, 1000)) {
    await locator.click();
    return true;
  }
  return false;
};

const ensureActionBarExpanded = async (page) => {
  const toggle = page.getByRole('button', { name: '操作を開く' }).first();
  if (await isVisible(toggle, 1200)) {
    await toggle.click();
    await page.waitForTimeout(400);
    return true;
  }
  return false;
};

const takeScreenshot = async (page, fileName) => {
  const target = path.join(OUT_DIR, fileName);
  await page.screenshot({ path: target, fullPage: true });
};

const runStep = async (name, fn, { critical = false } = {}) => {
  const startedAt = Date.now();
  log(`STEP START: ${name}`);
  try {
    const detail = await fn();
    const elapsedMs = Date.now() - startedAt;
    stepResults.push({ name, status: 'passed', elapsedMs, detail: detail ?? null });
    log(`STEP PASS: ${name} (${elapsedMs}ms)`);
    return detail;
  } catch (error) {
    const elapsedMs = Date.now() - startedAt;
    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    stepResults.push({ name, status: 'failed', elapsedMs, error: message, critical });
    log(`STEP FAIL: ${name} (${elapsedMs}ms) ${message}`);
    if (critical) {
      throw error;
    }
    return null;
  }
};

const keyUrlPatterns = [
  '/orca/visits/mutation',
  '/orca11/acceptmodv2',
  '/karte/freedocument/',
  '/karte/safety/',
  '/karte/rpHistory/list/',
  '/api21/medicalmodv2',
  '/api21/medicalmodv23',
];

const main = async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();

  page.on('console', (msg) => {
    consoleLogs.push({ type: msg.type(), text: msg.text() });
  });

  page.on('requestfailed', (req) => {
    requestFailedLogs.push({
      url: req.url(),
      method: req.method(),
      failure: req.failure()?.errorText ?? 'unknown',
    });
  });

  page.on('response', async (res) => {
    const url = res.url();
    const status = res.status();
    if (status >= 400) {
      httpErrorLogs.push({ url, status, method: res.request().method() });
    }
    if (keyUrlPatterns.some((pattern) => url.includes(pattern))) {
      const headers = await res.allHeaders().catch(() => ({}));
      keyResponses.push({
        url,
        status,
        method: res.request().method(),
        runId: headers['x-run-id'] ?? null,
        traceId: headers['x-trace-id'] ?? null,
      });
    }
  });

  let selectedPatientId = '0001';
  let receptionApiResult = null;
  let sendToastText = '';
  let invoiceNumber = null;
  let dataId = null;

  await runStep(
    'ログイン',
    async () => {
      const loginUrl = `${BASE_URL}/f/${encodeURIComponent(FACILITY_ID)}/login`;
      await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
      await page.waitForTimeout(1200);

      if (page.url().includes('/reception')) {
        log('既存セッションで Reception へ到達済み。');
      } else {
        const facilityInput = page.locator('#login-facility-id');
        const userInput = page.locator('#login-user-id');
        const passwordInput = page.locator('#login-password');

        if (!(await isVisible(facilityInput, 10_000))) {
          throw new Error(`ログイン入力欄が見つかりません: ${page.url()}`);
        }

        await facilityInput.fill(FACILITY_ID);
        await userInput.fill(USER_ID);
        await passwordInput.fill(PASSWORD);

        await Promise.all([
          page.waitForURL((url) => url.pathname.includes('/reception'), { timeout: 60_000 }),
          page.getByRole('button', { name: 'ログイン' }).click(),
        ]);
      }

      await page.waitForSelector('.reception-page', { timeout: 30_000 });
      await takeScreenshot(page, '01-reception-login.png');
      return { url: page.url() };
    },
    { critical: true },
  );

  await runStep(
    '患者0001検索と選択',
    async () => {
      const candidateIds = ['0001', '00001', '000001'];
      let selected = false;

      for (const candidate of candidateIds) {
        await page.locator('#reception-patient-search-patient-id').fill('');
        await page.locator('#reception-patient-search-name-sei').fill('');
        await page.locator('#reception-patient-search-name-mei').fill('');
        await page.locator('#reception-patient-search-kana-sei').fill('');
        await page.locator('#reception-patient-search-kana-mei').fill('');

        await page.locator('#reception-patient-search-patient-id').fill(candidate);
        await page.locator('[data-test-id="reception-patient-search-submit"]').click();
        await page.waitForTimeout(1500);

        const direct = page.locator(`[data-test-id="reception-patient-search-select-${candidate}"]`).first();
        if (await isVisible(direct, 1500)) {
          await direct.click();
          selectedPatientId = candidate;
          selected = true;
          break;
        }

        const byText = page.locator('.reception-patient-search__item-select').filter({ hasText: candidate }).first();
        if (await isVisible(byText, 1500)) {
          const text = (await byText.textContent()) ?? '';
          const match = text.match(/患者ID:\s*([0-9]+)/);
          if (match?.[1]) {
            selectedPatientId = match[1];
          } else {
            selectedPatientId = candidate;
          }
          await byText.click();
          selected = true;
          break;
        }
      }

      if (!selected) {
        throw new Error('患者0001の検索結果を選択できませんでした。');
      }

      const selectedItem = page.locator('.reception-patient-search__item.is-selected').first();
      if (!(await isVisible(selectedItem, 5000))) {
        throw new Error('患者選択状態になっていません。');
      }

      await takeScreenshot(page, '02-reception-patient-selected.png');
      return { selectedPatientId };
    },
    { critical: true },
  );

  await runStep(
    '受付登録（患者0001）',
    async () => {
      const registerButton = page.locator('[data-test-id="reception-accept-register"]').first();
      if (!(await isVisible(registerButton, 10_000))) {
        throw new Error('受付登録ボタンが見つかりません。');
      }

      if (await registerButton.isDisabled()) {
        await clickIfVisible(page, '[data-test-id="reception-accept-toggle-details"]');
        await page.waitForTimeout(300);

        const department = page.locator('#reception-accept-department');
        if (await isVisible(department, 2000)) {
          const options = await department.locator('option').count();
          if (options >= 2) await department.selectOption({ index: 1 });
        }
        const physician = page.locator('#reception-accept-physician');
        if (await isVisible(physician, 2000)) {
          const options = await physician.locator('option').count();
          if (options >= 2) await physician.selectOption({ index: 1 });
        }
        const payment = page.locator('#reception-accept-payment-mode');
        if (await isVisible(payment, 2000)) {
          await payment.selectOption('self').catch(() => {});
        }
        const visitKind = page.locator('#reception-accept-visit-kind');
        if (await isVisible(visitKind, 2000)) {
          await visitKind.selectOption('1').catch(() => {});
        }
      }

      const acceptResponsePromise = page.waitForResponse(
        (response) => {
          const url = response.url();
          return (
            (url.includes('/orca/visits/mutation') || url.includes('/orca11/acceptmodv2')) &&
            response.request().method() === 'POST'
          );
        },
        { timeout: 60_000 },
      );

      await registerButton.click();
      const acceptResponse = await acceptResponsePromise;
      await page.waitForTimeout(1200);

      const apiResultLocator = page.locator('[data-test-id="accept-api-result"]').first();
      if (!(await isVisible(apiResultLocator, 10_000))) {
        throw new Error('受付送信結果（Api_Result）が表示されません。');
      }

      receptionApiResult = (await apiResultLocator.innerText()).trim();
      const toneMessage = (await page.locator('.reception-accept__result .tone-banner').first().innerText()).trim();

      if (acceptResponse.status() >= 500) {
        throw new Error(`受付APIが500系を返却: HTTP ${acceptResponse.status()}`);
      }
      if (/エラー|失敗/.test(toneMessage)) {
        throw new Error(`受付結果が失敗表示です: ${toneMessage}`);
      }
      if (!/Api_Result:\s*(00|0000|K3|16)/.test(receptionApiResult)) {
        throw new Error(`受付結果が成功/許容結果(00/0000/K3/16)ではありません: ${receptionApiResult}`);
      }

      await takeScreenshot(page, '03-reception-accepted.png');
      return {
        apiResult: receptionApiResult,
        status: acceptResponse.status(),
        toneMessage,
      };
    },
    { critical: true },
  );

  await runStep(
    'カルテを開く',
    async () => {
      const selectedItem = page.locator('.reception-patient-search__item.is-selected').first();
      if (!(await isVisible(selectedItem, 10_000))) {
        throw new Error('選択済み患者カードが見つかりません。');
      }

      const openButton = selectedItem.getByRole('button', { name: 'カルテを開く' });
      await Promise.all([
        page.waitForURL((url) => url.pathname.includes('/charts'), { timeout: 60_000 }),
        openButton.click(),
      ]);

      await page.waitForSelector('.charts-page', { timeout: 30_000 });
      await page.waitForTimeout(2000);
      await takeScreenshot(page, '04-charts-opened.png');

      const key500 = keyResponses.filter(
        (row) =>
          (row.url.includes('/karte/freedocument/') || row.url.includes('/karte/safety/') || row.url.includes('/karte/rpHistory/list/')) &&
          row.status >= 500,
      );
      if (key500.length > 0) {
        throw new Error(`カルテ初期APIで500が発生: ${JSON.stringify(key500)}`);
      }

      return { url: page.url() };
    },
    { critical: true },
  );

  await runStep('患者サマリ保存', async () => {
    const summaryToggle = page.locator('summary.charts-fold__summary', { hasText: '患者サマリ' }).first();
    if (!(await isVisible(summaryToggle, 5000))) {
      return { skipped: true, reason: '患者サマリUIが表示されていません。' };
    }
    await summaryToggle.click();
    const textarea = page.locator('.charts-free-doc__textarea').first();
    if (!(await isVisible(textarea, 5000))) {
      return { skipped: true, reason: '患者サマリの入力欄が表示されていません。' };
    }
    await textarea.fill(`[${RUN_ID}] 患者サマリE2E更新`);
    const saveButton = page.locator('.charts-free-doc__actions .charts-free-doc__save').first();
    await saveButton.click();
    await page.waitForTimeout(1200);
    await takeScreenshot(page, '05-patient-summary-saved.png');
    return { saved: true };
  });

  await runStep('病名入力（追加）', async () => {
    const panel = page.locator('[data-test-id="diagnosis-edit-panel"]').first();
    if (!(await isVisible(panel, 10_000))) {
      throw new Error('病名編集パネルが見つかりません。');
    }

    await panel.getByRole('button', { name: '追加' }).click();
    const dialog = page.locator('[data-test-id="charts-diagnosis-editor-dialog"]').first();
    if (!(await isVisible(dialog, 10_000))) {
      throw new Error('病名編集ダイアログが開きません。');
    }

    await dialog.locator('#diagnosis-prefix').fill('E2E');
    await dialog.locator('#diagnosis-name').fill(`高血圧症-${RUN_ID.slice(-6)}`);
    await dialog.locator('#diagnosis-suffix').fill('疑い');

    await dialog.locator('#diagnosis-main').check();
    await dialog.locator('#diagnosis-suspected').check();

    const advanced = dialog.locator('summary.charts-diagnosis__advanced-summary').first();
    if (await isVisible(advanced, 1000)) {
      await advanced.click();
      await dialog.locator('#diagnosis-code').fill('I10');
      await dialog.locator('#diagnosis-outcome').fill('継続');
    }

    let saved = false;
    try {
      await dialog.getByRole('button', { name: '追加', exact: true }).click();
      await page.waitForTimeout(1800);

      const savedNotice = page.locator('.charts-side-panel__notice').filter({ hasText: /病名.*保存/ }).first();
      const savedLabel = panel.locator('.charts-diagnosis__item').filter({ hasText: /高血圧症-/ }).first();
      saved = (await isVisible(savedNotice, 1500)) || (await isVisible(savedLabel, 1500));
    } finally {
      const closeButton = dialog.getByRole('button', { name: '閉じる' });
      if (await isVisible(closeButton, 1000)) {
        await closeButton.click();
      } else {
        await page.keyboard.press('Escape').catch(() => {});
      }
      await page.waitForTimeout(300);
    }
    if (!saved) {
      throw new Error('病名保存完了の表示（通知/一覧反映）が確認できません。');
    }
    await takeScreenshot(page, '06-diagnosis-added.png');
    return { saved: true };
  });

  await runStep('SOAP全欄記載と保存', async () => {
    const soapPanel = page.locator('section.soap-note').first();
    if (!(await isVisible(soapPanel, 10_000))) {
      throw new Error('SOAPパネルが見つかりません。');
    }

    for (let i = 0; i < 3; i += 1) {
      const subjectiveVisible = await isVisible(page.locator('#soap-note-subjective').first(), 1000);
      const freeVisible = await isVisible(page.locator('#soap-note-free').first(), 1000);
      if (subjectiveVisible && freeVisible) break;
      const cycleButton = soapPanel.locator('.soap-note__actions button', { hasText: /^表示:/ }).first();
      if (await isVisible(cycleButton, 1000)) {
        await cycleButton.click();
      }
      await page.waitForTimeout(300);
    }

    const sectionTexts = {
      subjective: `[${RUN_ID}] 主観: 咽頭痛あり`,
      objective: `[${RUN_ID}] 客観: 体温37.2℃`,
      assessment: `[${RUN_ID}] 評価: 上気道炎疑い`,
      plan: `[${RUN_ID}] 計画: 対症療法`,
      free: `[${RUN_ID}] Free記載: 生活指導実施`,
    };

    for (const [section, text] of Object.entries(sectionTexts)) {
      const field = page.locator(`#soap-note-${section}`).first();
      if (!(await isVisible(field, 4000))) {
        throw new Error(`SOAP欄が見つかりません: ${section}`);
      }
      await field.fill(text);
    }

    const saveButton = soapPanel.locator('.soap-note__actions .soap-note__primary').first();
    await saveButton.click();
    await page.waitForTimeout(1800);

    const feedback = soapPanel.locator('.soap-note__feedback').first();
    if (!(await isVisible(feedback, 5000))) {
      throw new Error('SOAP保存フィードバックが表示されません。');
    }

    const feedbackText = (await feedback.innerText()).trim();
    if (!/保存|更新|OK|server/.test(feedbackText)) {
      throw new Error(`SOAP保存結果が不明です: ${feedbackText}`);
    }

    await takeScreenshot(page, '07-soap-saved.png');
    return { feedback: feedbackText };
  }, { critical: true });

  await runStep('症状詳記入力と登録', async () => {
    const foldSummary = page.locator('.soap-note__subjectives-fold > summary').first();
    if (!(await isVisible(foldSummary, 5000))) {
      return { skipped: true, reason: '症状詳記パネルが見つかりません。' };
    }
    await foldSummary.click();
    await page.waitForTimeout(600);

    const codeArea = page.locator('#subjectives-code').first();
    if (!(await isVisible(codeArea, 5000))) {
      return { skipped: true, reason: '症状詳記入力欄が見つかりません。' };
    }

    await codeArea.fill(`[${RUN_ID}] 症状詳記テスト入力`);

    const submitButton = page.getByRole('button', { name: '症状詳記を登録' }).first();
    if (!(await isVisible(submitButton, 3000))) {
      return { skipped: true, reason: '症状詳記登録ボタンが見つかりません。' };
    }

    await submitButton.click();
    await page.waitForTimeout(2500);
    await takeScreenshot(page, '08-subjectives-registered.png');

    const banner = page.locator('.soap-note__subjectives .tone-banner').last();
    const bannerText = (await isVisible(banner, 1000)) ? (await banner.innerText()).trim() : '';
    return { message: bannerText || 'banner not found' };
  });

  const saveOrderTab = async ({ action, entity, label }) => {
    const tabButton = page.locator(`[data-utility-action="${action}"]`).first();
    if (!(await isVisible(tabButton, 5000))) {
      throw new Error(`ユーティリティタブが見つかりません: ${action}`);
    }
    await tabButton.click();
    await page.waitForTimeout(700);

    const panel = page.locator('#charts-docked-panel').first();
    if (!(await isVisible(panel, 5000))) {
      throw new Error('ユーティリティパネルが開きません。');
    }

    const bundleName = panel.locator(`#${entity}-bundle-name`).first();
    if (!(await isVisible(bundleName, 5000))) {
      throw new Error(`オーダー入力欄(bundle)が見つかりません: ${entity}`);
    }

    const suffix = RUN_ID.slice(-4);
    const bundleLabel = `${label}-E2E-${suffix}`;
    await bundleName.fill(bundleLabel);

    const admin = panel.locator(`#${entity}-admin`).first();
    if (await isVisible(admin, 1000)) {
      await admin.fill('1日1回 朝食後');
    }

    const itemName = panel.locator(`#${entity}-item-name-0`).first();
    if (!(await isVisible(itemName, 5000))) {
      throw new Error(`オーダー入力欄(item)が見つかりません: ${entity}`);
    }
    await itemName.fill(`${label}項目`);

    const quantity = panel.locator(`#${entity}-item-quantity-0`).first();
    if (await isVisible(quantity, 1000)) {
      await quantity.fill('1');
    }
    const unit = panel.locator(`#${entity}-item-unit-0`).first();
    if (await isVisible(unit, 1000)) {
      await unit.fill('回');
    }

    const submit = panel.locator('button[type="submit"]').first();
    await submit.click();
    await page.waitForTimeout(1800);

    const errorNotice = panel.locator('.charts-side-panel__notice--error').first();
    if (await isVisible(errorNotice, 1000)) {
      const msg = (await errorNotice.innerText()).trim();
      throw new Error(`オーダー保存エラー(${action}): ${msg}`);
    }

    const bundleInList = panel.locator('.charts-side-panel__items li strong', { hasText: bundleLabel }).first();
    if (!(await isVisible(bundleInList, 5000))) {
      throw new Error(`保存済み一覧に反映されません: ${bundleLabel}`);
    }

    await takeScreenshot(page, `09-order-${sanitize(action)}.png`);
    return { action, entity, bundleLabel };
  };

  await runStep('処方オーダー保存', async () => saveOrderTab({ action: 'prescription-edit', entity: 'medOrder', label: '処方' }));
  await runStep('注射オーダー保存', async () => saveOrderTab({ action: 'order-injection', entity: 'injectionOrder', label: '注射' }));
  await runStep('処置オーダー保存', async () => saveOrderTab({ action: 'order-treatment', entity: 'treatmentOrder', label: '処置' }));
  await runStep('検査オーダー保存', async () => saveOrderTab({ action: 'order-test', entity: 'testOrder', label: '検査' }));
  await runStep('算定オーダー保存', async () => saveOrderTab({ action: 'order-charge', entity: 'baseChargeOrder', label: '算定' }));

  await runStep('文書作成と保存', async () => {
    const tabButton = page.locator('[data-utility-action="document"]').first();
    if (!(await isVisible(tabButton, 5000))) {
      return { skipped: true, reason: '文書タブが表示されていません。' };
    }

    await tabButton.click();
    await page.waitForTimeout(800);
    const panel = page.locator('#charts-docked-panel').first();

    const template = panel.locator('#document-template').first();
    if (await isVisible(template, 3000)) {
      const options = await template.locator('option').count();
      if (options >= 2) {
        await template.selectOption({ index: 1 });
      }
      const apply = panel.getByRole('button', { name: 'テンプレート適用' }).first();
      if (await isVisible(apply, 1000)) {
        await apply.click();
        await page.waitForTimeout(500);
      }
    }

    await fillIfVisible(page, '#referral-hospital', 'E2E病院');
    await fillIfVisible(page, '#referral-doctor', 'E2E医師');
    await fillIfVisible(page, '#referral-purpose', '精査依頼');
    await fillIfVisible(page, '#referral-diagnosis', '高血圧症');
    await fillIfVisible(page, '#referral-body', `[${RUN_ID}] 紹介内容テスト`);

    const saveButton = panel.locator('.charts-side-panel__actions > button', { hasText: /^保存$/ }).first();
    if (!(await isVisible(saveButton, 3000))) {
      throw new Error('文書保存ボタンが見つかりません。');
    }
    await saveButton.click();
    await page.waitForTimeout(2200);

    const notice = panel.locator('.charts-side-panel__notice').first();
    if (await isVisible(notice, 3000)) {
      const text = (await notice.innerText()).trim();
      if (/失敗|エラー/.test(text)) {
        throw new Error(`文書保存エラー: ${text}`);
      }
    }

    await takeScreenshot(page, '10-document-saved.png');
    return { saved: true };
  });

  await runStep('診療終了', async () => {
    await ensureActionBarExpanded(page);
    const finishButton = page.locator('#charts-action-finish').first();
    if (!(await isVisible(finishButton, 5000))) {
      return { skipped: true, reason: '診療終了ボタンが見つかりません。' };
    }
    if (await finishButton.isDisabled()) {
      const reason = await finishButton.getAttribute('data-disabled-reason');
      return { skipped: true, reason: `診療終了ボタンが無効です: ${reason ?? 'unknown'}` };
    }

    await finishButton.click();
    await page.waitForTimeout(2500);

    const toast = page.locator('.charts-actions__toast').first();
    const toastText = (await isVisible(toast, 1000)) ? (await toast.innerText()).trim() : '';
    await takeScreenshot(page, '11-finish.png');
    return { toast: toastText || null };
  });

  await runStep(
    'ORCA送信',
    async () => {
      await ensureActionBarExpanded(page);
      const sendButton = page.locator('#charts-action-send').first();
      if (!(await isVisible(sendButton, 10_000))) {
        throw new Error('ORCA送信ボタンが見つかりません。');
      }
      if (await sendButton.isDisabled()) {
        const reason = await sendButton.getAttribute('data-disabled-reason');
        throw new Error(`ORCA送信ボタンが無効です: ${reason ?? 'unknown'}`);
      }

      await sendButton.click();
      const dialog = page.getByRole('alertdialog', { name: 'ORCA送信の確認' });
      if (!(await isVisible(dialog, 10_000))) {
        throw new Error('ORCA送信確認ダイアログが表示されません。');
      }

      const sendResponsePromise = page.waitForResponse((response) => {
        const url = response.url();
        return (
          response.request().method() === 'POST' &&
          (url.includes('/api21/medicalmodv2') ||
            url.includes('/orca21/medicalmodv2/outpatient') ||
            url.includes('/api21/medicalmodv23'))
        );
      }, { timeout: 180_000 });

      await dialog.getByRole('button', { name: '送信する' }).click();
      const sendResponse = await sendResponsePromise;

      const toast = page.locator('.charts-actions__toast').first();
      await page.waitForTimeout(4000);
      if (!(await isVisible(toast, 15_000))) {
        throw new Error('ORCA送信後のトーストが表示されません。');
      }

      sendToastText = (await toast.innerText()).trim();
      if (!/ORCA送信を完了/.test(sendToastText)) {
        throw new Error(`ORCA送信が成功表示ではありません: ${sendToastText}`);
      }

      const invoiceMatch = sendToastText.match(/Invoice_Number=([A-Za-z0-9_-]+)/);
      const dataMatch = sendToastText.match(/Data_Id=([A-Za-z0-9_-]+)/);
      invoiceNumber = invoiceMatch?.[1] ?? null;
      dataId = dataMatch?.[1] ?? null;

      await takeScreenshot(page, '12-orca-send-success.png');
      return {
        status: sendResponse.status(),
        toast: sendToastText,
        invoiceNumber,
        dataId,
      };
    },
    { critical: true },
  );

  await runStep(
    '会計反映確認（Reception）',
    async () => {
      const billingButton = page.getByRole('button', { name: '会計へ' }).first();
      if (await isVisible(billingButton, 5000)) {
        await Promise.all([
          page.waitForURL((url) => url.pathname.includes('/reception'), { timeout: 60_000 }),
          billingButton.click(),
        ]);
      } else {
        await page.goto(`${BASE_URL}/f/${encodeURIComponent(FACILITY_ID)}/reception?patientId=${encodeURIComponent(selectedPatientId)}`, {
          waitUntil: 'domcontentloaded',
          timeout: 60_000,
        });
      }

      await page.waitForSelector('.reception-page', { timeout: 20_000 });
      await page.waitForTimeout(2000);

      const targetCard = page
        .locator(`[data-test-id="reception-entry-card"][data-patient-id="${selectedPatientId}"]`)
        .first();
      const fallbackCard = page.locator('[data-test-id="reception-entry-card"]').filter({ hasText: selectedPatientId }).first();

      let card = targetCard;
      if (!(await isVisible(card, 5000))) {
        card = fallbackCard;
      }
      if (!(await isVisible(card, 10_000))) {
        throw new Error(`患者カードが見つかりません: patientId=${selectedPatientId}`);
      }

      const cardText = (await card.innerText()).trim();
      if (!/ORCA送信:\s*成功/.test(cardText)) {
        throw new Error(`ReceptionカードにORCA送信成功が反映されていません: ${cardText}`);
      }
      if (!/invoice:/i.test(cardText) || !/data:/i.test(cardText)) {
        throw new Error(`Receptionカードにinvoice/dataが表示されていません: ${cardText}`);
      }

      await takeScreenshot(page, '13-reception-billing-verified.png');
      return { patientId: selectedPatientId, invoiceNumber, dataId };
    },
    { critical: true },
  );

  await context.close();
  await browser.close();

  const http500 = httpErrorLogs.filter((row) => row.status >= 500);
  const keySummary = {
    freedocument: keyResponses.filter((row) => row.url.includes('/karte/freedocument/')).slice(-3),
    safety: keyResponses.filter((row) => row.url.includes('/karte/safety/')).slice(-3),
    rpHistory: keyResponses.filter((row) => row.url.includes('/karte/rpHistory/list/')).slice(-3),
    accept: keyResponses.filter((row) => row.url.includes('/orca/visits/mutation') || row.url.includes('/orca11/acceptmodv2')).slice(-3),
    medicalmodv2: keyResponses.filter((row) => row.url.includes('/api21/medicalmodv2')).slice(-3),
    medicalmodv23: keyResponses.filter((row) => row.url.includes('/api21/medicalmodv23')).slice(-3),
  };

  const failedSteps = stepResults.filter((step) => step.status === 'failed');

  saveJson('step-results.json', stepResults);
  saveJson('http-errors.json', httpErrorLogs);
  saveJson('request-failed.json', requestFailedLogs);
  saveJson('key-responses.json', keyResponses);
  saveJson('console-logs.json', consoleLogs);
  saveJson('summary.json', {
    runId: RUN_ID,
    baseUrl: BASE_URL,
    facilityId: FACILITY_ID,
    selectedPatientId,
    receptionApiResult,
    sendToastText,
    invoiceNumber,
    dataId,
    failedStepCount: failedSteps.length,
    http500Count: http500.length,
    keySummary,
  });
  writeFile('execution.log', `${logs.join('\n')}\n`);

  const md = [
    `# Live E2E Fullflow (${RUN_ID})`,
    `- baseUrl: ${BASE_URL}`,
    `- facilityId: ${FACILITY_ID}`,
    `- patientId: ${selectedPatientId}`,
    `- receptionApiResult: ${receptionApiResult ?? 'n/a'}`,
    `- sendToast: ${sendToastText || 'n/a'}`,
    `- invoice: ${invoiceNumber ?? 'n/a'}`,
    `- dataId: ${dataId ?? 'n/a'}`,
    `- failedSteps: ${failedSteps.length}`,
    `- http500: ${http500.length}`,
    '',
    '## Steps',
    ...stepResults.map((step) =>
      step.status === 'passed'
        ? `- [PASS] ${step.name} (${step.elapsedMs}ms)`
        : `- [FAIL] ${step.name} (${step.elapsedMs}ms) ${step.error}`,
    ),
    '',
    '## Key API Status',
    `- freedocument: ${keySummary.freedocument.map((x) => x.status).join(', ') || 'none'}`,
    `- safety: ${keySummary.safety.map((x) => x.status).join(', ') || 'none'}`,
    `- rpHistory: ${keySummary.rpHistory.map((x) => x.status).join(', ') || 'none'}`,
    `- accept: ${keySummary.accept.map((x) => x.status).join(', ') || 'none'}`,
    `- medicalmodv2: ${keySummary.medicalmodv2.map((x) => x.status).join(', ') || 'none'}`,
    `- medicalmodv23: ${keySummary.medicalmodv23.map((x) => x.status).join(', ') || 'none'}`,
  ].join('\n');
  writeFile('summary.md', `${md}\n`);

  if (failedSteps.length > 0 || http500.length > 0) {
    process.exitCode = 1;
  }
};

main().catch((error) => {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  log(`FATAL: ${message}`);
  writeFile('fatal.log', `${message}\n${logs.join('\n')}\n`);
  process.exitCode = 1;
});
