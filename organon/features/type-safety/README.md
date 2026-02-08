# Type Safety

> **Path**: `organon/features/type-safety/`
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
| [PHILOSOPHY.md](./PHILOSOPHY.md) | Why compile-time validation matters |
| [ETHOS.md](./ETHOS.md) | Constraints for LLMs working on the type system |
| [record-types.md](./record-types.md) | Record type syntax and operations |
| [type-algebra.md](./type-algebra.md) | Merge, projection, element-wise operations |
| [optionals.md](./optionals.md) | Optional types and null handling |
| [unions.md](./unions.md) | Union types and discrimination |

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
| `core` | Runtime type/value representation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` |
| `lang-parser` | Parse type expressions | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| `lang-compiler` | Type checking, inference | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala` |
| `lang-compiler` | Semantic type definitions | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala` |
| `lang-compiler` | Subtyping rules | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala` |

## See Also

- [website/docs/language/types/](../../language/types/) - Language reference for all types
- [website/docs/language/expressions/](../../language/expressions/) - Expression syntax including type operations
