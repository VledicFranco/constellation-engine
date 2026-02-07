# Tooling Ethos

> Behavioral constraints for LLMs working on tooling features.

---

## Core Invariants

1. **Dashboard is a specialized IDE and observability tool.** It provides a complete pipeline development workflow: write, visualize, test, debug, monitor, and manage.

2. **LSP is the single protocol.** All IDE features (autocomplete, diagnostics, hover, semantic tokens) go through LSP. No editor-specific implementations.

3. **Error messages are actionable.** Every diagnostic includes location (line, column), context (expected vs. found), and suggestion (how to fix) when possible.

4. **Dashboard complements general IDEs.** The dashboard excels at pipeline-specific workflows; developers can also use VSCode/Neovim via LSP for large-scale editing.

5. **WebSocket is the transport.** LSP uses WebSocket for bidirectional communication. HTTP is used only for request-response patterns (REST API, file serving).

---

## Design Constraints

### When Adding LSP Features

- **Implement in `lang-lsp`, not in editor plugins.** The editor plugin should be a thin LSP client.
- **Follow LSP specification.** Use standard method names (`textDocument/completion`, not custom names).
- **Add tests in `lang-lsp/test`.** All LSP features must have unit tests.
- **Update server capabilities.** New features must be advertised in `initialize` response.
- **Document in three places:** Feature docs, language reference, and the LSP module README.

### When Adding Dashboard Features

- **Embrace the specialized IDE role.** The dashboard supports writing, testing, and observing pipelines. Features should enhance this workflow.
- **Use the existing API.** Dashboard features use `DashboardRoutes` endpoints, not direct module access.
- **Test with Playwright.** Dashboard features require E2E tests in `dashboard-tests/`.
- **Follow the dark theme.** UI additions use the established color palette.
- **Support keyboard navigation.** All interactive elements should be accessible via keyboard.
- **Optimize for the inner loop.** Features should make write → visualize → test → iterate as fast as possible.

### When Modifying Error Messages

- **Include location.** Line and column, at minimum. Span (start to end) when available.
- **Include context.** What was expected, what was found.
- **Include suggestion when possible.** "Did you mean X?" or "Try adding Y".
- **Use error codes.** Each error type has a stable code (e.g., `E001`). Codes are documented.
- **Test the message.** Error messages are tested alongside the behavior that produces them.

---

## Decision Heuristics

### "Should this be an LSP method or an HTTP endpoint?"

**LSP** if:
- It's triggered by editor events (file open, file change, cursor position)
- It needs to push updates to the client (diagnostics, progress)
- It's part of the standard LSP specification

**HTTP** if:
- It's a one-shot request-response (execute pipeline, get file list)
- It's accessed outside of editors (CLI, scripts, other tools)
- It doesn't need real-time bidirectional communication

### "Should the dashboard include this feature?"

**Yes** if:
- It helps understand pipeline structure or behavior
- It speeds up the write → visualize → test → iterate loop
- It provides observability into execution or performance
- It can be visualized or interacted with effectively

**No** if:
- It's better suited to a general IDE (large-scale refactoring, multi-file search)
- It's internal implementation detail
- It would clutter the UI without adding insight

### "Should this error message include a suggestion?"

**Yes** if:
- There's a common fix (typo correction, missing import)
- The suggestion is unambiguous (only one likely intent)
- The suggestion won't mislead (avoid guessing wrong)

**No** if:
- The error is ambiguous (multiple possible causes)
- The fix depends on developer intent (can't know what they wanted)
- Suggesting would require understanding business logic

---

## Component Boundaries

| Component | Tooling Responsibility |
|-----------|------------------------|
| `lang-lsp` | LSP protocol implementation, autocomplete, diagnostics, hover, semantic tokens |
| `http-api` | WebSocket handler for LSP, dashboard routes, static file serving |
| `vscode-extension` | VSCode-specific integration, keybindings, UI panels |
| `dashboard/` | TypeScript source for browser UI |
| `dashboard-tests/` | Playwright E2E tests for dashboard |

**Never:**
- Put IDE-specific logic in `lang-lsp` (LSP is editor-agnostic)
- Put LSP logic in `http-api` (http-api only handles transport)
- Put dashboard rendering in Scala (dashboard is TypeScript/HTML)
- Skip E2E tests for dashboard changes (UI changes require visual verification)

---

## Error Message Standards

All error messages must follow this format:

```
Error: <brief description> (E<code>)
  at <file>:<line>:<column>

  <context showing the error location>
        ^^^^^ <pointer to error>

  Expected: <what was expected>
  Found: <what was actually present>

  Suggestion: <how to fix, if known>
```

Example:

```
Error: Undefined module (E101)
  at pipeline.cst:5:10

  result = NonExistent(text)
           ^^^^^^^^^^^ undefined

  Expected: Module call
  Found: Unknown identifier 'NonExistent'

  Suggestion: Did you mean 'Uppercase'?
```

### Error Codes

| Range | Category |
|-------|----------|
| E001-E099 | Syntax errors |
| E100-E199 | Semantic errors (undefined, type mismatch) |
| E200-E299 | Compilation errors |
| E300-E399 | Runtime errors |
| E400-E499 | LSP protocol errors |

---

## What Is Out of Scope

Do not add:

- **Offline dashboard.** Dashboard requires server connection for compilation and execution.
- **Editor-native debugging.** Deep integration with VSCode debugger. Use LSP debug protocol.
- **Custom editor plugins.** Neovim/Emacs plugins beyond LSP client. LSP is sufficient.
- **Dashboard authentication.** Dashboard inherits server auth settings. No separate auth.
- **Large-scale refactoring tools.** Multi-file rename, search-replace across projects. Use a general IDE.

These boundaries are intentional. The dashboard excels at pipeline-specific workflows; general IDEs handle everything else.

---

## Testing Requirements

When modifying tooling:

### LSP Changes
1. **Unit tests** in `lang-lsp/test` for all new methods
2. **Integration tests** for end-to-end LSP flows
3. **Performance tests** for latency-sensitive operations (autocomplete < 50ms)

### Dashboard Changes
1. **Smoke tests** via `make test-dashboard-smoke`
2. **E2E tests** in `dashboard-tests/` for new features
3. **Screenshot audit** for visual changes (see Playwright dev loop)

### VSCode Extension Changes
1. **Unit tests** in `vscode-extension/src/test`
2. **Integration tests** for LSP client behavior
3. **Manual testing** via F5 Extension Development Host

All tooling components have dedicated test suites. Changes without tests will not be merged.

---

## Performance Targets

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Autocomplete response | < 50ms | 95th percentile |
| Diagnostics after edit | < 100ms | 95th percentile |
| Hover information | < 30ms | 95th percentile |
| Dashboard load | < 500ms | First contentful paint |
| DAG render (< 50 nodes) | < 100ms | Cytoscape layout complete |

If these targets are not met, investigate before merging. Performance regressions in tooling directly impact developer experience.
