---
title: "Language Overview"
sidebar_position: 1
description: "Complete reference for constellation-lang, a DSL for type-safe data transformation pipelines with strong typing."
---

# constellation-lang Reference

constellation-lang is a domain-specific language for defining data transformation pipelines. It provides a declarative syntax with strong typing, type algebra, and field projections.

:::note Learning Path
If you are new to constellation-lang, start with [Pipeline Structure](./pipeline-structure.md), then explore [Types](./types.md) and [Declarations](./declarations.md). The orchestration features build on these fundamentals.
:::

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
- [Guard Expressions](./guards.md) - Conditional execution with `when`
- [Coalesce Operator](./coalesce.md) - Fallback values with `??`
- [Lambda Expressions](./lambdas.md) - Inline functions for higher-order operations

### Module Call Options

- [Module Options Reference](./module-options.md) - Complete guide to `with` clause options
  - [Resilience Options](./options/retry.md) - retry, timeout, delay, backoff, fallback
  - [Caching Options](./options/retry.md) - cache, cache_backend
  - [Rate Control Options](./options/retry.md) - throttle, concurrency
  - [Advanced Options](./options/retry.md) - on_error, lazy, priority
- [Resilient Pipelines Guide](./resilient-pipelines.md) - Real-world patterns

:::tip Module Options for Production
When building production pipelines, explore the [Module Options Reference](./module-options.md) for resilience features like retry, timeout, and caching that make your pipelines robust.
:::

### Reference

- [Examples](./examples.md) - Complete example pipelines
- [Error Messages](./error-messages.md) - Understanding compiler errors
