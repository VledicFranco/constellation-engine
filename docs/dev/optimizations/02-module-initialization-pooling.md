# Optimization 02: Module Initialization Pooling

**Priority:** 2 (High Impact)
**Expected Gain:** 5-20ms per request
**Complexity:** Medium
**Status:** Not Implemented

---

## Problem Statement

Every DAG execution creates fresh runtime objects:

1. **Deferred instances** - One `Deferred[IO, Any]` per data node
2. **Module.Runnable instances** - One per module in the DAG
3. **Runtime.State** - Fresh state container per execution

This allocation overhead is significant for high-throughput ML pipelines executing many small DAGs.

### Current Flow

```scala
// Runtime.scala:130-141
private def initModules(
  dagSpec: DagSpec,
  registry: ModuleRegistry
): IO[List[Module.Runnable]] = {
  dagSpec.moduleNodes.toList.traverse { spec =>
    registry.getModule(spec.name).flatMap {
      case Some(module) =>
        module.init(spec)  // Creates new Runnable each time
      case None =>
        IO.raiseError(...)
    }
  }
}

// Runtime.scala:156-170
private def initDataTable(dagSpec: DagSpec): IO[MutableDataTable] = {
  dagSpec.dataNodes.toList
    .filter(hasModuleConnections)
    .traverse { node =>
      Deferred[IO, Any].map(node.id -> _)  // New Deferred each time
    }
    .map(_.toMap)
}
```

---

## Proposed Solution

Implement object pooling for frequently allocated runtime objects.

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Object Pool Manager                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌─────────────────┐    ┌─────────────────────────────┐ │
│  │  Deferred Pool  │    │  Module.Runnable Templates  │ │
│  │  ───────────────│    │  ─────────────────────────  │ │
│  │  • Pre-allocated│    │  • Cached per module type   │ │
│  │  • Reset on     │    │  • Clone for each execution │ │
│  │    return       │    │                             │ │
│  └─────────────────┘    └─────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐│
│  │              Runtime.State Pool                      ││
│  │  ───────────────────────────────────────────────────││
│  │  • Pre-allocated state containers                   ││
│  │  • Clear and reuse between executions               ││
│  └─────────────────────────────────────────────────────┘│
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Implementation

#### Step 1: Deferred Pool

```scala
// New file: modules/runtime/.../pool/DeferredPool.scala

import cats.effect.{IO, Deferred, Ref}
import scala.collection.mutable

class DeferredPool(initialSize: Int = 100, maxSize: Int = 10000) {

  // Pool of pre-allocated, uncompleted deferreds
  private val pool: mutable.Queue[Deferred[IO, Any]] = mutable.Queue.empty
  private val poolLock = new Object()

  // Pre-warm the pool
  def initialize: IO[Unit] = {
    IO.parTraverseN(Runtime.getRuntime.availableProcessors())(
      (1 to initialSize).toList
    )(_ => Deferred[IO, Any]).flatMap { deferreds =>
      IO.delay {
        poolLock.synchronized {
          deferreds.foreach(pool.enqueue(_))
        }
      }
    }
  }

  // Acquire a deferred from pool or create new
  def acquire: IO[Deferred[IO, Any]] = IO.defer {
    poolLock.synchronized {
      if (pool.nonEmpty) {
        IO.pure(pool.dequeue())
      } else {
        Deferred[IO, Any]
      }
    }
  }

  // Acquire multiple deferreds efficiently
  def acquireN(n: Int): IO[List[Deferred[IO, Any]]] = {
    IO.defer {
      val fromPool = poolLock.synchronized {
        val available = math.min(n, pool.size)
        (1 to available).map(_ => pool.dequeue()).toList
      }

      val remaining = n - fromPool.size
      if (remaining > 0) {
        (1 to remaining).toList.traverse(_ => Deferred[IO, Any]).map(fromPool ++ _)
      } else {
        IO.pure(fromPool)
      }
    }
  }

  // Return deferreds to pool (only uncompleted ones can be reused)
  // Note: Completed deferreds cannot be reset, so we create fresh ones
  def release(count: Int): IO[Unit] = IO.defer {
    if (pool.size < maxSize) {
      (1 to math.min(count, maxSize - pool.size)).toList
        .traverse(_ => Deferred[IO, Any])
        .flatMap { fresh =>
          IO.delay {
            poolLock.synchronized {
              fresh.foreach(pool.enqueue(_))
            }
          }
        }
    } else IO.unit
  }
}
```

#### Step 2: Module Template Cache

```scala
// Modification to ModuleRegistry

trait ModuleRegistry {
  def getModule(name: String): IO[Option[Module.Uninitialized]]

  // New: Get a pre-configured template for fast cloning
  def getModuleTemplate(name: String): IO[Option[ModuleTemplate]]
}

case class ModuleTemplate(
  module: Module.Uninitialized,
  // Pre-computed initialization data
  inputSchema: Map[String, CType],
  outputSchema: Map[String, CType]
) {
  def instantiate(spec: ModuleNodeSpec): IO[Module.Runnable] = {
    // Fast path: use pre-computed schemas
    module.initFast(spec, inputSchema, outputSchema)
  }
}

// In Module.Uninitialized
trait Uninitialized {
  def init(spec: ModuleNodeSpec): IO[Module.Runnable]

  // New: Fast initialization with pre-computed data
  def initFast(
    spec: ModuleNodeSpec,
    inputSchema: Map[String, CType],
    outputSchema: Map[String, CType]
  ): IO[Module.Runnable] = {
    // Skip schema computation, go directly to Runnable creation
    IO.pure(new Module.Runnable {
      // ... implementation using pre-computed schemas
    })
  }
}
```

#### Step 3: Pooled Runtime State

```scala
// New file: modules/runtime/.../pool/StatePool.scala

import scala.collection.mutable

class RuntimeStatePool(poolSize: Int = 50) {

  case class PooledState(
    moduleStatus: mutable.HashMap[UUID, Module.Status],
    dataValues: mutable.HashMap[UUID, CValue],
    var inUse: Boolean = false
  ) {
    def reset(): Unit = {
      moduleStatus.clear()
      dataValues.clear()
      inUse = false
    }
  }

  private val pool: Array[PooledState] = Array.fill(poolSize) {
    PooledState(
      mutable.HashMap.empty,
      mutable.HashMap.empty
    )
  }

  def acquire: Option[PooledState] = {
    pool.synchronized {
      pool.find(!_.inUse).map { state =>
        state.inUse = true
        state
      }
    }
  }

  def release(state: PooledState): Unit = {
    pool.synchronized {
      state.reset()
    }
  }
}
```

#### Step 4: Integrate with Runtime

```scala
// Runtime.scala modifications

class Runtime(
  deferredPool: DeferredPool,
  statePool: RuntimeStatePool
) {

  private def initDataTable(dagSpec: DagSpec): IO[MutableDataTable] = {
    val nodeCount = dagSpec.dataNodes.count(hasModuleConnections)

    deferredPool.acquireN(nodeCount).map { deferreds =>
      dagSpec.dataNodes
        .filter(hasModuleConnections)
        .zip(deferreds)
        .map { case (node, deferred) => node.id -> deferred }
        .toMap
    }
  }

  def run(dagSpec: DagSpec, inputs: Map[String, CValue]): IO[ExecutionResult] = {
    // Try to get pooled state, fall back to fresh allocation
    val state = statePool.acquire.getOrElse(
      PooledState(mutable.HashMap.empty, mutable.HashMap.empty)
    )

    val execution = for {
      dataTable <- initDataTable(dagSpec)
      modules   <- initModules(dagSpec)
      result    <- executeWithState(state, dataTable, modules, inputs)
    } yield result

    execution.guarantee(IO.delay(statePool.release(state)))
  }
}
```

---

## Memory Layout Optimization

### Current (Scattered Allocations)

```
Heap:
  [Deferred1] ... [other objects] ... [Deferred2] ... [Module1] ...

Cache misses when iterating through modules/deferreds
```

### Optimized (Contiguous Pools)

```
Heap:
  [Deferred Pool: D1, D2, D3, D4, D5, ...]  // Contiguous
  [State Pool: S1, S2, S3, ...]              // Contiguous

Better cache locality, fewer allocations
```

---

## Benchmarking

### Test Scenario

```scala
// 10-node DAG, 1000 executions
val dagSpec = compile("...")

// Without pooling
val baseline = benchmark {
  (1 to 1000).foreach(_ => runtime.run(dagSpec, inputs))
}

// With pooling
val optimized = benchmark {
  (1 to 1000).foreach(_ => pooledRuntime.run(dagSpec, inputs))
}
```

### Expected Results

| Metric | Without Pooling | With Pooling | Improvement |
|--------|-----------------|--------------|-------------|
| Allocations/req | ~50 objects | ~5 objects | 90% reduction |
| GC pauses | Frequent | Rare | Significant |
| p99 latency | Variable | Stable | More predictable |
| Throughput | Baseline | +15-30% | Notable |

---

## Thread Safety Considerations

### Deferred Pool

- Use `synchronized` blocks for pool access (contention is brief)
- Alternative: Use `java.util.concurrent.ConcurrentLinkedQueue` for lock-free access

### State Pool

- Each pooled state is exclusively owned during execution
- Reset on release ensures no data leaks between requests

### Module Templates

- Templates are immutable after creation
- Safe for concurrent access without synchronization

---

## Configuration

```hocon
# application.conf

constellation.pool {
  deferred {
    initial-size = 100
    max-size = 10000
  }

  state {
    pool-size = 50
  }

  module-template {
    cache-enabled = true
  }
}
```

---

## Implementation Checklist

- [ ] Implement `DeferredPool` class
- [ ] Implement `RuntimeStatePool` class
- [ ] Add `ModuleTemplate` caching to registry
- [ ] Modify `Runtime` to use pools
- [ ] Add pool metrics (utilization, wait time)
- [ ] Add configuration options
- [ ] Write stress tests for pool behavior
- [ ] Benchmark with realistic workloads

---

## Files to Modify

| File | Changes |
|------|---------|
| New: `modules/runtime/.../pool/DeferredPool.scala` | Deferred pooling |
| New: `modules/runtime/.../pool/RuntimeStatePool.scala` | State pooling |
| `modules/runtime/.../Runtime.scala` | Integrate pools |
| `modules/runtime/.../ModuleRegistry.scala` | Add template caching |
| `modules/runtime/.../impl/ModuleRegistryImpl.scala` | Implement templates |

---

## Related Optimizations

- [Compilation Caching](./01-compilation-caching.md) - Reduces work before pooling kicks in
- [Mutable Execution State](./05-mutable-execution-state.md) - Complements pooling strategy
