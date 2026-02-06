---
title: "Why Constellation"
sidebar_position: 0
description: "When and why to use Constellation Engine for pipeline orchestration"
---

# Why Constellation

## The Problem

:::note Pain Point
Backend services that aggregate data from multiple sources accumulate a specific class of bugs over time: **runtime type errors in pipeline composition**. Field name typos, type mismatches, and null values hide in code that compiles fine but fails at production runtime.
:::

Consider a typical Scala service that fetches user data from three APIs, merges the results, and returns a subset of fields:

```scala
for {
  profile  <- profileApi.get(userId)
  activity <- activityApi.get(userId)
  prefs    <- prefsApi.get(userId)
} yield {
  val merged = profile ++ activity ++ prefs
  // Typo: "usrName" instead of "userName" — compiles, fails at runtime
  Json.obj("name" -> merged("usrName"), "score" -> merged("activityScore"))
}
```

These bugs share common traits:

- **They compile successfully** — the types are all `Map[String, Any]` or `Json`
- **They surface late** — in staging, in production, or during edge-case inputs
- **They're hard to trace** — the error message says "key not found" with no indication of which pipeline step produced the wrong shape

## How Constellation Solves It

Constellation validates field accesses and type operations **at compile time**, before any code runs:

```constellation
in profile: { userName: String, email: String }
in activity: { activityScore: Int, lastLogin: String }
in prefs: { theme: String }

merged = profile + activity + prefs

# Compile error: field 'usrName' not found. Did you mean 'userName'?
result = merged[usrName, activityScore]
```

The compiler knows the exact shape of `merged` — `{ userName: String, email: String, activityScore: Int, lastLogin: String, theme: String }` — and rejects any access to a field that doesn't exist.

:::tip Separation of Concerns
This works because Constellation separates **pipeline definition** (a declarative `.cst` script) from **module implementation** (Scala functions with typed inputs/outputs). The compiler can reason about the entire pipeline graph before execution.
:::

### What you get

| Capability | How it works |
|---|---|
| **Compile-time field validation** | Record types track every field name and type through merges, projections, and module calls |
| **Automatic parallelization** | The DAG compiler identifies independent branches and runs them concurrently — no manual `parMapN` |
| **Declarative resilience** | `retry`, `timeout`, `fallback`, `cache` are per-call options, not wrapper code |
| **Hot reload** | Change a `.cst` file, hit the API — no recompile, no restart |
| **Built-in observability** | Execution traces, DAG visualization, and metrics are available out of the box |

## When to Use Constellation

**API composition (Backend-for-Frontend)**
You aggregate data from 3+ microservices, merge fields, and return a shaped response. Constellation's type algebra (`+` for merge, `[]` for projection) makes this declarative and type-safe.

**Data enrichment pipelines**
You take a batch of records, enrich each with data from external sources, and filter/score the results. `Candidates<T>` batch types and per-call resilience options handle this pattern directly.

**Type-safety-first teams**
You already use Scala 3, Cats Effect, and value compile-time guarantees. Constellation extends those guarantees to your pipeline composition layer.

## When Not to Use Constellation

:::warning Know the Limits
Constellation is designed for pipeline orchestration within a single JVM. It is not a replacement for distributed compute frameworks or stream processing systems.
:::

| Use case | Better tool | Why |
|---|---|---|
| CRUD applications | An ORM (Slick, Doobie) | Constellation orchestrates pipelines, not database operations |
| Stream processing | Kafka Streams, Flink, fs2 | Constellation handles request/response pipelines, not unbounded streams |
| ETL at scale | Spark, dbt | Constellation runs in a single JVM; it's not a distributed compute framework |
| Simple request handlers | Direct Scala code | If your endpoint calls one service and returns the result, Constellation adds overhead without benefit |

## Key Differentiators

| Feature | Constellation | Manual Scala | Workflow engines (Temporal, Airflow) |
|---|---|---|---|
| Compile-time type checking | Yes — field-level | Scala compiler only | No — YAML/JSON configs |
| Declarative DSL | `.cst` files | Scala code | YAML/DAG definitions |
| Auto-parallelization | From DAG structure | Manual `parMapN` | Task-level scheduling |
| Hot reload | Yes — no restart | Requires recompile | Varies |
| Built-in resilience | Per-call options | Manual retry/circuit-breaker wrappers | Framework-specific |
| Latency | Sub-100ms compile + execute | Native | Seconds to minutes (scheduler overhead) |

## Next Steps

- [Introduction](introduction.md) — overview of the framework and its components
- [Tutorial](tutorial.md) — build your first pipeline step by step
- [Cookbook](/docs/cookbook/) — 25 example pipelines for common patterns
