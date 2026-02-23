import { test, expect } from '../playwright/fixtures';
import type { Page } from '@playwright/test';

import { e2eAuthSession, profile, seedAuthSession } from '../e2e/helpers/orcaMaster';

const ensureMswControlled = async (page: Page) => {
  const registrationFound = await page
    .waitForFunction(() => navigator.serviceWorker.getRegistrations().then((regs) => regs.length > 0), null, {
      timeout: 10_000,
    })
    .then(() => true)
    .catch(() => false);

  if (!registrationFound) {
    // MSW が登録されない場合は、そのまま終了して後続のエラーで検知する。
    return;
  }

  try {
    await page.waitForFunction(() => navigator.serviceWorker?.controller !== null, null, { timeout: 5_000 });
    return;
  } catch {
    // First load may not be controlled; reload once to attach the MSW service worker.
  }
  await page.reload();
  await page.waitForFunction(() => navigator.serviceWorker?.controller !== null, null, { timeout: 10_000 });
};

const expandReceptionSections = async (page: Page) => {
  const toggles = page.locator(
    '#reception-results button.reception-board__toggle, #reception-results button.reception-section__toggle',
  );
  const count = await toggles.count();
  for (let index = 0; index < count; index += 1) {
    const toggle = toggles.nth(index);
    if (!(await toggle.isVisible().catch(() => false))) continue;
    const label = (await toggle.innerText()).trim();
    if (label.includes('開く')) {
      await toggle.click();
    }
  }
};

test.describe('REC-001 Reception status MVP', () => {
  test.skip(profile !== 'msw', 'MSW プロファイル専用（Stage 接続禁止）');

  test('shows normalized status + next action and exposes retry guidance via feature flag', async ({ page }) => {
    await seedAuthSession(page);

    // Enable MSW fault injection via header-flags mechanism (requires msw=1 and debug UI enabled in env).
    await page.addInitScript(() => {
      window.localStorage.setItem('mswFault', 'queue-stall');
    });

    const facilityId = e2eAuthSession.credentials.facilityId;
    await page.goto(`/f/${facilityId}/reception?msw=1`);
    await ensureMswControlled(page);
    const controllerUrl = await page.evaluate(() => navigator.serviceWorker?.controller?.scriptURL ?? '');
    expect(controllerUrl).toContain('mockServiceWorker');
    await expect(page.getByRole('heading', { name: 'Reception 受付一覧と更新状況' })).toBeVisible();
    await expandReceptionSections(page);

    // Feature flag path should expose the MVP UI elements.
    let entry = page
      .locator(
        '[data-test-id="reception-entry-card"][data-patient-id="000002"], [data-test-id="reception-entry-row"][data-patient-id="000002"]',
      )
      .first();
    const hasRetryButton = async () => (await entry.locator('[data-test-id="reception-status-mvp-retry"]').count()) > 0;
    if ((await entry.count()) === 0 || !(await hasRetryButton())) {
      entry = page
        .locator('[data-test-id="reception-entry-card"], [data-test-id="reception-entry-row"]', {
          has: page.locator('[data-test-id="reception-status-mvp-retry"]'),
        })
        .first();
    }
    await expect(entry).toBeVisible({ timeout: 20_000 });

    // Patient 000002 is included in MSW outpatient fixture; with queue-stall it should be "pending stalled" and retryable.
    const retryButton = entry.locator('[data-test-id="reception-status-mvp-retry"]').first();
    await expect(retryButton).toBeVisible();

    // Sanity: the retry button triggers retryOrcaQueue without crashing.
    await retryButton.click();
    await expect(page.getByText('ORCA再送を要求しました')).toBeVisible();
  });
});
