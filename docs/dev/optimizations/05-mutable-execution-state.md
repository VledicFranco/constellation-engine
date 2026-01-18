# Optimization 05: Mutable Execution State

**Priority:** 5 (Medium Impact)
**Expected Gain:** 1-5ms, reduced GC pressure
**Complexity:** Low
**Status:** Not Implemented

---

## Problem Statement

The runtime maintains execution state using immutable data structures:

```scala
// Runtime.scala:24-25
case class State(
  moduleStatus: Map[UUID, Eval[Module.Status]],
  data: Map[UUID, Eval[CValue]]
)

// State updates create new Map instances
def update(state: State, id: UUID, status: Module.Status): State = {
  state.copy(moduleStatus = state.moduleStatus + (id -> Eval.now(status)))
}
```

Each state update allocates a new `Map` instance. For a 20-module DAG with multiple status transitions per module, this creates 60+ intermediate `Map` objects per execution.

### Impact

| DAG Size | Status Updates | Map Allocations | GC Impact |
|----------|----------------|-----------------|-----------|
| 5 modules | ~15 | ~30 Maps | Low |
| 20 modules | ~60 | ~120 Maps | Medium |
| 100 modules | ~300 | ~600 Maps | High |

---

## Proposed Solution

Use mutable collections during execution, snapshot to immutable at the end if needed.

### Implementation

#### Step 1: Mutable State Container

```scala
// New: MutableRuntimeState.scala

import scala.collection.mutable
import java.util.concurrent.ConcurrentHashMap

class MutableRuntimeState(estimatedSize: Int = 32) {

  // Module status - updated frequently during execution
  private val moduleStatus: ConcurrentHashMap[UUID, Module.Status] =
    new ConcurrentHashMap[UUID, Module.Status](estimatedSize)

  // Data values - written once per node
  private val dataValues: ConcurrentHashMap[UUID, CValue] =
    new ConcurrentHashMap[UUID, CValue](estimatedSize)

  // Latency tracking
  private val moduleLatencies: ConcurrentHashMap[UUID, Long] =
    new ConcurrentHashMap[UUID, Long](estimatedSize)

  // Status updates
  def setModuleStatus(id: UUID, status: Module.Status): Unit = {
    moduleStatus.put(id, status)
  }

  def getModuleStatus(id: UUID): Option[Module.Status] = {
    Option(moduleStatus.get(id))
  }

  // Data updates
  def setDataValue(id: UUID, value: CValue): Unit = {
    dataValues.put(id, value)
  }

  def getDataValue(id: UUID): Option[CValue] = {
    Option(dataValues.get(id))
  }

  // Latency tracking
  def recordLatency(id: UUID, nanos: Long): Unit = {
    moduleLatencies.put(id, nanos)
  }

  // Snapshot for final result (if immutable view needed)
  def snapshot: RuntimeState.Snapshot = RuntimeState.Snapshot(
    moduleStatus = Map.from(moduleStatus.asScala),
    dataValues = Map.from(dataValues.asScala),
    latencies = Map.from(moduleLatencies.asScala)
  )

  // Reset for reuse (see pooling optimization)
  def clear(): Unit = {
    moduleStatus.clear()
    dataValues.clear()
    moduleLatencies.clear()
  }
}

object RuntimeState {
  case class Snapshot(
    moduleStatus: Map[UUID, Module.Status],
    dataValues: Map[UUID, CValue],
    latencies: Map[UUID, Long]
  )
}
```

#### Step 2: Update Runtime to Use Mutable State

```scala
// Runtime.scala modifications

class Runtime {

  def run(
    dagSpec: DagSpec,
    inputs: Map[String, CValue],
    registry: ModuleRegistry
  ): IO[ExecutionResult] = {

    // Create mutable state for this execution
    val state = new MutableRuntimeState(dagSpec.moduleNodes.size)

    for {
      dataTable <- initDataTable(dagSpec)
      modules   <- initModules(dagSpec, registry)

      // Complete top-level inputs
      _ <- completeInputs(dagSpec, inputs, dataTable, state)

      // Execute modules in parallel (they update state directly)
      _ <- modules.parTraverse(_.run(dataTable, state))

      // Collect results
      result <- collectResults(dagSpec, state)
    } yield result
  }
}
```

#### Step 3: Update Module Execution

```scala
// Module execution with mutable state

trait Runnable {
  def run(dataTable: MutableDataTable, state: MutableRuntimeState): IO[Unit] = {
    for {
      // Update status: starting
      _ <- IO.delay(state.setModuleStatus(id, Module.Status.Running))

      // Execute with timing
      startTime <- IO.monotonic
      result    <- executeModule(dataTable)
      endTime   <- IO.monotonic

      // Update status: completed
      latency = (endTime - startTime).toNanos
      _ <- IO.delay {
        state.recordLatency(id, latency)
        state.setModuleStatus(id, Module.Status.Completed(latency))
      }

      // Record output values
      _ <- recordOutputs(result, state)
    } yield ()
  }
}
```

---

## Thread Safety Analysis

### ConcurrentHashMap Guarantees

- **Atomic puts:** Single `put` operations are atomic
- **Visibility:** Changes are visible to other threads after `put` completes
- **No blocking:** Lock-free reads, segmented writes

### Our Usage Pattern

| Operation | Frequency | Contention Risk |
|-----------|-----------|-----------------|
| `setModuleStatus` | 2-3 per module | Low (different keys) |
| `getModuleStatus` | Rare during execution | None |
| `setDataValue` | 1 per data node | Low (different keys) |
| `getDataValue` | N per downstream module | None (read-only) |

**Conclusion:** ConcurrentHashMap is appropriate; contention is minimal because modules operate on different keys.

---

## Alternative: Thread-Local State

For even better performance, use thread-local mutable state:

```scala
class ThreadLocalRuntimeState {
  // Each fiber gets its own mutable maps
  private val localStatus = ThreadLocal.withInitial(() =>
    mutable.HashMap.empty[UUID, Module.Status]
  )

  private val localData = ThreadLocal.withInitial(() =>
    mutable.HashMap.empty[UUID, CValue]
  )

  def setModuleStatus(id: UUID, status: Module.Status): Unit = {
    localStatus.get().put(id, status)
  }

  // Merge all thread-local states at the end
  def merge(): RuntimeState.Snapshot = {
    // Collect from all threads...
  }
}
```

**Tradeoff:** More complex merging, but zero contention during execution.

---

## Benchmarks

### Test Scenario

```scala
// 50-module DAG, 1000 executions
val dagSpec = compile(largeProgram)

// Immutable state
val immutable = benchmark {
  (1 to 1000).foreach(_ => runtimeImmutable.run(dagSpec, inputs))
}

// Mutable state
val mutable = benchmark {
  (1 to 1000).foreach(_ => runtimeMutable.run(dagSpec, inputs))
}
```

### Expected Results

| Metric | Immutable State | Mutable State | Improvement |
|--------|-----------------|---------------|-------------|
| Allocations/exec | ~300 Maps | ~3 CHMs | 99% reduction |
| GC pauses | Frequent minor | Rare | Significant |
| Latency (p50) | 12ms | 10ms | 17% |
| Latency (p99) | 25ms | 12ms | 52% |

The p99 improvement is most significant because GC pauses cause tail latency.

---

## Memory Layout

### Before (Immutable)

```
Execution 1: [Map1] [Map2] [Map3] ... [Map300]
Execution 2: [Map1] [Map2] [Map3] ... [Map300]
             ↑ All become garbage after execution
```

### After (Mutable)

```
Execution 1: [ConcurrentHashMap] ← reused or cleared
Execution 2: [ConcurrentHashMap] ← same instance
```

---

## Integration with Pooling

Combine with [Module Initialization Pooling](./02-module-initialization-pooling.md):

```scala
class RuntimeStatePool(size: Int = 50) {
  private val pool = new ArrayBlockingQueue[MutableRuntimeState](size)

  // Pre-warm pool
  def initialize(): Unit = {
    (1 to size).foreach(_ => pool.offer(new MutableRuntimeState()))
  }

  def acquire(): MutableRuntimeState = {
    Option(pool.poll()).getOrElse(new MutableRuntimeState())
  }

  def release(state: MutableRuntimeState): Unit = {
    state.clear()
    pool.offer(state)  // Returns false if full, that's OK
  }
}
```

---

## Configuration

```hocon
constellation.runtime {
  state {
    # Use mutable state during execution
    mutable = true

    # Initial capacity for hash maps
    initial-capacity = 32

    # Pool mutable state objects
    pooling {
      enabled = true
      size = 50
    }
  }
}
```

---

## Implementation Checklist

- [ ] Create `MutableRuntimeState` class
- [ ] Update `Runtime` to use mutable state
- [ ] Update `Module.Runnable` to update state directly
- [ ] Add snapshot capability for final results
- [ ] Optional: Integrate with state pooling
- [ ] Add metrics for state operations
- [ ] Benchmark with various DAG sizes
- [ ] Profile GC behavior before/after

---

## Files to Modify

| File | Changes |
|------|---------|
| New: `modules/runtime/.../MutableRuntimeState.scala` | Mutable state container |
| `modules/runtime/.../Runtime.scala` | Use mutable state |
| `modules/runtime/.../Module.scala` | Update state interface |

---

## Related Optimizations

- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Pool state objects together with other runtime objects
- [Inline Synthetic Modules](./04-inline-synthetic-modules.md) - Fewer modules = fewer state updates
