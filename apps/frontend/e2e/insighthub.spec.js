// InsightHub client experience: the tab is open to all authed users, a topic
// selector is present, and a client can ask a question. (Reviewer-only controls
// and per-topic gating are covered at the JUnit/API layer; a browser check of
// those needs a second identity + grant management, which lands with the console.)
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester } = require('./helpers');

async function openInsightHub(page) {
  const tab = page.getByRole('button', { name: /insighthub/i });
  await expect(tab).toBeVisible({ timeout: 20_000 });   // PW-01: tab visible to all
  await tab.click();
}

test('PW-01/02: InsightHub tab is visible and shows a topic selector', async ({ page }) => {
  const token = await getTesterToken(page.request);
  await signInAsTester(page, token);
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });

  await openInsightHub(page);

  // PW-02: topic selector present, with an Investing option
  const selector = page.locator('select').filter({ hasText: /investing/i }).first();
  await expect(selector).toBeVisible({ timeout: 20_000 });
});

test('PW-03: a client can ask a question', async ({ page }) => {
  const token = await getTesterToken(page.request);
  await signInAsTester(page, token);
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });

  await openInsightHub(page);

  const q = 'E2E: what is a covered call? ' + Date.now();
  const box = page.getByPlaceholder(/difference between a covered call/i);
  await expect(box).toBeVisible({ timeout: 20_000 });
  await box.fill(q);
  await page.getByRole('button', { name: /^ask$/i }).click();

  // the asked question shows up in the client's own list
  await expect(page.getByText(q, { exact: false })).toBeVisible({ timeout: 20_000 });
});

test('PW-04: removed strips are gone; ask flow retained', async ({ page }) => {
  const token = await getTesterToken(page.request);
  await signInAsTester(page, token);
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });

  await openInsightHub(page);

  // the vestigial onboarding UI is gone in every view (client or reviewer)
  await expect(page.getByText(/add a learner/i)).toHaveCount(0);
  await expect(page.getByText(/start a hub/i)).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^create$/i })).toHaveCount(0);

  // the retained ask flow is still present
  await expect(page.getByText(/ask a question/i).first()).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('button', { name: /^ask$/i })).toBeVisible();
});
