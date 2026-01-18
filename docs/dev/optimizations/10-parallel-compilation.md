# Optimization 10: Parallel Compilation

**Priority:** 10 (Lower Priority)
**Expected Gain:** 10-30% compile time reduction
**Complexity:** Medium
**Status:** Not Implemented

---

## Problem Statement

The compilation pipeline runs sequentially:

```scala
// LangCompiler.scala:86-99

def compile(source: String, modules: ...): Either[CompileError, DagSpec] = {
  for {
    ast     <- parser.parse(source)           // Phase 1: Sequential
    typed   <- typeChecker.check(ast, modules) // Phase 2: Sequential
    ir      <- irGenerator.generate(typed)     // Phase 3: Sequential
    dagSpec <- dagCompiler.compile(ir, modules) // Phase 4: Sequential
  } yield dagSpec
}
```

While some phases depend on previous outputs, opportunities for parallelism exist.

---

## Analysis: Compilation Dependencies

```
┌─────────────────────────────────────────────────────────────────────┐
│                    COMPILATION PIPELINE                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Source ──► Parser ──► AST                                          │
│                         │                                            │
│                         ▼                                            │
│  Modules ─────────► TypeChecker ──► TypedAST                        │
│  Signatures         (parallel     │                                  │
│                      per func?)    │                                 │
│                         ▼          │                                 │
│                    IRGenerator ◄───┘                                │
│                         │                                            │
│                         ▼                                            │
│  Modules ─────────► DagCompiler ──► DagSpec                         │
│  Implementations                                                     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Parallelization Opportunities

| Opportunity | Parallelism Type | Potential Gain |
|-------------|------------------|----------------|
| Type check independent functions | Data parallel | 10-20% |
| IR generate independent nodes | Data parallel | 5-10% |
| Speculative parsing + module loading | Task parallel | 10-15% |

---

## Proposed Solutions

### Solution A: Parallel Type Checking

Type check independent top-level functions in parallel:

```scala
// TypeChecker.scala modifications

def check(ast: Program, modules: Map[String, FunctionSignature]): Either[TypeError, TypedProgram] = {
  // 1. Build dependency graph between functions
  val dependencies = buildDependencyGraph(ast)

  // 2. Find independent functions (no mutual dependencies)
  val independentGroups = dependencies.topologicalLayers()

  // 3. Type check each layer in parallel
  independentGroups.foldLeftM(TypeContext.empty) { (ctx, layer) =>
    layer.toList
      .parTraverse(func => checkFunction(func, ctx, modules))
      .map(results => ctx.merge(results))
  }
}

private def buildDependencyGraph(ast: Program): DependencyGraph = {
  // Function A depends on B if A calls B
  val graph = new DependencyGraph()

  ast.functions.foreach { func =>
    val calls = findFunctionCalls(func.body)
    calls.foreach(callee => graph.addEdge(func.name, callee))
  }

  graph
}

private def checkFunction(
  func: FunctionDef,
  ctx: TypeContext,
  modules: Map[String, FunctionSignature]
): IO[Either[TypeError, TypedFunction]] = IO {
  // Type check single function
  ???
}
```

### Solution B: Parallel IR Generation

Generate IR for independent AST nodes in parallel:

```scala
// IRGenerator.scala modifications

def generate(typed: TypedProgram): Either[IRError, IRProgram] = {
  // 1. Identify independent statement groups
  val groups = partitionByDependency(typed.statements)

  // 2. Generate IR for each group in parallel
  groups.toList
    .parTraverse(generateGroup)
    .map(mergeIRNodes)
}

private def partitionByDependency(stmts: List[TypedStatement]): List[List[TypedStatement]] = {
  // Statements that don't share variables can be processed in parallel
  val graph = new DependencyGraph()

  stmts.foreach { stmt =>
    val reads = findReadVariables(stmt)
    val writes = findWriteVariables(stmt)

    // Dependency if one writes what another reads
    stmts.foreach { other =>
      if (stmt != other) {
        val otherReads = findReadVariables(other)
        val otherWrites = findWriteVariables(other)

        if ((writes intersect otherReads).nonEmpty ||
            (reads intersect otherWrites).nonEmpty) {
          graph.addEdge(stmt, other)
        }
      }
    }
  }

  graph.topologicalLayers()
}
```

### Solution C: Speculative Parallelism

Start later phases speculatively while earlier phases complete:

```scala
// LangCompiler.scala - Speculative compilation

def compileSpeculative(source: String, modules: ...): IO[Either[CompileError, DagSpec]] = {
  // Start parsing immediately
  val parseTask = IO.defer(IO.fromEither(parser.parse(source)))

  // Start loading module implementations (can happen in parallel with parsing)
  val modulesTask = IO.defer(loadModuleImplementations(modules))

  // When parse completes, start type checking
  val typedTask = parseTask.flatMap { ast =>
    IO.fromEither(typeChecker.check(ast, modules))
  }

  // Combine results
  (typedTask, modulesTask).parMapN { (typed, moduleImpls) =>
    for {
      ir      <- irGenerator.generate(typed)
      dagSpec <- dagCompiler.compile(ir, moduleImpls)
    } yield dagSpec
  }
}
```

---

## Implementation: Parallel DAG Compilation

The most impactful parallelization is in DAG compilation:

```scala
// DagCompiler.scala modifications

def compile(ir: IRProgram, modules: ...): Either[CompileError, DagSpec] = {
  // 1. Process IR nodes in topological layers
  val layers = ir.topologicalLayers()

  layers.foldLeftM(DagSpec.empty) { (dag, layer) =>
    // 2. Compile all nodes in this layer in parallel
    layer.toList
      .parTraverse(node => compileNode(node, dag, modules))
      .map { results =>
        results.foldLeft(dag)(_.merge(_))
      }
  }
}

private def compileNode(
  node: IRNode,
  currentDag: DagSpec,
  modules: Map[String, Module.Uninitialized]
): IO[Either[CompileError, DagSpecFragment]] = IO {
  node match {
    case input: IRInput =>
      compileInputNode(input)

    case call: IRModuleCall =>
      compileModuleCall(call, currentDag, modules)

    case merge: IRMergeNode =>
      compileMergeNode(merge, currentDag)

    // ... other cases
  }
}
```

---

## Thread Pool Considerations

```scala
// Configure appropriate thread pool for compilation

import cats.effect.unsafe.IORuntime
import java.util.concurrent.Executors

val compilationRuntime: IORuntime = {
  val cpuBound = Executors.newFixedThreadPool(
    Runtime.getRuntime.availableProcessors()
  )

  IORuntime.builder()
    .setCompute(ExecutionContext.fromExecutor(cpuBound), () => cpuBound.shutdown())
    .build()
}

// Use for parallel compilation
def compile(source: String): IO[DagSpec] = {
  compileParallel(source).evalOn(compilationRuntime.compute)
}
```

---

## Benchmarks

### Test Programs

```scala
// Small: 5 functions, 10 statements
val small = """
  in a: Int
  out b: Int
  b = Module1(Module2(a))
"""

// Medium: 20 functions, 50 statements
val medium = /* ... */

// Large: 100 functions, 200+ statements
val large = /* ... */
```

### Expected Results

| Program Size | Sequential | Parallel | Improvement |
|--------------|------------|----------|-------------|
| Small | 20ms | 18ms | 10% |
| Medium | 80ms | 60ms | 25% |
| Large | 300ms | 200ms | 33% |

Parallelization benefits increase with program size.

---

## Limitations

### Fundamental Sequential Dependencies

Some phases cannot be parallelized:

1. **Parsing:** Inherently sequential (though could parallelize multi-file)
2. **Type inference:** May require iterative refinement
3. **Error collection:** Must maintain source order

### Overhead Considerations

| Program Size | Parallelism Overhead | Net Benefit |
|--------------|---------------------|-------------|
| Very small (<10ms) | ~5ms | Negative |
| Small (10-50ms) | ~5ms | Marginal |
| Medium (50-200ms) | ~5ms | Positive |
| Large (>200ms) | ~5ms | Significant |

**Recommendation:** Only enable parallel compilation for programs above a size threshold.

---

## Configuration

```hocon
constellation.compiler {
  parallelism {
    enabled = true
    min-program-size = 20  # statements
    thread-pool-size = 4   # or "auto" for CPU count
  }
}
```

---

## Implementation Checklist

- [ ] Add dependency graph builder for AST
- [ ] Implement parallel type checking for independent functions
- [ ] Add topological layering to IR generator
- [ ] Implement parallel IR generation
- [ ] Update DAG compiler with parallel node processing
- [ ] Add compilation thread pool configuration
- [ ] Add size-based parallelism toggle
- [ ] Benchmark with various program sizes

---

## Files to Modify

| File | Changes |
|------|---------|
| `modules/lang-compiler/.../TypeChecker.scala` | Parallel function checking |
| `modules/lang-compiler/.../IRGenerator.scala` | Parallel node generation |
| `modules/lang-compiler/.../DagCompiler.scala` | Parallel compilation |
| `modules/lang-compiler/.../LangCompiler.scala` | Orchestration |
| New: `modules/lang-compiler/.../DependencyGraph.scala` | Dependency analysis |

---

## Related Optimizations

- [Compilation Caching](./01-compilation-caching.md) - Caching reduces need for compilation
- [Quick Wins](./12-quick-wins.md) - Profile compilation first
