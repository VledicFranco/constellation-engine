import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

test.describe('Smoke Tests', () => {
  test('dashboard loads without JavaScript errors', async ({ page }) => {
    const dashboard = new DashboardPage(page);
    const errors = dashboard.collectConsoleErrors();

    await dashboard.goto();
    await dashboard.expectReady();

    // Filter out non-critical errors (favicon, external CDN, 404 for static assets)
    const criticalErrors = errors.filter(
      e => !e.includes('favicon') &&
           !e.includes('net::') &&
           !e.includes('404') &&  // Static asset 404s are not critical
           !e.includes('Failed to load resource')
    );
    expect(criticalErrors).toHaveLength(0);
  });

  test('health endpoint returns OK', async ({ request }) => {
    const res = await request.get('/health');
    expect(res.ok()).toBeTruthy();
  });

  test('files API endpoint returns data', async ({ request }) => {
    const res = await request.get('/api/v1/files');
    expect(res.ok()).toBeTruthy();
    const data = await res.json();
    expect(data.files).toBeDefined();
    expect(Array.isArray(data.files)).toBeTruthy();
  });

  test('dashboard has correct HTML title', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveTitle('Constellation Dashboard');
  });

  test('status endpoint returns data', async ({ request }) => {
    const res = await request.get('/api/v1/status');
    expect(res.ok()).toBeTruthy();
    const data = await res.json();
    expect(data).toBeDefined();
  });

  test('navigation elements are visible on load', async ({ page }) => {
    const dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();

    // Scripts tab is visible and active
    await expect(dashboard.scriptsTab).toBeVisible();
    await expect(dashboard.scriptsTab).toHaveClass(/active/);

    // History tab is visible
    await expect(dashboard.historyTab).toBeVisible();

    // Refresh button is visible
    await expect(dashboard.refreshButton).toBeVisible();

    // Status bar elements are visible
    await expect(dashboard.statusMessage).toBeVisible();
    await expect(dashboard.connectionStatus).toBeVisible();

    // File browser panel is visible
    await expect(dashboard.fileBrowser.panel).toBeVisible();
  });
});
