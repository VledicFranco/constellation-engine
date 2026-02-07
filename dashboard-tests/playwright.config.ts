import { defineConfig, devices } from '@playwright/test';

const PORT = process.env.CONSTELLATION_PORT || '8080';
const BASE_URL = `http://localhost:${PORT}`;

// In CI, run only smoke tests unless FULL_E2E=true
const ciTestMatch = process.env.FULL_E2E ? undefined : ['**/smoke.spec.ts'];

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0, // Reduced from 2 to speed up CI
  workers: 1, // Sequential — tests share a server
  fullyParallel: false,
  // In CI: run only smoke tests (fast); locally: run all except screenshot-audit
  testMatch: process.env.CI ? ciTestMatch : undefined,
  testIgnore: process.env.CI ? ['**/screenshot-audit.spec.ts'] : ['**/screenshot-audit.spec.ts'],

  reporter: [
    ['html', { outputFolder: 'reports', open: 'never' }],
    ['json', { outputFile: 'reports/results.json' }],
    ['list'],
  ],

  use: {
    baseURL: BASE_URL,
    headless: true, // Always run headless to avoid opening browser windows
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  // Server management: start Constellation before tests
  webServer: {
    command: process.platform === 'win32'
      ? `powershell -ExecutionPolicy Bypass -File ../scripts/dev.ps1 -ServerOnly -Port ${PORT}`
      : `make -C .. server`,
    port: parseInt(PORT),
    reuseExistingServer: !process.env.CI,
    timeout: 180_000, // 3 minutes for CI cold start (sbt download + compile)
  },
});
