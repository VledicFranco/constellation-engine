import { Page, Locator } from '@playwright/test';

export class DagVisualizerPage {
  readonly page: Page;
  readonly canvas: Locator;
  readonly layoutTB: Locator;
  readonly layoutLR: Locator;
  readonly zoomIn: Locator;
  readonly zoomOut: Locator;
  readonly zoomFit: Locator;

  constructor(page: Page) {
    this.page = page;
    this.canvas = page.locator('#dag-canvas');
    this.layoutTB = page.locator('#layout-tb');
    this.layoutLR = page.locator('#layout-lr');
    this.zoomIn = page.locator('#zoom-in');
    this.zoomOut = page.locator('#zoom-out');
    this.zoomFit = page.locator('#zoom-fit');
  }

  /** Wait for the Cytoscape canvas to render nodes */
  async expectRendered(): Promise<void> {
    await this.canvas.locator('canvas').waitFor();
  }

  /** Get the number of nodes rendered via Cytoscape API */
  async getNodeCount(): Promise<number> {
    return this.page.evaluate(() => {
      return (window as any).dashboard?.dagVisualizer?.cy?.nodes()?.length ?? 0;
    });
  }

  /** Get the number of edges rendered */
  async getEdgeCount(): Promise<number> {
    return this.page.evaluate(() => {
      return (window as any).dashboard?.dagVisualizer?.cy?.edges()?.length ?? 0;
    });
  }

  /** Click a DAG node by its label (via Cytoscape API) */
  async clickNode(label: string): Promise<void> {
    await this.page.evaluate((lbl) => {
      const cy = (window as any).dashboard?.dagVisualizer?.cy;
      const node = cy?.nodes().filter(`[label = "${lbl}"]`).first();
      if (node) node.emit('tap');
    }, label);
  }

  /** Click the canvas background to deselect */
  async clickBackground(): Promise<void> {
    await this.canvas.click({ position: { x: 5, y: 5 } });
  }

  /** Switch to Top-to-Bottom layout */
  async setLayoutTB(): Promise<void> {
    await this.layoutTB.click();
  }

  /** Switch to Left-to-Right layout */
  async setLayoutLR(): Promise<void> {
    await this.layoutLR.click();
  }

  /** Get the current zoom level */
  async getZoomLevel(): Promise<number> {
    return this.page.evaluate(() => {
      return (window as any).dashboard?.dagVisualizer?.cy?.zoom() ?? 1;
    });
  }

  /** Take a screenshot of just the DAG canvas */
  async screenshot(name: string): Promise<Buffer> {
    return this.canvas.screenshot({ path: `reports/screenshots/${name}.png` });
  }
}
