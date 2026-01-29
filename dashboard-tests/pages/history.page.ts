import { Page, Locator } from '@playwright/test';

export class HistoryPage {
  readonly page: Page;
  readonly filterInput: Locator;
  readonly historyList: Locator;
  readonly executionDetail: Locator;

  constructor(page: Page) {
    this.page = page;
    this.filterInput = page.locator('#history-filter');
    this.historyList = page.locator('#history-list');
    this.executionDetail = page.locator('#execution-detail');
  }

  /** Get all history items */
  items(): Locator {
    return this.historyList.locator('.history-item');
  }

  /** Click a history item by index */
  async selectItem(index: number): Promise<void> {
    await this.items().nth(index).click();
  }

  /** Filter history by script name */
  async filter(text: string): Promise<void> {
    await this.filterInput.fill(text);
    // Wait for debounce (300ms in dashboard code)
    await this.page.waitForTimeout(400);
  }

  /** Assert the execution detail panel shows content */
  async expectDetailLoaded(): Promise<void> {
    await this.executionDetail.locator('.execution-info').waitFor();
  }

  /** Get the status badge text from the detail panel */
  async getDetailStatus(): Promise<string> {
    return (await this.executionDetail.locator('.status-badge').first().textContent()) ?? '';
  }
}
