# Parallelization Ethos

> Behavioral constraints for LLMs working on scheduling and parallelization.

---

## Core Invariants

1. **Dependencies are inviolable.** A module NEVER executes before ALL its input data nodes are complete. This is the fundamental correctness guarantee.

2. **Layer ordering is strict.** Layer N completes entirely before Layer N+1 begins. No early starts.

3. **Parallelism is automatic.** Independent modules in the same layer run concurrently without explicit annotation.

4. **Results are deterministic.** Given the same inputs, the DAG produces the same outputs regardless of execution timing.

5. **Priority is advisory.** Priority hints influence scheduling order but do not guarantee strict ordering across layers or under contention.

---

## Design Constraints

### When Modifying Layer Computation

- **Dependencies are transitive.** If A depends on B, and B depends on C, then A depends on C.
- **Layer assignment is deterministic.** Same DAG structure produces same layer assignments.
- **Input nodes are Layer 0.** All declared inputs and literals start in Layer 0.
- **Module layer = 1 + max(input layers).** This formula is fixed and not configurable.

### When Modifying Parallel Execution

- **Use `parTraverse`.** Parallel execution uses cats-effect `parTraverse`, not custom threading.
- **Respect cancellation.** If the execution is cancelled, all running modules should be cancelled.
- **Error isolation.** A failing module does not terminate sibling modules in the same layer.
- **State isolation.** Modules share data only through the data table; no shared mutable state.

### When Modifying the Scheduler

- **Starvation prevention is required.** Low-priority tasks must eventually run (aging mechanism).
- **Metrics are mandatory.** Scheduler operations emit metrics for observability.
- **Graceful shutdown.** Pending tasks complete or fail cleanly on shutdown.
- **Queue has no hidden limits.** If `maxQueueSize=0` (unlimited), the queue truly is unlimited.

---

## Decision Heuristics

### "Should this operation be a module or an inline transform?"

**Module** if:
- It has side effects (network, disk, external service)
- It needs priority, timeout, retry, or caching
- It should appear in metrics and tracing

**Inline transform** if:
- It is pure computation (merge, project, field access)
- It is fast (sub-millisecond)
- It doesn't need individual observability

### "Should I add priority to this module call?"

**Add priority** if:
- The module is on a latency-critical path
- The module is blocking user-visible response
- Under contention, this module should preempt others

**Don't add priority** if:
- All modules are equally important
- There's no resource contention
- You're optimizing prematurely

### "Should I enable the bounded scheduler?"

**Enable bounded** if:
- Downstream resources have concurrency limits (DB connections, API rate limits)
- You need priority ordering under contention
- Memory usage from unbounded parallelism is a concern

**Keep unbounded** if:
- All modules are independent and resources are ample
- Simplicity is preferred over fine-grained control
- The overhead of queue management isn't justified

### "How do I handle a priority inversion?"

Priority inversion occurs when a high-priority task waits for a low-priority task.

In Constellation, this is **expected and correct** when there's a data dependency:

```constellation
lowData = LowPriorityModule(input) with priority: low
highResult = HighPriorityModule(lowData) with priority: high
```

`HighPriorityModule` will wait for `LowPriorityModule` because it needs `lowData`. This is not a bug.

True priority inversion (scheduler artifact) is prevented by:
1. Layer-based execution (all Layer 1 runs before Layer 2)
2. Starvation prevention (aging boosts low-priority tasks)

---

## Component Boundaries

| Component | Parallelization Responsibility |
|-----------|--------------------------------|
| `lang-compiler` | Build DAG structure, compute topological order |
| `runtime` | Assign layers, execute layers in parallel |
| `runtime` | `GlobalScheduler` for bounded concurrency |
| `runtime` | `parTraverse` for unbounded concurrency |

**Never:**
- Assign layers in the compiler (layers are a runtime concern)
- Put scheduling logic in modules (modules are pure business logic)
- Modify execution order without going through the scheduler
- Skip the data table for inter-module communication

---

## Scheduling Invariants

### Dependency Ordering

```
For any module M with inputs [D1, D2, ...]:
  execute(M) happens-after complete(D1) AND complete(D2) AND ...
```

This invariant is enforced by the Deferred-based data table. Modules await their inputs; they cannot proceed until inputs are complete.

### Layer Ordering

```
For Layer N and Layer N+1:
  all(complete(module) for module in Layer N) happens-before any(execute(module) for module in Layer N+1)
```

This is enforced by `parTraverse(layerN)` completing before `parTraverse(layerN+1)` starts.

### Priority Ordering (Bounded Scheduler Only)

```
For tasks A (priority 80) and B (priority 20) submitted simultaneously:
  start(A) happens-before start(B) if slots are limited
```

This is enforced by the `TreeSet`-based priority queue in `GlobalScheduler`.

### Starvation Prevention

```
For any task T with priority P:
  after 30 seconds of waiting: effectivePriority(T) >= P + 60
```

This ensures that even `priority: 0` tasks eventually run.

---

## What Is Out of Scope

Do not add:

- **Work stealing.** (Start tasks early if dependencies are met) - Adds complexity, makes debugging harder.
- **Affinity hints.** (Run this module on the same thread as that one) - Requires RFC.
- **Priority inheritance.** (Boost dependencies of high-priority tasks) - Requires RFC.
- **Per-layer concurrency limits.** (Layer 2 can only use 4 threads) - Use bounded scheduler instead.
- **Execution ordering hints.** (Run A before B, even if independent) - Creates hidden dependencies.

These are potential future features, not current scope.

---

## Testing Requirements

When modifying parallelization:

1. **Unit tests** for layer assignment in compiler
2. **Unit tests** for parallel execution correctness in runtime
3. **Unit tests** for priority queue ordering in scheduler
4. **Unit tests** for starvation prevention (aging mechanism)
5. **Integration tests** for end-to-end DAG execution with parallelism
6. **Benchmark tests** for scheduler overhead (should be < 1ms per task)

All parallelization tests verify that results are independent of execution timing.

---

## Debugging Parallelization Issues

### Symptom: Module reads incomplete data

**Cause:** Data table bypass or incorrect deferred completion.

**Check:**
1. All inter-module data flows through `runtime.getTableData`
2. Inputs are awaited before module logic runs
3. Outputs are set via `runtime.setTableData`

### Symptom: Modules execute out of order

**Cause:** Layer assignment error or parallel execution bug.

**Check:**
1. Layer = 1 + max(input layers) is correctly computed
2. `parTraverse` is awaited before next layer starts
3. No `start` without `join` for module fibers

### Symptom: Low-priority tasks never run

**Cause:** Starvation (aging not working).

**Check:**
1. Aging fiber is running (not cancelled)
2. Effective priority increases over time
3. Queue is re-sorted after aging

### Symptom: Priority is ignored

**Cause:** Unbounded scheduler (priority only matters when bounded).

**Check:**
1. `CONSTELLATION_SCHEDULER_ENABLED=true`
2. `maxConcurrency` is less than total concurrent tasks
3. Priority values are in 0-100 range
