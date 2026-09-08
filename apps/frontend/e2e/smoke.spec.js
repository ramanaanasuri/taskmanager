// Day-one green: the app boots authenticated via the tester JWT,
// a task created through the real form appears, and cleanup works.
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester, apiTasks, apiDeleteByTitle } = require('./helpers');

const MARK = `PW-smoke-${Date.now()}`;

test('boots authenticated and creates one task through the form', async ({ page, request }) => {
  const token = await getTesterToken(request);
  await signInAsTester(page, token);

  // Authenticated shell rendered: the create form is on screen.
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });

  await page.locator('input.task-input').fill(`${MARK} water the plants`);
  await page.locator('button.add-btn').click();

  // The new task shows up in the rendered list…
  await expect(page.locator(`.task-row:has-text("${MARK}")`)).toHaveCount(1, { timeout: 60_000 });
  // …and the API agrees.
  const mine = (await apiTasks(request, token)).filter(t => t.title.includes(MARK));
  expect(mine).toHaveLength(1);

  await apiDeleteByTitle(request, token, MARK);
});

test.afterAll(async ({ request }) => {
  const token = await getTesterToken(request);
  await apiDeleteByTitle(request, token, 'PW-smoke-');
});
