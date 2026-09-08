// Shared helpers: tester authentication + task API access + cleanup.
const API = process.env.PW_API_URL || 'https://api-taskmanager.gcp.sriinfosoft.com';

/** Mint a real JWT from the tester door (same mechanism as the Karate suite). */
async function getTesterToken(request) {
  const res = await request.post(`${API}/api/tester/auth`, {
    data: {
      username: process.env.PW_TESTER_USER || 'admin',
      password: process.env.PW_TESTER_PASSWORD || '',
    },
  });
  if (!res.ok()) throw new Error(`tester auth failed: ${res.status()}`);
  return (await res.json()).token;
}

/** Seed the token the way the app reads it, then load the page authenticated. */
async function signInAsTester(page, token) {
  await page.addInitScript(t => localStorage.setItem('jwt_token', t), token);
  await page.goto('/');
}

/** Authoritative task list straight from the API. */
async function apiTasks(request, token) {
  const res = await request.get(`${API}/api/tasks`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) throw new Error(`list failed: ${res.status()}`);
  return res.json();
}

/** Delete every task whose title contains the marker (self-cleaning suites). */
async function apiDeleteByTitle(request, token, marker) {
  const tasks = await apiTasks(request, token);
  for (const t of tasks.filter(t => t.title && t.title.includes(marker))) {
    await request.delete(`${API}/api/tasks/${t.id}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  }
}

module.exports = { API, getTesterToken, signInAsTester, apiTasks, apiDeleteByTitle };
