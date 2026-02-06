# Parallelization Philosophy

> Why automatic parallelization matters and why it belongs in the orchestration layer.

---

## The Problem

Traditional data pipelines serialize execution, even when operations are independent:

```scala
// Without Constellation: Sequential by default
def buildProfile(userId: String): IO[Profile] = {
  for {
    user        <- getUser(userId)         // 100ms
    orders      <- getOrders(userId)       // 150ms
    preferences <- getPreferences(userId)  // 80ms
    profile     <- buildProfile(user, orders, preferences)
  } yield profile
}
// Total: 330ms + build time
```

To parallelize, developers must manually restructure:

```scala
// Manual parallelization: Complex and error-prone
def buildProfile(userId: String): IO[Profile] = {
  (
    getUser(userId),
    getOrders(userId),
    getPreferences(userId)
  ).parMapN { (user, orders, preferences) =>
    buildProfileSync(user, orders, preferences)
  }
}
// Total: max(100, 150, 80)ms + build time = 150ms
```

The problems:

1. **Manual analysis.** Developer must identify which operations can parallelize.
2. **Structural coupling.** Changing dependencies requires restructuring code.
3. **Hidden parallelism.** Reading the code doesn't reveal execution strategy.
4. **Inconsistent application.** Some call sites parallelize, others don't.

---

## The Bet

Parallelization is a **property of the data flow graph**, not of the code structure. If two operations have no data dependency, they can execute concurrently. The orchestrator knows the dependency graph; it should parallelize automatically.

By making parallelization automatic:

```constellation
user = GetUser(userId)
orders = GetOrders(userId)
preferences = GetPreferences(userId)

profile = BuildProfile(user, orders, preferences)

out profile
```

Constellation:

1. **Analyzes dependencies.** `GetUser`, `GetOrders`, `GetPreferences` have no cross-dependencies.
2. **Groups into layers.** Layer 1 contains all three independent calls.
3. **Executes layers in parallel.** All Layer 1 modules run concurrently.
4. **Respects ordering.** `BuildProfile` waits for Layer 1 to complete.

---

## The DAG-Based Execution Model

### Why DAGs?

A Directed Acyclic Graph (DAG) naturally represents data dependencies:

- **Nodes** are computations (modules, transforms)
- **Edges** are data dependencies (output -> input)
- **No cycles** means execution order is well-defined

The DAG structure is **implicit in the code** but **explicit to the runtime**:

```
┌────────────────────────────────────────────┐
│  User writes:                              │
│    user = GetUser(id)                      │
│    orders = GetOrders(id)                  │
│    profile = Build(user, orders)           │
└────────────────────────────────────────────┘
                    │
                    ▼ Compile
┌────────────────────────────────────────────┐
│  Compiler sees:                            │
│                                            │
│           [id]                             │
│          /    \                            │
│    [GetUser]  [GetOrders]                  │
│          \    /                            │
│         [Build]                            │
│             │                              │
│         [profile]                          │
└────────────────────────────────────────────┘
                    │
                    ▼ Analyze
┌────────────────────────────────────────────┐
│  Runtime sees:                             │
│                                            │
│    Layer 0: [id]                           │
│    Layer 1: [GetUser, GetOrders]  ← parallel│
│    Layer 2: [Build]                        │
│    Layer 3: [profile]                      │
└────────────────────────────────────────────┘
```

### Layer-Based Scheduling

Modules are grouped into **layers** based on their maximum dependency depth:

```
Layer(node) = 1 + max(Layer(dependency) for dependency in node.inputs)
Layer(input) = 0
```

Within a layer, all nodes are independent and execute in parallel. Between layers, execution is sequential (Layer N completes before Layer N+1 starts).

This is a **conservative but correct** strategy:

- **Conservative:** Some parallelism may be left on the table (a Layer 2 node could start as soon as its inputs are ready, even if other Layer 1 nodes are still running)
- **Correct:** No node ever reads an incomplete value

The trade-off favors simplicity and predictability over maximum throughput.

---

## Design Decisions

### 1. Automatic Over Manual

Parallelization is automatic; no annotations required:

```constellation
# YES: Parallelism is inferred
user = GetUser(id)
orders = GetOrders(id)

# NO: Manual parallel directive (rejected approach)
parallel {
  user = GetUser(id)
  orders = GetOrders(id)
}
```

**Why:** The compiler has complete knowledge of dependencies. Manual annotations are error-prone (forgetting to parallelize) or incorrect (parallelizing dependent operations).

### 2. Layer Execution Over Work Stealing

Execution is layer-by-layer, not opportunistic work stealing:

```
Layer 1: [A, B, C] ────────► all complete
Layer 2: [D, E]    ────────► all complete
Layer 3: [F]       ────────► done
```

Not:

```
A completes → immediately start D (if D only depends on A)
```

**Why:** Layer execution is predictable and easier to reason about. Work stealing can improve throughput but makes debugging harder and can cause priority inversions.

### 3. Bounded Concurrency is Opt-In

By default, all independent modules run simultaneously:

```scala
// Default: parTraverse all modules in a layer
layer.modules.parTraverse(_.run)
```

When resources are constrained, users can enable bounded scheduling:

```bash
CONSTELLATION_SCHEDULER_ENABLED=true
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8
```

**Why:** Unbounded is correct for most workloads and has minimal overhead. Bounding adds queue management complexity that isn't always needed.

### 4. Priority is a Hint, Not a Guarantee

Priority controls relative ordering when contention exists:

```constellation
fast = GetFastData(id) with priority: high
slow = GetSlowData(id) with priority: low
```

**Why:** Priority is useful for latency-sensitive paths, but the system doesn't guarantee strict ordering. A low-priority task might run before a high-priority task if they're in different layers.

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Automatic parallelization | No manual analysis | Can't express "run serially for debugging" |
| Layer-based execution | Predictable, debuggable | Some parallelism left unused |
| Unbounded default | Simple, fast | Risk of resource exhaustion |
| Priority hints | Control latency-sensitive paths | Not a strict guarantee |

### What We Gave Up

- **Fine-grained control.** Can't say "run A before B" without a data dependency.
- **Maximum parallelism.** Layer boundaries may delay work that could start sooner.
- **Fairness guarantees.** Priority is best-effort, not strict.

These limitations are intentional. The 90% case is covered by automatic parallelization; the 10% case uses priority hints or restructured data flow.

---

## When Not to Parallelize

Automatic parallelization is not always desirable:

1. **Rate-limited APIs.** Too many concurrent calls may trigger rate limits.
   - Solution: Use `throttle` option or bounded scheduler.

2. **Shared resources.** Concurrent access to a single database may cause contention.
   - Solution: Use bounded scheduler or connection pooling.

3. **Debugging.** Parallel execution makes logs interleaved.
   - Solution: Enable single-threaded mode for debugging.

4. **Deterministic testing.** Parallel execution may expose race conditions.
   - Solution: Use fixed-seed test mode.

---

## Influences

- **Apache Spark:** DAG-based execution planning
- **Dask:** Lazy evaluation with parallel execution
- **Make:** Dependency-based task scheduling
- **Temporal/Airflow:** Workflow DAGs with parallel branches

The key insight from these systems: dependencies define the execution order, and independence enables parallelism. Constellation applies this to module-level orchestration.
