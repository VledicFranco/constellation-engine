# Constellation Engine Architecture Improvements - Orchestration Plan

**Created:** 2026-01-22
**Source:** `docs/dev/architecture-review-and-improvements.md`
**Status:** Phase 2 Complete - Phase 3 Ready

---

## Overview

This plan orchestrates the implementation of architectural improvements to Constellation Engine across three phases. Each phase builds on the previous, with tasks ordered by impact and complexity.

**Goal:** Transform Constellation Engine into a high-performance, developer-friendly platform with a powerful type system for data transformation pipelines.

---

## Phase Dependencies

```
Phase 1: Quick Wins ✅ COMPLETE
    │
    ├── Task 1.1: Compilation Caching ✅ ──────────┐
    ├── Task 1.2: Debounced LSP ✅ ────────────────┤
    ├── Task 1.3: Completion Trie ✅ ──────────────┤
    └── Task 1.4: Error Messages ✅ ───────────────┘
                                                  │
                                                  ▼
Phase 2: Core Improvements ✅ COMPLETE
    │
    ├── Task 2.1: IR Optimization ✅ ────────────────┐
    ├── Task 2.2: Parser Memoization ✅ ─────────────┤
    ├── Task 2.3: Semantic Tokens ✅ ────────────────┤
    └── Task 2.4: Subtyping ✅ ──────────────────────┘
                                                  │
                                                  ▼
Phase 3: Type System Enhancements (UNBLOCKED - ready to start)
    │
    ├── Task 3.1: Bidirectional Inference ◄─ Requires: Task 2.4 ✅
    └── Task 3.2: Row Polymorphism ◄──────── Requires: Tasks 2.4 ✅, 3.1
```

---

## Phase 1: Quick Wins ✅ COMPLETE

**Timeline:** 1-2 weeks
**Context Directory:** `agents/phase-1/`

| Task | Title | Status |
|------|-------|--------|
| 1.1 | Compilation Caching | ✅ Merged |
| 1.2 | Debounced LSP Analysis | ✅ Merged |
| 1.3 | Completion Trie | ✅ Merged |
| 1.4 | Improved Error Messages | ✅ Merged |

---

## Phase 2: Core Improvements ✅ COMPLETE

**Timeline:** 2-4 weeks
**Context Directory:** `agents/phase-2/`

| Task | Issue | Title | Agent | Status |
|------|-------|-------|-------|--------|
| 2.1 | [#116](https://github.com/VledicFranco/constellation-engine/issues/116) | IR Optimization Passes | Agent 1 | ✅ Merged |
| 2.2 | [#117](https://github.com/VledicFranco/constellation-engine/issues/117) | Parser Memoization | Agent 1 | ✅ Merged |
| 2.3 | [#118](https://github.com/VledicFranco/constellation-engine/issues/118) | Semantic Tokens | Agent 3 | ✅ Merged |
| 2.4 | [#119](https://github.com/VledicFranco/constellation-engine/issues/119) | Subtyping Implementation | Agent 2 | ✅ Merged |

---

## Phase 3: Type System Enhancements

**Timeline:** 3-5 weeks
**Parallelization:** 3.1 can start immediately; 3.2 depends on 3.1
**Risk Level:** Medium-High
**Context Directory:** `agents/phase-3/`
**Status:** READY TO START

| Task | Title | Context File | Effort | Impact | Depends On |
|------|-------|--------------|--------|--------|------------|
| 3.1 | Bidirectional Type Inference | `context-bidirectional-inference.md` | High (2 weeks) | High | Task 2.4 ✅ |
| 3.2 | Row Polymorphism | `context-row-polymorphism.md` | Very High (3 weeks) | High | Tasks 2.4 ✅, 3.1 |

### Task Descriptions

**3.1 Bidirectional Type Inference**
- Propagate expected types downward into expressions
- Infer lambda parameter types from context (no annotations needed)
- Better error messages with type context
- Example: `Filter(users, u => u.active)` works without `(u: User)`

**3.2 Row Polymorphism**
- Allow functions to accept records with "at least" certain fields
- Enable flexible data transformation pipelines
- Records with extra fields pass through without explicit projection
- Example: `GetName(user)` works whether user has 2 fields or 20

---

## Implementation Guidelines

> **Full Protocol:** See `CLAUDE.md` section "Architecture Improvement Tasks (Phase-Based)" and each agent's `QUEUE.md` for the complete implementation protocol.

### Context Files Are Canonical

Each task has a context file in `agents/phase-3/context-*.md`. These are the **authoritative source** for implementation.

### Quick Reference: Task Workflow

```
BEFORE CODING:
1. Verify worktree exists (create if needed)
2. Complete Agent Startup Checklist (CLAUDE.md)
3. Verify build: make compile && make test
4. Read context file THOROUGHLY (ALL sections)
5. Update QUEUE.md and claim issue on GitHub

WHILE IMPLEMENTING:
6. Follow Implementation Guide steps in order
7. Write tests (MANDATORY) - >80% coverage target
8. Update documentation (MANDATORY)
9. Make incremental commits, run tests before each

BEFORE MERGING:
10. Verify ALL Acceptance Criteria pass
11. Self-review (CLAUDE.md checklist)
12. Rebase on master, resolve conflicts, re-run tests
13. Merge and push to master
14. Update QUEUE.md (move to Completed Issues)
```

---

## Success Criteria

### Phase 1 Complete When:
- [x] All 4 tasks merged to master
- [x] Benchmark shows measurable improvement
- [x] No test regressions

### Phase 2 Complete When:
- [x] All 4 tasks merged to master
- [x] Parse time reduced by 2x+ on benchmark suite
- [x] Runtime improved by 10%+ on benchmark suite

### Phase 3 Complete When:
- [ ] Both tasks merged to master
- [ ] Lambda parameter inference works without explicit annotations
- [ ] Records with extra fields accepted where compatible (row polymorphism)

---

*Last Updated: 2026-01-23 (Simplified to 3 phases)*
