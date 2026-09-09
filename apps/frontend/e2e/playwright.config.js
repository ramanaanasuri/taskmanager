// Browser-layer tests for Task Manager. Self-contained: run via scripts/run-e2e.sh.
// Targets a DEPLOYED environment (gcp default, aws via env) — the frontend always
// calls the public API (config.js), so tests do too.
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: '.',
  timeout: 180_000,   // small-VM headroom: app mount alone can take 30s+ under memory pressure
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'report' }]],
  use: {
    baseURL: process.env.PW_BASE_URL || 'https://taskmanager.gcp.sriinfosoft.com',
    navigationTimeout: 90_000,
    screenshot: 'on',      // final-state screenshot for every test, pass or fail
    trace: 'on',           // full film strip per test: every action with before/after screens
  },
});
