# Type Safety

> **Path**: `docs/features/type-safety/`
> **Parent**: [features/](../README.md)

Compile-time validation of field accesses and type operations.

## Ethos Summary

- Types are **structural**, not nominal
- Field accesses are **validated at compile time**
- Type errors **never reach runtime**
- Type inference is **decidable** (always terminates)

## Contents

| File | Description |
|------|-------------|
| PHILOSOPHY.md | Why compile-time validation matters |
| ETHOS.md | Constraints for LLMs working on the type system |
| record-types.md | Record type syntax and operations |
| type-algebra.md | Merge, projection, element-wise operations |
| optionals.md | Optional types and null handling |
| unions.md | Union types and discrimination |

## Quick Reference

```constellation
# Record types
type User = {id: Int, name: String, email: String}

# Type algebra
merged = record1 + record2           # merge
projected = record[field1, field2]   # projection
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `core` | Type definitions (CType) | `TypeSystem.scala` |
| `lang-compiler` | Type checking, inference | `TypeChecker.scala`, `SemanticType.scala` |
