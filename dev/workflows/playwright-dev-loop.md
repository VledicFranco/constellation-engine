# Playwright Dev Loop Protocol

A closed-loop, screenshot-driven development protocol for iterating on dashboard UI changes. State a visual objective, make changes, capture screenshots, analyze results, and repeat -- up to 5 autonomous iterations before presenting results for human review.

## Purpose

When modifying dashboard CSS, HTML, or JavaScript, it can be difficult to verify visual correctness from code alone. This protocol uses Playwright's screenshot audit test to capture the dashboard in multiple states, giving the agent visual feedback to evaluate whether an objective has been met.

The loop replaces guesswork with evidence: instead of hoping a CSS change looks right, the agent sees the actual rendered result and can adjust iteratively.

## Prerequisites

1. **Playwright installed:** `make install-dashboard-tests`
2. **Server running:** `make server` (or `.\scripts\dev.ps1 -ServerOnly` on Windows)
3. **Screenshot audit spec exists:** `dashboard-tests/tests/screenshot-audit.spec.ts`

## The Dev Loop

```
          +-----------------------+
          |  1. Receive objective |
          +-----------------------+
                     |
                     v
          +-----------------------+
   +----->|  2. Make code changes |
   |      +-----------------------+
   |                 |
   |                 v
   |      +-----------------------+
   |      |  3. Compile/restart   |
   |      |     (if needed)       |
   |      +-----------------------+
   |                 |
   |                 v
   |      +-----------------------+
   |      |  4. Run screenshot    |
   |      |     audit             |
   |      +-----------------------+
   |                 |
   |                 v
   |      +-----------------------+
   |      |  5. Analyze           |
   |      |     screenshots       |
   |      +-----------------------+
   |                 |
   |          met?  / \  iteration < 5?
   |          yes  /   \  no (not met)
   |              v     v
   |     +------+   +--------+
   |     | Done |   | Repeat |---+
   |     +------+   +--------+   |
   |                              |
   +------------------------------+
          (iteration = 5 and not met)
                     |
                     v
          +-----------------------+
          | Present screenshots   |
          | to user for guidance  |
          +-----------------------+
```

### Step-by-step

1. **Receive objective** from the user (e.g., "make edge labels readable on dark background", "increase node spacing in the DAG", "fix the output panel clipping on small viewports").

2. **Make code changes** to the relevant files:
   - CSS: `modules/http-api/src/main/resources/dashboard/static/css/main.css`
   - HTML: `modules/http-api/src/main/resources/dashboard/index.html`
   - JS: `modules/http-api/src/main/resources/dashboard/static/js/components/dag-visualizer.js`
   - Scala (backend): any file under `modules/`

3. **Compile and restart** the server, then **run the screenshot audit** (steps 3-4 combined):

   **Automated (recommended):** Use `dev-loop.ps1` to handle kill, compile, restart, and screenshot in one command:
   ```powershell
   .\scripts\dev-loop.ps1                              # Frontend-only: skip compile
   .\scripts\dev-loop.ps1 -Compile                     # Backend changes: compile first
   .\scripts\dev-loop.ps1 -TestFilter "1-simple"       # Run only one test (faster)
   .\scripts\dev-loop.ps1 -Compile -Port 8082          # Agent 2 on custom port
   ```

   **Manual (if needed):** See [Manual Server Lifecycle](#manual-server-lifecycle) below.

4. **Analyze screenshots** from `dashboard-tests/screenshots/` against the stated objective.

5. **Decision:**
   - **Objective met:** Present the final screenshots to the user. Done.
   - **Not met, iteration < 5:** Go back to step 2 with refined changes.
   - **Iteration = 5:** Present current screenshots to the user for review and further guidance.

## Screenshot Inventory

The screenshot audit produces the following images in `dashboard-tests/screenshots/`:

| # | File | What It Captures |
|---|------|------------------|
| 01 | `01-simple-dag-full.png` | Full page with simple script loaded (file browser + DAG + script runner) |
| 02 | `02-simple-dag-canvas.png` | DAG canvas only for simple script (node layout, edge rendering) |
| 03 | `03-simple-dag-executed.png` | DAG canvas after executing simple script (execution state colors) |
| 04 | `04-complex-dag-tb.png` | Complex script DAG in top-to-bottom layout |
| 05 | `05-complex-dag-lr.png` | Complex script DAG in left-to-right layout |
| 06 | `06-node-details-open.png` | Full page with node details panel open after clicking a node |
| 07 | `07-dag-*.png` | DAG canvas for each example script (up to 8 files, dynamic names) |
| 08 | `08-zoom-default.png` | DAG at default zoom level |
| 09 | `09-zoom-in.png` | DAG zoomed in 3x (detail visibility check) |
| 10 | `10-zoom-fit.png` | DAG after zoom-to-fit (all nodes visible) |
| 11 | `11-zoom-out.png` | DAG zoomed out 3x (overview perspective) |
| 12 | `12-text-analysis-executed.png` | Text analysis DAG canvas after execution (state colors on complex graph) |
| 13 | `13-text-analysis-full.png` | Full page after text analysis execution (outputs visible) |
| 14 | `14-branch-dag.png` | Branch expressions DAG (if script exists) |
| 15 | `15-data-pipeline-dag.png` | Data pipeline DAG before execution |
| 16 | `16-data-pipeline-executed.png` | Data pipeline DAG after execution |

### Which screenshots to check for common objectives

| Objective Type | Key Screenshots |
|---------------|-----------------|
| Edge/label styling | 02, 04, 05, 09 |
| Node colors/shapes | 02, 03, 12 |
| Execution state colors | 03, 12, 16 |
| Layout spacing | 04, 05 |
| Zoom behavior | 08, 09, 10, 11 |
| Overall page layout | 01, 06, 13 |
| Node details panel | 06 |
| Multiple script types | 07-series |

## Automation Scripts

Two PowerShell scripts automate the repetitive parts of the dev loop (kill server, compile, restart, wait for health, run screenshots). Use these instead of running each step manually.

### `dev-loop.ps1` -- Full iteration in one command

Runs a complete dev loop iteration: stops the server, optionally compiles, restarts the server, waits for health check, and runs the screenshot audit.

```powershell
.\scripts\dev-loop.ps1 [options]
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `-Port` | `8080` | Server port to use |
| `-Compile` | off | Run `sbt compile` before restarting (required for Scala changes) |
| `-TestFilter` | `""` (all) | Run only matching tests, e.g. `"1-simple-script-dag"` |

**Examples:**

```powershell
# Frontend-only change (CSS/HTML/JS) -- skip compile, run all screenshots
.\scripts\dev-loop.ps1

# Backend change (Scala) -- compile first
.\scripts\dev-loop.ps1 -Compile

# Fast iteration -- only capture simple DAG screenshots
.\scripts\dev-loop.ps1 -TestFilter "1-simple-script-dag"

# Agent 2 on port 8082
.\scripts\dev-loop.ps1 -Port 8082

# Backend change, specific test, custom port
.\scripts\dev-loop.ps1 -Compile -TestFilter "6-executed" -Port 8082
```

**What it does internally:**

| Step | Action |
|------|--------|
| 1/4 | Kills any process on the target port + all Java processes |
| 2/4 | Runs `sbt compile` (if `-Compile` flag is set) |
| 3/4 | Starts the server via `sbt exampleApp/runMain ...` in a background job, polls `/health` every 3s (up to 90s timeout) |
| 4/4 | Runs `npx playwright test screenshot-audit` (with optional `-g` filter) |

**Exit code:** 0 if screenshots captured successfully, non-zero on compile failure, server timeout, or test failure.

### `restart-server.ps1` -- Server restart only

Restarts the server without running screenshots. Useful when you want to test manually in the browser or run a different test suite.

```powershell
.\scripts\restart-server.ps1 [options]
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `-Port` | `8080` | Server port to use |
| `-Compile` | off | Run `sbt compile` before restarting |

**Examples:**

```powershell
# Restart server (no compile)
.\scripts\restart-server.ps1

# Recompile and restart
.\scripts\restart-server.ps1 -Compile

# Restart on agent port
.\scripts\restart-server.ps1 -Port 8082
```

**What it does internally:**
1. Kills any process on the target port
2. Kills all lingering Java processes
3. Runs `sbt compile` (if `-Compile` flag is set)
4. Starts the server in a background PowerShell job
5. Polls `/health` every 3s until the server responds (up to 90s timeout)

### Typical workflow

```
# 1. Make your code changes (CSS/JS/HTML)
#    ... edit files ...

# 2. Run a full iteration
.\scripts\dev-loop.ps1

# 3. Analyze screenshots in dashboard-tests/screenshots/
#    ... read images, decide if objective is met ...

# 4. If not met, make more changes and repeat
.\scripts\dev-loop.ps1

# 5. For quick targeted iteration on a specific area
.\scripts\dev-loop.ps1 -TestFilter "2-complex-script-dag"
```

## Manual Server Lifecycle

If you need to manage the server manually (e.g., on Unix/macOS or for debugging):

**Windows (without scripts):**
```powershell
# Kill existing server on port 8080
$proc = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($proc) { Stop-Process -Id $proc -Force }

# Recompile
make compile

# Restart server (background)
.\scripts\dev.ps1 -ServerOnly
```

**Unix/macOS:**
```bash
# Kill existing server on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null

# Recompile and restart
make compile
make server &
```

Wait for health check to pass before running screenshots:
```bash
curl http://localhost:8080/health
```

Then run the screenshot audit manually:
```bash
cd dashboard-tests && npx playwright test screenshot-audit --reporter=list
```

## Key Files and Helpers

### Page Objects

| Page Object | File | Purpose |
|-------------|------|---------|
| `DashboardPage` | `dashboard-tests/pages/dashboard.page.ts` | Main page, composes all sub-pages |
| `DagVisualizerPage` | `dashboard-tests/pages/dag-visualizer.page.ts` | DAG canvas interactions |
| `ScriptRunnerPage` | `dashboard-tests/pages/script-runner.page.ts` | Input filling, execution, output reading |
| `FileBrowserPage` | `dashboard-tests/pages/file-browser.page.ts` | File tree navigation |
| `NodeDetailsPage` | `dashboard-tests/pages/node-details.page.ts` | Node detail panel |
| `HistoryPage` | `dashboard-tests/pages/history.page.ts` | Execution history |

### Helpers

| Helper | File | Purpose |
|--------|------|---------|
| `ApiClient` | `dashboard-tests/helpers/api-client.ts` | Direct REST API calls (files, execute, health) |
| `FIXTURE_SCRIPTS` | `dashboard-tests/helpers/fixtures.ts` | Known script paths for test fixtures |
| Server Manager | `dashboard-tests/helpers/server-manager.ts` | Server lifecycle utilities |

### Dashboard Source Files

| File | What to Edit |
|------|-------------|
| `modules/http-api/src/main/resources/dashboard/index.html` | Page structure, panel layout |
| `modules/http-api/src/main/resources/dashboard/static/css/main.css` | All styling |
| `modules/http-api/src/main/resources/dashboard/static/js/components/dag-visualizer.js` | Cytoscape config, node/edge styles, layout |

## Multi-Agent Port Configuration

Each agent targets its own server instance via `CONSTELLATION_PORT`:

```bash
# Agent 2 running screenshot audit against port 8082
cd dashboard-tests
CONSTELLATION_PORT=8082 npx playwright test screenshot-audit --reporter=list
```

The port is read in `playwright.config.ts` and applied to both `baseURL` and the `webServer` config.

## Extending the Screenshot Audit

To capture additional UI areas:

1. Add a new test block in `dashboard-tests/tests/screenshot-audit.spec.ts`:
   ```typescript
   test('9-my-new-area', async ({ page }) => {
     // Navigate to the state you want to capture
     await dashboard.fileBrowser.expandAllFolders();
     await dashboard.fileBrowser.selectFile('simple-test.cst');
     await page.waitForTimeout(2000);

     // Capture
     const element = page.locator('#my-element');
     await element.screenshot({ path: 'screenshots/17-my-new-area.png' });
   });
   ```

2. Use `page.screenshot({ fullPage: true })` for full-page captures or `element.screenshot()` for specific regions.

3. Update the screenshot inventory table in this document.

## Tips

### Wait Times

The screenshot audit uses `page.waitForTimeout()` after navigation and execution. Cytoscape layout animations need time to settle before screenshots are meaningful. The default waits (500ms-2000ms) work for most cases; increase them if screenshots show mid-animation states.

### Cytoscape API Access

The DAG visualizer exposes its Cytoscape instance on the window object:
```typescript
const cy = (window as any).dashboard.dagVisualizer.cy;
```

This allows programmatic interaction with the graph (clicking nodes, reading positions, checking styles) from within Playwright's `page.evaluate()`.

### Console Error Trapping

The E2E test infrastructure traps JavaScript console errors. If your changes introduce JS errors, the screenshot audit may fail before producing screenshots. Check the Playwright output for console error messages.

### Partial Screenshot Runs

To run only a specific test within the audit (faster iteration):
```bash
cd dashboard-tests && npx playwright test screenshot-audit -g "1-simple-script-dag" --reporter=list
```

### Reading Screenshots

The agent can read screenshot images directly from `dashboard-tests/screenshots/` using the Read tool, which supports image files. This enables visual analysis without leaving the development loop.
