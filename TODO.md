# Constellation Engine - Sprint Backlog

This document tracks the current sprint issues and their status. **Agents update this file directly** to reflect progress without requiring GitHub API calls.

**Last Updated:** 2026-01-18

---

## Status Legend

| Mark | Status | Description |
|------|--------|-------------|
| `[ ]` | Open | Not started |
| `[~]` | In Progress | Agent actively working on it |
| `[x]` | Complete | PR merged to master |
| `[!]` | Blocked | Waiting on dependency or clarification |

**Format for claiming an issue:**
```markdown
- [~] #3 Fix CI pipeline @agent-1 (branch: `agent-1/issue-3-fix-ci`)
```

---

## Dependency Graph

```
                    ┌──────────────────────────────────────────────────────────┐
                    │                    INDEPENDENT (P0)                       │
                    │  #3 CI Fix    #4 AST Tests    #5 App Tests               │
                    │  #6 WS Fix    #7 Imports                                 │
                    └──────────────────────────────────────────────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
           ┌─────────────┐           ┌─────────────┐           ┌─────────────┐
           │ COMPILER    │           │  VSCode     │           │ INFRA       │
           │ TRACK       │           │  TRACK      │           │ TRACK       │
           └─────────────┘           └─────────────┘           └─────────────┘
                    │                         │                         │
           #8 Field Access           #14 Webview Refactor       #21 Logging
           #9 Comparison ───┐        ┌───────┼───────┐          #25 Coverage
           #10 Boolean ─────┼────┐   │       │       │          #26 Linting
           #11 Arithmetic   │    │   ▼       ▼       ▼
                    │       │    │  #12     #13     #2
                    │       ▼    │  Layout  Export  Errors
                    │      #15 when (guard)
                    │       │    │
                    │       ▼    │          #19 Step-Through
                    │      #16 Optional ───────────┼
                    │       │                      ▼
                    │       ▼               #18 Exec Highlighting
                    │      #17 Coalesce
                    │       │
                    ▼       ▼
           ┌─────────────────────┐
           │ P3 FEATURES         │
           │ #22 Branch          │ ← needs #9
           │ #23 Lambda          │ ← needs #8
           │ #24 Union           │
           │ #27 Interpolation   │
           │ #28 StdLib Modular  │
           └─────────────────────┘
```

---

## Sprint 1: Foundation & Core Language Features

**Sprint Goal:** Establish solid foundation with tests and critical cleanups, then implement core language operators.

### P0 - Critical (Must Complete First)

| Status | Issue | Title | Track | Dependencies | Blocked By |
|--------|-------|-------|-------|--------------|------------|
| [x] | #3 | Fix CI pipeline - sbt command not found @agent-1 | Compiler | None | - |
| [~] | #4 | Add test coverage for lang-ast module @agent-1 | Compiler | None | - |
| [~] | #5 | Add test coverage for example-app modules @agent-2 | Compiler | None | - |
| [x] | #6 | Fix "Empty message" error logging in WebSocket @agent-3 | VSCode | None | - |
| [x] | #7 | Remove redundant imports in LSP server @agent-2 | VSCode | None | - |

### P1 - High Priority (Target Completion)

| Status | Issue | Title | Track | Dependencies | Blocked By |
|--------|-------|-------|-------|--------------|------------|
| [ ] | #8 | Implement field access operator (`.`) | Compiler | None | - |
| [ ] | #9 | Implement comparison operators | Compiler | None | - |
| [ ] | #10 | Implement boolean operators (`and`, `or`, `not`) | Compiler | None | - |
| [ ] | #11 | Implement arithmetic operators | Compiler | None | - |
| [ ] | #12 | DAG Visualizer: Add layout direction toggle | VSCode | #14 (soft) | - |
| [ ] | #13 | DAG Visualizer: Export as PNG/SVG | VSCode | #14 (soft) | - |
| [~] | #14 | Refactor: Extract common webview patterns @agent-3 | VSCode | None | - |

### P2 - Medium Priority

| Status | Issue | Title | Track | Dependencies | Blocked By |
|--------|-------|-------|-------|--------------|------------|
| [ ] | #15 | Implement guard expressions (`when`) | Compiler | #9, #10, #16 | - |
| [ ] | #16 | Implement Optional type | Compiler | None | - |
| [ ] | #17 | Implement coalesce operator (`??`) | Compiler | #16 | - |
| [ ] | #18 | DAG Visualizer: Add execution highlighting | VSCode | #19 | - |
| [ ] | #19 | Script Runner: Add step-through execution | VSCode | None | - |
| [ ] | #20 | Documentation: Create Getting Started tutorial | Docs | None | - |
| [ ] | #21 | Add structured logging with log levels | Infra | None | - |
| [ ] | #2 | Display errors in VSCode extension results | VSCode | None | - |

### P3 - Low Priority (Future)

| Status | Issue | Title | Track | Dependencies | Blocked By |
|--------|-------|-------|-------|--------------|------------|
| [ ] | #1 | Improve DAG Visualizer UX | VSCode | None | - |
| [ ] | #22 | Implement branch expressions | Compiler | #9 | - |
| [ ] | #23 | Implement lambda expressions | Compiler | #8 | - |
| [ ] | #24 | Implement union types | Compiler | None | - |
| [ ] | #25 | Add code coverage reporting | Infra | None | - |
| [ ] | #26 | Add linting with scalafmt and scalafix | Infra | None | - |
| [ ] | #27 | Implement string interpolation | Compiler | None | - |
| [ ] | #28 | Modularize StdLib into separate files | Compiler | None | - |

---

## Parallel Development Tracks

### Track 1: Compiler/Language (Recommended: Agent 1)

Focus: Parser, Type System, Core Language

| Order | Issue | Title | Effort |
|-------|-------|-------|--------|
| 1 | #3 | Fix CI pipeline | S |
| 2 | #4 | Add lang-ast tests | M |
| 3 | #8 | Field access operator (`.`) | L |
| 4 | #11 | Arithmetic operators | M |
| 5 | #22 | Branch expressions | M |

### Track 2: Language Operators (Recommended: Agent 2)

Focus: Operators, Type System Extensions

| Order | Issue | Title | Effort |
|-------|-------|-------|--------|
| 1 | #5 | Add example-app tests | M |
| 2 | #9 | Comparison operators | L |
| 3 | #10 | Boolean operators | M |
| 4 | #16 | Optional type | M |
| 5 | #15 | Guard expressions (`when`) | L |
| 6 | #17 | Coalesce operator (`??`) | M |

### Track 3: VSCode Extension (Recommended: Agent 3)

Focus: Extension UI, Error Handling

| Order | Issue | Title | Effort |
|-------|-------|-------|--------|
| 1 | #6 | Fix WebSocket logging | S |
| 2 | #7 | Remove redundant imports | XS |
| 3 | #14 | Webview refactor | M |
| 4 | #12 | Layout direction toggle | S |
| 5 | #13 | Export as PNG/SVG | M |
| 6 | #2 | Display errors in results | M |

### Track 4: Infrastructure (Any Agent)

Can be picked up when blocked on other work.

| Issue | Title | Effort |
|-------|-------|--------|
| #21 | Structured logging | M |
| #25 | Code coverage | M |
| #26 | Linting setup | M |
| #20 | Getting Started tutorial | L |
| #28 | Modularize StdLib | M |

---

## Critical Path

The **critical path** for language features is:

```
#9 Comparison → #10 Boolean → #16 Optional → #15 Guard → #17 Coalesce
     └────────────────────────────┴──────────────────────┘
                     (can run in parallel)
```

This sequence unlocks the conditional orchestration capabilities described in `docs/constellation-lang/orchestration-algebra.md`.

---

## Effort Estimates

| Size | Description |
|------|-------------|
| XS | < 1 hour, trivial change |
| S | 1-2 hours, single file |
| M | 2-4 hours, few files |
| L | 4-8 hours, multiple files/modules |
| XL | 1+ days, significant feature |

---

## How Agents Update This File

### Claiming an Issue

Change from:
```markdown
| [ ] | #3 | Fix CI pipeline | Compiler | None | - |
```

To:
```markdown
| [~] | #3 | Fix CI pipeline @agent-1 | Compiler | None | - |
```

### Marking Complete

After PR is merged, change to:
```markdown
| [x] | #3 | Fix CI pipeline @agent-1 | Compiler | None | - |
```

### Marking Blocked

If waiting on a dependency:
```markdown
| [!] | #15 | Guard expressions @agent-2 | Compiler | #9, #10, #16 | #16 not done |
```

---

## Completed Issues Log

Track merged PRs here for historical reference.

| Issue | Title | Agent | PR | Merged Date |
|-------|-------|-------|-----|-------------|
| #3 | Fix CI pipeline - sbt command not found | @agent-1 | #30 | 2026-01-18 |
| #6 | Fix "Empty message" error logging in WebSocket | @agent-3 | #33 | 2026-01-18 |
| #7 | Remove redundant imports in LSP server | @agent-2 | #31 | 2026-01-18 |

---

## Notes

- **Read CLAUDE.md** for full agent workflow instructions
- **Check GitHub before starting** - issue may already be closed
- **Self-review your PR** before requesting human review
- **Update this file** when claiming, completing, or getting blocked

