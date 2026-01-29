import { Page, Locator, expect } from '@playwright/test';

export class NodeDetailsPage {
  readonly page: Page;
  readonly panel: Locator;
  readonly closeButton: Locator;
  readonly content: Locator;

  constructor(page: Page) {
    this.page = page;
    this.panel = page.locator('#node-details-panel');
    this.closeButton = page.locator('#close-details');
    this.content = page.locator('#node-details');
  }

  /** Assert the node details panel is visible with node data */
  async expectVisible(): Promise<void> {
    await expect(this.panel).toBeVisible();
    await expect(this.content.locator('.detail-section').first()).toBeVisible();
  }

  /** Assert the node details panel is hidden */
  async expectHidden(): Promise<void> {
    await expect(this.panel).not.toBeVisible();
  }

  /** Close the details panel */
  async close(): Promise<void> {
    await this.closeButton.click();
  }

  /** Get the node label from the details panel */
  async getLabel(): Promise<string> {
    return (await this.content.locator('.node-label, h3').first().textContent()) ?? '';
  }

  /** Get the node kind from the details panel */
  async getKind(): Promise<string> {
    return (await this.content.locator('.node-kind-badge, .node-kind').first().textContent()) ?? '';
  }

  /** Get the node type from the details panel */
  async getType(): Promise<string> {
    return (await this.content.locator('.node-type').first().textContent()) ?? '';
  }
}
