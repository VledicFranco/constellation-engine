# RFC-019: Dashboard UX Analysis and Architecture Improvements

**Status:** Draft
**Priority:** P1 (Strategic)
**Author:** Human + Claude
**Created:** 2026-02-07

---

## Summary

This RFC analyzes the dashboard UX against the Constellation tooling organon and proposes architecture improvements to scale dashboard development.

---

## Part 1: UX Analysis Against the Organon

### Organon Principles (Updated 2026-02-07)

From `docs/features/tooling/PHILOSOPHY.md` and `ETHOS.md`:

1. **Dashboard is a specialized IDE and observability tool.** Write, test, visualize, and monitor pipelines in one place.
2. **LSP is the single protocol.** All IDE features go through LSP.
3. **Dashboard complements general IDEs.** Dashboard excels at pipeline-specific workflows; general IDEs handle large-scale editing.
4. **Error messages are actionable.**
5. **Optimize for the inner loop.** Write → visualize → test → iterate should be fast.

### Current State Assessment

| Feature | Status | Alignment with Organon |
|---------|--------|------------------------|
| File browser | Implemented | Aligned - navigation |
| Code editor | Implemented | **Aligned** - specialized IDE capability |
| DAG visualization | Implemented | Aligned - real-time visualization |
| Execution inputs | Implemented | Aligned - test execution |
| Execution history | Implemented | Aligned - observation |
| Pipelines panel | Implemented | Aligned - lifecycle visibility |
| Suspend/resume UI | Implemented | Aligned - workflow continuation |

### Organon Update Complete

The tooling organon has been updated to position the dashboard as a **specialized IDE and observability tool** rather than a read-only visualization surface. This aligns with the current implementation and enables future development features.

### Gaps for Full Pipeline Development Workflow

Even following the organon (IDE for editing, dashboard for visualization), these UX gaps exist:

#### 1. No Live Execution Visualization
**Problem:** Users run pipelines and see final outputs. No visibility into execution flow.
**Need:** Real-time node status updates during execution (pending → running → completed).

#### 2. No Performance Profiling View
**Problem:** Users can see timing in history but no profiling visualization.
**Need:** Flame graph or waterfall view of node execution times.

#### 3. No Pipeline Comparison View
**Problem:** Cannot compare two pipeline versions side-by-side.
**Need:** Diff view for DAG structure between versions.

#### 4. No Debugging Capabilities
**Problem:** When a pipeline fails, users see error message but no execution context.
**Need:** Breakpoint-like inspection (pause before node, inspect intermediate values).

#### 5. No Module Discovery
**Problem:** Users must know available modules. No searchable catalog.
**Need:** Module browser showing all registered modules with signatures and docs.

#### 6. No Input History
**Problem:** Users re-type inputs for repeated executions.
**Need:** Input presets / recent inputs autocomplete.

#### 7. Limited Error Context
**Problem:** Errors show message but not surrounding context.
**Need:** Error panel with source highlighting, suggested fixes.

---

## Part 2: Feature Prioritization

### Must Have (P0) - Enable Core Workflow

| Feature | Value | Effort |
|---------|-------|--------|
| Live execution visualization | See what's running | Medium |
| Module browser/catalog | Discover available modules | Medium |
| Input presets | Faster repeated runs | Low |

### Should Have (P1) - Improve Experience

| Feature | Value | Effort |
|---------|-------|--------|
| Performance profiling view | Identify bottlenecks | High |
| Error panel with context | Faster debugging | Medium |
| Recent inputs history | Reduce re-typing | Low |

### Nice to Have (P2) - Advanced Features

| Feature | Value | Effort |
|---------|-------|--------|
| Pipeline version diff | Compare changes | High |
| Execution stepping/debugging | Deep inspection | Very High |
| Collaborative features | Multi-user | Very High |

---

## Part 3: Architecture Analysis

### Current Architecture

```
dashboard/
├── src/
│   ├── main.ts              # Application entry, wiring
│   ├── types.d.ts           # TypeScript type definitions
│   └── components/
│       ├── file-browser.ts   # File tree component
│       ├── code-editor.ts    # Code editor (if kept)
│       ├── dag-visualizer.ts # Cytoscape DAG renderer
│       ├── execution-panel.ts # Inputs, outputs, history
│       └── pipelines-panel.ts # Pipeline lifecycle UI
```

**Build:** `tsc` compiles TypeScript to JavaScript. Files loaded via `<script>` tags in order.

**State:** Each component manages its own state. Cross-component communication via callbacks.

**Dependencies:** Cytoscape.js, dagre loaded from CDN.

### Architecture Problems

#### 1. No Module System
**Problem:** Scripts loaded via `<script>` tags, order-dependent. Global namespace pollution.
**Impact:** Adding new components requires careful ordering. No tree-shaking.

#### 2. No State Management
**Problem:** State scattered across components. Cross-component updates require callback chains.
**Impact:** Hard to implement features like "execution updates DAG visualization" reliably.

#### 3. No Build Optimization
**Problem:** Raw tsc output, no bundling, no minification.
**Impact:** Multiple HTTP requests, larger payload, no code splitting.

#### 4. Limited Component Composition
**Problem:** Components are classes with manual DOM manipulation.
**Impact:** Building complex UI (like profiling view) requires significant boilerplate.

#### 5. No Testing Infrastructure
**Problem:** Only E2E tests (Playwright). No unit tests for components.
**Impact:** Refactoring is risky, behavior verification is slow.

---

## Part 4: Architecture Recommendations

### Option A: Minimal Enhancement (Recommended for Near-Term)

Keep current architecture but add:

1. **Vite as build tool** - Fast dev server, automatic bundling, HMR
2. **ES modules** - Replace script tags with proper imports
3. **Simple event bus** - Cross-component communication without callbacks

**Effort:** Low (1-2 days)
**Benefit:** Modern DX, easier to add features

```typescript
// event-bus.ts
type EventHandler<T> = (data: T) => void;

class EventBus {
  private handlers = new Map<string, Set<EventHandler<unknown>>>();

  on<T>(event: string, handler: EventHandler<T>) {
    if (!this.handlers.has(event)) this.handlers.set(event, new Set());
    this.handlers.get(event)!.add(handler as EventHandler<unknown>);
  }

  emit<T>(event: string, data: T) {
    this.handlers.get(event)?.forEach(h => h(data));
  }
}

export const bus = new EventBus();
```

### Option B: Component Framework Migration

Adopt a lightweight component framework:

| Framework | Pros | Cons |
|-----------|------|------|
| **Preact** | Tiny (3kb), React-compatible | Still needs state management |
| **Solid** | Fine-grained reactivity, fast | Smaller ecosystem |
| **Svelte** | Compiled, minimal runtime | Different paradigm |
| **Lit** | Web components, small | Less ecosystem |

**Effort:** High (1-2 weeks)
**Benefit:** Scalable component model, ecosystem tools

### Option C: Full React Migration

Adopt React with modern tooling:

- React 18 + TypeScript
- Vite or Next.js
- Zustand or Jotka for state
- Tailwind for styling

**Effort:** Very High (2-4 weeks)
**Benefit:** Industry standard, large ecosystem, easy hiring

---

## Part 5: Recommended Roadmap

### Phase 1: Foundation (Week 1)
1. **Decision on code editor** - Keep or remove per organon
2. **Migrate to Vite** - Modern build, ES modules
3. **Add event bus** - Cross-component communication

### Phase 2: Core UX Features (Weeks 2-3)
1. **Live execution visualization** - Real-time node status
2. **Module browser** - Searchable module catalog
3. **Input presets** - Save/load input configurations

### Phase 3: Enhanced Debugging (Weeks 4-5)
1. **Error panel** - Contextual error display
2. **Performance view** - Execution timing visualization
3. **Value inspector** - Drill into execution results

### Phase 4: Component Framework (Future)
1. Evaluate framework options based on team preference
2. Incremental migration (new features in framework, existing code stable)
3. Full migration when justified by complexity

---

## Decision Points

### Decision 1: Code Editor ✅ RESOLVED

The organon has been updated to position the dashboard as a "specialized IDE and observability tool." The code editor is now aligned with the philosophy.

### Decision 2: Build Tool

| Option | Effort | Benefit |
|--------|--------|---------|
| **Stay with tsc** | None | None |
| **Add Vite** | Low | Modern DX, HMR, bundling |
| **Add Webpack** | Medium | More configurable, larger ecosystem |

**Recommendation:** Vite - minimal config, fast, modern.

### Decision 3: State Management

| Option | Complexity | Scalability |
|--------|------------|-------------|
| **Callbacks (current)** | Low | Poor |
| **Event bus** | Low | Medium |
| **Zustand/Jotai** | Medium | High |
| **Redux** | High | High |

**Recommendation:** Start with event bus, migrate to Zustand if complexity grows.

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Time to first meaningful paint | ~500ms | < 300ms |
| Bundle size | N/A (no bundling) | < 200kb gzipped |
| Component test coverage | 0% | > 60% |
| E2E test coverage | ~40% | > 80% |
| Developer iteration time | ~2s (full reload) | < 100ms (HMR) |

---

## Related Documents

- [PHILOSOPHY.md](../features/tooling/PHILOSOPHY.md) - Tooling philosophy
- [ETHOS.md](../features/tooling/ETHOS.md) - Behavioral constraints
- [RFC-015e](./rfc-015e-dashboard-integration.md) - Pipeline lifecycle dashboard integration
- [RFC-012](./rfc-012-dashboard-e2e-tests.md) - Dashboard E2E testing
