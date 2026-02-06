# Tooling

> **Path**: `docs/tooling/`
> **Parent**: [docs/](../README.md)

Developer tools for pipeline authoring.

## Contents

| File | Description |
|------|-------------|
| [dashboard.md](./dashboard.md) | Web UI for browsing, editing, running |
| [vscode.md](./vscode.md) | VSCode extension features |
| [lsp.md](./lsp.md) | Language Server Protocol details |

## Overview

| Tool | Purpose | Access |
|------|---------|--------|
| Dashboard | Browser-based IDE | `GET /dashboard` |
| VSCode Extension | IDE integration | Marketplace |
| LSP | Protocol for any IDE | `WS /lsp` |

## Dashboard

Web-based UI for pipeline development:

- **File browser** — Navigate .cst files
- **Editor** — Syntax highlighting, inline errors
- **Input form** — Type-appropriate controls
- **Run** — Execute with Ctrl+Enter
- **DAG view** — Interactive visualization
- **History** — Recent executions

```
http://localhost:8080/dashboard
```

## VSCode Extension

IDE features for .cst files:

| Feature | Shortcut |
|---------|----------|
| Autocomplete | Ctrl+Space |
| Run script | Ctrl+Shift+R |
| DAG view | Ctrl+Shift+D |
| Hover docs | (hover) |
| Diagnostics | (real-time) |

### Configuration

```json
{
  "constellation.server.url": "ws://localhost:8080/lsp"
}
```

## LSP Protocol

WebSocket-based Language Server Protocol:

| Method | Feature |
|--------|---------|
| `textDocument/completion` | Autocomplete |
| `textDocument/hover` | Documentation |
| `textDocument/publishDiagnostics` | Errors |
| `textDocument/semanticTokens` | Highlighting |
| `constellation/executePipeline` | Run script |

Connect any LSP-compatible editor to `ws://localhost:8080/lsp`.

## Enabling/Disabling

```bash
# Dashboard
CONSTELLATION_DASHBOARD_ENABLED=true  # default

# LSP is always available at /lsp when server runs
```
