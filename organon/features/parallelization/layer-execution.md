# Layer Execution

> **Path**: `organon/features/parallelization/layer-execution.md`
> **Parent**: [parallelization/](./README.md)

How the runtime computes layers from dependencies and executes modules in parallel.

## Quick Example

```constellation
# Inputs (Layer 0)
in userId: String

# Independent calls (Layer 1 - parallel)
user = GetUser(userId)
orders = GetOrders(userId)
preferences = GetPreferences(userId)

# Dependent call (Layer 2 - waits for Layer 1)
profile = BuildProfile(user, orders, preferences)

out profile
```

Execution:

```
Layer 0: [userId]            ─────────► immediate
Layer 1: [GetUser, GetOrders, GetPreferences] ─────────► parallel
Layer 2: [BuildProfile]      ─────────► sequential (after Layer 1)
Layer 3: [profile output]    ─────────► immediate
```

## Layer Computation

### Algorithm

Each node's layer is computed as:

```
Layer(input node)  = 0
Layer(module node) = 1 + max(Layer(dependency) for each input dependency)
Layer(output node) = 1 + max(Layer(dependency) for each input dependency)
```

### Example

```constellation
in a: Int
in b: Int

x = ModuleX(a)        # Layer = 1 + max(0) = 1
y = ModuleY(b)        # Layer = 1 + max(0) = 1
z = ModuleZ(x, y)     # Layer = 1 + max(1, 1) = 2
w = ModuleW(z, a)     # Layer = 1 + max(2, 0) = 3

out w
```

Layer assignment:

| Node | Dependencies | Layer |
|------|--------------|-------|
| `a` | (input) | 0 |
| `b` | (input) | 0 |
| `x = ModuleX(a)` | a (layer 0) | 1 |
| `y = ModuleY(b)` | b (layer 0) | 1 |
| `z = ModuleZ(x, y)` | x (layer 1), y (layer 1) | 2 |
| `w = ModuleW(z, a)` | z (layer 2), a (layer 0) | 3 |

## Parallel Execution Within Layers

### Execution Strategy

All modules in the same layer are independent (by construction) and execute in parallel:

```scala
// Simplified runtime logic
layers.foldLeft(IO.unit) { (prev, layer) =>
  prev *> layer.modules.parTraverse(_.run).void
}
```

### Why Layers?

1. **Correctness.** Dependencies within a layer would violate the layer formula.
2. **Simplicity.** No need to track individual completion for early starts.
3. **Predictability.** Execution phases are clear boundaries for debugging.

### Trade-Off: Some Parallelism Left Unused

Consider:

```
Layer 1: [A (100ms), B (10ms)]
Layer 2: [C (depends on B only)]
```

With layer execution, C waits for both A and B (100ms). With work stealing, C could start after B (10ms).

We accept this trade-off for predictability and simpler implementation.

## Data Flow Between Layers

### Deferred-Based Communication

Modules communicate through the **data table** (Map of UUID -> Deferred):

```scala
// Producing module sets the value
runtime.setTableData(outputId, result)

// Consuming module waits for the value
val input = runtime.getTableData(inputId).get  // blocks until set
```

The Deferred ensures:
- A consumer cannot read until the producer has written
- The value is computed exactly once
- Multiple consumers read the same value

### Example Flow

```
Layer 0: userId is set in data table

Layer 1:
  GetUser: waits for userId, produces user
  GetOrders: waits for userId, produces orders
  (both run in parallel, both can read userId)

Layer 2:
  BuildProfile: waits for user AND orders, produces profile
  (cannot start until both GetUser and GetOrders complete)
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `lang-compiler` | Topological sort of IR nodes | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` |
| `runtime` | Layer computation (implicit in execution) | `modules/runtime/src/main/scala/io/constellation/Runtime.scala:178` |
| `runtime` | Parallel layer execution via `parTraverse` | `modules/runtime/src/main/scala/io/constellation/Runtime.scala:179-184` |
| `runtime` | Deferred-based data table | `modules/runtime/src/main/scala/io/constellation/Runtime.scala:38-49` |

## Inline Transforms

Not all operations are modules. **Inline transforms** are pure computations that run as fibers alongside modules:

```constellation
merged = user & orders  # Merge transform (not a module)
```

Inline transforms:
- Execute as soon as their inputs are ready
- Run in parallel with modules (as separate fibers)
- Have no priority, timeout, or retry options

### Inline Transform Types

| Transform | Description |
|-----------|-------------|
| `MergeTransform` | Combine two records (`a & b`) |
| `ProjectTransform` | Select fields (`{a.x, a.y}`) |
| `FieldAccessTransform` | Access single field (`a.name`) |
| `ConditionalTransform` | If-then-else (`if cond then a else b`) |
| `LiteralTransform` | Constant values |
| `AndTransform`, `OrTransform`, `NotTransform` | Logical operations |
| `GuardTransform` | Conditional optional (`x when cond`) |
| `CoalesceTransform` | Null coalescing (`a ?? b`) |
| `FilterTransform`, `MapTransform` | Higher-order functions |

## Edge Cases

### Empty Layers

A layer with no modules is skipped:

```constellation
in x: Int
out x  # Layer 0 -> Layer 1 directly, no modules
```

### Single-Module Layers

A layer with one module runs "in parallel" with itself (just runs):

```constellation
in x: Int
y = Transform(x)
out y
```

### Diamond Dependencies

Multiple paths to the same node are handled correctly:

```constellation
in x: Int
a = ModuleA(x)
b = ModuleB(x)
c = ModuleC(a, b)
out c
```

Layer assignment:

```
Layer 0: [x]
Layer 1: [ModuleA, ModuleB]  # Both run in parallel
Layer 2: [ModuleC]           # Waits for both A and B
```

### Long Chains

Sequential dependencies create sequential layers:

```constellation
a = Step1(input)
b = Step2(a)
c = Step3(b)
d = Step4(c)
out d
```

Each step is a separate layer with no parallelism. This is correct - the data dependencies enforce sequential execution.

## Debugging Layer Issues

### Viewing Layer Assignments

The compiler produces a topological order that reflects layer boundaries:

```scala
// DagCompileOutput contains topologicalOrder
dagOutput.dagSpec.modules.keys  // Module UUIDs in execution order
```

### Unexpected Serialization

If modules run sequentially when you expect parallelism:

1. **Check dependencies.** Do modules share an input? They should parallelize.
2. **Check the DAG.** Use the dashboard's DAG view to visualize.
3. **Check for implicit dependencies.** Field accesses create dependencies.

### Unexpected Parallelization

If modules run in parallel when you expect serialization:

1. **Add a data dependency.** Pass output of first to input of second.
2. **Check the DAG.** Ensure edges exist in the visualized DAG.

## Best Practices

1. **Structure for parallelism.** Independent data fetches should have no cross-dependencies.
2. **Minimize layer count.** Deep chains of dependencies serialize execution.
3. **Use wide layers.** Many independent operations in one layer maximize parallelism.
4. **Profile before optimizing.** The dashboard shows per-module latency.

## Related

- [scheduling.md](./scheduling.md) - Priority scheduling when concurrency is bounded
- [performance.md](./performance.md) - Tuning parallelization behavior
- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why automatic parallelization
