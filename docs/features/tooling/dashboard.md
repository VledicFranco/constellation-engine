# Dashboard

> **Path**: `docs/features/tooling/dashboard.md`
> **Parent**: [tooling/](./README.md)

> **Status: BETA**
> The dashboard is currently in beta. Features are functional but may have rough edges. We welcome feedback and bug reports. Some advanced features (Monaco Editor, Performance Profiling) are experimental.

Browser-based UI for browsing scripts, executing pipelines, and visualizing DAGs.

---

## Overview

The Constellation Dashboard is a web-based interface that provides:

- **File browser** for navigating `.cst` script files
- **Script runner** for executing pipelines with input forms
- **DAG visualization** for understanding pipeline structure
- **Execution history** for reviewing past executions

The dashboard is read-only by design. It visualizes and executes pipelines but does not edit them. Editing happens in the IDE, where LSP provides real-time feedback.

---

## Quick Start

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withDashboard
  .run
```

Access at `http://localhost:8080/dashboard`.

---

## Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `CONSTELLATION_CST_DIR` | Current directory | Directory containing `.cst` files |
| `CONSTELLATION_SAMPLE_RATE` | `1.0` | Execution sampling rate (0.0-1.0) |
| `CONSTELLATION_MAX_EXECUTIONS` | `1000` | Maximum stored executions |
| `CONSTELLATION_DASHBOARD_ENABLED` | `true` | Enable/disable dashboard |

### Programmatic Configuration

```scala
import io.constellation.http.DashboardConfig

ConstellationServer
  .builder(constellation, compiler)
  .withDashboard(DashboardConfig(
    cstDirectory = java.nio.file.Paths.get("./scripts"),
    maxStoredExecutions = 500,
    defaultSampleRate = 0.5
  ))
  .run
```

---

## Features

### File Browser

The left panel displays `.cst` files as a navigable tree:

- Hierarchical folder navigation with expand/collapse
- Click file to load in the script runner
- Auto-filters `node_modules`, `.git`, `target` directories
- Folders sorted first, then files alphabetically

**Keyboard navigation:**
| Key | Action |
|-----|--------|
| `Arrow Up/Down` | Navigate tree |
| `Enter` | Select file / Toggle folder |
| `Arrow Right` | Expand folder |
| `Arrow Left` | Collapse folder |

### Script Runner

The central panel provides a live compilation preview and execution interface:

**Input controls by type:**

| Input Type | Control |
|------------|---------|
| `String` | Text input |
| `Int` | Number input (step=1) |
| `Boolean` | Checkbox |
| `Float`/`Double` | Number input (decimal) |
| `List<T>` | JSON text area |
| `Map<K,V>` | JSON text area |

**Example annotations** pre-populate input values:

```constellation
@example("Hello, World!")
in text: String

@example(42)
in count: Int
```

When a file with example annotations loads, the input fields are pre-filled.

### DAG Visualization

The right panel renders the pipeline graph using Cytoscape.js:

**Node types and styles:**

| Node Type | Color | Shape |
|-----------|-------|-------|
| Input | Green (#56d364) | Circle |
| Output | Blue (#58a6ff) | Circle |
| Operation | Purple (#d2a8ff) | Rectangle |
| Literal | Orange (#ffa657) | Rectangle |
| Conditional | Red (#ff7b72) | Diamond |
| Merge | Pink (#f778ba) | Octagon |

**Interactions:**

| Action | Result |
|--------|--------|
| Drag background | Pan view |
| Mouse wheel | Zoom in/out |
| Click node | Show node details |
| Hover node | Show tooltip with type |

**Layout controls:**

| Button | Action |
|--------|--------|
| TB | Top-to-bottom layout (default) |
| LR | Left-to-right layout |
| + | Zoom in |
| - | Zoom out |
| Fit | Fit graph to viewport |

### IDE Features (Beta)

The dashboard includes experimental IDE features accessible from the toolbar:

| Feature | Icon | Description |
|---------|------|-------------|
| **Module Browser** | Hamburger menu | Search and browse available modules with documentation |
| **Performance Profile** | Bar chart | View execution timing waterfall after running a pipeline |
| **Monaco Editor** | Code brackets | Advanced editor with Constellation syntax highlighting |
| **Value Inspector** | (auto) | Click DAG nodes to inspect intermediate values |
| **Input Presets** | (in panel) | Save and load input configurations per script |
| **Error Panel** | (auto) | Enhanced compilation errors with source context |

These features are in beta and may change. To use:
1. Click the toolbar buttons to open features
2. Value Inspector appears when clicking executed DAG nodes
3. Error Panel shows automatically on compilation errors

### Execution History

The history panel lists stored executions:

- DAG name and script path
- Status: completed, failed, or running
- Timestamp and duration
- Filter by script path

Click any execution to view:
- Full input values
- Full output values
- Per-node timing breakdown
- Error details (if failed)

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Enter` | Execute script |
| `Ctrl+S` | Save (if editing enabled) |
| `Escape` | Close details panel |

---

## Security

The dashboard inherits server security settings:

| Role | Dashboard Access | Execution | History Delete |
|------|-----------------|-----------|----------------|
| `Admin` | Full | Yes | Yes |
| `Execute` | Full | Yes | No |
| `ReadOnly` | View only | No | No |

Public paths exempt from auth: `/health`, `/health/live`, `/health/ready`, `/metrics`.

### Path Traversal Protection

All file paths are validated to prevent directory traversal attacks. Requests for files outside `cstDirectory` are rejected with HTTP 400.

### Limits

- Maximum file size: 10 MB
- Maximum directory depth: 20 levels

---

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/files` | List script files |
| `POST /api/v1/preview` | Compile and preview DAG |
| `POST /api/v1/execute` | Execute pipeline |
| `GET /api/v1/executions` | List execution history |
| `GET /api/v1/executions/{id}` | Get execution details |

---

## Performance Tuning

| DAG Size | Render Time | Recommendation |
|----------|-------------|----------------|
| < 50 nodes | < 100ms | All layouts work |
| 50-200 nodes | 100-500ms | Use hierarchical layout |
| 200-500 nodes | 500ms-2s | Consider splitting pipeline |
| > 500 nodes | > 2s | Split into sub-pipelines |

### Memory Optimization

```scala
DashboardConfig(
  maxStoredExecutions = 100,       // Limit history size
  defaultSampleRate = 0.01         // Store 1% of executions
)
```

---

## Theme

Dark theme optimized for DAG visualization:

| Element | Color |
|---------|-------|
| Background | #0d1117 |
| Text | #f0f6fc |
| Accent | #58a6ff |
| Success | #3fb950 |
| Error | #f85149 |

---

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `http-api` | Dashboard routes, static file serving, API endpoints | `DashboardRoutes.scala`, `DashboardConfig.scala`, `DashboardModels.scala` |
| `http-api` | Execution storage and history | `ExecutionStorage.scala`, `ExecutionHelper.scala` |
| `http-api` | Security middleware | `AuthMiddleware.scala`, `RateLimitMiddleware.scala` |
| `lang-compiler` | DAG visualization data | `DagVizCompiler.scala` |
| `dashboard/` | TypeScript/HTML/CSS source | `src/main.ts`, `src/dag.ts`, `src/styles.css` |
| `dashboard-tests/` | Playwright E2E tests | `tests/*.spec.ts`, `pages/*.ts` |

---

## Troubleshooting

### Dashboard Not Loading

1. Check server is running: `curl http://localhost:8080/health`
2. Check port is correct in URL
3. Check browser console for JavaScript errors
4. Verify `withDashboard` is enabled in server config

### Files Not Appearing

1. Verify `CONSTELLATION_CST_DIR` points to correct directory
2. Check file has `.cst` extension
3. Check file is not in filtered directory (`node_modules`, `.git`, `target`)

### DAG Not Rendering

1. Check file compiles successfully (no red error banner)
2. Check browser console for rendering errors
3. Try a smaller pipeline to isolate the issue

### Execution Fails

1. Check all required inputs are provided
2. Check input types match expected types
3. Check server logs for detailed error message

---

## Related

- [lsp.md](./lsp.md) - LSP protocol for IDE integration
- [vscode.md](./vscode.md) - VSCode extension
