# Agent 1 Work Queue

**Track:** Compiler/Language
**Focus:** Parser, Type System, Core Language

## Current Assignment

| Priority | Issue | Title | Status | Blocked By |
|----------|-------|-------|--------|------------|
| 1 | [#122](https://github.com/VledicFranco/constellation-engine/issues/122) | DAG Visualization: Core IR and Sugiyama Layout | Ready | - |
| 2 | [#125](https://github.com/VledicFranco/constellation-engine/issues/125) | DAG Visualization: Alternative Renderers | Blocked | #122 |

## Notes

**Phase: DAG Visualization Redesign**

#122 creates the foundational `DagVizIR` data structure and Sugiyama layout algorithm. This must complete before any other visualization work can proceed.

#125 (Mermaid, ASCII, DOT renderers) can start as soon as #122 is done - it only needs the IR, not the SVG renderer.

**Key files to create:**
```
modules/lang-compiler/src/main/scala/io/constellation/lang/viz/
├── DagVizIR.scala
├── DagVizCompiler.scala
├── SugiyamaLayout.scala
├── MermaidRenderer.scala   (for #125)
├── ASCIIRenderer.scala     (for #125)
└── DOTRenderer.scala       (for #125)
```

---

## Completed Work (Archived)

See `agents/archive/` for historical completed issues.

**Summary:** 27 issues completed across @example annotations, architecture improvements (Phases 1-3), and core language features.

---
*Last updated: 2026-01-23*
