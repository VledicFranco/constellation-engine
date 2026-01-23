# Agent 3 Work Queue

**Track:** Integration
**Focus:** LSP, HTTP API, VSCode Extension

## Current Assignment

| Priority | Issue | Title | Status | Blocked By |
|----------|-------|-------|--------|------------|
| 1 | [#123](https://github.com/VledicFranco/constellation-engine/issues/123) | DAG Visualization: SVG Renderer and VSCode Integration | Blocked | #122 |
| 2 | [#124](https://github.com/VledicFranco/constellation-engine/issues/124) | DAG Visualization: Execution State (LSP + VSCode part) | Blocked | #122, #123 |

## Notes

**Phase: DAG Visualization Redesign**

### Issue #123 - SVG Renderer + VSCode

Once Agent 1 completes #122 (Core IR), this work can begin:

**Backend (lang-compiler):**
- Create `SVGRenderer.scala` - generate SVG from `DagVizIR`
- Node shapes per design spec (input, output, module, merge, project, etc.)
- Edge styling (solid, dashed, arrows)

**Frontend (vscode-extension):**
- Update `DagVisualizerPanel.ts` to consume new `DagVizIR` format
- Call new `getDagVisualization` LSP method
- Maintain backward compatibility

### Issue #124 - Execution State (LSP + VSCode portions)

Agent 2 handles the runtime `ExecutionTracker`. Agent 3 handles:

**LSP:**
- Add execution state to `DagVizIR` response
- Endpoint: `getDagVisualization(uri, executionId?)`

**VSCode:**
- Render execution states (pending, running, completed, failed)
- Display values below completed nodes
- Show errors inline for failed nodes

**Key files to modify:**
```
modules/lang-compiler/src/main/scala/io/constellation/lang/viz/
└── SVGRenderer.scala                    # New

modules/lang-lsp/src/main/scala/io/constellation/lsp/
├── protocol/LspMessages.scala           # Add new message types
└── ConstellationLanguageServer.scala    # Add getDagVisualization

vscode-extension/src/
├── panels/DagVisualizerPanel.ts         # Major update
└── utils/dagLayoutUtils.ts              # May be simplified/removed
```

---

## Completed Work (Archived)

See `agents/archive/` for historical completed issues.

**Summary:** 24 issues completed across LSP enhancements, VSCode extension features, and architecture improvements (Phases 1-3).

---
*Last updated: 2026-01-23*
