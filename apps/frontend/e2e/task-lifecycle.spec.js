// Core journey: complete, edit, and delete a task through the real UI,
// with the API as ground truth at every step. Seeds via the API (fast),
// exercises the actions via the browser (the thing under test).
const { test, expect } = require('@playwright/test');
const { getTesterToken, signInAsTester, apiTasks, apiCreateTask, apiDeleteByTitle } = require('./helpers');

const MARK = `PW-life-${Date.now()}`;
let token;

test.beforeAll(async ({ request }) => { token = await getTesterToken(request); });
test.afterAll(async ({ request }) => { await apiDeleteByTitle(request, token, 'PW-life-'); });

test('a task can be completed from the list', async ({ page, request }) => {
  const t = await apiCreateTask(request, token, `${MARK} walk the dog`);
  await signInAsTester(page, token);
  const row = page.locator(`.task-row[data-task-id="${t.id}"]`);
  await expect(row).toBeVisible({ timeout: 90_000 });

  await row.locator('.task-checkbox').click();   // controlled input: state flips after the API round-trip
  await expect(row).toHaveClass(/completed-task/, { timeout: 30_000 });

  await expect.poll(async () => {
    const all = await apiTasks(request, token);
    return all.find(x => x.id === t.id)?.completed;
  }, { timeout: 30_000 }).toBe(true);
});

test('a task title can be edited through the modal', async ({ page, request }) => {
  const t = await apiCreateTask(request, token, `${MARK} original title`);
  await signInAsTester(page, token);
  const row = page.locator(`.task-row[data-task-id="${t.id}"]`);
  await expect(row).toBeVisible({ timeout: 90_000 });

  await row.locator('.edit-btn').click();
  const modal = page.locator('.modal-content');
  await expect(modal).toBeVisible();
  const titleInput = modal.locator('input[type="text"]').first();
  await titleInput.fill(`${MARK} renamed title`);
  await modal.locator('.modal-btn-save').click();

  await expect(row).toContainText('renamed title', { timeout: 30_000 });
  await expect.poll(async () => {
    const all = await apiTasks(request, token);
    return all.find(x => x.id === t.id)?.title;
  }, { timeout: 30_000 }).toContain('renamed title');
});

test('a task can be deleted from the list', async ({ page, request }) => {
  const t = await apiCreateTask(request, token, `${MARK} delete me`);
  await signInAsTester(page, token);
  const row = page.locator(`.task-row[data-task-id="${t.id}"]`);
  await expect(row).toBeVisible({ timeout: 90_000 });

  await row.locator('.delete-btn').click();
  await expect(row).toHaveCount(0, { timeout: 30_000 });

  await expect.poll(async () => {
    const all = await apiTasks(request, token);
    return all.some(x => x.id === t.id);
  }, { timeout: 30_000 }).toBe(false);
});
