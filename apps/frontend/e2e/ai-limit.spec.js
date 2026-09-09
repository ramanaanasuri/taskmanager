// The AI metering surface in the UI: the tester pseudo-user has no credit,
// so a ✨ parse attempt must surface the limit message and the plans modal —
// the browser-layer twin of the Karate 402 contract scenario. Deterministic:
// the gate refuses before any model call, so no AI quota is spent.
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester } = require('./helpers');

test('AI parse without credit shows the limit message and the plans modal', async ({ page, request }) => {
  const token = await getTesterToken(request);
  await signInAsTester(page, token);
  const aiBox = page.getByPlaceholder(/renew car insurance/);
  await expect(aiBox).toBeVisible({ timeout: 90_000 });

  await aiBox.fill('book dentist appointment tomorrow 3pm');
  await aiBox.press('Enter');

  await expect(page.getByText("You've used all AI requests in your plan"))
    .toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('💎 Premium Plans')).toBeVisible({ timeout: 30_000 });
});
