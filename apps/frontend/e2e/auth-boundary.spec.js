// The logged-out boundary: with no token, the app must show the sign-in
// view and must NOT render the task workspace.
const { test, expect } = require('@playwright/test');

test('without a token the app shows the sign-in view, not the workspace', async ({ page }) => {
  await page.goto('/');   // no token seeded — anonymous visitor
  await expect(page.locator('.google-btn')).toBeVisible({ timeout: 90_000 });
  await expect(page.locator('form.task-form')).toHaveCount(0);
});
