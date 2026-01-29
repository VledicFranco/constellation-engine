import { Page, Locator } from '@playwright/test';
import { FileBrowserPage } from './file-browser.page';
import { ScriptRunnerPage } from './script-runner.page';
import { DagVisualizerPage } from './dag-visualizer.page';
import { NodeDetailsPage } from './node-details.page';
import { HistoryPage } from './history.page';

export class DashboardPage {
  readonly page: Page;
  readonly fileBrowser: FileBrowserPage;
  readonly scriptRunner: ScriptRunnerPage;
  readonly dagVisualizer: DagVisualizerPage;
  readonly nodeDetails: NodeDetailsPage;
  readonly history: HistoryPage;

  // Navigation
  readonly scriptsTab: Locator;
  readonly historyTab: Locator;
  readonly refreshButton: Locator;

  // Status bar
  readonly statusMessage: Locator;
  readonly connectionStatus: Locator;

  // Loading overlay
  readonly loadingOverlay: Locator;

  constructor(page: Page) {
    this.page = page;

    // Compose child page objects
    this.fileBrowser = new FileBrowserPage(page);
    this.scriptRunner = new ScriptRunnerPage(page);
    this.dagVisualizer = new DagVisualizerPage(page);
    this.nodeDetails = new NodeDetailsPage(page);
    this.history = new HistoryPage(page);

    // Top-level elements
    this.scriptsTab = page.locator('.nav-btn[data-view="scripts"]');
    this.historyTab = page.locator('.nav-btn[data-view="history"]');
    this.refreshButton = page.locator('#refresh-btn');
    this.statusMessage = page.locator('#status-message');
    this.connectionStatus = page.locator('#connection-status');
    this.loadingOverlay = page.locator('#loading-overlay');
  }

  /** Navigate to the dashboard and wait for initialization */
  async goto(): Promise<void> {
    await this.page.goto('/dashboard');
    await this.page.waitForLoadState('networkidle');
    await this.statusMessage.waitFor();
  }

  /** Switch to the Scripts view */
  async goToScripts(): Promise<void> {
    await this.scriptsTab.click();
  }

  /** Switch to the History view */
  async goToHistory(): Promise<void> {
    await this.historyTab.click();
  }

  /** Click the refresh button */
  async refresh(): Promise<void> {
    await this.refreshButton.click();
  }

  /** Get the current status bar text */
  async getStatus(): Promise<string> {
    return (await this.statusMessage.textContent()) ?? '';
  }

  /** Assert the loading overlay is hidden */
  async expectReady(): Promise<void> {
    await this.loadingOverlay.waitFor({ state: 'hidden' });
  }

  /** Close the node details sidebar */
  async closeNodeDetails(): Promise<void> {
    await this.nodeDetails.close();
  }

  /** Collect all console errors during a test */
  collectConsoleErrors(): string[] {
    const errors: string[] = [];
    this.page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    return errors;
  }
}
