import { Page, Locator, expect } from '@playwright/test';

export class ScriptRunnerPage {
  readonly page: Page;
  readonly runButton: Locator;
  readonly currentFileName: Locator;
  readonly inputsForm: Locator;
  readonly outputsDisplay: Locator;

  constructor(page: Page) {
    this.page = page;
    this.runButton = page.locator('#run-btn');
    this.currentFileName = page.locator('#current-file');
    this.inputsForm = page.locator('#inputs-form');
    this.outputsDisplay = page.locator('#outputs-display');
  }

  /** Fill a text/number input field by its declared name */
  async fillInput(name: string, value: string): Promise<void> {
    await this.page.locator(`#input-${name}`).fill(value);
  }

  /** Select a boolean dropdown value */
  async selectBoolean(name: string, value: boolean): Promise<void> {
    await this.page.locator(`#input-${name}`).selectOption(String(value));
  }

  /** Click the Run button */
  async run(): Promise<void> {
    await this.runButton.click();
  }

  /** Run using keyboard shortcut */
  async runWithKeyboard(): Promise<void> {
    await this.page.keyboard.press('Control+Enter');
  }

  /** Wait for execution to complete and return the output text */
  async waitForOutput(): Promise<string> {
    await this.outputsDisplay.locator('.output-success, .output-error').waitFor();
    return (await this.outputsDisplay.textContent()) ?? '';
  }

  /** Assert the output panel shows a success result */
  async expectSuccess(): Promise<void> {
    await this.outputsDisplay.locator('.output-success').waitFor();
  }

  /** Assert the output panel shows an error */
  async expectError(): Promise<void> {
    await this.outputsDisplay.locator('.output-error').waitFor();
  }

  /** Get the JSON output as parsed object */
  async getOutputJson(): Promise<unknown> {
    const text = await this.outputsDisplay.locator('.output-json').textContent();
    return JSON.parse(text ?? '{}');
  }

  /** Assert the Run button is enabled */
  async expectRunEnabled(): Promise<void> {
    await expect(this.runButton).toBeEnabled();
  }

  /** Assert the Run button is disabled */
  async expectRunDisabled(): Promise<void> {
    await expect(this.runButton).toBeDisabled();
  }

  /** Get all input field labels */
  async getInputNames(): Promise<string[]> {
    const labels = this.inputsForm.locator('label');
    const count = await labels.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
      names.push((await labels.nth(i).textContent())?.trim() ?? '');
    }
    return names;
  }
}
