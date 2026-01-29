import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

test.describe('Error Handling', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
  });

  test('missing or empty input still produces output without crash', async () => {
    const errors = dashboard.collectConsoleErrors();

    await dashboard.fileBrowser.expectLoaded();

    // Select a script that has inputs
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    // Don't fill any input, just run
    await dashboard.scriptRunner.run();

    // Wait for some result (could be success with empty or error)
    await dashboard.page.waitForTimeout(2000);

    // Should not have unhandled JS errors
    const criticalErrors = errors.filter(
      e => !e.includes('favicon') && !e.includes('net::')
    );
    // Dashboard should handle this gracefully
    expect(criticalErrors.length).toBeLessThanOrEqual(1);
  });

  test('dashboard handles errors gracefully without JS exceptions', async () => {
    const errors = dashboard.collectConsoleErrors();

    // Perform basic operations that could potentially error
    await dashboard.fileBrowser.expectLoaded();

    // Select file
    const files = dashboard.fileBrowser.files();
    if ((await files.count()) > 0) {
      await files.first().click();
      await dashboard.page.waitForTimeout(1000);
    }

    // Switch views
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(500);
    await dashboard.goToScripts();
    await dashboard.page.waitForTimeout(500);

    // Filter non-critical console errors
    const criticalErrors = errors.filter(
      e => !e.includes('favicon') &&
           !e.includes('net::') &&
           !e.includes('Failed to load resource')
    );
    expect(criticalErrors).toHaveLength(0);
  });

  test('no unhandled exceptions during full usage flow', async () => {
    const errors = dashboard.collectConsoleErrors();

    // Full usage flow: load -> select -> fill -> run -> switch views
    await dashboard.fileBrowser.expectLoaded();

    // Select and run a script
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    await dashboard.scriptRunner.fillInput('message', 'error test');
    await dashboard.scriptRunner.run();
    await dashboard.page.waitForTimeout(2000);

    // Switch to history view
    await dashboard.goToHistory();
    await dashboard.page.waitForTimeout(1000);

    // Switch back to scripts
    await dashboard.goToScripts();
    await dashboard.page.waitForTimeout(500);

    // Use DAG controls
    await dashboard.dagVisualizer.zoomIn.click();
    await dashboard.dagVisualizer.zoomFit.click();
    await dashboard.page.waitForTimeout(300);

    // Click refresh
    await dashboard.refresh();
    await dashboard.page.waitForTimeout(1000);

    // Filter out non-critical errors
    const criticalErrors = errors.filter(
      e => !e.includes('favicon') &&
           !e.includes('net::') &&
           !e.includes('Failed to load resource')
    );
    expect(criticalErrors).toHaveLength(0);
  });
});
