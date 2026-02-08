# Tooling

> **Path**: `organon/features/tooling/`
> **Parent**: [features/](../README.md)

Dashboard, LSP integration, and VSCode extension.

## Ethos Summary

- Tooling **observes**, it does not **control** (read-only where possible)
- LSP is the **single protocol** for IDE integration
- Dashboard is for **visualization**, not editing
- Error messages are **actionable**, not just descriptive

## Contents

| File | Description |
|------|-------------|
| PHILOSOPHY.md | Why invest in tooling |
| ETHOS.md | Constraints for LLMs working on tooling |
| dashboard.md | Web-based pipeline visualization |
| lsp.md | Language Server Protocol implementation |
| vscode.md | VSCode extension features |

## Quick Reference

### Dashboard
- URL: `http://localhost:8080/`
- Features: File browser, script runner, DAG visualization, execution history

### LSP
- WebSocket: `ws://localhost:8080/lsp`
- Features: Autocomplete, hover docs, diagnostics, go-to-definition

### VSCode Extension
- Shortcuts: `Ctrl+Shift+R` (run), `Ctrl+Shift+D` (show DAG)
- Features: Syntax highlighting, LSP integration, inline diagnostics

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `http-api` | Dashboard routes, LSP WebSocket | `DashboardRoutes.scala`, `LspWebSocketHandler.scala` |
| `lang-lsp` | LSP implementation | `ConstellationLanguageServer.scala`, `DocumentManager.scala` |
| `vscode-extension` | VSCode integration | `extension.ts`, `ScriptRunnerPanel.ts` |
| `dashboard/` | Browser UI source | `src/main.ts`, `src/dag.ts` |
| `dashboard-tests/` | Playwright E2E tests | `tests/*.spec.ts` |
