# Phase 3: Type System Enhancements

**Timeline:** 3-5 weeks
**Risk Level:** Medium-High
**Prerequisites:** Phase 2 complete (Subtyping #119 is key dependency)

---

## Overview

Phase 3 enhances the type system to make Constellation more ergonomic for data transformation pipelines. Both features reduce boilerplate and make the language more flexible.

## Tasks

| Task | Title | Context File | Dependencies | Effort |
|------|-------|--------------|--------------|--------|
| 3.1 | Bidirectional Type Inference | `context-bidirectional-inference.md` | Task 2.4 ✅ | High (2 weeks) |
| 3.2 | Row Polymorphism | `context-row-polymorphism.md` | Tasks 2.4 ✅, 3.1 | Very High (3 weeks) |

## Dependency Graph

```
Phase 2 Complete (Subtyping ✅)
       │
       ▼
Task 3.1: Bidirectional Inference
(Lambda params inferred from context)
       │
       ▼
Task 3.2: Row Polymorphism
(Records with extra fields accepted)
       │
       ▼
Phase 3 Complete
```

## Context Files

- `context-bidirectional-inference.md` - Type inference improvements (ready)
- `context-row-polymorphism.md` - Flexible record types (ready)

## What These Enable

### 3.1 Bidirectional Type Inference

**Before:**
```constellation
# Must annotate lambda parameters
active = Filter(users, (u: { name: String, active: Boolean }) => u.active)
```

**After:**
```constellation
# Parameter type inferred from context
active = Filter(users, u => u.active)
```

### 3.2 Row Polymorphism

**Before:**
```constellation
# Must project to exact fields expected
name = GetName(user{name})
```

**After:**
```constellation
# Extra fields pass through - GetName just needs 'name' field
name = GetName(user)  # Works whether user has 2 fields or 20
```

## Key Resources

### Bidirectional Type Inference
- [Bidirectional Typing](https://arxiv.org/abs/1908.05839)
- [Complete and Easy Bidirectional Typechecking](https://arxiv.org/abs/1306.6032)

### Row Polymorphism
- [Row Types Paper](http://homepages.inf.ed.ac.uk/wadler/papers/row-poly/row-poly.pdf)
- [OCaml Object Types](https://ocaml.org/docs/objects)
- [TypeScript Structural Typing](https://www.typescriptlang.org/docs/handbook/type-compatibility.html)

## Success Criteria

Phase 3 is complete when:
- [ ] Lambda parameter inference works without explicit annotations
- [ ] Records with extra fields accepted where compatible
- [ ] All tests pass, no regressions
