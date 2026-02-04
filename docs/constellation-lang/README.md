# constellation-lang Reference

constellation-lang is a domain-specific language for defining data transformation pipelines. It provides a declarative syntax with strong typing, type algebra, and field projections.

## Table of Contents

### Core Language

- [Pipeline Structure](./pipeline-structure.md) - Overall pipeline organization
- [Types](./types.md) - Primitive, record, union, optional, and parameterized types
- [Declarations](./declarations.md) - Type definitions, inputs, assignments, outputs
  - [Input Annotations](./declarations.md#input-annotations) - `@example` for input metadata
- [Expressions](./expressions.md) - Variables, function calls, projections, conditionals
- [Type Algebra](./type-algebra.md) - Merging types with the `+` operator
- [Comments](./comments.md) - Line comments syntax

### Orchestration

- [Orchestration Algebra](./orchestration-algebra.md) - Boolean algebra for control flow
  - Guard expressions (`when`)
  - Coalesce operator (`??`)
  - Branch expressions
  - Lambda expressions

### Module Call Options

- [Module Options Reference](./module-options.md) - Complete guide to `with` clause options
  - [Resilience Options](./options/) - retry, timeout, delay, backoff, fallback
  - [Caching Options](./options/) - cache, cache_backend
  - [Rate Control Options](./options/) - throttle, concurrency
  - [Advanced Options](./options/) - on_error, lazy, priority
- [Resilient Pipelines Guide](./examples/resilient-pipelines.md) - Real-world patterns

### Reference

- [Examples](./examples.md) - Complete example pipelines
- [Error Messages](./error-messages.md) - Understanding compiler errors
