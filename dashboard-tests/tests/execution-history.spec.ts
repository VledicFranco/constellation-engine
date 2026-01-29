import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';
import { ApiClient } from '../helpers/api-client';
import { FIXTURE_SCRIPTS } from '../helpers/fixtures';

test.describe('Execution History', () => {
  let dashboard: DashboardPage;

  // Ensure at least one execution exists before tests
  test.beforeAll(async ({ request }) => {
    const api = new ApiClient(request);
    await api.executeScript(FIXTURE_SCRIPTS.SIMPLE_TEST, { message: 'history setup' });
  });

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
  });

  test('history view loads with entries', async () => {
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    const items = dashboard.history.items();
    const count = await items.count();
    expect(count).toBeGreaterThan(0);
  });

  test('history item shows metadata', async () => {
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    const firstItem = dashboard.history.items().first();
    await expect(firstItem).toBeVisible();

    // Should have a script name
    const scriptName = firstItem.locator('.script-name');
    await expect(scriptName).toBeVisible();

    // Should have a status badge
    const badge = firstItem.locator('.status-badge');
    await expect(badge).toBeVisible();
  });

  test('filter by script name narrows results', async () => {
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    const initialCount = await dashboard.history.items().count();

    // Filter by the full script path (backend uses Option.contains which requires exact match)
    await dashboard.history.filter('modules/example-app/examples/simple-test.cst');

    const filteredCount = await dashboard.history.items().count();

    // Filtered count should be <= initial count
    expect(filteredCount).toBeLessThanOrEqual(initialCount);
    // And should still have at least one result (we executed simple-test in beforeAll)
    expect(filteredCount).toBeGreaterThan(0);
  });

  test('clearing filter restores all entries', async () => {
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    const initialCount = await dashboard.history.items().count();

    // Apply filter (full path needed — backend uses Option.contains for exact match)
    await dashboard.history.filter('modules/example-app/examples/simple-test.cst');
    const filteredCount = await dashboard.history.items().count();

    // Clear filter
    await dashboard.history.filter('');

    const restoredCount = await dashboard.history.items().count();

    // Restored count should match initial
    expect(restoredCount).toBeGreaterThanOrEqual(initialCount);
  });

  test('selecting execution shows detail panel', async () => {
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    const items = dashboard.history.items();
    const count = await items.count();
    expect(count).toBeGreaterThan(0);

    // Select first execution
    await dashboard.history.selectItem(0);
    await dashboard.page.waitForTimeout(1000);

    // Detail panel should load
    await dashboard.history.expectDetailLoaded();

    // Should show a status
    const status = await dashboard.history.getDetailStatus();
    expect(status.length).toBeGreaterThan(0);
  });

  test('execution detail shows execution information', async () => {
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    // Select first execution
    await dashboard.history.selectItem(0);
    await dashboard.page.waitForTimeout(1000);

    // Detail should contain execution info sections
    const detail = dashboard.history.executionDetail;
    const sections = detail.locator('.detail-section');
    const sectionCount = await sections.count();
    expect(sectionCount).toBeGreaterThan(0);
  });
});
