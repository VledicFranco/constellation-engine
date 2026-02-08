# Tooling Philosophy

> Why investing in developer tooling is core to Constellation Engine, not an afterthought.

---

## The Problem

Pipeline orchestration frameworks typically provide:
- A runtime for executing pipelines
- An API for submitting pipelines
- Maybe a CLI for ad-hoc operations

Developers are left to fend for themselves:
- **No feedback during authoring.** Syntax errors discovered at runtime.
- **No visibility during execution.** Pipelines are black boxes.
- **No standardization across IDEs.** Each editor has different (or no) support.
- **No unified development environment.** Switching between tools to write, test, and observe.

The result is friction. Developers spend time on mechanics instead of logic. Errors that could be caught in milliseconds take minutes to surface. Understanding pipeline behavior requires reading code instead of observing execution.

---

## The Bet

Developer tooling is **core infrastructure**, not a polish feature. Investing in tooling multiplies developer productivity and reduces the cost of every pipeline authored against the framework.

Constellation Engine treats tooling as first-class:

1. **LSP is the single protocol.** One implementation serves all editors.
2. **Dashboard is a specialized IDE and observability tool.** Write, test, visualize, and monitor pipelines in one place.
3. **Error messages are actionable.** They tell you what to fix, not just what broke.

---

## Design Decisions

### 1. LSP as the Single Protocol

The Language Server Protocol is a standardized interface between editors and language services. By implementing LSP once, Constellation Engine supports every editor that speaks LSP: VSCode, Neovim, Emacs, Sublime Text, JetBrains IDEs.

```
┌──────────────────────────────────────────────────────┐
│                   Any LSP Client                      │
│  (VSCode, Neovim, Emacs, Sublime, JetBrains, etc.)   │
└──────────────────────────────────────────────────────┘
                          │
                          │ JSON-RPC over WebSocket
                          ▼
┌──────────────────────────────────────────────────────┐
│          ConstellationLanguageServer                  │
│  (autocomplete, diagnostics, hover, semantic tokens)  │
└──────────────────────────────────────────────────────┘
```

**Why:** Maintaining N editor plugins is O(N) work. Maintaining one LSP server is O(1) work. The LSP abstracts away editor differences.

**What we get:**
- Autocomplete in every editor
- Real-time diagnostics in every editor
- Hover documentation in every editor
- Semantic highlighting in every editor

**What we give up:**
- Editor-specific features (VSCode's native debugging UI, for example)
- Deep editor integration (we use generic LSP, not editor APIs)

The trade-off is correct. The 90% case is covered by LSP; the 10% case can use editor-specific extensions on top.

### 2. Dashboard as Specialized IDE and Observability Tool

The dashboard is a **specialized IDE** purpose-built for Constellation pipeline development. It combines authoring, testing, and observability in a single unified interface connected to a live Constellation server.

```
┌──────────────────────────────────────────────────────────────┐
│                  Constellation Dashboard                      │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │ File Browser│ │ Code Editor │ │ DAG Visualization       │ │
│  │             │ │ + Syntax HL │ │ + Live Execution State  │ │
│  │             │ │ + Errors    │ │ + Performance Profiling │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Execution Panel: Inputs | Outputs | History | Pipelines │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
                          │
                          │ HTTP + WebSocket
                          ▼
┌──────────────────────────────────────────────────────────────┐
│              Constellation Server                             │
│  (Runtime, Compiler, LSP, Pipeline Store, Execution History) │
└──────────────────────────────────────────────────────────────┘
```

**Why a specialized IDE:** General-purpose editors (VSCode, Neovim) excel at text editing but lack pipeline-specific capabilities:
- Real-time DAG visualization as you type
- Live execution state overlay on the graph
- Integrated performance profiling
- Module discovery and documentation
- Pipeline lifecycle management (versions, canary, rollback)

**The dashboard provides a complete development workflow:**

| Capability | Description |
|------------|-------------|
| **Write** | Code editor with syntax highlighting and live compilation feedback |
| **Visualize** | DAG updates in real-time as code changes |
| **Test** | Execute pipelines with input forms, see outputs immediately |
| **Debug** | Inspect intermediate values, identify bottlenecks |
| **Monitor** | Execution history, timing breakdown, error tracking |
| **Manage** | Pipeline versions, canary deployments, suspend/resume |

**Why visualization matters:** Humans process visual information faster than text. A DAG with 20 nodes is comprehensible at a glance; the equivalent 20 lines of code require sequential reading.

**Complementary to general IDEs:** Developers can still use VSCode or their preferred editor via LSP. The dashboard is optimized for the pipeline development inner loop: write → visualize → test → iterate. For larger projects or complex refactoring, a full IDE with LSP integration remains valuable.

### 3. Actionable Error Messages

Bad error message:
```
Error: Type mismatch at line 3
```

Good error message:
```
Error: Type mismatch at line 3, column 12
  Expected: String
  Found:    Int

  Did you mean to use ToString() to convert the integer?
```

Constellation Engine's tooling prioritizes **actionable** errors:

1. **Location.** Exactly where the error is (line, column, span).
2. **Context.** What was expected vs. what was found.
3. **Suggestion.** How to fix it, when possible.

This investment pays off in developer time saved. A precise error message that suggests a fix costs seconds to resolve. A vague error message can cost minutes or hours.

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| LSP as single protocol | One implementation, all editors | Editor-specific features require separate work |
| Dashboard as specialized IDE | Complete dev workflow in browser | Maintaining two editing surfaces (dashboard + IDE) |
| WebSocket for LSP | Real-time bidirectional communication | Slightly more complex than HTTP |
| Actionable errors | Faster error resolution | More work per error message |

### What We Gave Up

- **Offline editing.** Dashboard requires server connection for compilation and execution.
- **Editor-native debugging.** Using LSP debugging, not editor-native debugging APIs.
- **Single editing surface.** Developers may use both dashboard and IDE; we embrace this rather than force one tool.

These limitations are intentional trade-offs. The dashboard excels at the pipeline-specific workflow; general IDEs excel at large-scale editing and refactoring.

---

## Influences

- **Language Server Protocol (Microsoft):** Standardized IDE-language communication
- **Monaco Editor:** Browser-based editing with LSP support
- **Cytoscape.js:** Graph visualization in the browser
- **GraphQL Playground:** Inspiration for the interactive execution experience
- **Rust's error messages:** Inspiration for actionable diagnostics

The key insight from these tools: developer tooling is a force multiplier. Every hour invested in tooling saves many hours of developer friction.

---

## Summary

Constellation Engine's tooling philosophy:

1. **One protocol (LSP) serves all editors.** Invest in the protocol, not N plugins.
2. **Dashboard is a specialized IDE and observability tool.** Write, test, visualize, and monitor in one place.
3. **Error messages suggest fixes.** Actionable feedback reduces friction.
4. **Tooling is infrastructure.** It is maintained with the same rigor as the runtime.

Tooling is not a feature to add later. It is core to the developer experience and is designed alongside the language and runtime.
