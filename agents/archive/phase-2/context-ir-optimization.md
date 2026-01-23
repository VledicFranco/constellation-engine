# Task 2.1: IR Optimization Passes

**Phase:** 2 - Core Improvements
**Effort:** Medium (1 week)
**Impact:** High (10-30% runtime improvement)
**Dependencies:** Task 1.1 (Compilation Caching)
**Blocks:** Task 3.1 (Incremental Compilation), Task 3.3 (Plugin Architecture)

---

## Objective

Add optimization passes between IR generation and DAG compilation to reduce DAG size, eliminate redundant computations, and improve runtime performance.

---

## Background

### Current Pipeline

```
Parse → Type Check → IR Generate → DAG Compile
                         │              │
                         │   (No optimization)
                         │              │
                         └──────────────┘
```

The IR is directly translated to DAG without any optimization. This means:
- Dead code (unused assignments) is compiled
- Constant expressions are evaluated at runtime
- Common subexpressions are computed multiple times

### Example: Unoptimized vs Optimized

```constellation
# Input
in x: Int
unused = add(x, 1)    # Dead code - never used
result = add(x, 2)
constant = add(5, 3)  # Constant - could be folded to 8
out result
```

**Current DAG:** 4 nodes (unused, result, constant, output)
**Optimized DAG:** 1 node (result computed, output)

---

## Technical Design

### Optimization Pipeline

```scala
object IROptimizer {
  def optimize(ir: IRProgram, config: OptimizationConfig): IRProgram = {
    val passes = config.enabledPasses.map(passFromName)

    passes.foldLeft(ir) { (program, pass) =>
      pass.run(program)
    }
  }
}

trait OptimizationPass {
  def name: String
  def run(ir: IRProgram): IRProgram
}
```

### Passes to Implement

#### 1. Dead Code Elimination (DCE)

Remove nodes not reachable from outputs.

```scala
object DeadCodeElimination extends OptimizationPass {
  def name = "dead-code-elimination"

  def run(ir: IRProgram): IRProgram = {
    // 1. Start from output nodes
    // 2. Traverse dependencies backwards (DFS)
    // 3. Mark all reachable nodes
    // 4. Remove unreachable nodes

    val reachable = computeReachable(ir.outputs, ir.nodes)
    ir.copy(nodes = ir.nodes.filter(n => reachable.contains(n.id)))
  }

  private def computeReachable(outputs: List[UUID], nodes: Map[UUID, IRNode]): Set[UUID] = {
    val visited = mutable.Set[UUID]()

    def visit(id: UUID): Unit = {
      if (!visited.contains(id)) {
        visited += id
        nodes.get(id).foreach { node =>
          node.dependencies.foreach(visit)
        }
      }
    }

    outputs.foreach(visit)
    visited.toSet
  }
}
```

#### 2. Constant Folding

Evaluate constant expressions at compile time.

```scala
object ConstantFolding extends OptimizationPass {
  def name = "constant-folding"

  def run(ir: IRProgram): IRProgram = {
    val folded = mutable.Map[UUID, CValue]()

    // Find nodes that can be folded
    for ((id, node) <- ir.nodes) {
      tryFold(node, folded) match {
        case Some(value) => folded(id) = value
        case None => // Cannot fold
      }
    }

    // Replace foldable nodes with literals
    val newNodes = ir.nodes.map { case (id, node) =>
      folded.get(id) match {
        case Some(value) => id -> Literal(id, value, node.outputType)
        case None => id -> node
      }
    }

    ir.copy(nodes = newNodes)
  }

  private def tryFold(node: IRNode, folded: Map[UUID, CValue]): Option[CValue] = {
    node match {
      case Literal(_, value, _) => Some(value)

      case ModuleCall(_, "add", inputs) if allConstant(inputs, folded) =>
        val a = getConstant(inputs("a"), folded).asInstanceOf[CInt].value
        val b = getConstant(inputs("b"), folded).asInstanceOf[CInt].value
        Some(CInt(a + b))

      case ModuleCall(_, "multiply", inputs) if allConstant(inputs, folded) =>
        val a = getConstant(inputs("a"), folded).asInstanceOf[CInt].value
        val b = getConstant(inputs("b"), folded).asInstanceOf[CInt].value
        Some(CInt(a * b))

      // Add more foldable operations...

      case _ => None
    }
  }
}
```

#### 3. Common Subexpression Elimination (CSE)

Deduplicate identical computations.

```scala
object CommonSubexpressionElimination extends OptimizationPass {
  def name = "common-subexpression-elimination"

  def run(ir: IRProgram): IRProgram = {
    // Hash each node by its "semantic identity" (operation + inputs)
    val signatures = ir.nodes.map { case (id, node) =>
      id -> computeSignature(node)
    }

    // Group nodes with same signature
    val groups = signatures.groupBy(_._2).values.filter(_.size > 1)

    // For each group, keep first occurrence, redirect others
    val replacements = mutable.Map[UUID, UUID]()
    for (group <- groups) {
      val canonical = group.head._1
      for ((id, _) <- group.tail) {
        replacements(id) = canonical
      }
    }

    // Apply replacements
    applyReplacements(ir, replacements.toMap)
  }

  private def computeSignature(node: IRNode): String = {
    node match {
      case ModuleCall(_, name, inputs) =>
        s"call:$name:${inputs.toSeq.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(",")}"
      case FieldAccessNode(_, source, field) =>
        s"field:$source:$field"
      // ... etc
    }
  }
}
```

---

## Deliverables

### Required

- [ ] **`OptimizationPass.scala`** - Base trait for passes
- [ ] **`IROptimizer.scala`** - Orchestrates passes
- [ ] **`DeadCodeElimination.scala`** - DCE pass
- [ ] **`ConstantFolding.scala`** - Constant folding pass
- [ ] **`CommonSubexpressionElimination.scala`** - CSE pass
- [ ] **`OptimizationConfig.scala`** - Configuration for enabling/disabling passes

- [ ] **Integration with LangCompiler**:
  - Add optimization step after IR generation
  - Make optimization configurable
  - Update caching to include optimization

- [ ] **Unit Tests**:
  - Each pass individually tested
  - Combined optimization tested
  - Edge cases (empty program, all dead code, etc.)
  - Correctness verified (optimized code produces same result)

- [ ] **Benchmarks**:
  - DAG size before/after
  - Compilation time impact
  - Runtime improvement

### Optional Enhancements

- [ ] Inline expansion for simple modules
- [ ] Strength reduction (multiply by 2 → shift left)
- [ ] Loop-invariant code motion (if loops are added)

---

## Files to Create/Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/optimizer/OptimizationPass.scala` | **New** | Base trait |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/optimizer/IROptimizer.scala` | **New** | Orchestrator |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/optimizer/DeadCodeElimination.scala` | **New** | DCE |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/optimizer/ConstantFolding.scala` | **New** | Constant folding |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/optimizer/CommonSubexpressionElimination.scala` | **New** | CSE |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala` | Modify | Add optimization step |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/optimizer/*.scala` | **New** | Tests |

---

## Implementation Guide

### Step 1: Define Base Infrastructure

```scala
// OptimizationPass.scala
package io.constellation.lang.optimizer

import io.constellation.lang.compiler.IR._

trait OptimizationPass {
  def name: String
  def run(ir: IRProgram): IRProgram

  /** Helper to transform all nodes */
  protected def transformNodes(ir: IRProgram)(f: IRNode => IRNode): IRProgram = {
    ir.copy(nodes = ir.nodes.map { case (id, node) => id -> f(node) })
  }

  /** Helper to filter nodes */
  protected def filterNodes(ir: IRProgram)(p: IRNode => Boolean): IRProgram = {
    ir.copy(nodes = ir.nodes.filter { case (_, node) => p(node) })
  }
}

case class OptimizationConfig(
  enableDCE: Boolean = true,
  enableConstantFolding: Boolean = true,
  enableCSE: Boolean = true,
  maxIterations: Int = 3  // For iterative optimization
)

object OptimizationConfig {
  val default: OptimizationConfig = OptimizationConfig()
  val none: OptimizationConfig = OptimizationConfig(false, false, false)
  val aggressive: OptimizationConfig = OptimizationConfig(maxIterations = 10)
}
```

### Step 2: Implement IROptimizer

```scala
// IROptimizer.scala
package io.constellation.lang.optimizer

import io.constellation.lang.compiler.IR._

object IROptimizer {

  def optimize(ir: IRProgram, config: OptimizationConfig = OptimizationConfig.default): IRProgram = {
    val passes = buildPassList(config)

    // Iterative optimization until fixpoint or max iterations
    var current = ir
    var iteration = 0
    var changed = true

    while (changed && iteration < config.maxIterations) {
      val before = current
      current = passes.foldLeft(current) { (prog, pass) =>
        pass.run(prog)
      }
      changed = current != before
      iteration += 1
    }

    current
  }

  private def buildPassList(config: OptimizationConfig): List[OptimizationPass] = {
    List(
      if (config.enableDCE) Some(DeadCodeElimination) else None,
      if (config.enableConstantFolding) Some(ConstantFolding) else None,
      if (config.enableCSE) Some(CommonSubexpressionElimination) else None
    ).flatten
  }

  /** Analyze IR and report statistics */
  def analyze(ir: IRProgram): OptimizationStats = {
    OptimizationStats(
      totalNodes = ir.nodes.size,
      inputNodes = ir.nodes.count { case (_, n) => n.isInstanceOf[Input] },
      moduleCallNodes = ir.nodes.count { case (_, n) => n.isInstanceOf[ModuleCall] },
      literalNodes = ir.nodes.count { case (_, n) => n.isInstanceOf[Literal] }
    )
  }
}

case class OptimizationStats(
  totalNodes: Int,
  inputNodes: Int,
  moduleCallNodes: Int,
  literalNodes: Int
)
```

### Step 3: Integrate with Compilation Pipeline

```scala
// In LangCompiler.scala
def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] = {
  for {
    program <- ConstellationParser.parse(source)
    typedProgram <- TypeChecker.check(program, registry)
    irProgram = IRGenerator.generate(typedProgram)

    // NEW: Optimization step
    optimizedIR = IROptimizer.optimize(irProgram, optimizationConfig)

    result <- DagCompiler.compile(optimizedIR, dagName, modules)
  } yield result
}
```

---

## Testing Strategy

### Unit Tests for Each Pass

```scala
class DeadCodeEliminationTest extends AnyFlatSpec with Matchers {

  "DeadCodeElimination" should "remove unused nodes" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("input") -> Input(uuid("input"), "x", SInt),
        uuid("unused") -> ModuleCall(uuid("unused"), "add", Map("a" -> uuid("input"), "b" -> uuid("literal"))),
        uuid("literal") -> Literal(uuid("literal"), CInt(1), SInt),
        uuid("used") -> ModuleCall(uuid("used"), "multiply", Map("a" -> uuid("input"), "b" -> uuid("literal2"))),
        uuid("literal2") -> Literal(uuid("literal2"), CInt(2), SInt)
      ),
      outputs = List(uuid("used"))
    )

    val optimized = DeadCodeElimination.run(ir)

    optimized.nodes.keys should contain(uuid("used"))
    optimized.nodes.keys should not contain(uuid("unused"))
  }

  it should "preserve all nodes when all are reachable" in {
    // Test where everything is used
  }

  it should "handle empty programs" in {
    // Test empty IR
  }
}
```

### Correctness Tests

```scala
class OptimizationCorrectnessTest extends AnyFlatSpec with Matchers {

  "Optimized IR" should "produce same result as unoptimized" in {
    val source = """
      in x: Int
      unused = add(x, 1)
      constant = add(5, 3)
      result = add(x, constant)
      out result
    """

    val unoptimized = compileWithoutOptimization(source)
    val optimized = compileWithOptimization(source)

    // Execute both with same input
    val input = Map("x" -> CInt(10))

    executeDAG(unoptimized, input) shouldBe executeDAG(optimized, input)
  }
}
```

---

## Web Resources

### Compiler Optimization Theory
- [SSA and Optimization](https://www.cs.cmu.edu/~fp/courses/15411-f14/lectures/06-ssa.pdf) - CMU lecture
- [Engineering a Compiler (Book)](https://www.elsevier.com/books/engineering-a-compiler/cooper/978-0-12-815412-0) - Comprehensive reference
- [LLVM Optimization Passes](https://llvm.org/docs/Passes.html) - Industry-standard passes

### Dead Code Elimination
- [Wikipedia: Dead Code Elimination](https://en.wikipedia.org/wiki/Dead_code_elimination)
- [Reachability Analysis](https://www.cs.cornell.edu/courses/cs4120/2020sp/lectures/28reachability/lec28-sp20.pdf)

### Constant Folding
- [Wikipedia: Constant Folding](https://en.wikipedia.org/wiki/Constant_folding)
- [Partial Evaluation](https://en.wikipedia.org/wiki/Partial_evaluation) - Related technique

### Common Subexpression Elimination
- [Wikipedia: CSE](https://en.wikipedia.org/wiki/Common_subexpression_elimination)
- [Value Numbering](https://en.wikipedia.org/wiki/Value_numbering) - Related technique

### Scala-Specific
- [Scala Collections Performance](https://docs.scala-lang.org/overviews/collections-2.13/performance-characteristics.html)
- [Cats Effect for Optimization](https://typelevel.org/cats-effect/) - If passes need IO

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] DCE removes all unreachable nodes
   - [ ] Constant folding evaluates: add, subtract, multiply, divide, concat
   - [ ] CSE deduplicates identical module calls
   - [ ] Optimization preserves program semantics

2. **Performance Requirements**
   - [ ] Optimization adds < 10ms to compilation
   - [ ] DAG size reduced by 10%+ on typical programs
   - [ ] Runtime improved measurably for programs with dead code/constants

3. **Quality Requirements**
   - [ ] Unit test coverage > 80%
   - [ ] Correctness tests verify semantics preserved
   - [ ] No test regressions

---

## Notes for Implementer

1. **Start with DCE** - It's the simplest and has immediate impact.

2. **Test correctness thoroughly** - Optimization bugs are subtle and can cause incorrect results.

3. **Consider iteration** - Some optimizations enable others. Running passes multiple times can find more opportunities.

4. **Watch for edge cases:**
   - Empty programs
   - Programs with no outputs (should be all dead code)
   - Self-referential structures (should not exist but handle gracefully)
   - Very large programs (performance of passes)

5. **Integrate with caching** - The cache key may need to include optimization config so different optimization levels produce different cached results.

6. **Existing optimization docs** - See `docs/dev/optimizations/` for runtime optimization context (this task is compile-time optimization).
