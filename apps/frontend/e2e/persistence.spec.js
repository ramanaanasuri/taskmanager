// Reload survival: a created task must still be there after a full page
// reload — proving the auth re-boot and list refetch cycle end to end.
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester, apiCreateTask, apiDeleteByTitle } = require('./helpers');

const MARK = `PW-persist-${Date.now()}`;

test('a task survives a full page reload', async ({ page, request }) => {
  const token = await getTesterToken(request);
  const t = await apiCreateTask(request, token, `${MARK} still here`);
  await signInAsTester(page, token);
  const row = page.locator(`.task-row[data-task-id="${t.id}"]`);
  await expect(row).toBeVisible({ timeout: 90_000 });

  await page.reload();
  await expect(page.locator(`.task-row[data-task-id="${t.id}"]`)).toBeVisible({ timeout: 90_000 });

  await apiDeleteByTitle(request, token, 'PW-persist-');
});
