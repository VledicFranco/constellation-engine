import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

test.describe('DAG Interaction', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
    await dashboard.fileBrowser.expectLoaded();

    // Select a script to load DAG
    await dashboard.fileBrowser.selectFile('simple-test.cst');
    await page.waitForTimeout(1500);
  });

  test('DAG renders on script load with nodes', async () => {
    const nodeCount = await dashboard.dagVisualizer.getNodeCount();
    expect(nodeCount).toBeGreaterThan(0);
  });

  test('DAG has edges connecting nodes', async () => {
    const edgeCount = await dashboard.dagVisualizer.getEdgeCount();
    expect(edgeCount).toBeGreaterThan(0);
  });

  test('clicking a node shows the details sidebar', async () => {
    // Get node labels to find a clickable node
    const nodeCount = await dashboard.dagVisualizer.getNodeCount();
    expect(nodeCount).toBeGreaterThan(0);

    // Click the first available node by getting its label
    const firstLabel = await dashboard.page.evaluate(() => {
      const cy = (window as any).dashboard?.dagVisualizer?.cy;
      const nodes = cy?.nodes();
      return nodes?.length > 0 ? nodes[0].data('label') : null;
    });

    if (firstLabel) {
      await dashboard.dagVisualizer.clickNode(firstLabel);
      await dashboard.page.waitForTimeout(500);

      // Node details panel should become visible
      await dashboard.nodeDetails.expectVisible();
    }
  });

  test('clicking canvas background hides details sidebar', async () => {
    // First, click a node to show details
    const firstLabel = await dashboard.page.evaluate(() => {
      const cy = (window as any).dashboard?.dagVisualizer?.cy;
      const nodes = cy?.nodes();
      return nodes?.length > 0 ? nodes[0].data('label') : null;
    });

    if (firstLabel) {
      await dashboard.dagVisualizer.clickNode(firstLabel);
      await dashboard.page.waitForTimeout(500);
      await dashboard.nodeDetails.expectVisible();

      // Click background to hide
      await dashboard.dagVisualizer.clickBackground();
      await dashboard.page.waitForTimeout(500);

      await dashboard.nodeDetails.expectHidden();
    }
  });

  test('toggle layout from TB to LR', async () => {
    // TB is the default active layout
    await expect(dashboard.dagVisualizer.layoutTB).toHaveClass(/active/);

    // Click LR
    await dashboard.dagVisualizer.setLayoutLR();
    await dashboard.page.waitForTimeout(500);

    // LR should now be active
    await expect(dashboard.dagVisualizer.layoutLR).toHaveClass(/active/);
  });

  test('toggle layout from LR back to TB', async () => {
    // Switch to LR first
    await dashboard.dagVisualizer.setLayoutLR();
    await dashboard.page.waitForTimeout(500);

    // Switch back to TB
    await dashboard.dagVisualizer.setLayoutTB();
    await dashboard.page.waitForTimeout(500);

    // TB should be active again
    await expect(dashboard.dagVisualizer.layoutTB).toHaveClass(/active/);
  });

  test('zoom in increases zoom level', async () => {
    const initialZoom = await dashboard.dagVisualizer.getZoomLevel();

    await dashboard.dagVisualizer.zoomIn.click();
    await dashboard.page.waitForTimeout(300);

    const newZoom = await dashboard.dagVisualizer.getZoomLevel();
    expect(newZoom).toBeGreaterThan(initialZoom);
  });

  test('zoom out decreases zoom level', async () => {
    const initialZoom = await dashboard.dagVisualizer.getZoomLevel();

    await dashboard.dagVisualizer.zoomOut.click();
    await dashboard.page.waitForTimeout(300);

    const newZoom = await dashboard.dagVisualizer.getZoomLevel();
    expect(newZoom).toBeLessThan(initialZoom);
  });

  test('zoom fit adjusts viewport', async () => {
    // Zoom in first to change the level
    await dashboard.dagVisualizer.zoomIn.click();
    await dashboard.dagVisualizer.zoomIn.click();
    await dashboard.page.waitForTimeout(300);

    const zoomedLevel = await dashboard.dagVisualizer.getZoomLevel();

    // Click fit
    await dashboard.dagVisualizer.zoomFit.click();
    await dashboard.page.waitForTimeout(300);

    const fitLevel = await dashboard.dagVisualizer.getZoomLevel();

    // Fit should adjust the zoom level (likely different from manual zoom)
    expect(fitLevel).not.toEqual(zoomedLevel);
  });

  test('close details button hides the panel', async () => {
    // Click a node to show details
    const firstLabel = await dashboard.page.evaluate(() => {
      const cy = (window as any).dashboard?.dagVisualizer?.cy;
      const nodes = cy?.nodes();
      return nodes?.length > 0 ? nodes[0].data('label') : null;
    });

    if (firstLabel) {
      await dashboard.dagVisualizer.clickNode(firstLabel);
      await dashboard.page.waitForTimeout(500);
      await dashboard.nodeDetails.expectVisible();

      // Click close button
      await dashboard.nodeDetails.close();
      await dashboard.page.waitForTimeout(300);

      await dashboard.nodeDetails.expectHidden();
    }
  });
});
