# RFC-012: Dashboard End-to-End Test Suite

**Status:** Implemented
**Priority:** P1 (Quality Infrastructure)
**Author:** Human + Claude
**Created:** 2026-01-29

---

## Summary

Introduce a Playwright-based end-to-end test suite for the Constellation Dashboard that serves as the **golden automated standard of quality** for the web UI. The suite covers all critical user paths, produces machine-readable output for coding agents, and provides tooling for agents to explore, debug, and identify gaps in the dashboard.

---

## Motivation

The Constellation Dashboard (`/dashboard`) is the primary web interface for browsing scripts, executing pipelines, visualizing DAGs, and reviewing execution history. Today it has **zero automated tests**. The backend Scala API routes have unit tests, but no test exercises the actual browser experience: DOM rendering, user interactions, navigation, keyboard shortcuts, or visual correctness.

This creates several problems:

1. **Regressions go undetected.** Changes to `DashboardRoutes.scala`, the JavaScript components, or the CSS can break the UI silently. The recent Windows line-ending fix (`5f20c1d`) was caught manually — an E2E test would have caught it automatically.

2. **Agents cannot verify UI work.** When a coding agent implements a dashboard feature, it has no way to confirm the feature works end-to-end in a browser. Agents must rely on backend unit tests and hope the frontend integration holds.

3. **No quality baseline.** There is no definition of "the dashboard works" that can be checked in CI. Every release is a manual QA exercise.

4. **Gap discovery requires manual exploration.** Identifying missing features, broken layouts, or UX issues requires a human to click through every flow. Agents exploring the UI have no structured tooling to assist.

This RFC proposes a test suite that solves all four problems.

---

## Design Goals

| Goal | How |
|------|-----|
| **Golden standard of quality** | Tests are the definitive answer to "does the dashboard work?" — run in CI on every PR |
| **Common user path coverage** | Every test maps to a real user journey, not implementation internals |
| **Agent-friendly** | Machine-readable JSON reports, screenshot capture, Page Object Models as documentation, codegen support |
| **Fast feedback** | Smoke suite completes in <30s; full suite in <3min |
| **Multi-agent compatible** | Port-configurable via `CONSTELLATION_PORT`, each agent tests their own server instance |

---

## Technology Choice: Playwright

| Criterion | Playwright | Cypress | Selenium |
|-----------|-----------|---------|----------|
| TypeScript native | Yes | Yes | Partial |
| Multi-browser | Chromium, Firefox, WebKit | Chromium only (free) | All |
| Auto-waiting | Built-in | Built-in | Manual |
| Network interception | Yes | Yes | Limited |
| Trace viewer | Yes (step-by-step replay) | No | No |
| Screenshot/video on failure | Built-in | Plugin | Manual |
| Codegen (record interactions) | `npx playwright codegen` | No | No |
| Headless CI | Excellent | Good | Requires setup |
| Parallel execution | Built-in worker isolation | Limited | Manual |
| Project maturity (2026) | Industry standard | Established | Legacy |

**Decision:** Playwright. It provides the best combination of DX, CI support, and agent tooling (codegen, traces, screenshots). The existing project already uses TypeScript for the VSCode extension, so the toolchain is familiar.

---

## Architecture

### Directory Structure

```
dashboard-tests/
├── playwright.config.ts              # Configuration (browsers, base URL, reporters)
├── package.json                      # Dependencies: @playwright/test
├── tsconfig.json                     # TypeScript configuration
│
├── pages/                            # Page Object Models
│   ├── dashboard.page.ts             # Top-level page: init, navigation, status bar
│   ├── file-browser.page.ts          # File tree: folders, file selection
│   ├── script-runner.page.ts         # Input form + output display + Run button
│   ├── dag-visualizer.page.ts        # Cytoscape canvas, zoom, layout, node clicks
│   ├── node-details.page.ts          # Right sidebar: node info panel
│   └── history.page.ts              # History view: list, filter, execution details
│
├── tests/                            # Test specs organized by user journey
│   ├── smoke.spec.ts                 # P0: Dashboard loads, health OK, no JS errors
│   ├── file-browsing.spec.ts         # P0: Browse folders, select .cst files
│   ├── script-execution.spec.ts      # P0: Fill inputs → Run → Verify outputs
│   ├── dag-interaction.spec.ts       # P1: Click nodes, zoom, layout toggle, details
│   ├── execution-history.spec.ts     # P1: History list, filter, execution details
│   ├── navigation.spec.ts            # P1: View switching, hash routing, deep links
│   ├── keyboard-shortcuts.spec.ts    # P2: Ctrl+Enter execution, focus management
│   └── error-handling.spec.ts        # P2: Missing inputs, server errors, bad scripts
│
├── fixtures/
│   ├── scripts/                      # .cst files used by tests
│   │   ├── simple-uppercase.cst      # in text: String → Uppercase → out
│   │   ├── multi-input.cst           # String + Int + Boolean inputs
│   │   ├── no-inputs.cst             # Script with no input declarations
│   │   ├── complex-types.cst         # List and Record input types
│   │   ├── multi-step.cst            # Pipeline with 3+ chained modules
│   │   ├── syntax-error.cst          # Intentionally broken script
│   │   └── with-examples.cst         # Script using @example() annotations
│   └── snapshots/                    # Visual regression baseline screenshots
│
├── helpers/
│   ├── server-manager.ts             # Start/stop Constellation server for tests
│   ├── api-client.ts                 # Direct REST calls for setup/teardown/assertions
│   └── fixtures.ts                   # Copy fixture .cst files to server's CST directory
│
└── reports/                          # Generated output (gitignored)
    ├── results.json                  # Machine-readable test results
    ├── report.html                   # Human-readable HTML report
    └── screenshots/                  # Failure screenshots + trace files
```

### Page Object Model

Each Page Object encapsulates a dashboard component's selectors and interactions. This serves **dual purpose**: test infrastructure and **living documentation** of the UI contract that agents can read.

```typescript
// pages/file-browser.page.ts
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

  /** Click a .cst file to load it */
  async selectFile(name: string): Promise<void> {
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
```

```typescript
// pages/script-runner.page.ts
import { Page, Locator } from '@playwright/test';

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
    return this.outputsDisplay.textContent() ?? '';
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
```

```typescript
// pages/dag-visualizer.page.ts
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
    // Cytoscape renders into a canvas element inside the container
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

  /** Take a screenshot of just the DAG canvas */
  async screenshot(name: string): Promise<Buffer> {
    return this.canvas.screenshot({ path: `reports/screenshots/${name}.png` });
  }
}
```

```typescript
// pages/history.page.ts
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
```

```typescript
// pages/dashboard.page.ts
import { Page, Locator } from '@playwright/test';
import { FileBrowserPage } from './file-browser.page';
import { ScriptRunnerPage } from './script-runner.page';
import { DagVisualizerPage } from './dag-visualizer.page';
import { HistoryPage } from './history.page';

export class DashboardPage {
  readonly page: Page;
  readonly fileBrowser: FileBrowserPage;
  readonly scriptRunner: ScriptRunnerPage;
  readonly dagVisualizer: DagVisualizerPage;
  readonly history: HistoryPage;

  // Navigation
  readonly scriptsTab: Locator;
  readonly historyTab: Locator;
  readonly refreshButton: Locator;

  // Status bar
  readonly statusMessage: Locator;
  readonly connectionStatus: Locator;

  // Node details sidebar
  readonly nodeDetailsPanel: Locator;
  readonly closeDetailsButton: Locator;

  // Loading overlay
  readonly loadingOverlay: Locator;

  constructor(page: Page) {
    this.page = page;

    // Compose child page objects
    this.fileBrowser = new FileBrowserPage(page);
    this.scriptRunner = new ScriptRunnerPage(page);
    this.dagVisualizer = new DagVisualizerPage(page);
    this.history = new HistoryPage(page);

    // Top-level elements
    this.scriptsTab = page.locator('.nav-btn[data-view="scripts"]');
    this.historyTab = page.locator('.nav-btn[data-view="history"]');
    this.refreshButton = page.locator('#refresh-btn');
    this.statusMessage = page.locator('#status-message');
    this.connectionStatus = page.locator('#connection-status');
    this.nodeDetailsPanel = page.locator('#node-details-panel');
    this.closeDetailsButton = page.locator('#close-details');
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
    await this.closeDetailsButton.click();
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
```

### Configuration

```typescript
// playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

const PORT = process.env.CONSTELLATION_PORT || '8080';
const BASE_URL = `http://localhost:${PORT}`;

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  retries: process.env.CI ? 2 : 0,
  workers: 1, // Sequential — tests share a server
  fullyParallel: false,

  reporter: [
    ['html', { outputFolder: 'reports', open: 'never' }],
    ['json', { outputFile: 'reports/results.json' }],
    ['list'],
  ],

  use: {
    baseURL: BASE_URL,
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Optional: multi-browser
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    // { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],

  // Server management: start Constellation before tests
  webServer: {
    command: process.platform === 'win32'
      ? `powershell -File ../scripts/dev.ps1 -ServerOnly -Port ${PORT}`
      : `make server`,
    port: parseInt(PORT),
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
```

### Server Manager Helper

```typescript
// helpers/server-manager.ts
import { request } from '@playwright/test';

/**
 * Utility for verifying and interacting with the Constellation server.
 * When running locally, expects the server is already running.
 * In CI, playwright.config.ts starts the server via webServer option.
 */
export class ServerManager {
  constructor(private baseUrl: string) {}

  /** Check that the server is healthy */
  async waitForHealthy(timeoutMs = 30_000): Promise<void> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const deadline = Date.now() + timeoutMs;

    while (Date.now() < deadline) {
      try {
        const res = await ctx.get('/health');
        if (res.ok()) return;
      } catch { /* retry */ }
      await new Promise(r => setTimeout(r, 500));
    }
    throw new Error(`Server not healthy after ${timeoutMs}ms`);
  }

  /** Get the list of available .cst files */
  async getFiles(): Promise<unknown> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const res = await ctx.get('/api/v1/files');
    return res.json();
  }

  /** Execute a script directly via API (for test setup) */
  async execute(scriptPath: string, inputs: Record<string, unknown>): Promise<unknown> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const res = await ctx.post('/api/v1/execute', {
      data: { scriptPath, inputs, source: 'test' }
    });
    return res.json();
  }

  /** Get execution history (for assertions) */
  async getExecutions(limit = 10): Promise<unknown> {
    const ctx = await request.newContext({ baseURL: this.baseUrl });
    const res = await ctx.get(`/api/v1/executions?limit=${limit}`);
    return res.json();
  }
}
```

---

## Test Specifications

### P0: Smoke Tests (`smoke.spec.ts`)

Fast sanity check that the dashboard loads and the server is alive.

```typescript
test('dashboard loads without errors', async ({ page }) => {
  const dashboard = new DashboardPage(page);
  const errors = dashboard.collectConsoleErrors();

  await dashboard.goto();
  await dashboard.expectReady();

  expect(errors).toHaveLength(0);
  await expect(dashboard.statusMessage).toHaveText('Ready');
  await expect(dashboard.connectionStatus).toBeVisible();
});

test('health endpoint returns OK', async ({ request }) => {
  const res = await request.get('/health');
  expect(res.ok()).toBeTruthy();
});

test('API files endpoint returns data', async ({ request }) => {
  const res = await request.get('/api/v1/files');
  expect(res.ok()).toBeTruthy();
  const data = await res.json();
  expect(data.files).toBeDefined();
});
```

### P0: File Browsing (`file-browsing.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| File tree loads | Open dashboard | File tree has at least one entry |
| Folder expand/collapse | Click folder | Children appear/disappear |
| File selection | Click `.cst` file | File highlighted, filename shown in header, Run enabled |
| Selection persistence | Select file, then select another | Only new file highlighted |
| Input form generated | Select script with inputs | Input fields match declared `in` types |
| No-input script | Select script without inputs | "No inputs" placeholder shown |

### P0: Script Execution (`script-execution.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| Execute with string input | Select `simple-uppercase.cst`, fill "hello", Run | Output contains "HELLO" |
| Execute with multiple inputs | Select `multi-input.cst`, fill all fields, Run | Output shows all processed values |
| Execute with Ctrl+Enter | Select script, fill input, press Ctrl+Enter | Same as clicking Run |
| Output displays success | Execute valid script | Green "Completed" badge, formatted JSON output |
| DAG updates after execution | Execute script | DAG nodes show Completed status (green borders) |
| Default values from @example | Select `with-examples.cst` | Input fields pre-filled with example values |

### P1: DAG Interaction (`dag-interaction.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| DAG renders on script load | Select a script | Canvas shows nodes and edges (node count > 0) |
| Click node shows details | Click a DAG node | Right sidebar opens with label, kind, type |
| Click background hides details | Click node, then click canvas background | Details sidebar hides |
| Toggle layout TB → LR | Click LR button | Layout re-renders, LR button active |
| Toggle layout LR → TB | Click TB button | Layout re-renders, TB button active |
| Zoom in | Click zoom-in button | Canvas zoom level increases |
| Zoom out | Click zoom-out button | Canvas zoom level decreases |
| Zoom fit | Zoom in, then click Fit | Entire graph visible in viewport |
| Close details button | Open details, click X | Details panel closes |
| Node kind colors | Load multi-step pipeline | Input nodes are green, Output blue, Operation purple |

### P1: Execution History (`execution-history.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| History view loads | Execute a script, switch to History | At least one history item |
| History item shows metadata | View history list | Script name, status badge, timestamp visible |
| Filter by script name | Type script name in filter input | Only matching executions shown |
| Clear filter shows all | Type filter, then clear | All executions visible again |
| Select execution shows details | Click a history item | Detail panel shows inputs, outputs, status, duration |
| Execution DAG loads | Select a history item | DAG visualization renders for that execution |

### P1: Navigation (`navigation.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| Default view is Scripts | Open `/dashboard` | Scripts tab active, scripts view visible |
| Switch to History | Click History tab | History tab active, history view visible |
| Switch back to Scripts | Click Scripts tab | Scripts tab active, scripts view visible |
| Hash routing: history | Navigate to `#/history` | History view shown |
| Deep link: execution | Navigate to `#/executions/{id}` | History view with that execution selected |
| Refresh button | Click refresh | File tree reloads, current script refreshes |

### P2: Keyboard Shortcuts (`keyboard-shortcuts.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| Ctrl+Enter executes | Load script, fill inputs, press Ctrl+Enter | Script executes, output shown |
| Ctrl+Enter does nothing without script | Press Ctrl+Enter on empty state | No errors, nothing happens |

### P2: Error Handling (`error-handling.spec.ts`)

| Test | Steps | Assertion |
|------|-------|-----------|
| Syntax error in script | Load `syntax-error.cst` | DAG empty or error state, no crash |
| Missing required input | Load script, leave input empty, Run | Error message in output panel |
| Server error handling | Stop server mid-test, try loading | Error displayed, no unhandled JS exception |
| Invalid JSON input | Enter malformed JSON in complex input | Graceful error, not a crash |

---

## Agent Integration

### How Agents Use This Suite

**1. Verify changes:**
```bash
cd dashboard-tests
npx playwright test                      # Run all tests
npx playwright test smoke.spec.ts        # Quick smoke check
npx playwright test --grep "execute"     # Run tests matching pattern
```

**2. Explore the UI interactively:**
```bash
npx playwright codegen http://localhost:8080/dashboard
```
This opens a browser with recording enabled. The agent can describe interactions and Playwright generates the test code — useful for discovering what the UI does.

**3. Read Page Objects to understand the UI contract:**
The `pages/` directory is a machine-readable map of every interaction the dashboard supports. An agent can read `dag-visualizer.page.ts` to learn the exact selectors, methods, and expected behaviors of the DAG component without needing to read the source JavaScript.

**4. Read JSON reports programmatically:**
```bash
cat reports/results.json | jq '.suites[].specs[] | select(.ok == false)'
```
Returns structured data about which tests failed and why.

**5. Review failure screenshots:**
When a test fails, Playwright saves a screenshot in `reports/screenshots/`. Agents with vision capabilities can read these to visually understand what went wrong.

**6. Debug with trace viewer:**
```bash
npx playwright show-trace reports/trace.zip
```
Opens a step-by-step replay of the failed test with DOM snapshots, network requests, and console logs.

### Machine-Readable Output Format

The JSON reporter produces:
```json
{
  "suites": [
    {
      "title": "Script Execution",
      "file": "tests/script-execution.spec.ts",
      "specs": [
        {
          "title": "executes script with string input",
          "ok": true,
          "duration": 1234
        },
        {
          "title": "shows error for missing input",
          "ok": false,
          "duration": 5678,
          "errors": ["Expected .output-error to be visible"]
        }
      ]
    }
  ]
}
```

---

## Build Integration

### Makefile Targets

```makefile
# Run all dashboard E2E tests
test-dashboard:
	cd dashboard-tests && npx playwright test

# Quick smoke check (~30s)
test-dashboard-smoke:
	cd dashboard-tests && npx playwright test smoke.spec.ts

# Full suite with HTML report
test-dashboard-full:
	cd dashboard-tests && npx playwright test --reporter=html

# Install Playwright browsers
install-dashboard-tests:
	cd dashboard-tests && npm ci && npx playwright install --with-deps chromium
```

### CI Workflow

```yaml
# .github/workflows/dashboard-tests.yml
name: Dashboard E2E Tests

on:
  push:
    branches: [master]
    paths:
      - 'modules/http-api/**'
      - 'dashboard-tests/**'
  pull_request:
    paths:
      - 'modules/http-api/**'
      - 'dashboard-tests/**'

jobs:
  e2e:
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
          cache: sbt

      - uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install Playwright
        working-directory: dashboard-tests
        run: |
          npm ci
          npx playwright install --with-deps chromium

      - name: Run E2E tests
        working-directory: dashboard-tests
        run: npx playwright test
        env:
          CI: true
          CONSTELLATION_PORT: 8080

      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: dashboard-e2e-report
          path: |
            dashboard-tests/reports/
            dashboard-tests/test-results/
          retention-days: 14
```

---

## Implementation Phases

### Phase 1: Foundation

- Set up `dashboard-tests/` directory with `package.json`, `playwright.config.ts`, `tsconfig.json`
- Implement all Page Object Models (`pages/*.page.ts`)
- Implement `smoke.spec.ts`
- Implement `helpers/server-manager.ts` and `helpers/fixtures.ts`
- Add `make test-dashboard` and `make test-dashboard-smoke` to Makefile
- Create fixture `.cst` scripts

### Phase 2: Core User Journeys

- Implement `file-browsing.spec.ts`
- Implement `script-execution.spec.ts`
- Implement `dag-interaction.spec.ts`
- Add CI workflow `.github/workflows/dashboard-tests.yml`

### Phase 3: Full Coverage

- Implement `execution-history.spec.ts`
- Implement `navigation.spec.ts`
- Implement `keyboard-shortcuts.spec.ts`
- Implement `error-handling.spec.ts`

### Phase 4: Visual Regression (Optional)

- Add `toHaveScreenshot()` assertions for key views
- Store baseline screenshots in `fixtures/snapshots/`
- Add visual diff reporting to CI

---

## Alternatives Considered

### 1. Cypress

Considered for its strong DX and built-in test runner UI. Rejected because:
- Single browser only in free tier (Chromium)
- No codegen feature for agent exploration
- No trace viewer for post-mortem debugging
- Playwright has become the industry standard for new projects

### 2. Scala-based HTTP Tests (http4s test client)

Already partially exists in `ConstellationRoutesTest.scala`. Rejected for E2E because:
- Cannot test DOM rendering, JavaScript execution, or CSS layout
- Cannot verify Cytoscape DAG visualization
- Cannot test user interactions (clicks, keyboard)
- Useful as a complement, not a replacement

### 3. VSCode Extension E2E Framework Reuse

The project has Mocha + `@vscode/test-electron` tests. Rejected because:
- Designed for VSCode extension testing, not web dashboard
- Cannot drive a standalone browser
- Different component surface area entirely

### 4. Testing Against Mock Server

Rejected. The value of E2E tests is exercising the full stack — Scala server, compilation, execution, and frontend. Mocking the server would test only the JavaScript layer, missing integration bugs.

---

## Open Questions

1. **Visual regression from day one?** Phase 4 proposes screenshot comparison. Should this be part of Phase 1 for immediate visual coverage, or deferred until the UI stabilizes?

2. **Multi-browser in CI?** The config includes Chromium only for speed. Should Firefox and WebKit be added to CI, or kept as optional local runs?

3. **Test data isolation.** Executing scripts creates history entries in the in-memory store. Should tests clean up after themselves via API, or should each test run get a fresh server instance?

4. **Accessibility testing.** Playwright supports `@axe-core/playwright` for automated a11y checks. Should this be included in the scope, or tracked as a separate RFC?

---

## References

- [Playwright Documentation](https://playwright.dev/)
- [Dashboard Source: `modules/http-api/src/main/resources/dashboard/`](../../modules/http-api/src/main/resources/dashboard/)
- [Dashboard Routes: `modules/http-api/.../DashboardRoutes.scala`](../../modules/http-api/src/main/scala/io/constellation/http/DashboardRoutes.scala)
- [Existing HTTP Tests: `ConstellationRoutesTest.scala`](../../modules/http-api/src/test/scala/io/constellation/http/ConstellationRoutesTest.scala)
- [VSCode E2E Tests (for pattern reference)](../../vscode-extension/src/test/suite/e2e/)
