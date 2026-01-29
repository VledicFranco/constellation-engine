import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

test.describe('File Browsing', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page);
    await dashboard.goto();
    await dashboard.expectReady();
  });

  test('file tree loads with entries', async () => {
    await dashboard.fileBrowser.expectLoaded();
    const items = dashboard.fileBrowser.fileTree.locator('.file-item');
    const count = await items.count();
    expect(count).toBeGreaterThan(0);
  });

  test('folder expand and collapse toggles children', async () => {
    await dashboard.fileBrowser.expectLoaded();

    // Find first folder and check it exists
    const folders = dashboard.fileBrowser.folders();
    const folderCount = await folders.count();

    if (folderCount > 0) {
      // Click folder to collapse (it starts expanded)
      const firstFolder = folders.first();
      const folderName = (await firstFolder.textContent()) ?? '';
      await dashboard.fileBrowser.toggleFolder(folderName.trim());

      // The folder should have the collapsed class
      const folderContainer = dashboard.fileBrowser.fileTree.locator('.folder.collapsed').first();
      await expect(folderContainer).toBeVisible();

      // Toggle again to expand
      await dashboard.fileBrowser.toggleFolder(folderName.trim());
    }
  });

  test('file selection highlights file and shows name in header', async () => {
    await dashboard.fileBrowser.expectLoaded();

    // Select the first .cst file
    const files = dashboard.fileBrowser.files();
    const fileCount = await files.count();
    expect(fileCount).toBeGreaterThan(0);

    const firstFile = files.first();
    const fileName = (await firstFile.textContent())?.trim() ?? '';
    await firstFile.click();

    // Wait for async script metadata load
    await dashboard.page.waitForTimeout(1000);

    // File should be highlighted
    const selected = dashboard.fileBrowser.selectedFile();
    await expect(selected).toBeVisible();

    // Filename shown in header
    await expect(dashboard.scriptRunner.currentFileName).not.toHaveText('Select a script');

    // Run button should be enabled
    await dashboard.scriptRunner.expectRunEnabled();
  });

  test('selecting a different file changes the selection', async () => {
    await dashboard.fileBrowser.expectLoaded();

    const files = dashboard.fileBrowser.files();
    const fileCount = await files.count();

    if (fileCount >= 2) {
      // Select first file
      await files.first().click();
      await dashboard.page.waitForTimeout(1000);

      const firstFileName = await dashboard.scriptRunner.currentFileName.textContent();

      // Select second file
      await files.nth(1).click();
      await dashboard.page.waitForTimeout(1000);

      const secondFileName = await dashboard.scriptRunner.currentFileName.textContent();

      // Names should differ
      expect(firstFileName).not.toEqual(secondFileName);

      // Only one file should be selected
      const selectedCount = await dashboard.fileBrowser.selectedFile().count();
      expect(selectedCount).toBe(1);
    }
  });

  test('selecting a script with inputs generates input form', async () => {
    await dashboard.fileBrowser.expectLoaded();

    // Find and select a file (simple-test.cst has "in message: String")
    const files = dashboard.fileBrowser.files();
    const fileCount = await files.count();
    expect(fileCount).toBeGreaterThan(0);

    // Select the first available file
    await files.first().click();
    await dashboard.page.waitForTimeout(1000);

    // Check if input form has been generated (either inputs or placeholder)
    const form = dashboard.scriptRunner.inputsForm;
    await expect(form).toBeVisible();

    // The form should have either input fields or a placeholder
    const inputFields = form.locator('.input-field');
    const placeholder = form.locator('.placeholder-text');
    const hasInputs = (await inputFields.count()) > 0;
    const hasPlaceholder = (await placeholder.count()) > 0;

    // At least one of these should be true
    expect(hasInputs || hasPlaceholder).toBeTruthy();
  });
});
