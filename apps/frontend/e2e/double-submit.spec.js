// The acceptance test for the double-submit guard (App.js).
// EXPECTED TO FAIL until the guard lands: two immediate clicks on
// "Add Task" must produce exactly ONE task. Manual testing cannot
// reproduce this timing reliably; a browser test can, every run.
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester, apiTasks, apiDeleteByTitle } = require('./helpers');

const MARK = `PW-dbl-${Date.now()}`;

test('two rapid clicks on Add Task create exactly one task', async ({ page, request }) => {
  const token = await getTesterToken(request);
  await signInAsTester(page, token);
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });

  await page.locator('input.task-input').fill(`${MARK} pay the water bill`);

  const btn = page.locator('button.add-btn');
  await btn.click();
  await btn.click({ force: true }); // second click lands before any UI reaction

  // First: wait for the create to land at all (slow backends get up to 20s).
  await expect.poll(async () =>
    (await apiTasks(request, token)).filter(t => t.title.includes(MARK)).length,
    { timeout: 20_000 }
  ).toBeGreaterThan(0);

  // Then: a settle window for any would-be duplicate, and the real assertion.
  await page.waitForTimeout(3000);
  const mine = (await apiTasks(request, token)).filter(t => t.title.includes(MARK));
  expect(mine, 'a double-click must not create duplicate tasks').toHaveLength(1);
});

test.afterAll(async ({ request }) => {
  const token = await getTesterToken(request);
  await apiDeleteByTitle(request, token, 'PW-dbl-');
});
