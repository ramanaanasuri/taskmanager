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

  // Give both requests time to land, then judge by the API (authoritative).
  await page.waitForTimeout(4000);
  const mine = (await apiTasks(request, token)).filter(t => t.title.includes(MARK));
  expect(mine, 'a double-click must not create duplicate tasks').toHaveLength(1);
});

test.afterAll(async ({ request }) => {
  const token = await getTesterToken(request);
  await apiDeleteByTitle(request, token, 'PW-dbl-');
});
