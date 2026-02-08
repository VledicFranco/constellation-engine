---
title: "Dashboard"
sidebar_position: 1
description: "Web dashboard, DAG visualization, and execution history"
---

# Web Dashboard Guide

:::caution Beta Feature
The interactive pipeline dashboard is currently in **beta**. Features are functional but undergoing active development and QA. Some behaviors may change before the stable release.
:::

The web dashboard is a browser-based UI for browsing scripts, executing pipelines, and visualizing DAGs. It is served by `ConstellationServer` when enabled.

## Quick Start

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withDashboard               // Enable with default config
  .run
```

Access at `http://localhost:8080/dashboard` (or your configured port).

## Configuration

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
| `CONSTELLATION_DASHBOARD_ENABLED` | `true` | Enable/disable dashboard endpoints |

## Security Model

### Authentication

The dashboard inherits security settings from the parent `ConstellationServer`. When authentication is enabled, all dashboard API endpoints require a valid API key.

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withDashboard
  .withAuth(AuthConfig(hashedKeys = List(
    HashedApiKey("admin-key", ApiRole.Admin),
    HashedApiKey("viewer-key", ApiRole.ReadOnly)
  )))
  .run
```

**Role permissions for dashboard access:**

| Role | Dashboard Access | Execution | History Delete |
|------|------------------|-----------|----------------|
| `Admin` | Full access | Yes | Yes |
| `Execute` | Full access | Yes | No |
| `ReadOnly` | View only | No | No |

### Public Paths

The following paths are always accessible without authentication:

- `/health` - Health check endpoint
- `/health/live` - Kubernetes liveness probe
- `/health/ready` - Kubernetes readiness probe
- `/metrics` - Prometheus metrics (if enabled)

### Path Traversal Protection

:::warning Security Note
Never expose the dashboard to the public internet without proper authentication configured. Always use `withAuth()` when deploying in production environments.
:::

The file browser API validates all file paths to prevent directory traversal attacks. Requests that attempt to access files outside the configured `cstDirectory` are rejected with a 400 Bad Request.

### File Size Limits

To prevent memory exhaustion:

- Maximum file size for content serving: **10 MB**
- Maximum directory recursion depth: **20 levels**

### CORS Configuration

When exposing the dashboard to external origins, configure CORS:

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withDashboard
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .run
```

## Core Features

### File Browser

The left panel displays `.cst` files from the configured directory as a tree structure.

**Features:**
- Hierarchical folder view with expand/collapse
- Click a file to load it in the editor
- Automatic filtering of development directories (`node_modules`, `.git`, `target`, etc.)
- Files sorted alphabetically with folders first

**Screenshot description:** A sidebar showing a tree of `.cst` files with folder icons for directories and document icons for script files. Selected file is highlighted in blue.

### Script Editor

The central panel provides a code editor with live compilation preview.

**Input Form:**
Inputs declared with `in` are displayed as a form with type-appropriate controls:

| Type | Control |
|------|---------|
| `String` | Text input |
| `Int` | Number input (step=1) |
| `Long` | Number input |
| `Boolean` | Checkbox |
| `Float` / `Double` | Number input (decimal) |
| `List<T>` | JSON text area |
| `Map<K,V>` | JSON text area |

**Example annotations:**
Use `@example` annotations to pre-populate input fields:

```constellation
@example("Hello, World!")
in text: String

@example(42)
in count: Int
```

**Live Preview:**
As you edit code, the DAG visualization updates in real-time. Compilation errors appear in a banner below the editor.

### Execution Output

After clicking **Run** (or pressing `Ctrl+Enter`):

- **Success:** Outputs display as formatted JSON with syntax highlighting
- **Failure:** Error message with stack trace in a red-highlighted panel
- **Suspended:** For pipelines with missing inputs, shows partial results and required inputs

### DAG Visualization

The right panel renders the pipeline as an interactive graph using Cytoscape.js.

**Node Types:**

| Node Type | Color | Shape | Description |
|-----------|-------|-------|-------------|
| Input | Green (#56d364) | Circle | Pipeline inputs |
| Output | Blue (#58a6ff) | Circle | Pipeline outputs |
| Operation | Purple (#d2a8ff) | Rectangle | Module calls |
| Literal | Orange (#ffa657) | Rectangle | Constant values |
| Conditional | Red (#ff7b72) | Diamond | If/else nodes |
| Merge | Pink (#f778ba) | Octagon | Branch merge points |

**Interactions:**

| Action | Result |
|--------|--------|
| Click + drag background | Pan the view |
| Mouse wheel | Zoom in/out |
| Click node | Show node details in side panel |
| Hover node | Show tooltip with type and value |

**Layout Controls:**

| Button | Description |
|--------|-------------|
| **TB** | Top-to-bottom hierarchical layout |
| **LR** | Left-to-right hierarchical layout |
| **+** | Zoom in (10%) |
| **-** | Zoom out (10%) |
| **Fit** | Fit entire DAG in viewport |

**Resizing:**
Drag the handle between the editor and DAG panels to resize.

### Execution History

The History view lists all stored executions with:

- DAG name and script path
- Execution status (completed, failed, running)
- Timestamp and duration
- Filter by script path

**Detail View:**
Click an execution to see:
- Full input/output values
- Per-node execution timing
- DAG visualization at execution time
- Error details (if failed)

## Execution History Retention

### Storage Configuration

Execution history is stored in memory by default with LRU (Least Recently Used) eviction:

```scala
DashboardConfig(
  maxStoredExecutions = 1000,  // Maximum entries before eviction
  defaultSampleRate = 1.0      // Store all executions (1.0) or sample (0.0-1.0)
)
```

### Sampling

For high-throughput scenarios, use sampling to reduce storage:

```scala
DashboardConfig(
  defaultSampleRate = 0.1  // Store 10% of executions
)
```

Sampling uses consistent hashing based on DAG name, so the same pipeline gets sampled consistently.

**Per-request override:**
The API accepts a `sampleRate` parameter to override the default:

```json
{
  "scriptPath": "example.cst",
  "inputs": {},
  "sampleRate": 1.0
}
```

### Persistence

By default, execution history is stored in memory and lost on server restart. For persistent storage, implement a custom `ExecutionStorage`:

```scala
// Custom storage implementation
val customStorage: ExecutionStorage[IO] = new ExecutionStorage[IO] {
  // Implement persistence to database, S3, etc.
}

// Use with dashboard
val routes = new DashboardRoutes(
  constellation, compiler, customStorage, config
)
```

## Export/Import Pipelines

### Exporting DAG Specifications

Use the API to export pipeline definitions:

```bash
# Export compiled pipeline as JSON
curl http://localhost:8080/api/v1/preview \
  -H "Content-Type: application/json" \
  -d '{"source": "in x: Int\nout x"}' \
  | jq '.dagVizIR'
```

### Importing Pipelines

Pipelines are loaded by placing `.cst` files in the configured directory:

```bash
# Add a new script
cp my-pipeline.cst ./scripts/

# Refresh the file browser in the dashboard
# Or call the refresh API
curl http://localhost:8080/api/v1/files
```

### Execution Export

Export execution history programmatically:

```bash
# List recent executions
curl "http://localhost:8080/api/v1/executions?limit=100"

# Get specific execution details
curl http://localhost:8080/api/v1/executions/{executionId}

# Get DAG visualization for an execution
curl http://localhost:8080/api/v1/executions/{executionId}/dag
```

## Keyboard Shortcuts

:::tip Productivity Boost
Master the keyboard shortcuts to speed up your workflow. `Ctrl+Enter` to execute and `Escape` to close panels are the most frequently used shortcuts.
:::

| Shortcut | Context | Action |
|----------|---------|--------|
| `Ctrl+Enter` / `Cmd+Enter` | Editor | Execute current script |
| `Ctrl+S` / `Cmd+S` | Editor | Save (if editing enabled) |
| `Escape` | Node details | Close details panel |

## Dark Mode / Themes

The dashboard uses a dark theme by default, optimized for extended use and DAG visualization contrast.

**Color Palette:**

| Element | Color |
|---------|-------|
| Background (primary) | #0d1117 |
| Background (secondary) | #161b22 |
| Text (primary) | #f0f6fc |
| Text (secondary) | #8b949e |
| Accent | #58a6ff |
| Success | #3fb950 |
| Error | #f85149 |
| Warning | #d29922 |

**Customization:**

Currently, themes are not user-configurable. To customize colors, modify the CSS variables in `main.css`:

```css
:root {
  --bg-primary: #0d1117;
  --bg-secondary: #161b22;
  --text-primary: #f0f6fc;
  --accent-primary: #58a6ff;
  /* ... */
}
```

## Performance Tuning for Large DAGs

:::note Browser Requirements
For optimal performance, use Chrome or Firefox with hardware acceleration enabled. Safari and Edge work but may have slower WebGL rendering for large DAGs.
:::

### Rendering Performance

For DAGs with 100+ nodes, consider these optimizations:

**1. Use Hierarchical Layout:**
The `TB` (top-to-bottom) or `LR` (left-to-right) layouts use the Dagre algorithm which is optimized for directed graphs. Avoid force-directed layouts for large DAGs.

**2. Limit Node Details:**
Click a node to see details rather than displaying all information inline.

**3. Browser Performance:**
- Use Chrome or Firefox for best WebGL performance
- Ensure hardware acceleration is enabled
- Close other browser tabs when visualizing large DAGs

### DAG Size Limits

| DAG Size | Render Time | Recommendation |
|----------|-------------|----------------|
| < 50 nodes | < 100ms | All layouts work well |
| 50-200 nodes | 100-500ms | Use hierarchical layout |
| 200-500 nodes | 500ms-2s | Consider splitting pipeline |
| > 500 nodes | > 2s | Split into sub-pipelines |

### Memory Optimization

For large execution histories:

```scala
DashboardConfig(
  maxStoredExecutions = 100,   // Reduce for memory savings
  defaultSampleRate = 0.01     // Store 1% of executions
)
```

## Custom Panels Overview

The dashboard is composed of modular JavaScript components:

| Component | File | Responsibility |
|-----------|------|----------------|
| File Browser | `file-browser.js` | Directory tree navigation |
| Code Editor | `code-editor.js` | Script editing with live preview |
| DAG Visualizer | `dag-visualizer.js` | Cytoscape.js graph rendering |
| Execution Panel | `execution-panel.js` | Input/output forms and history |
| Pipelines Panel | `pipelines-panel.js` | Pipeline management and canary status |

### Extending the Dashboard

To add custom panels:

1. Create a new component in `dashboard/static/js/components/`
2. Register it in `main.js`
3. Add HTML structure in `index.html`
4. Style in `main.css`

**Example custom component structure:**

```javascript
class CustomPanel {
  constructor(containerId, options = {}) {
    this.container = document.getElementById(containerId);
    this.options = options;
  }

  init() {
    // Initialize the panel
  }

  render(data) {
    // Render content
  }

  destroy() {
    // Cleanup
  }
}
```

### Component Communication

Components communicate through the main `ConstellationDashboard` class:

```javascript
// In main.js
window.dashboard.customPanel = new CustomPanel('custom-container');
window.dashboard.customPanel.init();

// Components can access each other via window.dashboard
this.dagVisualizer.render(dagVizIR);
```

## Architecture

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

See the internal developer documentation (`docs/dev/playwright-dev-loop.md` in the repository) for the full protocol.

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

- [VSCode Extension](./vscode-extension.md) - IDE integration for `.cst` files
- [LSP Integration](./lsp-integration.md) - WebSocket protocol and message formats
- [Troubleshooting](./troubleshooting.md) - Common issues and solutions
