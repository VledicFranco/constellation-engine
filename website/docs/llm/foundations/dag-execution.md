---
title: "DAG Execution"
sidebar_position: 3
description: "How Constellation Engine executes pipelines in parallel using directed acyclic graphs"
---

# DAG Execution

**Goal:** Understand how Constellation Engine executes your pipelines through parallel DAG traversal.

## Overview

When you run a pipeline, Constellation Engine compiles it into a **directed acyclic graph (DAG)** and executes it using **layer-based parallel execution**. This means:

- Independent operations run in parallel automatically
- Dependencies are resolved through topological sorting
- Execution happens on lightweight Cats Effect fibers
- No manual thread management required

This document explains how DAG execution works under the hood.

## DAG Structure

### What is a DAG?

A DAG is a graph with:
- **Nodes:** Processing steps (modules) and data values
- **Edges:** Dependencies between nodes
- **Direction:** Data flows from inputs to outputs
- **Acyclic:** No circular dependencies allowed

### Two Types of Nodes

#### 1. Module Nodes
Processing units that consume data and produce results.

```scala
case class ModuleNodeSpec(
  metadata: ComponentMetadata,
  consumes: Map[String, CType],  // Input parameter types
  produces: Map[String, CType],  // Output field types
  config: ModuleConfig
)
```

**Example:** A module that converts text to uppercase
- Consumes: `Map("text" -> CType.CString)`
- Produces: `Map("result" -> CType.CString)`

#### 2. Data Nodes
Values that flow between modules or come from external inputs.

```scala
case class DataNodeSpec(
  name: String,
  nicknames: Map[UUID, String],      // Parameter names for consuming modules
  cType: CType,                      // Type of data
  inlineTransform: Option[InlineTransform],  // Optional computation
  transformInputs: Map[String, UUID]         // Inputs for inline transform
)
```

**Types of data nodes:**
- **User inputs:** External data entering the pipeline
- **Module outputs:** Results produced by modules
- **Inline transforms:** Computed values (merge, project, conditional)

### Edges: Connecting the Graph

**inEdges:** Data node → Module node (module inputs)
```scala
inEdges: Set[(UUID, UUID)]  // (dataNodeId, moduleNodeId)
```

**outEdges:** Module node → Data node (module outputs)
```scala
outEdges: Set[(UUID, UUID)]  // (moduleNodeId, dataNodeId)
```

### Complete DAG Specification

```scala
case class DagSpec(
  metadata: ComponentMetadata,
  modules: Map[UUID, ModuleNodeSpec],
  data: Map[UUID, DataNodeSpec],
  inEdges: Set[(UUID, UUID)],
  outEdges: Set[(UUID, UUID)],
  declaredOutputs: List[String],
  outputBindings: Map[String, UUID]
)
```

## From Pipeline to DAG

### Example Pipeline

```constellation
in text: String

# Layer 0 - runs first
cleaned = Trim(text)

# Layer 1 - waits for cleaned
uppercase = Uppercase(cleaned)
lowercase = Lowercase(cleaned)

# Layer 2 - waits for both uppercase and lowercase
combined = Merge(uppercase, lowercase)

out combined
```

### Compiled DAG Structure

**Nodes:**
```
Data nodes:
- d1: text (user input)
- d2: cleaned (Trim output)
- d3: uppercase (Uppercase output)
- d4: lowercase (Lowercase output)
- d5: combined (Merge output)

Module nodes:
- m1: Trim
- m2: Uppercase
- m3: Lowercase
- m4: Merge (synthetic)
```

**Edges:**
```
inEdges:
- (d1, m1)  // text → Trim
- (d2, m2)  // cleaned → Uppercase
- (d2, m3)  // cleaned → Lowercase
- (d3, m4)  // uppercase → Merge
- (d4, m4)  // lowercase → Merge

outEdges:
- (m1, d2)  // Trim → cleaned
- (m2, d3)  // Uppercase → uppercase
- (m3, d4)  // Lowercase → lowercase
- (m4, d5)  // Merge → combined
```

### Visual Representation

```
┌──────────┐
│ d1:text  │ (user input)
└────┬─────┘
     │
┌────▼─────┐
│ m1:Trim  │ (Layer 0)
└────┬─────┘
     │
┌────▼─────────┐
│ d2:cleaned   │
└──┬───────┬───┘
   │       │
   │   ┌───▼────────────┐
   │   │ m3:Lowercase   │ (Layer 1 - parallel)
   │   └───┬────────────┘
   │       │
┌──▼────────────┐   ┌───▼─────────┐
│ m2:Uppercase  │   │ d4:lowercase│
└──┬────────────┘   └───┬─────────┘
   │                    │
┌──▼─────────┐          │
│ d3:uppercase│          │
└──┬──────────┘          │
   │                    │
   └──────┬─────────────┘
          │
     ┌────▼────────┐
     │ m4:Merge    │ (Layer 2)
     └────┬────────┘
          │
     ┌────▼─────────┐
     │ d5:combined  │ (output)
     └──────────────┘
```

## Topological Sorting

### Why Topological Sort?

To execute modules in the correct order, we need to ensure:
1. A module runs only after all its inputs are ready
2. Modules with no dependencies can run immediately
3. The order respects all data dependencies

### Algorithm

The topological sort happens in the **IR (Intermediate Representation)** layer:

```scala
case class IRPipeline(
  nodes: Map[UUID, IRNode],
  inputs: List[UUID],
  declaredOutputs: List[String],
  variableBindings: Map[String, UUID],
  topologicalOrder: List[UUID]  // ← Sorted execution order
)
```

**Implementation (simplified):**
```scala
def topologicalSort(nodes: Map[UUID, IRNode]): List[UUID] = {
  var visited = Set.empty[UUID]
  var result = List.empty[UUID]

  def visit(nodeId: UUID): Unit = {
    if (!visited.contains(nodeId)) {
      visited += nodeId
      // Visit all dependencies first
      val deps = getDependencies(nodeId)
      deps.foreach(visit)
      // Then add this node
      result = result :+ nodeId
    }
  }

  nodes.keys.foreach(visit)
  result
}
```

### Topological Order for Example

For our example pipeline:
```scala
topologicalOrder = List(
  inputNodeId,      // text input
  trimNodeId,       // Trim module
  uppercaseNodeId,  // Uppercase module
  lowercaseNodeId,  // Lowercase module
  mergeNodeId       // Merge module
)
```

**Key insight:** Uppercase and Lowercase appear sequentially in the list, but they run **in parallel** during execution because they don't depend on each other.

## Layer-Based Parallel Execution

### What are Execution Layers?

Execution layers group modules by their dependency depth:

```
Layer 0: Modules with no module dependencies (only user inputs)
Layer 1: Modules depending only on Layer 0
Layer 2: Modules depending on Layer 0 or Layer 1
...
```

### How Layers Enable Parallelism

**Within a layer:** All modules run in parallel using `parTraverse`

**Between layers:** Execution proceeds sequentially (Layer N+1 waits for Layer N)

### Layer Computation (Conceptual)

While layers aren't explicitly computed in the current implementation, the parallel execution happens through `parTraverse`:

```scala
// From Runtime.scala
runnable.parTraverse { module =>
  val priority = modulePriorities.getOrElse(module.id, DefaultPriority)
  scheduler.submit(priority, module.run(runtime))
}
```

**How it works:**
1. All modules in `runnable` are submitted to the scheduler
2. Each module waits on its input `Deferred` values
3. Modules with ready inputs execute immediately
4. Modules with pending inputs wait (non-blocking)
5. Natural parallelism emerges from data dependencies

### Parallel Execution Example

For our example pipeline:

```
Layer 0: [Trim]
  ↓ (Trim completes, releases "cleaned" data node)
Layer 1: [Uppercase, Lowercase] ← Run in parallel
  ↓ (Both complete, release their data nodes)
Layer 2: [Merge]
```

**Execution timeline:**
```
t=0ms:   Start Trim
t=10ms:  Trim completes
t=10ms:  Start Uppercase (fiber 1) and Lowercase (fiber 2) in parallel
t=25ms:  Uppercase completes (15ms duration)
t=30ms:  Lowercase completes (20ms duration)
t=30ms:  Start Merge (all inputs ready)
t=35ms:  Merge completes
```

**Total time:** 35ms (not 10+15+20+5 = 50ms sequential)

## Dependency Resolution

### Deferred-Based Coordination

Constellation uses `cats.effect.Deferred` for dependency resolution:

```scala
type MutableDataTable = Map[UUID, Deferred[IO, Any]]
```

**How it works:**

1. **Initialization:** Create a `Deferred[IO, Any]` for each data node
2. **Awaiting inputs:** Modules block on `deferred.get` for their inputs
3. **Providing outputs:** Modules complete their output deferreds with `deferred.complete(value)`
4. **Natural ordering:** Fibers suspend until dependencies are ready

### Example: Module Execution

```scala
// Simplified from Runtime.scala
Module.Runnable(
  id = moduleId,
  data = dataTable,  // Map of UUID -> Deferred
  run = runtime => {
    for {
      // 1. Wait for all input data nodes (blocks until ready)
      inputs <- awaitOnInputs(consumesNamespace, runtime)

      // 2. Execute the module logic
      (latency, outputs) <- moduleImplementation(inputs).timed

      // 3. Complete output data nodes (releases waiting modules)
      _ <- provideOnOutputs(producesNamespace, runtime, outputs.data)

      // 4. Update module status
      _ <- runtime.setModuleStatus(moduleId, Status.Fired(latency))
    } yield ()
  }
)
```

### Await on Inputs (Blocking Point)

```scala
inline def awaitOnInputs[T <: Product](
  namespace: Namespace,
  runtime: Runtime
)(using m: Mirror.ProductOf[T]): IO[T] = {
  val names = getFieldNames[T]
  for {
    values <- names.traverse { name =>
      for {
        dataId <- namespace.nameId(name)
        value  <- runtime.getTableData(dataId)  // Blocks here!
      } yield value
    }
    tuple = Tuple.fromArray(values.toArray)
  } yield m.fromTuple(tuple.asInstanceOf[m.MirroredElemTypes])
}
```

**Key:** `runtime.getTableData(dataId)` calls `deferred.get`, which suspends the fiber until the data is ready.

### Provide on Outputs (Release Point)

```scala
inline def provideOnOutputs[T <: Product](
  namespace: Namespace,
  runtime: Runtime,
  outputs: T
)(using m: Mirror.ProductOf[T]): IO[Unit] = {
  val names = getFieldNames[T]
  val values = outputs.productIterator.toList

  names.zip(values).traverse { case (name, value) =>
    for {
      dataId <- namespace.nameId(name)
      _      <- runtime.setTableData(dataId, value)  // Completes deferred!
    } yield ()
  }.void
}
```

**Key:** `runtime.setTableData` calls `deferred.complete(value)`, which releases all waiting fibers.

## Execution Context and State Management

### Runtime State

The runtime maintains two pieces of mutable state:

#### 1. Data Table (Coordination)
```scala
type MutableDataTable = Map[UUID, Deferred[IO, Any]]
```
- Purpose: Coordinate data flow between modules
- Lifecycle: Created at runtime start, released at runtime end
- Thread-safety: Cats Effect `Deferred` is thread-safe

#### 2. Execution State (Observability)
```scala
type MutableState = Ref[IO, State]

case class State(
  processUuid: UUID,
  dag: DagSpec,
  moduleStatus: Map[UUID, Eval[Module.Status]],
  data: Map[UUID, Eval[CValue]],
  latency: Option[FiniteDuration]
)
```
- Purpose: Track execution progress and results
- Lifecycle: Updated as modules execute
- Thread-safety: Cats Effect `Ref` provides atomic updates

### Module Status Tracking

Modules transition through states:

```scala
sealed trait Status
object Status {
  case object Unfired extends Status
  case class Fired(latency: FiniteDuration, context: Option[Map[String, Json]]) extends Status
  case class Timed(latency: FiniteDuration) extends Status
  case class Failed(error: Throwable) extends Status
}
```

**Lifecycle:**
```
Unfired → Fired (success)
        ↘ Timed (timeout)
        ↘ Failed (error)
```

### State Updates During Execution

```scala
// Before execution
_ <- runtime.setModuleStatus(moduleId, Module.Status.Unfired)

// After successful execution
_ <- runtime.setModuleStatus(
  moduleId,
  Module.Status.Fired(latency, producesContext())
)

// On timeout
case _: TimeoutException =>
  runtime.setModuleStatus(
    moduleId,
    Module.Status.Timed(partialSpec.config.inputsTimeout)
  )

// On error
case e =>
  runtime.setModuleStatus(moduleId, Module.Status.Failed(e))
```

## Inline Transforms

### What are Inline Transforms?

Inline transforms are **synthetic computations** that run as data nodes rather than module nodes. They eliminate the overhead of creating full modules for simple operations.

**Examples:**
- Merge: Combine two records
- Project: Select specific fields
- FieldAccess: Extract a single field
- Conditional: if-then-else
- Guard: when expressions
- Literals: Constant values

### How Inline Transforms Execute

Inline transforms run as **separate fibers** in parallel with modules:

```scala
// From Runtime.scala - start inline transform fibers
transformFibers <- startInlineTransformFibers(dag, runtime)

// Execute modules and transforms in parallel
latency <- (
  runnable.parTraverse { module =>
    scheduler.submit(priority, module.run(runtime))
  },
  transformFibers.parTraverse(_.join)
).parMapN((_, _) => ()).timed.map(_._1)
```

### Inline Transform Structure

```scala
case class DataNodeSpec(
  name: String,
  nicknames: Map[UUID, String],
  cType: CType,
  inlineTransform: Option[InlineTransform],     // ← The computation
  transformInputs: Map[String, UUID]            // ← Input data nodes
)
```

### Example: Merge Transform

**Pipeline code:**
```constellation
result = Merge(a, b)
```

**DAG representation:**
```scala
DataNodeSpec(
  name = "result",
  cType = CType.CProduct(Map("field1" -> CString, "field2" -> CInt)),
  inlineTransform = Some(InlineTransform.MergeTransform(leftType, rightType)),
  transformInputs = Map("left" -> aDataId, "right" -> bDataId)
)
```

**Execution:**
```scala
private def computeInlineTransform(
  dataId: UUID,
  spec: DataNodeSpec,
  runtime: Runtime
): IO[Unit] = {
  spec.inlineTransform match {
    case Some(transform) =>
      for {
        // Wait for all input values
        inputValues <- spec.transformInputs.toList.traverse {
          case (inputName, inputDataId) =>
            runtime.getTableData(inputDataId).map(inputName -> _)
        }
        inputMap = inputValues.toMap

        // Apply the transform
        result = transform.apply(inputMap)

        // Complete the output deferred
        _ <- runtime.setTableData(dataId, result)

        // Store result in state
        cValue = anyToCValue(result, spec.cType)
        _ <- runtime.setStateData(dataId, cValue)
      } yield ()
  }
}
```

### Types of Inline Transforms

```scala
sealed trait InlineTransform {
  def apply(inputs: Map[String, Any]): Any
}

object InlineTransform {
  case class MergeTransform(leftType: CType, rightType: CType) extends InlineTransform
  case class ProjectTransform(fields: List[String], sourceType: CType) extends InlineTransform
  case class FieldAccessTransform(field: String, sourceType: CType) extends InlineTransform
  case object ConditionalTransform extends InlineTransform
  case object AndTransform extends InlineTransform
  case object OrTransform extends InlineTransform
  case object NotTransform extends InlineTransform
  case object GuardTransform extends InlineTransform
  case object CoalesceTransform extends InlineTransform
  case class LiteralTransform(value: Any) extends InlineTransform
  case class StringInterpolationTransform(parts: List[String]) extends InlineTransform
  case class FilterTransform(predicate: Any => Boolean) extends InlineTransform
  case class MapTransform(mapper: Any => Any) extends InlineTransform
  // ... more transforms
}
```

## Performance Implications

### Parallelism Benefits

**Sequential execution:**
```
Time = Σ(all module durations)
```

**Parallel execution:**
```
Time = Σ(longest path through DAG)
```

### Example: Fan-out Pattern

```constellation
in input: String

# Fan-out: All run in parallel
a = ProcessA(input)  # 100ms
b = ProcessB(input)  # 150ms
c = ProcessC(input)  # 120ms
d = ProcessD(input)  # 80ms

# Fan-in: Waits for all
result = Combine(a, b, c, d)  # 20ms

out result
```

**Sequential time:** 100 + 150 + 120 + 80 + 20 = 470ms

**Parallel time:** max(100, 150, 120, 80) + 20 = 170ms

**Speedup:** 2.76x

### Fiber Overhead

Cats Effect fibers are extremely lightweight:
- **Creation cost:** ~200 nanoseconds
- **Context switch:** ~1-2 microseconds
- **Memory per fiber:** ~400 bytes

This means parallelism is essentially free - even for short-running modules.

### Scheduler Impact

Constellation supports priority-based scheduling:

```scala
def runWithScheduler(
  dag: DagSpec,
  initData: Map[String, CValue],
  modules: Map[UUID, Module.Uninitialized],
  modulePriorities: Map[UUID, Int],  // ← Priority per module
  scheduler: GlobalScheduler         // ← Bounded or unbounded
): IO[Runtime.State]
```

**Scheduler types:**

1. **Unbounded (default):** All modules run as soon as dependencies are ready
2. **Bounded:** Limits concurrent module execution

**Configuration:**
```bash
CONSTELLATION_SCHEDULER_ENABLED=true
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=16
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=30s
```

### Priority Levels

```scala
modulePriorities = Map(
  criticalModuleId -> 100,  // Critical priority
  highModuleId     -> 80,   // High priority
  normalModuleId   -> 50,   // Normal (default)
  lowModuleId      -> 20    // Low priority
)
```

**Effect:** High-priority modules are scheduled before low-priority ones when the scheduler is bounded.

## Common Execution Patterns

### 1. Linear Chain

```constellation
a = ModuleA(input)
b = ModuleB(a)
c = ModuleC(b)
out c
```

**Execution:** Purely sequential
```
Layer 0: [ModuleA]
Layer 1: [ModuleB]
Layer 2: [ModuleC]
```

**Time:** t_A + t_B + t_C

### 2. Fork-Join (Diamond)

```constellation
a = ModuleA(input)

# Fork
b = ModuleB(a)
c = ModuleC(a)

# Join
result = ModuleD(b, c)
out result
```

**Execution:**
```
Layer 0: [ModuleA]
Layer 1: [ModuleB, ModuleC] ← Parallel
Layer 2: [ModuleD]
```

**Time:** t_A + max(t_B, t_C) + t_D

### 3. Wide Fan-out

```constellation
# All run in parallel
a = ModuleA(input)
b = ModuleB(input)
c = ModuleC(input)
d = ModuleD(input)
e = ModuleE(input)

result = Combine(a, b, c, d, e)
out result
```

**Execution:**
```
Layer 0: [ModuleA, ModuleB, ModuleC, ModuleD, ModuleE] ← All parallel
Layer 1: [Combine]
```

**Time:** max(t_A, t_B, t_C, t_D, t_E) + t_Combine

### 4. Pipeline with Multiple Outputs

```constellation
a = ModuleA(input)
b = ModuleB(a)
c = ModuleC(a)

out b
out c
```

**Execution:**
```
Layer 0: [ModuleA]
Layer 1: [ModuleB, ModuleC] ← Parallel
```

**Time:** t_A + max(t_B, t_C)

### 5. Conditional Branching

```constellation
condition = CheckCondition(input)

result = if condition then
  ExpensivePathA(input)
else
  ExpensivePathB(input)

out result
```

**Execution:**
```
Layer 0: [CheckCondition]
Layer 1: [ConditionalSyntheticModule]
         ↓ (internally evaluates condition and chooses path)
       [ExpensivePathA] OR [ExpensivePathB]
```

**Time:** t_Check + t_PathA OR t_PathB (only one path executes)

### 6. Map Over List

```constellation
items = GetItems()
processed = items.map(item => Process(item))
out processed
```

**Execution:**
```
Layer 0: [GetItems]
Layer 1: [MapSyntheticModule]
         ↓ (applies Process to each item in parallel)
```

**Time:** t_GetItems + max(t_Process_per_item)

**Note:** Items are processed in parallel within the map operation.

## Visual DAG Diagrams

### Complex Pipeline Example

```constellation
in userInput: String
in threshold: Int

# Data cleaning layer
cleaned = Trim(userInput)
normalized = Normalize(cleaned)

# Parallel analysis layer
sentiment = AnalyzeSentiment(normalized)
keywords = ExtractKeywords(normalized)
length = CountWords(normalized)

# Decision layer
isLongText = length.value > threshold
category = if isLongText then
  ClassifyLong(normalized, keywords)
else
  ClassifyShort(normalized, keywords)

# Final aggregation
result = BuildReport(sentiment, keywords, category)

out result
```

### DAG Visualization

```
┌─────────────┐  ┌───────────┐
│ userInput   │  │ threshold │
└──────┬──────┘  └─────┬─────┘
       │               │
   ┌───▼────┐          │
   │  Trim  │          │
   └───┬────┘          │
       │               │
  ┌────▼─────────┐     │
  │  Normalize   │     │
  └────┬─────────┘     │
       │               │
       └────┬──────────┘
            │
    ┌───────┼───────────┬──────────┐
    │       │           │          │
┌───▼────────┐  ┌──────▼──────┐  ┌▼──────────┐
│ AnalyzeSent│  │ExtractKeywd │  │CountWords │ (Parallel layer)
└───┬────────┘  └──────┬──────┘  └┬──────────┘
    │                  │           │
    │              ┌───┴───┐   ┌───▼────────┐
    │              │       │   │ Compare    │
    │              │       │   │ > thresh   │
    │              │       │   └───┬────────┘
    │              │       │       │
    │              │       │   ┌───▼─────────┐
    │              │       │   │Conditional  │
    │              │       │   │  Module     │
    │              │       │   └───┬─────────┘
    │              │       │       │
    │              │   ┌───┴───┐   │
    │              │   │       │   │
    │          ┌───▼───▼───┐   │   │
    │          │ClassifyLong│  OR  │
    │          └─────┬──────┘   │   │
    │                │      ┌───▼───▼────┐
    │                │      │ClassifyShort│
    │                │      └─────┬───────┘
    │                │            │
    │                └─────┬──────┘
    │                      │
    └────────┬─────────────┘
             │
        ┌────▼─────────┐
        │ BuildReport  │
        └────┬─────────┘
             │
        ┌────▼────────┐
        │   result    │
        └─────────────┘
```

### Execution Layers

```
Layer 0: [Trim]
  ↓
Layer 1: [Normalize]
  ↓
Layer 2: [AnalyzeSentiment, ExtractKeywords, CountWords] ← 3 parallel
  ↓
Layer 3: [Compare > threshold, Conditional logic]
  ↓
Layer 4: [ClassifyLong OR ClassifyShort] ← Only one executes
  ↓
Layer 5: [BuildReport]
```

## Error Handling in DAG Execution

### Module Failure Propagation

When a module fails:

1. **Status update:** Module marked as `Status.Failed(error)`
2. **Deferred completion:** Output deferreds are NOT completed
3. **Downstream blocking:** Modules waiting on failed outputs remain suspended
4. **Graceful termination:** Other branches continue executing
5. **Final result:** State contains partial results + error information

### Example: Partial Execution

```constellation
a = ModuleA(input)  # Succeeds
b = ModuleB(input)  # Fails
c = ModuleC(input)  # Succeeds

# This blocks forever - 'b' never completes its output deferred
result = Combine(a, b, c)
```

**Runtime behavior:**
- ModuleA completes successfully
- ModuleB fails, sets status to `Failed(error)`
- ModuleC completes successfully
- Combine blocks waiting for 'b' deferred
- Execution times out or is cancelled

### Timeout Protection

Modules have two timeout levels:

```scala
case class ModuleConfig(
  inputsTimeout: FiniteDuration,   // Max time to wait for inputs
  moduleTimeout: FiniteDuration    // Max time for module logic
)
```

**Example timeout handling:**
```scala
(for {
  inputs <- awaitOnInputs(namespace, runtime)
  (latency, outputs) <- moduleImplementation(inputs)
    .timed
    .timeout(moduleTimeout)  // ← Module logic timeout
  _ <- provideOnOutputs(namespace, runtime, outputs)
} yield ())
  .timeout(inputsTimeout)  // ← Input wait timeout
  .handleErrorWith {
    case _: TimeoutException =>
      runtime.setModuleStatus(moduleId, Status.Timed(inputsTimeout))
    case e =>
      runtime.setModuleStatus(moduleId, Status.Failed(e))
  }
```

## Cancellation

Constellation supports cancelling in-flight executions:

```scala
def runCancellable(
  dag: DagSpec,
  initData: Map[String, CValue],
  modules: Map[UUID, Module.Uninitialized],
  modulePriorities: Map[UUID, Int],
  scheduler: GlobalScheduler,
  backends: ConstellationBackends
): IO[CancellableExecution]
```

**How cancellation works:**

1. Each module runs as an individual fiber
2. Fibers are stored in `moduleFibers: List[Fiber[IO, Throwable, Unit]]`
3. Calling `cancel` cancels all module fibers
4. In-flight modules are interrupted
5. Partial results are returned

### Cancellation Example

```scala
for {
  exec <- Runtime.runCancellable(dag, inputs, modules, priorities, scheduler, backends)

  // Start execution (non-blocking)
  resultFiber <- exec.result.start

  // Cancel after 5 seconds if still running
  _ <- IO.sleep(5.seconds) >> exec.cancel

  // Get partial results
  state <- resultFiber.join
} yield state
```

## Advanced: Circuit Breakers

Constellation supports per-module circuit breakers to prevent cascading failures:

```scala
val protectedRun = backends.circuitBreakers match {
  case Some(registry) =>
    registry.getOrCreate(moduleName).flatMap(_.protect(module.run(runtime)))
  case None =>
    module.run(runtime)
}
```

**Circuit breaker states:**
```
Closed → (failures exceed threshold) → Open
  ↑                                       ↓
  └────── (test succeeds) ← Half-Open ←──┘
```

**Effect on DAG execution:**
- Modules protected by open circuit breakers fail fast
- Reduces load on failing dependencies
- Allows DAG to continue executing other branches

## Summary

**Key takeaways:**

1. **DAG Structure:** Nodes (modules + data) and edges (dependencies)
2. **Topological Sort:** Determines valid execution order
3. **Layer-Based Execution:** Natural parallelism from data dependencies
4. **Deferred Coordination:** `cats.effect.Deferred` enables fiber synchronization
5. **Inline Transforms:** Lightweight synthetic computations run as fibers
6. **Performance:** Time = longest path, not sum of all paths
7. **Error Handling:** Partial execution with timeout protection
8. **Cancellation:** Individual fiber control for graceful shutdown

**Mental model:**

Think of DAG execution as **water flowing through a network of pipes**:
- Water (data) enters at inputs
- Flows through modules (processing nodes)
- Splits at fan-outs (parallel branches)
- Merges at fan-ins (joins)
- Exits at outputs

Modules are **active pumps** that wait for input water, process it, and release output water. The runtime ensures water flows in the right direction and multiple pumps can work simultaneously.

## Next Steps

- [Pipeline Lifecycle](./pipeline-lifecycle.md) — How pipelines are compiled and cached
- [Type System](./type-system.md) — CType, CValue, and type algebra
- [Resilience Patterns](../patterns/resilience.md) — Retry, timeout, circuit breakers
- [Performance Tuning](../../operations/performance-tuning.md) — Optimizing DAG execution
- [Scheduler Configuration](../../operations/scheduler.md) — Priority-based execution control
