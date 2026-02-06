---
title: "Roadmap"
sidebar_position: 0
description: "Public roadmap for Constellation Engine"
---

# Roadmap

This page tracks what has shipped, what is planned, and what is explicitly out of scope. It is updated with each release.

:::note Roadmap Priorities
Items in **v1.1 Planned** are committed for the next release. Items in **v2.0 Ideas** are under consideration and may change based on community feedback.
:::

## v1.0 — Shipped

The v1.0 release provides a stable foundation for type-safe pipeline orchestration.

### Core

- Type-safe pipeline compilation with field-level validation
- Record type algebra: merge (`+`), projection (`[]`), field access (`.`)
- `Candidates<T>` batch type with merge and projection
- `Optional<T>` with guard expressions (`when`) and coalesce (`??`)
- Union types (`A | B`)
- Branch expressions (`branch { condition -> value, otherwise -> default }`)
- Lambda expressions and higher-order functions (`filter`, `map`, `all`, `any`)
- String interpolation with escape sequences
- Namespace imports with aliasing (`use stdlib.math as m`)

### Runtime

- Automatic parallelization from DAG structure
- Per-call resilience: `retry`, `timeout`, `delay`, `backoff`, `fallback`
- Caching with configurable TTL and pluggable backends
- Rate limiting (`throttle`) and concurrency control
- Lazy evaluation and priority scheduling
- Error handling strategies (`on_error: skip | log | fail`)
- Graceful shutdown with drain timeout
- Cancellable execution via Cats Effect fibers

### Infrastructure

- HTTP API with auth, CORS, and rate limiting middleware
- LSP server with diagnostics, autocomplete, hover, and go-to-definition
- Web dashboard with DAG visualization and execution history
- VSCode extension with syntax highlighting and integrated LSP
- Structured health checks (liveness, readiness, detail)
- SPI extension points: metrics, tracing, execution listener, cache backend, execution storage
- Docker and Kubernetes deployment support

## v1.1 — Planned

Focus: developer experience and onboarding.

- **CLI tool** — `constellation init`, `constellation run`, `constellation validate` for working with pipelines outside the JVM
- **Giter8 project template** — `sbt new constellation/constellation.g8` for quick project scaffolding
- **Additional standard library modules** — date/time operations, JSON manipulation, HTTP client module
- **Expanded test coverage** — property-based tests for the type checker, fuzzing for the parser
- **Performance baseline CI** — automated benchmark regression checks in CI

## v2.0 — Ideas

These are directions under consideration, not commitments.

- **Distributed execution** — run pipeline steps across multiple JVM nodes with work-stealing
- **Streaming mode** — support unbounded input streams alongside request/response pipelines
- **Python/TypeScript module SDKs** — define modules in languages other than Scala
- **Visual pipeline editor** — drag-and-drop pipeline composition in the web dashboard
- **Pipeline versioning** — deploy multiple versions of a pipeline with traffic splitting

## Explicitly Out of Scope

:::warning Not a General-Purpose Tool
Constellation is purpose-built for type-safe pipeline orchestration. If your use case matches one of the items below, consider the suggested alternatives instead.
:::

These are things Constellation Engine is not intended to become:

| Area | Rationale |
|---|---|
| General-purpose programming language | Constellation-lang is a DSL for pipeline composition, not application logic |
| Distributed compute framework | Use Spark, Flink, or Ray for distributed data processing |
| Workflow scheduler | Use Temporal, Airflow, or Dagster for long-running multi-step workflows with human-in-the-loop |
| Database or storage layer | Constellation orchestrates calls to external systems; it doesn't store data |

## How to Influence the Roadmap

:::tip Want a Feature?
Open a GitHub Issue with the `enhancement` label and describe your use case. Issues with clear business value and community upvotes are prioritized for future releases.
:::

- **Feature requests** — open a [GitHub Issue](https://github.com/VledicFranco/constellation-engine/issues) with the `enhancement` label
- **Bug reports** — open an issue with reproduction steps
- **Contributions** — see the [Contributing Guide](/docs/resources/contributing) for how to submit changes
- **Discussion** — comment on existing issues to add context or vote with a thumbs-up reaction
