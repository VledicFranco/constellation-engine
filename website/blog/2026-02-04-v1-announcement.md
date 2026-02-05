---
title: Constellation Engine v1.0
authors: [team]
tags: [release, v1]
---

Constellation Engine v1.0 is here — a type-safe pipeline orchestration framework for Scala 3.

<!-- truncate -->

## What is Constellation Engine?

Constellation Engine separates **what** your data pipeline does from **how** it's implemented. You define processing modules in Scala, compose them into pipelines using a declarative DSL (constellation-lang), and get compile-time validation of field accesses, type operations, and module signatures — before any code runs.

```constellation
in userId: String

profile  = ProfileService(userId)
activity = ActivityService(userId)

combined = profile + activity
summary  = combined[userName, activityScore]

out summary
```

The compiler knows the exact shape of every intermediate value. A typo in a field name is a compile error, not a runtime surprise.

## What's New in v1.0

### Type System

- **Record type algebra** — merge records with `+`, project fields with `[]`, access fields with `.`
- **Candidates batch type** — `Candidates<T>` for batch operations with automatic per-item merge and projection
- **Optional types** — `Optional<T>` with `when` guards and `??` coalesce
- **Union types** — `A | B` for variant data modeling
- **Lambda expressions** — `(x) => expr` syntax with `filter`, `map`, `all`, `any`
- **Branch expressions** — clean multi-way conditionals with `branch { condition -> value }`
- **String interpolation** — `"Hello, ${name}!"` with escape sequences

### Runtime

- **Automatic parallelization** — independent pipeline steps run concurrently without explicit `parMapN`
- **Per-call resilience** — `retry`, `timeout`, `delay`, `backoff`, `fallback` as declarative options on any module call
- **Caching** — configurable TTL with pluggable backends (in-memory, Redis via SPI)
- **Rate limiting** — `throttle` and `concurrency` options for external API calls
- **Priority scheduling** — `critical`, `high`, `normal`, `low`, `background` execution priorities
- **Lazy evaluation** — defer computation until the result is consumed
- **Graceful shutdown** — three-phase drain with configurable timeout and fiber cancellation

### Infrastructure

- **HTTP API** — with authentication, CORS, and rate limiting middleware
- **LSP server** — diagnostics, autocomplete, hover, and go-to-definition
- **Web dashboard** — DAG visualization, execution history, and script management
- **VSCode extension** — syntax highlighting, integrated LSP, and one-click execution
- **Docker and Kubernetes** — production deployment with health checks and structured logging
- **SPI extension points** — metrics, tracing, execution listeners, cache backends, and execution storage

## API Stability

v1.0 marks the beginning of semantic versioning guarantees:

- **Stable APIs** — `ModuleBuilder`, `Constellation`, `ConstellationServer`, and constellation-lang syntax will not have breaking changes in 1.x releases
- **SPI contracts** — extension point interfaces (`MetricsProvider`, `TracerProvider`, `CacheBackend`, etc.) are stable
- **HTTP API** — endpoint paths and response shapes are stable

See the [API Stability](/docs/architecture/api-stability) page for the full stability matrix.

## Getting Started

Add Constellation to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.constellation" %% "constellation-core"     % "1.0.0",
  "io.constellation" %% "constellation-runtime"  % "1.0.0",
  "io.constellation" %% "constellation-compiler" % "1.0.0",
  "io.constellation" %% "constellation-http-api" % "1.0.0"
)
```

Then follow the [Tutorial](/docs/getting-started/tutorial) to build your first pipeline, or browse the [Cookbook](/docs/cookbook/) for 25 ready-to-use patterns.

## What's Next

The [Roadmap](/docs/resources/roadmap) covers planned work for v1.1 and beyond:

- **CLI tool** for working with pipelines outside the JVM
- **Project template** via Giter8 for quick scaffolding
- **Additional standard library modules** for dates, JSON, and HTTP
- **Performance baseline CI** for automated regression checks

We'd love your feedback — open an [issue](https://github.com/VledicFranco/constellation-engine/issues) or check the [Contributing Guide](/docs/resources/contributing) to get involved.
