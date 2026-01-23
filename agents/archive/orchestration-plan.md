# Orchestration Plan: @example Annotation

**Epic:** [#100](https://github.com/VledicFranco/constellation-engine/issues/100)
**Status:** In Progress (64% complete)
**Started:** 2026-01-21

## Overview

Add support for `@example` annotations on input declarations in constellation-lang. This metadata enables the VSCode extension to pre-populate the run widget with example values.

```constellation
@example("hello world")
in text: String

@example({ name: "Alice", age: 30 })
in user: { name: String, age: Int }
```

## Implementation Tasks

| Phase | Issue | Title | Agent | Status |
|-------|-------|-------|-------|--------|
| **1: Foundation** |||||
| | [#101](https://github.com/VledicFranco/constellation-engine/issues/101) | AST: Add Annotation types | Agent 1 | ✅ Complete |
| | [#102](https://github.com/VledicFranco/constellation-engine/issues/102) | Parser: Add @example parsing | Agent 1 | ✅ Complete |
| **2: Semantic** |||||
| | [#103](https://github.com/VledicFranco/constellation-engine/issues/103) | Type Checker: Validate examples | Agent 1 | ✅ Complete |
| **3: LSP** |||||
| | [#104](https://github.com/VledicFranco/constellation-engine/issues/104) | LSP Protocol: Add example field | Agent 3 | ✅ Complete |
| | [#105](https://github.com/VledicFranco/constellation-engine/issues/105) | LSP Server: Extract examples | Agent 3 | ✅ Complete |
| **4: Extension** |||||
| | [#106](https://github.com/VledicFranco/constellation-engine/issues/106) | VSCode: Update ScriptRunnerPanel | Agent 3 | ✅ Complete |
| **5: Docs & QA** |||||
| | [#107](https://github.com/VledicFranco/constellation-engine/issues/107) | Documentation: Language reference | Agent 4 | 🟡 Ready |
| | [#108](https://github.com/VledicFranco/constellation-engine/issues/108) | Tests: Parser tests | Agent 4 | ✅ Complete |
| | [#109](https://github.com/VledicFranco/constellation-engine/issues/109) | Tests: Type checker tests | Agent 4 | 🔄 In Progress |
| | [#110](https://github.com/VledicFranco/constellation-engine/issues/110) | Tests: LSP tests | Agent 4 | 🟡 Ready |
| | [#111](https://github.com/VledicFranco/constellation-engine/issues/111) | QA: End-to-end testing | Agent 4 | ⏳ Blocked by #109, #110 |

**Legend:** ✅ Complete | 🟡 Ready | ⏳ Blocked | 🔄 In Progress

## Dependency Graph

```
#101 (AST) ✅
  │
  ├──▶ #102 (Parser) ✅ ──▶ #108 (Parser Tests) ✅
  │         │         └──▶ #107 (Docs) 🟡
  │         │
  │         └──▶ #103 (Type Checker) ✅ ──▶ #109 (TC Tests) 🔄
  │                    │
  │    ┌───────────────┘
  │    │
  ▼    ▼
#104 (LSP Protocol) ✅
  │
  └──▶ #105 (LSP Server) ✅ ──▶ #110 (LSP Tests) 🟡
              │
              └──▶ #106 (VSCode Extension) ✅
                          │
                          └──▶ #111 (QA) ⏳
```

## Wave Execution Plan

| Wave | Agent 1 | Agent 3 | Agent 4 | Status |
|------|---------|---------|---------|--------|
| 1 | #101 (AST) | - | - | ✅ Complete |
| 2 | #102 (Parser) | #104 (LSP Protocol) | - | ✅ Complete |
| 3 | #103 (Type Checker) | #105 (LSP Server) | #107, #108 | ✅ Complete |
| 4 | - | #106 (VSCode) | #109, #110 (Tests) | 🔄 In Progress |
| 5 | - | - | #111 (QA) | ⏳ Pending |

## Agent Assignments

| Agent | Track | Issues | Status |
|-------|-------|--------|--------|
| Agent 1 | Compiler/Language | #101 ✅, #102 ✅, #103 ✅ | ✅ Done (3/3) |
| Agent 2 | Runtime | _(none - no runtime work)_ | N/A |
| Agent 3 | Integration | #104 ✅, #105 ✅, #106 ✅ | ✅ Done (3/3) |
| Agent 4 | Infrastructure | #107 🟡, #108 ✅, #109 🔄, #110 🟡, #111 ⏳ | 🔄 1/5 done |

## Progress Summary

- **Total Tasks:** 11
- **Completed:** 7 (64%)
- **In Progress:** 1
- **Ready:** 2
- **Blocked:** 1

## Current Actions

**In progress:**
- Agent 4: #109 (TC Tests) - Type checker tests for @example validation

**Ready to start:**
- Agent 4: #107 (Docs) - Update language reference
- Agent 4: #110 (LSP Tests) - LSP integration tests

**Blocked:**
- #111 (QA) - Waiting for #109, #110

## Completion Log

| Date | Issue | Agent | Notes |
|------|-------|-------|-------|
| 2026-01-21 | #108 | Agent 4 | Parser tests for @example syntax |
| 2026-01-21 | #106 | Agent 3 | VSCode ScriptRunnerPanel uses example values |
| 2026-01-21 | #105 | Agent 3 | LSP Server extracts and exposes examples |
| 2026-01-21 | #104 | Agent 3 | LSP Protocol: example field added to InputField |
| 2026-01-21 | #103 | Agent 1 | Type Checker: validate @example types match input types |
| 2026-01-21 | #102 | Agent 1 | Parser: @example annotation parsing with backtrack fallback |
| 2026-01-21 | #101 | Agent 1 | Added Annotation sealed trait, updated InputDecl |

---
*Last Updated: 2026-01-21*
