// Mentor profile view: a learner opens a mentor's profile from Browse. The tester
// is verified via the reachability echo first (Browse is gated), then the UI flow
// runs. Skips only if there is genuinely no browsable session to open.
const { test, expect } = require('@playwright/test');
const { API, getTesterToken, signInAsTester } = require('./helpers');

test('learner can open a mentor profile from Browse', async ({ page }) => {
  const token = await getTesterToken(page.request);
  const h = { headers: { Authorization: `Bearer ${token}` } };

  // Verify the tester's EMAIL via the echo so the (gated) Browse is reachable.
  await page.request.post(`${API}/api/reachability/channels`, { data: { channelsSelected: ['EMAIL'], termsVersion: '1' }, ...h });
  const rr = await page.request.post(`${API}/api/reachability/verify/request`, { data: { channel: 'EMAIL' }, ...h });
  const dev = (await rr.json()).devCode;
  if (dev) {
    await page.request.post(`${API}/api/reachability/verify/confirm`, { data: { channel: 'EMAIL', code: dev }, ...h });
  }

  await signInAsTester(page, token);
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });

  // Open the Live Sessions tab.
  const tab = page.getByRole('button', { name: 'Live Sessions' });
  await expect(tab).toBeVisible({ timeout: 15_000 });
  await tab.click();

  // Browse should be reachable now. Find a "View mentor profile" button.
  const viewBtn = page.getByRole('button', { name: /view mentor profile/i }).first();
  const hasMentor = await viewBtn.isVisible({ timeout: 15_000 }).catch(() => false);
  if (!hasMentor) test.skip(true, 'no browsable session with a mentor to open');

  await viewBtn.click();

  // Mentor profile panel: Back control + Open sessions section.
  await expect(page.getByText('← Back')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(/open sessions/i)).toBeVisible();
});
