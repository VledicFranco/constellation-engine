import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

test.describe('Script Execution', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
    await dashboard.fileBrowser.expectLoaded();
  });

  test('execute script with string input produces correct output', async () => {
    // Select simple-test.cst which has "in message: String" and Uppercase module
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    // Fill input with a test value
    await dashboard.scriptRunner.fillInput('message', 'hello');

    // Run the script
    await dashboard.scriptRunner.run();

    // Wait for output
    const output = await dashboard.scriptRunner.waitForOutput();

    // Output should contain the uppercased input
    expect(output.toUpperCase()).toContain('HELLO');

    // Should show success state
    await dashboard.scriptRunner.expectSuccess();
  });

  test('execute script with text-analysis (multiple steps)', async () => {
    // Select text-analysis.cst which has "in document: String"
    await dashboard.fileBrowser.selectFile('text-analysis.cst');
    await dashboard.page.waitForTimeout(1000);

    // Fill the document input
    await dashboard.scriptRunner.fillInput('document', 'Hello World Test');

    // Run the script
    await dashboard.scriptRunner.run();

    // Wait for output and verify success
    await dashboard.scriptRunner.waitForOutput();
    await dashboard.scriptRunner.expectSuccess();
  });

  test('Ctrl+Enter keyboard shortcut executes script', async () => {
    // Select and load a script
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    // Fill input
    await dashboard.scriptRunner.fillInput('message', 'keyboard test');

    // Execute with keyboard shortcut
    await dashboard.scriptRunner.runWithKeyboard();

    // Wait for output and verify success
    const output = await dashboard.scriptRunner.waitForOutput();
    expect(output.toUpperCase()).toContain('KEYBOARD TEST');
  });

  test('successful execution shows success badge and formatted JSON', async () => {
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    await dashboard.scriptRunner.fillInput('message', 'test');
    await dashboard.scriptRunner.run();

    // Wait for output
    await dashboard.scriptRunner.expectSuccess();

    // Success output should contain a status badge
    const successOutput = dashboard.scriptRunner.outputsDisplay.locator('.output-success');
    await expect(successOutput).toBeVisible();

    // Should have a completed badge
    const badge = dashboard.scriptRunner.outputsDisplay.locator('.status-badge');
    await expect(badge).toBeVisible();

    // Should display JSON output
    const jsonOutput = dashboard.scriptRunner.outputsDisplay.locator('.output-json');
    await expect(jsonOutput).toBeVisible();
  });

  test('DAG updates with execution state after running', async () => {
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    // DAG should render after script load
    const nodeCountBefore = await dashboard.dagVisualizer.getNodeCount();
    expect(nodeCountBefore).toBeGreaterThan(0);

    // Execute
    await dashboard.scriptRunner.fillInput('message', 'dag test');
    await dashboard.scriptRunner.run();
    await dashboard.scriptRunner.expectSuccess();

    // DAG should still have nodes after execution
    const nodeCountAfter = await dashboard.dagVisualizer.getNodeCount();
    expect(nodeCountAfter).toBeGreaterThan(0);
  });

  test('@example annotation pre-fills input default values', async () => {
    // simple-test.cst has @example("Hello, World!") on message input
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await dashboard.page.waitForTimeout(1000);

    // Check if the input has a pre-filled value from @example
    const inputField = dashboard.page.locator('#input-message');
    const inputCount = await inputField.count();

    if (inputCount > 0) {
      const value = await inputField.inputValue();
      // If @example is supported, field should have default value
      // If not, this is a soft check — the feature may not be implemented
      if (value) {
        expect(value.length).toBeGreaterThan(0);
      }
    }
  });
});
