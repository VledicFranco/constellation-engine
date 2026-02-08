# Execution Modes: Philosophy

> **Path**: `organon/features/execution/PHILOSOPHY.md`
> **Parent**: [execution/](./README.md)

Why Constellation Engine provides three distinct execution modes.

## The Core Insight

Pipeline execution is not a single problem. It is three related but fundamentally different problems:

1. **Interactive development** needs speed of iteration
2. **Production services** need throughput and predictability
3. **Multi-step workflows** need durability and resumability

A single execution model cannot optimally serve all three. Attempting to do so creates friction: developers wait for unnecessary caching overhead, production systems pay compilation costs on every request, and workflow systems lose state on restarts.

## Hot Execution: Optimize for Developer Speed

### The Problem It Solves

During development, the critical metric is **time from change to feedback**. Developers modify code, run it, see results, and iterate. Any delay breaks flow and reduces productivity.

Traditional approaches force a separation: compile in one step, run in another. This separation is unnecessary overhead when the developer's intent is "run this now and show me what happens."

### The Design Choice

Hot execution combines compilation and execution into a single request. The pipeline source is sent directly, compiled on the fly, and executed immediately. The result returns in one response.

This optimizes the inner development loop. There is no need to pre-register pipelines, manage versions, or coordinate between compilation and execution phases. The developer thinks "run this" and the system runs it.

### The Trade-off

Hot execution pays compilation costs on every request. For a simple pipeline, this might add 50-100ms of latency. This is acceptable for development but wasteful for production.

The compiled pipeline image is cached by structural hash, so identical source will not recompile. But any source change triggers recompilation. This is the correct trade-off for development where source changes are frequent.

## Cold Execution: Optimize for Production Throughput

### The Problem It Solves

Production systems run the same pipelines repeatedly. The pipeline source changes rarely (at deployment time), but executions happen continuously (on every request).

Recompiling on every request wastes compute. More importantly, it introduces variance: compilation time varies with source complexity, making latency unpredictable. Production systems need consistent, fast response times.

### The Design Choice

Cold execution separates compilation from execution. Pipelines are compiled once, stored by reference (name or structural hash), and executed by reference thereafter.

This moves compilation cost to deployment time. Runtime execution pays only the cost of actual module invocation. Latency drops from ~100ms to ~1ms for the execution overhead.

### The Trade-off

Cold execution requires explicit pipeline management. Pipelines must be compiled and registered before they can be executed. This adds operational complexity but provides clear version boundaries.

The structural hash provides content-addressed storage: identical source always produces identical hash. This enables safe updates, rollbacks, and canary deployments.

## Suspended Execution: Optimize for Workflow Durability

### The Problem It Solves

Real-world workflows are not atomic. A pipeline might need human approval before proceeding. Data might arrive incrementally over hours or days. External systems might respond asynchronously.

Traditional execution models fail here. Either the execution blocks waiting for input (tying up resources), or it fails and must be restarted from scratch (losing progress).

### The Design Choice

Suspended execution persists partial progress. When a pipeline encounters a missing input, it suspends rather than failing. All computed values are saved. The execution can resume later when the missing input becomes available.

This transforms pipelines from atomic operations into durable workflows. Progress is never lost. Resources are not held while waiting. The execution naturally models the workflow's temporal structure.

### The Trade-off

Suspended execution adds storage and coordination overhead. Each suspension requires persisting state. Resumption requires retrieving and validating that state. There are edge cases around pipeline version changes and stale suspensions.

The complexity is justified for workflows that genuinely span time. For atomic operations that should either complete or fail, suspension is unnecessary overhead.

## Mode Selection is Explicit

Constellation Engine does not attempt to infer which mode you want. The mode is determined by which endpoint you call:

| Mode | Endpoint | Explicit Action |
|------|----------|-----------------|
| Hot | `POST /run` | Send source directly |
| Cold | `POST /execute` | Reference pre-compiled pipeline |
| Suspended | `POST /executions/{id}/resume` | Resume existing execution |

This explicitness is intentional. Implicit mode selection would require heuristics that could guess wrong. A request with source code might be development (compile fresh) or deployment (register for cold execution). A request with missing inputs might want suspension (workflow) or failure (validation).

By making mode explicit, the system behavior is predictable. Developers know what will happen. Operations can reason about system load. Debugging is straightforward.

## The Unified Model

Despite providing three modes, Constellation Engine uses a unified internal model. All execution flows through the same runtime. All pipelines use the same compilation. All suspensions use the same state management.

The modes differ in:
- **When** compilation happens (hot: at request time; cold: before request)
- **Whether** state persists (hot/cold: no; suspended: yes)
- **How** inputs are provided (hot/cold: all at once; suspended: incrementally)

This unified model means:
- A pipeline developed with hot execution works identically in cold execution
- A cold pipeline can suspend if inputs are missing
- Suspended executions can be resumed against cold pipelines
- Behavior is consistent regardless of mode

The three modes are not three systems. They are three interfaces to the same execution engine, each optimized for a different use case.

## Summary

| Mode | Optimizes For | Accepts Trade-off Of |
|------|--------------|----------------------|
| Hot | Developer iteration speed | Compilation cost per request |
| Cold | Production throughput | Pipeline management overhead |
| Suspended | Workflow durability | State management complexity |

Choose the mode that matches your use case. For most projects:
- Hot during development
- Cold in production
- Suspended for multi-step workflows

The modes compose naturally. A typical deployment uses all three: developers iterate with hot execution, production runs cold, and approval workflows use suspension.
