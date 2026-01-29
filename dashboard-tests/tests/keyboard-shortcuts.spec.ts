import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

test.describe('Keyboard Shortcuts', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
  });

  test('Ctrl+Enter executes loaded script', async () => {
    await dashboard.fileBrowser.expectLoaded();

    // Select and load a script
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    // Fill input
    await dashboard.scriptRunner.fillInput('message', 'shortcut test');

    // Execute with Ctrl+Enter
    await dashboard.scriptRunner.runWithKeyboard();

    // Should produce output
    const output = await dashboard.scriptRunner.waitForOutput();
    expect(output.toUpperCase()).toContain('SHORTCUT TEST');
  });

  test('Ctrl+Enter does nothing without loaded script (no errors)', async () => {
    const errors = dashboard.collectConsoleErrors();

    // Press Ctrl+Enter without any script loaded
    await dashboard.scriptRunner.runWithKeyboard();

    // Wait a moment to see if any errors occur
    await dashboard.page.waitForTimeout(500);

    // Filter out non-critical errors
    const criticalErrors = errors.filter(
      e => !e.includes('favicon') && !e.includes('net::')
    );
    expect(criticalErrors).toHaveLength(0);

    // Run button should still be disabled
    await dashboard.scriptRunner.expectRunDisabled();
  });
});
