import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5173';
const FACILITY_ID = process.env.FACILITY_ID ?? '1.3.6.1.4.1.9414.10.1';
const USER_ID = process.env.USER_ID ?? 'dolphindev';
const PASSWORD = process.env.PASSWORD ?? 'dolphindev';
const PATIENT_CANDIDATES = ['0001', '00001'];

const log = (...args) => console.log(new Date().toISOString(), ...args);

const isVisible = async (locator, timeout = 0) => {
  try {
    return await locator.isVisible({ timeout });
  } catch {
    return false;
  }
};

const main = async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();

  try {
    const loginUrl = `${BASE_URL}/f/${encodeURIComponent(FACILITY_ID)}/login`;
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(1200);

    if (!page.url().includes('/reception')) {
      await page.locator('#login-facility-id').fill(FACILITY_ID);
      await page.locator('#login-user-id').fill(USER_ID);
      await page.locator('#login-password').fill(PASSWORD);
      await Promise.all([
        page.waitForURL((url) => url.pathname.includes('/reception'), { timeout: 60_000 }),
        page.getByRole('button', { name: 'ログイン' }).click(),
      ]);
    }
    log('logged-in', page.url());

    let selectedId = null;
    for (const candidate of PATIENT_CANDIDATES) {
      await page.locator('#reception-patient-search-patient-id').fill('');
      await page.locator('#reception-patient-search-patient-id').fill(candidate);
      await page.locator('[data-test-id="reception-patient-search-submit"]').click();
      await page.waitForTimeout(1200);

      const direct = page.locator(`[data-test-id="reception-patient-search-select-${candidate}"]`).first();
      if (await isVisible(direct, 1200)) {
        await direct.click();
        selectedId = candidate;
        break;
      }
      const byText = page.locator('.reception-patient-search__item-select').filter({ hasText: candidate }).first();
      if (await isVisible(byText, 1200)) {
        const text = (await byText.textContent()) ?? '';
        const m = text.match(/患者ID:\s*([0-9]+)/);
        selectedId = m?.[1] ?? candidate;
        await byText.click();
        break;
      }
    }
    if (!selectedId) throw new Error('patient not found');
    log('patient-selected', selectedId);

    const selectedItem = page.locator('.reception-patient-search__item.is-selected').first();
    await selectedItem.getByRole('button', { name: 'カルテを開く' }).click();
    await page.waitForURL((url) => url.pathname.includes('/charts'), { timeout: 60_000 });
    await page.waitForSelector('.charts-page', { timeout: 30_000 });
    await page.waitForTimeout(1500);
    log('charts-opened', page.url());

    const expand = page.getByRole('button', { name: '操作を開く' }).first();
    if (await isVisible(expand, 1500)) {
      await expand.click();
      await page.waitForTimeout(500);
      log('actionbar-expanded');
    }

    const sendButton = page.locator('#charts-action-send').first();
    const sendVisible = await isVisible(sendButton, 5000);
    if (!sendVisible) {
      await page.screenshot({ path: 'artifacts/webclient/debug/20260211T041852Z-fullflow/orca-send-verify-missing-send.png', fullPage: true });
      throw new Error('send button not visible');
    }

    const disabled = await sendButton.isDisabled();
    const reason = await sendButton.getAttribute('data-disabled-reason');
    log('send-state', { disabled, reason });
    if (disabled) {
      await page.screenshot({ path: 'artifacts/webclient/debug/20260211T041852Z-fullflow/orca-send-verify-send-disabled.png', fullPage: true });
      throw new Error(`send disabled: ${reason ?? 'unknown'}`);
    }

    const responsePromise = page.waitForResponse(
      (response) => response.url().includes('/api21/medicalmodv2') && response.request().method() === 'POST',
      { timeout: 120_000 },
    );

    await sendButton.click();
    const dialog = page.getByRole('alertdialog', { name: 'ORCA送信の確認' }).first();
    if (!(await isVisible(dialog, 10_000))) {
      throw new Error('confirm dialog not visible');
    }
    await dialog.getByRole('button', { name: '送信する' }).click();
    const response = await responsePromise;
    log('medicalmodv2-response', response.status(), response.url());

    const toast = page.locator('.charts-actions__toast').first();
    await page.waitForTimeout(3500);
    if (!(await isVisible(toast, 10000))) throw new Error('toast not found');
    const toastText = (await toast.innerText()).trim();
    log('toast', toastText);
    if (!/ORCA送信を完了/.test(toastText)) {
      await page.screenshot({ path: 'artifacts/webclient/debug/20260211T041852Z-fullflow/orca-send-verify-toast-failed.png', fullPage: true });
      throw new Error(`orca send not success: ${toastText}`);
    }

    const toReception = page.getByRole('button', { name: '会計へ' }).first();
    if (await isVisible(toReception, 5000)) {
      await Promise.all([
        page.waitForURL((url) => url.pathname.includes('/reception'), { timeout: 60_000 }),
        toReception.click(),
      ]);
    } else {
      await page.goto(`${BASE_URL}/f/${encodeURIComponent(FACILITY_ID)}/reception?patientId=${encodeURIComponent(selectedId)}`, {
        waitUntil: 'domcontentloaded',
        timeout: 60_000,
      });
    }

    await page.waitForSelector('.reception-page', { timeout: 20_000 });
    await page.waitForTimeout(1500);

    const card = page
      .locator(`[data-test-id="reception-entry-card"][data-patient-id="${selectedId}"]`)
      .or(page.locator('[data-test-id="reception-entry-card"]').filter({ hasText: selectedId }))
      .first();
    if (!(await isVisible(card, 10000))) {
      await page.screenshot({ path: 'artifacts/webclient/debug/20260211T041852Z-fullflow/orca-send-verify-card-missing.png', fullPage: true });
      throw new Error('reception card not found');
    }

    const cardText = (await card.innerText()).trim();
    log('reception-card', cardText.replace(/\s+/g, ' '));
    if (!/ORCA送信:\s*成功/.test(cardText)) {
      await page.screenshot({ path: 'artifacts/webclient/debug/20260211T041852Z-fullflow/orca-send-verify-card-failed.png', fullPage: true });
      throw new Error(`reception card missing success: ${cardText}`);
    }

    await page.screenshot({ path: 'artifacts/webclient/debug/20260211T041852Z-fullflow/orca-send-verify-success.png', fullPage: true });
    log('completed');
  } finally {
    await context.close();
    await browser.close();
  }
};

main().catch((error) => {
  console.error(new Date().toISOString(), 'FATAL', error);
  process.exit(1);
});
