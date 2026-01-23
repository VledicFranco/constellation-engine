# Phase 2: Core Improvements

**Timeline:** 2-4 weeks
**Risk Level:** Medium
**Parallelization:** Tasks 2.2 and 2.4 can run in parallel; 2.1 and 2.3 have dependencies

---

## Overview

Phase 2 focuses on core improvements to the parser, compiler, and type system. These changes are more substantial than Phase 1 but provide significant performance and usability gains.

## Prerequisites

Phase 1 should be substantially complete before starting Phase 2:
- Task 1.1 (Compilation Caching) is required for Task 2.1
- Task 1.2 (Debounced LSP) is recommended for Task 2.3

## Tasks

| Task | Issue | Title | Context File | Agent | Dependencies | Status |
|------|-------|-------|--------------|-------|--------------|--------|
| 2.1 | [#116](https://github.com/VledicFranco/constellation-engine/issues/116) | IR Optimization Passes | [context-ir-optimization.md](./context-ir-optimization.md) | Agent 1 | #112 ✅ | Queued |
| 2.2 | [#117](https://github.com/VledicFranco/constellation-engine/issues/117) | Parser Memoization | [context-parser-memoization.md](./context-parser-memoization.md) | Agent 1 | None | Queued |
| 2.3 | [#118](https://github.com/VledicFranco/constellation-engine/issues/118) | Semantic Tokens | [context-semantic-tokens.md](./context-semantic-tokens.md) | Agent 3 | #113 ✅ | Queued |
| 2.4 | [#119](https://github.com/VledicFranco/constellation-engine/issues/119) | Subtyping Implementation | [context-subtyping.md](./context-subtyping.md) | Agent 2 | None | Queued |

## Dependency Graph

```
Phase 1 Complete
       │
       ▼
  ┌────┴────┐
  │         │
  ▼         ▼
Task 2.1   Task 2.2 ──────────────────┐
(IR Opt)   (Parser Memo)              │
  │                                   │
  ▼                                   │
Task 2.3 ◄── (Task 1.2)               │
(Semantic Tokens)                     │
  │                                   │
  └─────────┬─────────────────────────┘
            │         │
            ▼         ▼
         Task 2.4 (Subtyping)
            │
            ▼
      Phase 2 Complete
```

## Parallel Execution Strategy

**Current assignments:**
- **Agent 1**: #116 (IR Optimization) + #117 (Parser Memoization)
- **Agent 2**: #119 (Subtyping)
- **Agent 3**: #118 (Semantic Tokens)

**Recommended order:**
- Agent 1: Start with #117 (independent), then #116
- Agent 2: Start #119 immediately (independent)
- Agent 3: Start #118 immediately (dependency #113 satisfied)

## Success Criteria

Phase 2 is complete when:
- [ ] All 4 tasks have been merged to master
- [ ] Parser benchmark shows 2x+ improvement on complex inputs
- [ ] IR optimizer reduces DAG size by 10%+ on typical programs
- [ ] Semantic tokens work in VSCode
- [ ] Subtyping allows `SNothing` as bottom type
- [ ] No test regressions

## Expected Outcomes

After Phase 2:
- **Parsing:** 2-5x faster on complex programs with backtracking
- **Compilation:** 10-30% smaller DAGs due to dead code elimination
- **IDE:** Semantic highlighting in VSCode
- **Type System:** More flexible type checking with subtyping
