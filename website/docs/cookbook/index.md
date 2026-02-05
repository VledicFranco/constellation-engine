---
title: "Cookbook"
sidebar_position: 0
description: "Practical recipes and patterns for Constellation Engine"
---

# Cookbook

Practical recipes for common pipeline patterns. Each page includes a runnable `.cst` pipeline, an explanation of how it works, sample input/output, and variations.

## Getting Started

Simple pipelines that introduce core concepts.

| Recipe | Concepts |
|---|---|
| [Hello World](hello-world.md) | Inputs, module calls, outputs |
| [Record Types](record-types.md) | Type definitions, field access |
| [Type Algebra](type-algebra.md) | Record merge (`+`), projection (`[]`) |
| [Candidates Batch](candidates-batch.md) | `Candidates<T>`, batch merge and projection |
| [Simple Transform](simple-transform.md) | Single module transformation |

## Language Patterns

Recipes that demonstrate constellation-lang features.

| Recipe | Concepts |
|---|---|
| [Text Analysis](text-analysis.md) | Multi-step pipelines, multiple outputs |
| [String Interpolation](string-interpolation.md) | `${expression}` syntax, escape sequences |
| [Namespaces](namespaces.md) | `use` imports, aliasing, fully qualified calls |
| [Lambdas and HOF](lambdas-and-hof.md) | `filter`, `map`, `all`, `any` with lambda syntax |
| [Branch Expressions](branch-expressions.md) | Multi-way conditionals with `branch {}` |
| [Guard and Coalesce](guard-and-coalesce.md) | `when` guards, `??` operator, fallback chains |
| [Optional Types](optional-types.md) | `Optional<T>` inputs, coalesce patterns |
| [Union Types](union-types.md) | `A \| B` type declarations |

## Data Processing

Real-world data transformation and analysis patterns.

| Recipe | Concepts |
|---|---|
| [Data Pipeline](data-pipeline.md) | Filter, transform, aggregate, format |
| [Lead Scoring](lead-scoring.md) | Record types, arithmetic, conditionals, guards |
| [Fan-Out / Fan-In](fan-out-fan-in.md) | Parallel service calls, merge, project |
| [Conditional Branching](conditional-branching.md) | Route execution with `when`, `??`, `branch` |

## Resilience

Patterns for building reliable pipelines that handle failures.

| Recipe | Concepts |
|---|---|
| [Retry and Fallback](retry-and-fallback.md) | `retry`, `delay`, `backoff`, `fallback` |
| [Caching](caching.md) | `cache` TTL, `cache_backend` |
| [Error Handling](error-handling.md) | `on_error: skip \| log \| fail` |
| [Rate Limiting](rate-limiting.md) | `throttle`, `concurrency` |
| [Priority and Lazy](priority-and-lazy.md) | `priority` levels, `lazy` evaluation |
| [Resilient Pipeline](resilient-pipeline.md) | All options combined in a realistic scenario |
| [Caching Strategies](caching-strategies.md) | Short TTL, long TTL, distributed backends |
