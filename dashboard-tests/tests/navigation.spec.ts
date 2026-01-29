import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';
import { ApiClient } from '../helpers/api-client';

test.describe('Navigation', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
  });

  test('default view is Scripts with active tab', async () => {
    // Scripts tab should be active
    await expect(dashboard.scriptsTab).toHaveClass(/active/);

    // Scripts view should be visible
    const scriptsView = dashboard.page.locator('#scripts-view');
    await expect(scriptsView).toHaveClass(/active/);

    // History view should not be active
    const historyView = dashboard.page.locator('#history-view');
    await expect(historyView).not.toHaveClass(/active/);
  });

  test('switch to History view', async () => {
    await dashboard.goToHistory();

    // History tab should be active
    await expect(dashboard.historyTab).toHaveClass(/active/);

    // History view should be visible
    const historyView = dashboard.page.locator('#history-view');
    await expect(historyView).toHaveClass(/active/);

    // Scripts tab should not be active
    await expect(dashboard.scriptsTab).not.toHaveClass(/active/);
  });

  test('switch back to Scripts view', async () => {
    // Go to History first
    await dashboard.goToHistory();
    await expect(dashboard.historyTab).toHaveClass(/active/);

    // Switch back to Scripts
    await dashboard.goToScripts();

    // Scripts tab should be active
    await expect(dashboard.scriptsTab).toHaveClass(/active/);

    // Scripts view should be visible
    const scriptsView = dashboard.page.locator('#scripts-view');
    await expect(scriptsView).toHaveClass(/active/);
  });

  test('hash routing: #/history navigates to History view', async () => {
    // Navigate to hash route
    await dashboard.page.goto('/dashboard#/history');
    await dashboard.page.waitForTimeout(500);

    // History tab should be active
    await expect(dashboard.historyTab).toHaveClass(/active/);
  });

  test('deep link: #/executions/{id} shows execution detail', async ({ request }) => {
    // Create an execution via API to get an ID
    const api = new ApiClient(request);
    const result = await api.executeScript('simple-test.cst', { message: 'deep link test' });
    const executionId = (result as any)?.executionId ?? (result as any)?.id;

    if (executionId) {
      // Navigate to the deep link
      await dashboard.page.goto(`/dashboard#/executions/${executionId}`);
      await dashboard.page.waitForTimeout(1500);

      // History tab should be active
      await expect(dashboard.historyTab).toHaveClass(/active/);
    }
  });

  test('refresh button reloads data', async () => {
    await dashboard.fileBrowser.expectLoaded();

    // Click refresh
    await dashboard.refresh();
    await dashboard.page.waitForTimeout(1000);

    // File tree should still be loaded
    await dashboard.fileBrowser.expectLoaded();

    // Status should be ready
    await expect(dashboard.statusMessage).toBeVisible();
  });
});
