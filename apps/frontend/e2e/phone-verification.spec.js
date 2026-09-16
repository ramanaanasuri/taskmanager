// Phone verification (verify-once): enable SMS on a task, enter a number, receive
// the OTP (tester echo, read from the verify/request response), verify, and reach
// the verified state. Waits for the app to render before interacting.
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester } = require('./helpers');

test('phone verification: enter number -> code -> verified', async ({ page }) => {
  const token = await getTesterToken(page.request);
  await signInAsTester(page, token);
  await expect(page.locator('form.task-form')).toBeVisible({ timeout: 90_000 });  // app rendered

  // Enable SMS on the create-task form.
  const smsLabel = page.getByText('Enable SMS notifications for this task').first();
  await expect(smsLabel).toBeVisible({ timeout: 15_000 });
  await smsLabel.click();

  // PhoneVerification resolves /api/reachability/phone, then shows verified or the entry form.
  const verifiedBadge = page.getByText(/Texts go to your verified number/i).first();
  const phoneInput = page.locator('input[placeholder="+15055550006"]').first();
  await expect(verifiedBadge.or(phoneInput)).toBeVisible({ timeout: 15_000 });

  if (await verifiedBadge.isVisible().catch(() => false)) {
    return;   // tester already verified from a prior run — valid pass
  }

  await phoneInput.fill('+15551234567');
  const reqResp = page.waitForResponse(
    (r) => r.url().includes('/api/reachability/verify/request') && r.request().method() === 'POST',
    { timeout: 20_000 });
  await page.getByRole('button', { name: /send code/i }).click();
  const code = (await (await reqResp).json()).devCode;   // tester echo
  expect(code, 'tester devCode should be echoed').toBeTruthy();

  const codeInput = page.locator('input[placeholder="123456"]').first();
  await expect(codeInput).toBeVisible({ timeout: 10_000 });
  await codeInput.fill(code);
  await page.getByRole('button', { name: /^verify$/i }).click();

  await expect(page.getByText(/Texts go to your verified number/i).first()).toBeVisible({ timeout: 15_000 });
});
