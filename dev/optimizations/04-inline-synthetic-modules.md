# Optimization 04: Inline Synthetic Modules

**Priority:** 4 (High Impact)
**Expected Gain:** 2-10ms per complex DAG
**Complexity:** Medium
**Status:** Not Implemented

---

## Problem Statement

The DAG compiler generates "synthetic modules" for basic operations:

- **Merge operations** - Combining record types
- **Project operations** - Extracting fields from records
- **Conditional operations** - If/else branching

Each synthetic module has full module overhead:
- UUID generation
- Module node creation
- Edge connections
- Deferred allocation
- Execution scheduling

For data-heavy transformation pipelines, synthetic modules can outnumber real modules.

### Current Implementation

```scala
// DagCompiler.scala:151-200 (simplified)

case ir: IRMergeNode =>
  val syntheticModule = createSyntheticMergeModule(ir)
  val moduleNode = ModuleNodeSpec(
    id = UUID.randomUUID(),
    name = s"__merge_${ir.id}",
    // ... full module spec
  )
  dagSpec.copy(
    moduleNodes = dagSpec.moduleNodes + moduleNode,
    syntheticModules = dagSpec.syntheticModules + syntheticModule
  )
```

**Example DAG:**
```
in a: {x: Int}
in b: {y: Int}
out merged: {x: Int, y: Int}
merged = merge(a, b)  // Creates synthetic merge module
result = Transform(merged)
```

**Generated Modules:**
1. `__merge_uuid1` (synthetic) - Just combines two records
2. `Transform` (real) - Actual computation

The synthetic module adds ~1-2ms overhead for what's essentially a map merge.

---

## Proposed Solution

Inline simple synthetic operations directly into the data flow, eliminating module overhead.

### Strategy

| Operation | Current | Optimized |
|-----------|---------|-----------|
| Merge | Full module | Inline map merge |
| Project | Full module | Inline field access |
| Conditional | Full module | Inline if/else |

### Implementation

#### Step 1: Inline Data Transformations

```scala
// New: InlineTransform.scala

sealed trait InlineTransform {
  def apply(inputs: Map[String, Any]): Any
}

case class MergeTransform(leftFields: Set[String], rightFields: Set[String]) extends InlineTransform {
  def apply(inputs: Map[String, Any]): Any = {
    val left = inputs("left").asInstanceOf[Map[String, Any]]
    val right = inputs("right").asInstanceOf[Map[String, Any]]
    left ++ right  // Simple map merge
  }
}

case class ProjectTransform(fields: Set[String]) extends InlineTransform {
  def apply(inputs: Map[String, Any]): Any = {
    val record = inputs("input").asInstanceOf[Map[String, Any]]
    fields.foldLeft(Map.empty[String, Any]) { (acc, field) =>
      acc + (field -> record(field))
    }
  }
}

case class ConditionalTransform() extends InlineTransform {
  def apply(inputs: Map[String, Any]): Any = {
    val condition = inputs("condition").asInstanceOf[Boolean]
    if (condition) inputs("then") else inputs("else")
  }
}
```

#### Step 2: Modify DataNodeSpec

```scala
// Spec.scala modifications

case class DataNodeSpec(
  id: UUID,
  name: String,
  nicknames: Map[UUID, String],
  cType: CType,
  // New: Optional inline transform
  inlineTransform: Option[InlineTransform] = None,
  transformInputs: List[UUID] = List.empty  // IDs of input data nodes
)
```

#### Step 3: Update DAG Compiler

```scala
// DagCompiler.scala modifications

case ir: IRMergeNode =>
  // Instead of creating a module, create a data node with inline transform
  val mergeDataNode = DataNodeSpec(
    id = UUID.randomUUID(),
    name = ir.outputName,
    nicknames = Map.empty,
    cType = ir.resultType,
    inlineTransform = Some(MergeTransform(ir.leftFields, ir.rightFields)),
    transformInputs = List(ir.leftId, ir.rightId)
  )

  dagSpec.copy(
    dataNodes = dagSpec.dataNodes + mergeDataNode
    // No new module node!
  )

case ir: IRProjectNode =>
  val projectDataNode = DataNodeSpec(
    id = UUID.randomUUID(),
    name = ir.outputName,
    nicknames = Map.empty,
    cType = ir.resultType,
    inlineTransform = Some(ProjectTransform(ir.fields)),
    transformInputs = List(ir.inputId)
  )

  dagSpec.copy(dataNodes = dagSpec.dataNodes + projectDataNode)
```

#### Step 4: Update Runtime Execution

```scala
// Runtime.scala modifications

private def initDataTable(dagSpec: DagSpec): IO[MutableDataTable] = {
  // First, create deferreds for all data nodes
  val allDeferreds = dagSpec.dataNodes.toList.traverse { node =>
    Deferred[IO, Any].map(node.id -> _)
  }.map(_.toMap)

  allDeferreds.flatMap { table =>
    // Then, set up inline transforms
    dagSpec.dataNodes.filter(_.inlineTransform.isDefined).toList.traverse { node =>
      setupInlineTransform(node, table)
    }.as(table)
  }
}

private def setupInlineTransform(
  node: DataNodeSpec,
  table: MutableDataTable
): IO[Unit] = {
  node.inlineTransform match {
    case Some(transform) =>
      // Wait for inputs, apply transform, complete output
      val inputDeferreds = node.transformInputs.map(table(_))

      inputDeferreds.traverse(_.get).flatMap { inputValues =>
        val inputs = node.transformInputs.zip(inputValues).toMap
        val result = transform.apply(inputs)
        table(node.id).complete(result)
      }.start.void  // Run in background fiber

    case None => IO.unit
  }
}
```

---

## Optimization: Fused Transforms

Chain multiple inline transforms into a single operation:

```scala
// Before optimization:
// data1 --[project]--> data2 --[merge]--> data3 --[project]--> data4

// After fusion:
// data1 ----[fused: project+merge+project]----> data4

case class FusedTransform(transforms: List[InlineTransform]) extends InlineTransform {
  def apply(inputs: Map[String, Any]): Any = {
    transforms.foldLeft(inputs) { (current, transform) =>
      Map("input" -> transform.apply(current))
    }("input")
  }
}

// In DagCompiler: detect chains of inline-able nodes and fuse them
def fuseTransformChains(dagSpec: DagSpec): DagSpec = {
  // Find sequences of data nodes with inline transforms
  // where output of one is sole input of next
  // Replace with single fused transform
  ???
}
```

---

## When NOT to Inline

Some synthetic operations should remain as modules:

| Keep as Module | Reason |
|----------------|--------|
| Complex conditionals | May have side effects in branches |
| User-visible operations | Need execution tracking |
| Operations with timeouts | Inline can't be timed out |
| Debug mode | Need to inspect intermediate values |

```scala
// Configuration option
case class CompilerConfig(
  inlineSyntheticModules: Boolean = true,
  inlineThreshold: Int = 3,  // Max transform chain length
  preserveForDebugging: Boolean = false
)
```

---

## Benchmarks

### Test DAG

```
in user: {id: Int, name: String, email: String}
in metadata: {created: String, updated: String}
out result: {name: String, created: String}

// Merge then project - 2 synthetic modules currently
combined = merge(user, metadata)
result = project(combined, [name, created])
```

### Expected Results

| Metric | With Synthetic Modules | With Inlining | Improvement |
|--------|------------------------|---------------|-------------|
| Module count | 2 | 0 | -2 modules |
| Deferred count | 4 | 2 | -2 deferreds |
| Execution time | 3ms | 0.5ms | 83% faster |
| Memory per exec | ~5KB | ~1KB | 80% less |

### Complex Pipeline (20 transforms)

| Metric | With Synthetic Modules | With Inlining | Improvement |
|--------|------------------------|---------------|-------------|
| Module count | 20 | 0 | -20 modules |
| Execution time | 25ms | 2ms | 92% faster |

---

## Implementation Checklist

- [ ] Define `InlineTransform` sealed trait and implementations
- [ ] Add `inlineTransform` field to `DataNodeSpec`
- [ ] Modify `DagCompiler` to generate inline transforms instead of synthetic modules
- [ ] Update `Runtime` to execute inline transforms
- [ ] Implement transform fusion optimization
- [ ] Add configuration to disable inlining (for debugging)
- [ ] Update visualization to show inline transforms differently
- [ ] Benchmark with real transformation pipelines

---

## Files to Modify

| File | Changes |
|------|---------|
| New: `modules/runtime/.../InlineTransform.scala` | Transform definitions |
| `modules/core/.../Spec.scala` | Add inlineTransform to DataNodeSpec |
| `modules/lang-compiler/.../DagCompiler.scala` | Generate inline transforms |
| `modules/runtime/.../Runtime.scala` | Execute inline transforms |
| `modules/lang-lsp/.../visualization/*` | Update DAG visualization |

---

## Visualization Update

Inline transforms should be shown differently in DAG visualization:

```
Current (with synthetic modules):
  [input_a] ──► [__merge_1] ──► [__project_1] ──► [RealModule]
  [input_b] ──┘

Optimized (with inline transforms):
  [input_a] ══╗
              ╠══► [merged_projected] ──► [RealModule]
  [input_b] ══╝
        (inline: merge+project)
```

---

## Related Optimizations

- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Fewer modules = less pooling needed
- [Mutable Execution State](./05-mutable-execution-state.md) - Inline transforms use direct mutation
