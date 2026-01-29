import { defineConfig, devices } from '@playwright/test';

const PORT = process.env.CONSTELLATION_PORT || '8080';
const BASE_URL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  workers: 1, // Sequential — tests share a server
  fullyParallel: false,

  reporter: [
    ['html', { outputFolder: 'reports', open: 'never' }],
    ['json', { outputFile: 'reports/results.json' }],
    ['list'],
  ],

  use: {
    baseURL: BASE_URL,
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
    timeout: 60_000,
  },
});
