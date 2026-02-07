# RFC-020: Dashboard IDE Features

**Status:** Draft
**Priority:** P1 (Strategic)
**Author:** Human + Claude
**Created:** 2026-02-07
**Depends On:** RFC-019 (Architecture recommendations)

---

## Summary

Implement features that fulfill the dashboard's role as a **specialized IDE and observability tool** per the updated tooling organon. This RFC covers the functionality gaps between the current dashboard and the complete development workflow: write → visualize → test → debug → monitor → manage.

---

## Motivation

The updated organon (2026-02-07) defines the dashboard as:

> "A specialized IDE and observability tool for a Constellation server. Write, test, visualize, and monitor pipelines in one place."

Current dashboard capabilities:

| Capability | Status | Gap |
|------------|--------|-----|
| **Write** | Partial | Syntax highlighting only, no autocomplete |
| **Visualize** | Good | DAG renders, but static during execution |
| **Test** | Good | Input forms, execution works |
| **Debug** | Missing | No intermediate value inspection |
| **Monitor** | Partial | History exists, no profiling |
| **Manage** | Good | Pipelines panel, suspend/resume |

This RFC addresses the gaps to deliver the complete workflow.

---

## Feature 1: Live Execution Visualization

### Problem
When a pipeline executes, users see final outputs. The DAG remains static. There's no visibility into which nodes are running, completed, or failed.

### Solution
Real-time node status updates during execution:

```
┌─────────────────────────────────────────────────────────────┐
│  DAG Visualization                                           │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐               │
│  │  input  │────▶│  Trim   │────▶│  Split  │               │
│  │    ✓    │     │    ⟳    │     │    ○    │               │
│  │  "hi "  │     │ running │     │ pending │               │
│  └─────────┘     └─────────┘     └─────────┘               │
│                        │                                     │
│                        ▼                                     │
│                  ┌─────────┐     ┌─────────┐               │
│                  │  Upper  │────▶│  output │               │
│                  │    ○    │     │    ○    │               │
│                  │ pending │     │ pending │               │
│                  └─────────┘     └─────────┘               │
└─────────────────────────────────────────────────────────────┘
Legend: ✓ completed  ⟳ running  ○ pending  ✗ failed
```

### Implementation

**Backend:**
- Add WebSocket endpoint for execution events: `ws://localhost:8080/executions/:id/events`
- Emit events: `node:start`, `node:complete`, `node:failed`
- Include node ID, timestamp, value (if complete), error (if failed)

**Frontend:**
- Subscribe to execution WebSocket when Run is clicked
- Update node styles in real-time via `dagVisualizer.updateExecutionState()`
- Show intermediate values on hover during/after execution
- Animate running nodes (pulsing border already styled)

### API

```typescript
// WebSocket messages (server → client)
interface ExecutionEvent {
  type: 'node:start' | 'node:complete' | 'node:failed';
  nodeId: string;
  timestamp: number;
  value?: unknown;      // for node:complete
  error?: string;       // for node:failed
  durationMs?: number;  // for node:complete
}
```

---

## Feature 2: Module Browser

### Problem
Users must know available modules by name. No way to discover modules, see their signatures, or read documentation from the dashboard.

### Solution
Module catalog panel with search and documentation:

```
┌─────────────────────────────────────────────────────────────┐
│  Modules                                        [Search: ___]│
├─────────────────────────────────────────────────────────────┤
│  ▸ Text Processing (12)                                      │
│    ├── Uppercase(text: String) → String                      │
│    ├── Lowercase(text: String) → String                      │
│    ├── Trim(text: String) → String                           │
│    └── ...                                                   │
│  ▸ Data Transform (8)                                        │
│  ▸ Math (6)                                                  │
│  ▸ List Operations (10)                                      │
├─────────────────────────────────────────────────────────────┤
│  Selected: Uppercase                                         │
│  ─────────────────────────────────────────────────────────  │
│  Converts text to uppercase.                                 │
│                                                              │
│  Inputs:  text: String                                       │
│  Outputs: String                                             │
│                                                              │
│  Example:                                                    │
│    result = Uppercase("hello")  # "HELLO"                   │
│                                                              │
│  [Insert into Editor]                                        │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

**Backend:**
- `GET /api/v1/modules` - List all registered modules with metadata
- Response includes: name, description, version, inputSchema, outputSchema, examples, category

**Frontend:**
- New "Modules" tab in navigation or collapsible sidebar panel
- Grouped by category (inferred from namespace or explicit tag)
- Search filters by name or description
- Click module to see full documentation
- "Insert" button adds module call template to editor at cursor

### API

```typescript
interface ModuleInfo {
  name: string;
  description: string;
  version: string;
  category?: string;
  inputs: Record<string, string>;   // name → type
  outputs: Record<string, string>;  // name → type
  examples?: string[];
}

// GET /api/v1/modules
interface ModulesResponse {
  modules: ModuleInfo[];
  categories: string[];
}
```

---

## Feature 3: Code Editor Enhancements

### Problem
The code editor has syntax highlighting but lacks IDE features: no autocomplete, no go-to-definition, no inline errors with source highlighting.

### Solution
Connect the code editor to the LSP via WebSocket for full IDE capabilities.

### Implementation

**Option A: Monaco Editor (Recommended)**
Replace textarea with Monaco Editor configured for constellation-lang:
- Native LSP client support
- Autocomplete, hover, diagnostics built-in
- Minimap, bracket matching, multi-cursor
- ~500kb additional bundle size

**Option B: CodeMirror 6**
Lighter alternative with LSP extension:
- ~150kb bundle size
- Good LSP integration via extensions
- Less feature-complete than Monaco

**Option C: LSP Overlay on Textarea**
Keep textarea, add LSP features manually:
- Lowest complexity
- Limited UX (no inline autocomplete dropdown)
- Custom implementation required

**Recommendation:** Monaco Editor. It's the industry standard for browser IDEs (VSCode, GitHub.dev) and provides the best developer experience with minimal custom code.

### Features Enabled
- Autocomplete as you type (Ctrl+Space)
- Hover documentation on modules and types
- Inline error squiggles with messages
- Go-to-definition for variables
- Bracket matching and auto-close
- Multi-cursor editing
- Search and replace

---

## Feature 4: Performance Profiling View

### Problem
Execution history shows total duration but no breakdown. Users can't identify bottlenecks without digging into raw data.

### Solution
Flame graph or waterfall view of node execution times:

```
┌─────────────────────────────────────────────────────────────┐
│  Execution Profile: abc-123                    Total: 245ms │
├─────────────────────────────────────────────────────────────┤
│  ████████████████████████████████████████████░░░░░░░░░░░░░  │
│  0ms                                                   245ms │
│                                                              │
│  input        ██ 2ms                                        │
│  Trim         ███ 5ms                                       │
│  FetchUser    █████████████████████████████ 180ms    ◀ slow │
│  Enrich       ████████ 45ms                                 │
│  output       ██ 3ms                                        │
│                                                              │
│  ⚠ FetchUser took 73% of total execution time              │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

**Backend:**
- Already have per-node timing in execution results
- Add `GET /api/v1/executions/:id/profile` with timing breakdown

**Frontend:**
- New "Profile" tab in execution detail view
- Horizontal bar chart (waterfall) showing node durations
- Highlight nodes exceeding threshold (e.g., >100ms)
- Calculate percentage of total time per node

---

## Feature 5: Input Presets

### Problem
Users re-type inputs for repeated executions. Testing the same pipeline with different inputs is tedious.

### Solution
Save and load input configurations:

```
┌─────────────────────────────────────────────────────────────┐
│  Inputs                            [Presets ▾] [Save] [Clear]│
├─────────────────────────────────────────────────────────────┤
│  text: [Hello, World!___________]                           │
│  count: [42_____]                                           │
│                                                              │
│  ┌─ Presets ─────────────────────┐                          │
│  │ ★ Default (from @example)     │                          │
│  │   Test Case 1                 │                          │
│  │   Edge Case - Empty           │                          │
│  │   Production Sample           │                          │
│  │ ─────────────────────────────│                          │
│  │ + Save Current as Preset...   │                          │
│  └───────────────────────────────┘                          │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

**Storage:**
- LocalStorage for client-side persistence (simple, no backend changes)
- Key: `constellation:presets:{scriptPath}`
- Value: Array of `{ name: string, inputs: Record<string, unknown> }`

**Features:**
- Auto-save last used inputs
- Named presets with save/load/delete
- Import/export presets as JSON
- Default preset from `@example` annotations

---

## Feature 6: Enhanced Error Panel

### Problem
Compilation errors show in a banner but lack source context. Users must mentally map error message to code location.

### Solution
Error panel with source highlighting and suggestions:

```
┌─────────────────────────────────────────────────────────────┐
│  Errors (2)                                           [×]   │
├─────────────────────────────────────────────────────────────┤
│  ✗ E101: Undefined module at line 5                         │
│    │                                                        │
│  4 │ cleaned = Trim(text)                                   │
│  5 │ result = Uppercse(cleaned)                             │
│    │          ^^^^^^^^                                      │
│    │                                                        │
│    │ Did you mean: Uppercase?                               │
│    │ [Apply Fix]                                            │
│  ──────────────────────────────────────────────────────────│
│  ✗ E102: Type mismatch at line 8                           │
│    │                                                        │
│  7 │ count = Length(words)                                  │
│  8 │ out count + "items"                                    │
│    │     ^^^^^^^^^^^^^^^^^                                  │
│    │                                                        │
│    │ Expected: String, Found: Int                           │
│    │ Suggestion: Use ToString(count) to convert             │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

**Backend:**
- Enhance error response to include source context (surrounding lines)
- Add suggested fixes where applicable
- Include error codes for documentation linking

**Frontend:**
- Replace simple error banner with expandable error panel
- Show source snippet with highlighted error span
- "Apply Fix" button for auto-fixable errors
- Click error to jump to location in editor

---

## Feature 7: Value Inspector

### Problem
After execution, users see final outputs but can't inspect intermediate values without adding debug outputs.

### Solution
Click any DAG node to inspect its computed value:

```
┌─────────────────────────────────────────────────────────────┐
│  Node: FetchUser                                     [×]    │
├─────────────────────────────────────────────────────────────┤
│  Type: { id: Int, name: String, email: String }             │
│  Status: Completed (45ms)                                   │
│  ───────────────────────────────────────────────────────── │
│  Value:                                                     │
│  {                                                          │
│    "id": 123,                                               │
│    "name": "Alice",                                         │
│    "email": "alice@example.com"                             │
│  }                                                          │
│  ───────────────────────────────────────────────────────── │
│  Inputs:                                                    │
│    userId: 123                                              │
│  ───────────────────────────────────────────────────────── │
│  [Copy Value] [View as Table] [Expand All]                  │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

**Backend:**
- Store intermediate values during execution (opt-in, memory implications)
- `GET /api/v1/executions/:id/nodes/:nodeId` returns full node state

**Frontend:**
- Enhance existing node details panel
- Add JSON tree viewer for complex values
- Add "Copy" button for easy extraction
- Table view for arrays of records

---

## Implementation Phases

### Phase 1: Foundation (Week 1-2)
**Goal:** Enable real-time feedback loop

1. **Live Execution Visualization** - See execution progress in real-time
2. **Input Presets** - Faster repeated testing
3. **Enhanced Error Panel** - Better error context

**Deliverables:**
- WebSocket endpoint for execution events
- Real-time node status in DAG
- LocalStorage presets
- Error panel with source context

### Phase 2: Discovery (Week 3-4)
**Goal:** Help users discover and use modules

4. **Module Browser** - Find and learn about modules
5. **Value Inspector** - Inspect intermediate values

**Deliverables:**
- Modules API endpoint
- Module browser panel with search
- Node click shows full value details

### Phase 3: IDE Experience (Week 5-6)
**Goal:** Full IDE capabilities in the browser

6. **Monaco Editor Integration** - Autocomplete, hover, diagnostics
7. **Performance Profiling View** - Identify bottlenecks

**Deliverables:**
- Monaco Editor replacing textarea
- LSP connection for autocomplete
- Execution profile visualization

---

## Architecture Changes

### Backend
| Change | Module | Files |
|--------|--------|-------|
| Execution WebSocket | http-api | `ExecutionWebSocket.scala` (new) |
| Modules endpoint | http-api | `ConstellationRoutes.scala` |
| Profile endpoint | http-api | `DashboardRoutes.scala` |
| Intermediate value storage | http-api | `ExecutionStorage.scala` |

### Frontend
| Change | Files |
|--------|-------|
| Live execution | `dag-visualizer.ts`, `execution-panel.ts` |
| Module browser | `modules-panel.ts` (new) |
| Input presets | `execution-panel.ts` |
| Error panel | `error-panel.ts` (new) |
| Value inspector | `node-details.ts` (new) |
| Monaco integration | `code-editor.ts` (major rewrite) |
| Profile view | `profile-view.ts` (new) |

### Build System
- Add Vite for bundling (per RFC-019 recommendation)
- Add Monaco Editor dependency
- Configure Monaco workers for syntax highlighting

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Time to first successful execution | ~30s | < 15s |
| Error-to-fix cycle time | ~20s | < 5s (with suggestions) |
| Module discovery time | N/A (not possible) | < 10s |
| Bottleneck identification time | ~60s (manual) | < 5s (profiler) |

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Monaco bundle size (+500kb) | Slower initial load | Code splitting, lazy load editor |
| WebSocket complexity | Connection management | Reconnection logic, fallback to polling |
| Intermediate value memory | High memory for large pipelines | Opt-in storage, size limits, TTL |
| LSP performance | Slow autocomplete | Cache, debounce, timeout |

---

## Testing Strategy

### Unit Tests
- Module browser filtering logic
- Input preset serialization
- Error panel rendering

### E2E Tests (Playwright)
- Live execution: run pipeline, verify nodes update in sequence
- Module browser: search, select, insert
- Input presets: save, load, delete
- Error panel: click error, editor navigates
- Value inspector: run, click node, verify value displayed
- Profile view: run, view timing breakdown

### Performance Tests
- Monaco editor initialization < 500ms
- Autocomplete response < 100ms
- Live execution update latency < 50ms

---

## Related Documents

- [RFC-019](./rfc-019-dashboard-ux-architecture.md) - Architecture recommendations
- [PHILOSOPHY.md](../features/tooling/PHILOSOPHY.md) - Tooling philosophy (updated)
- [ETHOS.md](../features/tooling/ETHOS.md) - Behavioral constraints (updated)
- [RFC-015e](./rfc-015e-dashboard-integration.md) - Pipeline lifecycle integration
