import { Page, Locator } from '@playwright/test';

export class FileBrowserPage {
  readonly page: Page;
  readonly panel: Locator;
  readonly fileTree: Locator;

  constructor(page: Page) {
    this.page = page;
    this.panel = page.locator('#file-browser-panel');
    this.fileTree = page.locator('#file-tree');
  }

  /** Get all visible folder elements */
  folders(): Locator {
    return this.fileTree.locator('.folder > .file-item');
  }

  /** Get all visible file elements */
  files(): Locator {
    return this.fileTree.locator('.file > .file-item');
  }

  /** Click a folder to expand/collapse it */
  async toggleFolder(name: string): Promise<void> {
    await this.folders().filter({ hasText: name }).click();
  }

  /** Expand all collapsed folders so all files become visible */
  async expandAllFolders(): Promise<void> {
    // Iteratively click collapsed folder headers until none remain
    while (true) {
      const collapsed = this.fileTree.locator('.folder.collapsed > .file-item');
      const count = await collapsed.count();
      if (count === 0) break;
      await collapsed.first().click();
      await this.page.waitForTimeout(150);
    }
  }

  /** Click a .cst file to load it, expanding parent folders if needed */
  async selectFile(name: string): Promise<void> {
    await this.expandAllFolders();
    await this.files().filter({ hasText: name }).click();
  }

  /** Get the currently selected file element */
  selectedFile(): Locator {
    return this.fileTree.locator('.file-item.selected');
  }

  /** Assert the file tree loaded with at least one entry */
  async expectLoaded(): Promise<void> {
    await this.fileTree.locator('.file-item').first().waitFor();
  }

  /** Assert an empty state message is shown */
  async expectEmpty(): Promise<void> {
    await this.fileTree.locator('.placeholder-text').waitFor();
  }
}
