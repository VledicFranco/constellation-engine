# Dashboard & Tooling Guide

This document covers the web dashboard, VSCode extension, and developer tooling included with Constellation Engine.

## Web Dashboard

### Overview

The web dashboard is a browser-based UI for browsing scripts, executing pipelines, and visualizing DAGs. It is served by `ConstellationServer` when enabled.

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withDashboard               // Enable with default config
  .run
```

Access at `http://localhost:8080/dashboard` (or your configured port).

### Configuration

`DashboardConfig` controls the dashboard behavior:

```scala
import io.constellation.http.DashboardConfig

ConstellationServer
  .builder(constellation, compiler)
  .withDashboard(DashboardConfig.fromEnv)   // Read from environment
  .withDashboard(DashboardConfig(           // Or configure directly
    cstDirectory = java.nio.file.Paths.get("./scripts")
  ))
  .run
```

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `CONSTELLATION_CST_DIR` | Current working directory | Directory containing `.cst` script files |
| `CONSTELLATION_SAMPLE_RATE` | `1.0` | Execution sampling rate (0.0 to 1.0) |
| `CONSTELLATION_MAX_EXECUTIONS` | `1000` | Maximum stored executions (LRU eviction) |

### Features

#### File Browser

The left panel displays `.cst` files from the configured directory as a tree. Click a file to load it in the editor. Folders can be expanded and collapsed.

#### Script Editor

The central panel shows the script source. Inputs declared with `in` are displayed as a form — each input gets a type-appropriate control:

| Type | Control |
|------|---------|
| `String` | Text input |
| `Int` | Number input |
| `Boolean` | Checkbox |
| `List<T>` | JSON text area |

Click **Run** (or press `Ctrl+Enter`) to execute the script.

#### Execution Output

After execution, outputs appear below the editor. Results are formatted as JSON. Errors display the error message and code.

#### DAG Visualization

The right panel renders the pipeline as an interactive graph using Cytoscape.js:

- **Data nodes** — blue, rounded rectangles (inputs and intermediate values)
- **Module nodes** — orange, rectangles (processing steps)
- **Edges** — arrows showing data flow direction

Interaction:
- Pan by clicking and dragging the background
- Zoom with mouse wheel
- Click a node to see its type information
- Toggle between hierarchical and force-directed layouts

#### Execution History

The bottom panel lists recent executions with:
- DAG name, status (success/failure), timestamp
- Filter by script path
- Click to view detailed results including per-node execution data

### Architecture

The dashboard is a TypeScript single-page application bundled into the server JAR:

```
dashboard/
├── src/
│   ├── index.ts           # Entry point
│   ├── api.ts             # HTTP API client
│   ├── editor.ts          # Script editor component
│   ├── dag-viz.ts         # Cytoscape.js DAG renderer
│   ├── file-browser.ts    # File tree component
│   ├── history.ts         # Execution history component
│   └── types.d.ts         # TypeScript type definitions
├── index.html             # Dashboard HTML shell
└── styles.css             # Dashboard styles
```

Build the dashboard TypeScript:

```bash
make dashboard          # One-time build
make dashboard-watch    # Watch mode (auto-rebuild)
```

## VSCode Extension

### Features

The VSCode extension provides a rich editing experience for `.cst` files:

| Feature | Trigger | Description |
|---------|---------|-------------|
| Syntax highlighting | Automatic | TextMate grammar + semantic tokens |
| Autocomplete | `Ctrl+Space` | Module names, keywords, types |
| Real-time diagnostics | Automatic | Compilation errors as you type |
| Hover information | Mouse hover | Module documentation and types |
| Run script | `Ctrl+Shift+R` | Execute with input form and output display |
| DAG visualization | `Ctrl+Shift+D` | Interactive pipeline graph |

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+R` | Run the current `.cst` script |
| `Ctrl+Shift+D` | Show DAG visualization for current script |
| `Ctrl+Space` | Trigger autocomplete |
| `F5` | Launch extension in debug mode (development) |

### Installation

```bash
cd vscode-extension
npm install
npm run compile
```

Then open VSCode, go to Extensions, and install from the `vscode-extension` directory (or press `F5` to launch a debug instance).

### Configuration

Extension settings (`settings.json`):

```json
{
  "constellation.server.url": "ws://localhost:8080/lsp"
}
```

For multi-agent setups, point each VSCode instance to the appropriate agent port:

```json
{
  "constellation.server.url": "ws://localhost:8082/lsp"
}
```

### LSP Integration

The extension communicates with the Constellation server via WebSocket at `ws://host:port/lsp`. The LSP server provides:

- **textDocument/completion** — autocomplete items
- **textDocument/hover** — type and documentation info
- **textDocument/publishDiagnostics** — real-time error markers
- **textDocument/semanticTokens** — rich syntax highlighting
- **constellation/executePipeline** — custom command to run scripts

See [LSP Integration Guide](LSP_INTEGRATION.md) for protocol details.

## Playwright Dev Loop

For iterating on dashboard visuals (CSS, HTML, JS, Cytoscape configuration), use the screenshot-driven development loop.

### Protocol

1. Receive a visual objective from the user
2. Make code changes
3. Run `.\scripts\dev-loop.ps1` (or with `-Compile` for Scala changes)
4. Examine screenshots in `dashboard-tests/screenshots/`
5. If objective met: done. If not (and iteration < 5): go to step 2. If iteration = 5: present screenshots for human review.

### Automation Scripts

| Command | Purpose |
|---------|---------|
| `.\scripts\dev-loop.ps1` | Full iteration: restart server + capture screenshots |
| `.\scripts\dev-loop.ps1 -Compile` | Compile Scala first, then restart + screenshots |
| `.\scripts\dev-loop.ps1 -TestFilter "1-simple"` | Only run matching screenshot tests |
| `.\scripts\restart-server.ps1` | Restart server without screenshots |

### Manual Screenshot Capture

```bash
cd dashboard-tests && npx playwright test screenshot-audit --reporter=list
```

Screenshots are saved to `dashboard-tests/screenshots/`.

See [Playwright Dev Loop Guide](dev/playwright-dev-loop.md) for the full protocol.

## Dashboard E2E Tests

Automated end-to-end tests verify dashboard functionality using Playwright.

### Setup

```bash
make install-dashboard-tests   # Install Playwright + browsers (first time)
```

### Running Tests

| Command | Purpose |
|---------|---------|
| `make test-dashboard` | Run all dashboard E2E tests |
| `make test-dashboard-smoke` | Quick smoke check |
| `make test-dashboard-full` | Full suite with HTML report |

### Test Coverage

| Suite | What it Tests |
|-------|---------------|
| Smoke | Dashboard loads, health endpoint OK, no JS errors |
| File browsing | Tree loading, folder toggle, file selection |
| Script execution | Input filling, Run button, output verification |
| DAG interaction | Node clicks, zoom, layout toggle |
| Execution history | List display, filtering, detail view |
| Navigation | View switching, hash routing, deep links |
| Keyboard shortcuts | `Ctrl+Enter` execution |
| Error handling | Missing inputs, graceful error display |

### Page Objects

Test page objects in `dashboard-tests/pages/` serve as machine-readable documentation of the UI contract. When adding new UI components, create a corresponding page object.

### Debugging Failed Tests

```bash
# Run with visible browser
cd dashboard-tests && npx playwright test --headed

# Run a single test file
cd dashboard-tests && npx playwright test file-browsing.spec.ts

# View HTML report
cd dashboard-tests && npx playwright show-report reports

# View trace for a failed test
cd dashboard-tests && npx playwright show-trace test-results/<trace>.zip
```

## Related Documentation

- [LSP Integration Guide](LSP_INTEGRATION.md) — WebSocket protocol and message formats
- [Playwright Dev Loop](dev/playwright-dev-loop.md) — Screenshot-driven development protocol
- [Dashboard E2E RFC](dev/rfcs/rfc-012-dashboard-e2e-tests.md) — Design document for E2E tests
