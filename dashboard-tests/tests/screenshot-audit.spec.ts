/**
 * DAG Visual Audit - Captures screenshots of the dashboard DAG in various states
 * for UX evaluation. Run with: npx playwright test screenshot-audit.ts
 */
import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';
import { ApiClient } from '../helpers/api-client';
import { FIXTURE_SCRIPTS } from '../helpers/fixtures';

test.describe('DAG Visual Audit', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
  });

  test('1-simple-script-dag', async ({ page }) => {
    // Load simple-test.cst (small DAG)
    await dashboard.fileBrowser.expandAllFolders();
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await page.waitForTimeout(2000);

    // Screenshot the full page
    await page.screenshot({ path: 'screenshots/01-simple-dag-full.png', fullPage: true });

    // Screenshot just the DAG area
    const dagCanvas = page.locator('#dag-canvas');
    await dagCanvas.screenshot({ path: 'screenshots/02-simple-dag-canvas.png' });

    // Execute and screenshot with execution state
    await dashboard.scriptRunner.fillInput('message', 'Hello World');
    await dashboard.scriptRunner.run();
    await dashboard.scriptRunner.waitForOutput();
    await page.waitForTimeout(1000);
    await dagCanvas.screenshot({ path: 'screenshots/03-simple-dag-executed.png' });
  });

  test('2-complex-script-dag', async ({ page }) => {
    // Load text-analysis.cst (more complex DAG)
    await dashboard.fileBrowser.expandAllFolders();
    await dashboard.fileBrowser.selectFile('text-analysis.cst');
    await page.waitForTimeout(2000);

    const dagCanvas = page.locator('#dag-canvas');
    await dagCanvas.screenshot({ path: 'screenshots/04-complex-dag-tb.png' });

    // Switch to LR layout
    await page.click('#layout-lr');
    await page.waitForTimeout(500);
    await dagCanvas.screenshot({ path: 'screenshots/05-complex-dag-lr.png' });

    // Switch back to TB
    await page.click('#layout-tb');
    await page.waitForTimeout(500);
  });

  test('3-node-details-interaction', async ({ page }) => {
    await dashboard.fileBrowser.expandAllFolders();
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await page.waitForTimeout(2000);

    // Click a node to show details
    const nodeCount = await page.evaluate(() => {
      const cy = (window as any).dashboard.dagVisualizer.cy;
      return cy.nodes().length;
    });

    if (nodeCount > 0) {
      // Click the first operation node
      await page.evaluate(() => {
        const cy = (window as any).dashboard.dagVisualizer.cy;
        const opNode = cy.nodes().filter('[kind = "Operation"]').first();
        if (opNode.length > 0) {
          opNode.emit('tap');
        } else {
          cy.nodes().first().emit('tap');
        }
      });
      await page.waitForTimeout(500);
      await page.screenshot({ path: 'screenshots/06-node-details-open.png', fullPage: true });
    }
  });

  test('4-all-example-scripts', async ({ page }) => {
    await dashboard.fileBrowser.expandAllFolders();

    // Get all file items
    const files = dashboard.fileBrowser.files();
    const fileCount = await files.count();

    for (let i = 0; i < Math.min(fileCount, 8); i++) {
      const file = files.nth(i);
      const fileName = await file.textContent() || `file-${i}`;
      const safeName = fileName.trim().replace(/[^a-zA-Z0-9-_.]/g, '_');

      await file.click();
      await page.waitForTimeout(2000);

      const dagCanvas = page.locator('#dag-canvas');
      await dagCanvas.screenshot({ path: `screenshots/07-dag-${safeName}.png` });
    }
  });

  test('5-dag-zoom-levels', async ({ page }) => {
    await dashboard.fileBrowser.expandAllFolders();
    await dashboard.fileBrowser.selectFile('text-analysis.cst');
    await page.waitForTimeout(2000);

    const dagCanvas = page.locator('#dag-canvas');

    // Default zoom
    await dagCanvas.screenshot({ path: 'screenshots/08-zoom-default.png' });

    // Zoom in 3x
    await page.click('#zoom-in');
    await page.click('#zoom-in');
    await page.click('#zoom-in');
    await page.waitForTimeout(300);
    await dagCanvas.screenshot({ path: 'screenshots/09-zoom-in.png' });

    // Zoom fit
    await page.click('#zoom-fit');
    await page.waitForTimeout(300);
    await dagCanvas.screenshot({ path: 'screenshots/10-zoom-fit.png' });

    // Zoom out 3x
    await page.click('#zoom-out');
    await page.click('#zoom-out');
    await page.click('#zoom-out');
    await page.waitForTimeout(300);
    await dagCanvas.screenshot({ path: 'screenshots/11-zoom-out.png' });
  });

  test('6-executed-with-state-colors', async ({ page }) => {
    await dashboard.fileBrowser.expandAllFolders();
    await dashboard.fileBrowser.selectFile('text-analysis.cst');
    await page.waitForTimeout(2000);

    // Execute with input
    await dashboard.scriptRunner.fillInput('document', 'The quick brown fox jumps over the lazy dog. The fox is clever.');
    await dashboard.scriptRunner.run();
    await dashboard.scriptRunner.waitForOutput();
    await page.waitForTimeout(1000);

    const dagCanvas = page.locator('#dag-canvas');
    await dagCanvas.screenshot({ path: 'screenshots/12-text-analysis-executed.png' });

    // Full page with outputs visible
    await page.screenshot({ path: 'screenshots/13-text-analysis-full.png', fullPage: true });
  });

  test('7-branch-expressions-dag', async ({ page }) => {
    await dashboard.fileBrowser.expandAllFolders();

    // Try to load branch-expressions if it exists
    const branchFile = dashboard.fileBrowser.files().filter({ hasText: 'branch' });
    if (await branchFile.count() > 0) {
      await branchFile.first().click();
      await page.waitForTimeout(2000);

      const dagCanvas = page.locator('#dag-canvas');
      await dagCanvas.screenshot({ path: 'screenshots/14-branch-dag.png' });
    }
  });

  test('8-data-pipeline-dag', async ({ page }) => {
    await dashboard.fileBrowser.expandAllFolders();

    const pipelineFile = dashboard.fileBrowser.files().filter({ hasText: 'data-pipeline' });
    if (await pipelineFile.count() > 0) {
      await pipelineFile.first().click();
      await page.waitForTimeout(2000);

      const dagCanvas = page.locator('#dag-canvas');
      await dagCanvas.screenshot({ path: 'screenshots/15-data-pipeline-dag.png' });

      // Execute it
      await dashboard.scriptRunner.run();
      await dashboard.scriptRunner.waitForOutput();
      await page.waitForTimeout(1000);
      await dagCanvas.screenshot({ path: 'screenshots/16-data-pipeline-executed.png' });
    }
  });
});
