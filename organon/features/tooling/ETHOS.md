# Tooling Ethos

> Behavioral constraints for LLMs working on tooling features.

---

## Identity

### What Tooling IS

- **A specialized IDE experience.** Dashboard provides write, visualize, test, debug, monitor workflow.
- **LSP-based.** All IDE features go through Language Server Protocol.
- **Observable.** Execution state, errors, and performance are always visible.

### What Tooling is NOT

- **Not a general-purpose IDE.** Use VSCode/Neovim for large-scale refactoring.
- **Not editor-specific.** LSP is editor-agnostic; no Vim/Emacs-specific code in lang-lsp.
- **Not offline-capable.** Dashboard requires server connection.

---

## Invariants

1. **LSP is the single protocol.** All IDE features go through LSP. No editor-specific implementations.
2. **Error messages are actionable.** Every diagnostic includes location, context, and suggestion when possible.
3. **WebSocket is the transport.** LSP uses WebSocket for bidirectional communication.
4. **Dashboard tests are required.** UI changes require Playwright E2E tests.

---

## Principles (Prioritized)

1. **Inner loop speed** — Write → visualize → test → iterate must be fast.
2. **Actionable errors** — Include location, expected vs found, and fix suggestions.
3. **Keyboard accessible** — All interactive elements navigable via keyboard.
4. **Dark theme consistency** — UI additions use established color palette.

---

## Decision Heuristics

| Situation | Use LSP | Use HTTP |
|-----------|---------|----------|
| Triggered by editor events | ✓ | |
| Needs push updates | ✓ | |
| One-shot request-response | | ✓ |
| Accessed outside editors | | ✓ |

| Dashboard Feature? | Yes | No |
|--------------------|-----|-----|
| Helps understand pipelines | ✓ | |
| Speeds up inner loop | ✓ | |
| Large-scale refactoring | | ✓ |
| Internal implementation detail | | ✓ |

---

## Component Boundaries

| Component | Responsibility |
|-----------|----------------|
| `lang-lsp` | LSP protocol, autocomplete, diagnostics, hover, semantic tokens |
| `http-api` | WebSocket handler, dashboard routes, static serving |
| `vscode-extension` | VSCode integration, keybindings, UI panels |
| `dashboard/` | TypeScript browser UI |
| `dashboard-tests/` | Playwright E2E tests |

---

## Error Message Format

```
Error: <description> (E<code>)
  at <file>:<line>:<column>
  <context with caret pointer>
  Expected: <X>  Found: <Y>
  Suggestion: <fix>
```

| Error Range | Category |
|-------------|----------|
| E001-E099 | Syntax |
| E100-E199 | Semantic |
| E200-E299 | Compilation |
| E300-E399 | Runtime |
| E400-E499 | LSP protocol |

---

## Performance Targets

| Operation | Target (p95) |
|-----------|--------------|
| Autocomplete | < 50ms |
| Diagnostics | < 100ms |
| Hover | < 30ms |
| Dashboard load | < 500ms |
| DAG render (< 50 nodes) | < 100ms |

---

## Testing Requirements

| Component | Required Tests |
|-----------|----------------|
| LSP changes | Unit + integration in `lang-lsp/test` |
| Dashboard changes | Smoke (`make test-dashboard-smoke`) + E2E |
| VSCode changes | Unit + integration + manual F5 testing |

---

## Out of Scope

- Offline dashboard
- Editor-native debugging (use LSP debug protocol)
- Custom Neovim/Emacs plugins (LSP is sufficient)
- Dashboard authentication (inherits server auth)
- Large-scale refactoring tools
