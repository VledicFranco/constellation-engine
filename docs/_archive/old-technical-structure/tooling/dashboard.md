# Dashboard

> **Path**: `docs/tooling/dashboard.md`
> **Parent**: [tooling/](./README.md)

Browser-based UI for browsing scripts, executing pipelines, and visualizing DAGs.

## Quick Start

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withDashboard
  .run
```

Access at `http://localhost:8080/dashboard`.

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

## Features

### File Browser

Left panel displays `.cst` files as a tree:

- Hierarchical folder navigation
- Click file to load in editor
- Auto-filters `node_modules`, `.git`, `target`
- Folders first, alphabetically sorted

### Script Editor

Central panel with live compilation preview:

| Input Type | Control |
|------------|---------|
| `String` | Text input |
| `Int` | Number input (step=1) |
| `Boolean` | Checkbox |
| `Float`/`Double` | Number input (decimal) |
| `List<T>` | JSON text area |
| `Map<K,V>` | JSON text area |

**Example annotations** pre-populate inputs:

```constellation
@example("Hello, World!")
in text: String

@example(42)
in count: Int
```

### DAG Visualization

Right panel renders pipeline graph using Cytoscape.js:

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
| Mouse wheel | Zoom |
| Click node | Show details |
| Hover node | Show tooltip |

**Layout controls:** TB (top-bottom), LR (left-right), +/-, Fit

### Execution History

Lists all stored executions:

- DAG name and script path
- Status (completed, failed, running)
- Timestamp and duration
- Filter by script path

Click execution for full input/output values and per-node timing.

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Enter` | Execute script |
| `Ctrl+S` | Save (if editing enabled) |
| `Escape` | Close details panel |

## Security

Dashboard inherits server security settings:

| Role | Dashboard | Execution | History Delete |
|------|-----------|-----------|----------------|
| `Admin` | Full | Yes | Yes |
| `Execute` | Full | Yes | No |
| `ReadOnly` | View only | No | No |

Public paths exempt from auth: `/health`, `/health/live`, `/health/ready`, `/metrics`.

### Path Traversal Protection

All file paths validated to prevent directory traversal. Requests outside `cstDirectory` rejected with 400.

### Limits

- Maximum file size: 10 MB
- Maximum directory depth: 20 levels

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
  maxStoredExecutions = 100,
  defaultSampleRate = 0.01  // Store 1% of executions
)
```

## Theme

Dark theme optimized for DAG visualization:

| Element | Color |
|---------|-------|
| Background | #0d1117 |
| Text | #f0f6fc |
| Accent | #58a6ff |
| Success | #3fb950 |
| Error | #f85149 |

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/files` | List script files |
| `POST /api/v1/preview` | Compile and preview DAG |
| `POST /api/v1/execute` | Execute pipeline |
| `GET /api/v1/executions` | List execution history |
| `GET /api/v1/executions/{id}` | Get execution details |

## Related

- [vscode.md](./vscode.md) - IDE integration
- [lsp.md](./lsp.md) - Protocol details
