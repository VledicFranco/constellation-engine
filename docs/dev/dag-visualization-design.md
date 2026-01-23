# DAG Visualization Design Document

## Overview

This document proposes a redesigned DAG visualization system for Constellation Engine. The goals are:
1. **Human readability** - Engineers should immediately understand the data flow
2. **Portability** - Render DAGs in multiple contexts (VSCode, web UI, docs, terminal)
3. **Execution state** - Show runtime data values at each node during/after execution

---

## Current State Analysis

### What Exists

**Data structures (Scala LSP → TypeScript):**
```typescript
interface DagStructure {
  modules: { [uuid: string]: ModuleNode };   // Operation nodes
  data: { [uuid: string]: DataNode };        // Data nodes
  inEdges: [string, string][];               // data → module
  outEdges: [string, string][];              // module → data
  declaredOutputs: string[];
}

interface ModuleNode { name: string; consumes: Map<string,string>; produces: Map<string,string> }
interface DataNode { name: string; cType: string }
```

**Current visualization (DagVisualizerPanel.ts):**
- Custom SVG rendering with manual layout
- Topological sort for layer positioning
- Node shapes: ellipse (input), rect (module), rounded rect (data), hexagon (output)
- Pan/zoom, search, export to PNG/SVG
- Execution state CSS classes (pending, running, completed, failed)

### Problems

1. **Layout is basic** - Simple top-down layers, no optimization for edge crossings or visual clarity
2. **Node density** - Complex DAGs become unreadable due to cramped spacing
3. **Edge routing** - Straight lines with no edge bundling; creates visual noise
4. **Type information** - Hidden until hover; not visible at a glance
5. **Execution values** - No actual data values shown, only state colors
6. **Not portable** - SVG generation is coupled to VSCode webview

---

## Design Goals

### G1: Clarity at First Glance
- Data flow direction is immediately obvious
- Node types distinguishable without color (accessibility)
- Important information visible without interaction

### G2: Scalable to Complex DAGs
- 50+ node DAGs should still be readable
- Hierarchical grouping for large pipelines
- Collapse/expand sections

### G3: Portable Rendering
- Render to: SVG, PNG, HTML (interactive), ASCII (terminal), Mermaid/Graphviz (docs)
- Single intermediate representation (IR) → multiple renderers

### G4: Execution Visualization
- Show data values at each node after execution
- Streaming updates during execution
- Failed node highlighting with error context

---

## Proposed Architecture

### Layer 1: DAG Visualization IR (Scala)

A new intermediate representation optimized for visualization, separate from the compiler IR.

```
┌─────────────────────────────────────────────────────────────┐
│                    DagVizIR (Scala)                         │
├─────────────────────────────────────────────────────────────┤
│ case class VizNode(                                         │
│   id: String,                                               │
│   kind: NodeKind,          // Input | Output | Operation |  │
│                            // Literal | Merge | Project     │
│   label: String,           // Display name                  │
│   typeSignature: String,   // e.g., "{ id: String, ... }"   │
│   position: Option[Pos],   // Computed by layout engine     │
│   collapsed: Boolean,      // For grouping                  │
│   executionState: Option[ExecutionState]                    │
│ )                                                           │
│                                                             │
│ case class VizEdge(                                         │
│   id: String,                                               │
│   source: String,                                           │
│   target: String,                                           │
│   label: Option[String],   // Parameter name for modules    │
│   edgeKind: EdgeKind       // Data | Control | Optional     │
│ )                                                           │
│                                                             │
│ case class ExecutionState(                                  │
│   status: Status,          // Pending | Running | Done | Err│
│   value: Option[Json],     // Actual runtime value          │
│   duration: Option[Long],  // Execution time in ms          │
│   error: Option[String]                                     │
│ )                                                           │
│                                                             │
│ case class DagVizIR(                                        │
│   nodes: List[VizNode],                                     │
│   edges: List[VizEdge],                                     │
│   groups: List[NodeGroup],  // For collapsible sections     │
│   metadata: VizMetadata     // Layout hints, title, etc.    │
│ )                                                           │
└─────────────────────────────────────────────────────────────┘
```

### Layer 2: Layout Engine (Scala)

Compute optimal positions before sending to renderers.

**Layout algorithms to support:**
1. **Sugiyama/Layered** - Classic DAG layout (current approach, but optimized)
2. **Force-directed** - Better for densely connected graphs
3. **Hierarchical** - Emphasize pipeline stages

**Layout optimizations:**
- Edge crossing minimization (barycenter method)
- Node compaction to reduce whitespace
- Port assignment for multi-input modules
- Edge bundling for parallel data flows

```scala
trait LayoutEngine {
  def layout(ir: DagVizIR, config: LayoutConfig): DagVizIR  // Returns IR with positions filled
}

case class LayoutConfig(
  direction: Direction,        // TopToBottom | LeftToRight
  algorithm: Algorithm,        // Sugiyama | Force | Hierarchical
  nodeSpacing: Int,
  layerSpacing: Int,
  edgeBundling: Boolean
)
```

### Layer 3: Renderers (Scala + TypeScript)

Multiple output formats from the same IR.

```
┌──────────────────┐    ┌───────────────────────────────────────┐
│   DagVizIR       │───►│  SVGRenderer                          │
│   (with layout)  │    │  → Static SVG for export              │
└────────┬─────────┘    └───────────────────────────────────────┘
         │              ┌───────────────────────────────────────┐
         ├─────────────►│  HTMLRenderer                         │
         │              │  → Interactive web component          │
         │              └───────────────────────────────────────┘
         │              ┌───────────────────────────────────────┐
         ├─────────────►│  MermaidRenderer                      │
         │              │  → Mermaid.js syntax for docs         │
         │              └───────────────────────────────────────┘
         │              ┌───────────────────────────────────────┐
         ├─────────────►│  ASCIIRenderer                        │
         │              │  → Terminal-friendly diagram          │
         │              └───────────────────────────────────────┘
         │              ┌───────────────────────────────────────┐
         └─────────────►│  DOTRenderer                          │
                        │  → Graphviz DOT format                │
                        └───────────────────────────────────────┘
```

---

## Visual Design Specification

### Node Shapes & Icons

Use consistent iconography to distinguish node types at a glance:

```
┌─────────────────────────────────────────────────────────────┐
│ INPUT                  OUTPUT                 OPERATION     │
│                                                             │
│   ╭──────────╮         ╭──────────╮         ┌──────────┐   │
│   │ ▶ order  │         │ result ▶ │         │ Module   │   │
│   │ Order    │         │ Response │         ├──────────┤   │
│   ╰──────────╯         ╰──────────╯         │ → a      │   │
│                                             │ ← result │   │
│   (rounded left)       (rounded right)      └──────────┘   │
│   Green accent         Blue accent          Gray header    │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ MERGE                  PROJECT              LITERAL         │
│                                                             │
│   ╭──────────╮         ╭──────────╮         ╭──────────╮   │
│   │ ⊕ merged │         │ ▣ subset │         │ # 42     │   │
│   │ Combined │         │ Selected │         │ Int      │   │
│   ╰──────────╯         ╰──────────╯         ╰──────────╯   │
│                                                             │
│   Purple accent        Orange accent        Teal accent    │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ CONDITIONAL            GUARD                BRANCH          │
│                                                             │
│   ╭──────────╮         ╭──────────╮         ╭──────────╮   │
│   │ ◇ if     │         │ ? guarded│         │ ⑂ branch │   │
│   │ then/else│         │ Optional │         │ cases    │   │
│   ╰──────────╯         ╰──────────╯         ╰──────────╯   │
│                                                             │
│   Diamond shape        Question mark        Fork icon      │
└─────────────────────────────────────────────────────────────┘
```

### Type Display

Show type signatures compactly:

```
┌─────────────────────────────────────────────────┐
│ FetchCustomer                                   │
├─────────────────────────────────────────────────┤
│ customerId: String                              │  ← Input params
│ ────────────────────────────                    │
│ → { name: String, tier: String }                │  ← Return type
└─────────────────────────────────────────────────┘
```

For complex record types, use abbreviated form:
- `{ id, name, ... +3 }` → expand on hover/click

### Edge Styling

```
Data flow (solid):      ─────────────────►
Optional data (dashed): ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈►
Control flow (dotted):  ·················►

Multi-edge bundling:    ════════════════►
                        (thicker for parallel flows)
```

### Execution State Visualization

```
┌─────────────────────────────────────────────────────────────┐
│ PENDING                RUNNING               COMPLETED      │
│                                                             │
│   ╭──────────╮         ╭──────────╮         ╭──────────╮   │
│   │ Module   │         │ Module ◌ │         │ Module ✓ │   │
│   │ (gray)   │         │ (pulse)  │         │ (green)  │   │
│   ╰──────────╯         ╰──────────╯         ╰──────────╯   │
│                                             │              │
│                                             │ "Premium"    │
│                                             │ 12ms         │
│                                             └──────────────┘
│                                               ↑             │
│                                         Actual value shown  │
│                                         below node when     │
│                                         completed           │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ FAILED                                                      │
│                                                             │
│   ╭──────────╮                                              │
│   │ Module ✗ │ ←── Red border, error icon                   │
│   │ (red)    │                                              │
│   ╰──────────╯                                              │
│   │ Error: Connection timeout                               │
│   └─────────────────────────                                │
│         ↑                                                   │
│   Error message shown inline                                │
└─────────────────────────────────────────────────────────────┘
```

### Data Value Display

For completed nodes, show values inline:

```
Simple values:
  ╭──────────────╮
  │ ▶ userId     │
  │ "U-123"      │  ← Value shown directly
  ╰──────────────╯

Records (collapsed):
  ╭────────────────────────╮
  │ → customer             │
  │ { name: "Alice", ... } │  ← Preview
  ╰────────────────────────╯

Records (expanded on click):
  ╭────────────────────────╮
  │ → customer             │
  ├────────────────────────┤
  │ name: "Alice"          │
  │ tier: "Premium"        │
  │ since: 2024-01-15      │
  ╰────────────────────────╯

Lists (count + preview):
  ╭────────────────────────╮
  │ → items                │
  │ List[3]: [{ id: "A"... │  ← Count + first item preview
  ╰────────────────────────╯
```

---

## Implementation Plan

### Phase 1: Core IR & Basic Layout (Backend)

**Scope:** Create DagVizIR data structure and basic Sugiyama layout

**Files to create/modify:**
- `modules/lang-compiler/src/.../viz/DagVizIR.scala` - New IR types
- `modules/lang-compiler/src/.../viz/DagVizCompiler.scala` - IRProgram → DagVizIR
- `modules/lang-compiler/src/.../viz/SugiyamaLayout.scala` - Layout algorithm
- `modules/lang-lsp/src/.../protocol/LspMessages.scala` - Add new message types

**Deliverables:**
- [x] DagVizIR case classes with JSON encoders
- [ ] Compiler from IRProgram to DagVizIR
- [ ] Sugiyama layout with edge crossing minimization
- [ ] LSP endpoint `getDagVisualization` returning laid-out IR

**Estimated complexity:** Medium

### Phase 2: SVG Renderer (Backend + Frontend)

**Scope:** Generate high-quality SVG from DagVizIR

**Files to create/modify:**
- `modules/lang-compiler/src/.../viz/SVGRenderer.scala` - Scala SVG generation
- `vscode-extension/src/panels/DagVisualizerPanel.ts` - Update to use new IR

**Deliverables:**
- [ ] SVGRenderer producing clean, styled SVG
- [ ] Updated VSCode panel consuming new format
- [ ] Backward compatibility with current LSP message

**Estimated complexity:** Medium

### Phase 3: Execution State Integration

**Scope:** Show runtime values in visualization

**Files to create/modify:**
- `modules/runtime/src/.../ExecutionTracker.scala` - Track per-node values
- `modules/lang-lsp/src/.../ConstellationLanguageServer.scala` - Stream updates
- `vscode-extension/src/panels/DagVisualizerPanel.ts` - Render values

**Protocol extension:**
```typescript
interface DagExecutionUpdate {
  nodeId: string;
  state: 'pending' | 'running' | 'completed' | 'failed';
  value?: any;          // Actual computed value
  duration?: number;    // Execution time
  error?: string;
}
```

**Deliverables:**
- [ ] ExecutionTracker capturing values per node
- [ ] LSP notification for execution updates
- [ ] Value rendering in visualization

**Estimated complexity:** High (requires runtime integration)

### Phase 4: Alternative Renderers

**Scope:** Add Mermaid, ASCII, DOT renderers for documentation/terminal

**Files to create:**
- `modules/lang-compiler/src/.../viz/MermaidRenderer.scala`
- `modules/lang-compiler/src/.../viz/ASCIIRenderer.scala`
- `modules/lang-compiler/src/.../viz/DOTRenderer.scala`

**Deliverables:**
- [ ] Mermaid output for markdown docs
- [ ] ASCII output for CLI/terminal tools
- [ ] DOT output for Graphviz integration
- [ ] CLI command: `constellation viz --format=mermaid file.cst`

**Estimated complexity:** Low-Medium (renderers are straightforward once IR exists)

### Phase 5: Advanced Layout & UX

**Scope:** Optimize for large DAGs, add grouping/collapse

**Features:**
- [ ] Node grouping with collapse/expand
- [ ] Force-directed layout option
- [ ] Edge bundling for parallel flows
- [ ] Minimap for large DAGs
- [ ] Search/filter highlighting

**Estimated complexity:** Medium-High

---

## Alternative Approaches Considered

### Option A: Use Existing Graph Library (e.g., ELK, Dagre)

**Pros:**
- Battle-tested layout algorithms
- Less code to maintain

**Cons:**
- Additional dependency
- Less control over output
- May not integrate well with Scala backend

**Decision:** Start with custom Sugiyama implementation for control, consider ELK integration for Phase 5 if needed.

### Option B: Client-Side Only Rendering

**Pros:**
- Simpler backend
- Faster iteration on visual design

**Cons:**
- Cannot render server-side (for docs, CLI)
- Duplicates layout logic if we want multi-format output

**Decision:** Backend-driven layout for portability, frontend handles interactivity only.

### Option C: Use Mermaid/Graphviz Directly

**Pros:**
- Zero custom rendering code
- Wide ecosystem support

**Cons:**
- Limited styling control
- Cannot show execution values inline
- Dependency on external tools

**Decision:** Support Mermaid/DOT as output formats, but primary visualization uses custom renderer for full control.

---

## Design Decisions (Resolved)

1. **Layout algorithm** - **Sugiyama** (layered layout optimized for pipelines). Force-directed can be added later for dense graphs.

2. **Value display depth** - 1 level deep by default, expand on click for nested records.

3. **Execution state updates** - **Final state only** (no streaming during execution). Simpler implementation, avoids UI flicker.

4. **Grouping semantics** - **Auto-detection** based on:
   - Module name prefixes (e.g., `Order.fetch`, `Order.validate` → "Order" group)
   - DAG structure (nodes at same depth feeding into same downstream node)
   - No new language syntax required; explicit syntax can be added later if needed.

5. **Color scheme** - Support light/dark themes via CSS variables (VSCode theme integration).

---

## Success Metrics

1. **Readability** - User study: engineers can trace data flow in <30 seconds for 20-node DAG
2. **Performance** - Render 100-node DAG in <500ms
3. **Portability** - Same DAG renders correctly in VSCode, browser, and terminal
4. **Execution insight** - Users can identify failed node and its input values without opening logs

---

## References

- [Sugiyama Framework](https://en.wikipedia.org/wiki/Layered_graph_drawing) - Classic layered DAG layout
- [ELK (Eclipse Layout Kernel)](https://www.eclipse.org/elk/) - Java layout library
- [Dagre](https://github.com/dagrejs/dagre) - JavaScript graph layout
- [Mermaid.js](https://mermaid.js.org/) - Text-to-diagram tool
- Current implementation: `vscode-extension/src/panels/DagVisualizerPanel.ts`
