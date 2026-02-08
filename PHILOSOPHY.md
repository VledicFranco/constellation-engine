# Constellation Engine Philosophy

> Version 1.0 — Product-Level Organon
>
> Part of the [Organon System](./organon/README.md). See also: [ETHOS.md](./ETHOS.md)
>
> Why Constellation exists and the thinking behind its design.

---

## The Problem

Modern backend systems compose many services: APIs, databases, caches, ML models. This composition is typically done in general-purpose code (Scala, Python, Go) where:

1. **Type errors hide until runtime.** A typo in a field name compiles fine but fails in production.
2. **Parallelization is manual.** Developers must identify independent calls and wire up concurrency.
3. **Resilience is scattered.** Retry logic, timeouts, and fallbacks are copy-pasted across services.
4. **Changes require redeploys.** Modifying pipeline logic means rebuilding and redeploying.

These problems compound. A team adding a new API call must: identify parallelization opportunities, add retry logic, handle timeouts, test failure scenarios, and redeploy. Most of this is boilerplate that obscures the actual business logic.

---

## The Bet

Constellation bets that **pipeline composition is a domain worth its own language**.

A domain-specific language (DSL) can:
- Validate field accesses at compile time (not runtime)
- Infer parallelization from data dependencies (not manual wiring)
- Express resilience declaratively (not imperatively)
- Enable hot-reload without redeploys (not baked into artifacts)

The trade-off: users learn a new language. We believe this cost is justified when:
- Pipeline logic is complex enough that type safety pays off
- Multiple services are composed (not simple CRUD)
- Resilience and observability matter (not fire-and-forget)

---

## Core Design Decisions

### 1. Type System First

Everything flows from the type system. Constellation's types are structural (records, lists, optionals, unions) rather than nominal. This enables:

- **Record merging:** `a + b` combines fields from two records
- **Projection:** `a[field1, field2]` extracts a subset of fields
- **Field access validation:** `a.field` is checked at compile time

We chose structural typing over nominal typing because pipelines transform data shapes, not class hierarchies. The question is "does this record have a `userId` field?" not "is this object an instance of `User`?"

### 2. Modules as Black Boxes

Modules are Scala functions wrapped in metadata. The DSL doesn't see inside them—it only knows their input and output types. This separation means:

- **DSL stays simple:** No need to express arbitrary computation
- **Scala stays powerful:** Complex logic lives in modules
- **Boundary is clear:** DSL for composition, Scala for computation

### 3. Declarative Resilience

Resilience options (`retry`, `timeout`, `fallback`, `cache`) are part of the language, not library calls. This means:

- **Compiler validates:** Invalid options are caught at compile time
- **Runtime optimizes:** The scheduler can make informed decisions
- **Syntax is uniform:** All resilience looks the same

We rejected embedding resilience in modules because it scatters policy across implementations. Keeping resilience in the DSL makes it visible and consistent.

### 4. DAG as IR

Pipelines compile to a Directed Acyclic Graph (DAG), not imperative code. The DAG:

- **Exposes parallelism:** Independent nodes can execute concurrently
- **Enables analysis:** Cycles, unreachable nodes, and type mismatches are detectable
- **Supports visualization:** The DAG can be rendered in the dashboard

### 5. Hot Execution for Development

"Hot" execution compiles and runs in a single request. This enables:

- **REPL-like iteration:** Change code, see results immediately
- **No build step:** Development doesn't require compilation artifacts

"Cold" execution (pre-compiled) exists for production throughput, but hot execution is the default developer experience.

---

## What Constellation Is Not

- **Not a general-purpose language.** Constellation expresses composition, not computation.
- **Not a workflow engine.** No long-running processes, no human-in-the-loop steps (yet).
- **Not a data pipeline tool.** Not optimized for batch ETL or streaming. Use Spark/Flink for that.
- **Not a replacement for your services.** Constellation orchestrates services; it doesn't replace them.

---

## Trade-Offs We Made

| Decision | Benefit | Cost |
|----------|---------|------|
| Custom DSL | Type-safe composition | Learning curve |
| Structural types | Flexible data transformation | No inheritance |
| Declarative resilience | Visible, consistent policy | Less flexibility than code |
| DAG IR | Automatic parallelization | No imperative control flow |
| Hot execution | Fast iteration | Compilation overhead per request |

---

## Influences

- **Functional programming:** Immutable data, composition over inheritance
- **Type theory:** Structural types, row polymorphism, type inference
- **Dataflow languages:** Implicit parallelism from dependencies
- **Resilience patterns:** Circuit breakers, bulkheads, retry with backoff

---

## Further Reading

- [features/type-safety/PHILOSOPHY.md](./features/type-safety/PHILOSOPHY.md) — Why compile-time validation matters
- [features/resilience/PHILOSOPHY.md](./features/resilience/PHILOSOPHY.md) — Why resilience is built into the language
- [features/execution/PHILOSOPHY.md](./features/execution/PHILOSOPHY.md) — Why hot/cold/suspended modes exist
