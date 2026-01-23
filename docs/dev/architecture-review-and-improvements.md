# Constellation Engine: Architectural Review & Improvement Recommendations

**Author:** Compiler Architecture Consultant
**Date:** January 2026
**Scope:** Parser, Compiler, Runtime, LSP, Type System

---

## Executive Summary

Constellation Engine is a well-structured pipeline orchestration framework with a clean separation of concerns between parsing, type checking, IR generation, DAG compilation, and runtime execution. The architecture follows functional programming best practices with Cats Effect for side effects and cats-parse for parsing.

This document identifies **architectural improvements** across six dimensions:
1. **Parser Performance** - Memoization and incremental parsing
2. **Compiler Optimizations** - Optimization passes and dead code elimination
3. **Runtime Efficiency** - Scheduling improvements and memory layout
4. **LSP Responsiveness** - Incremental analysis and caching
5. **Type System Enhancements** - Subtyping, effect tracking, and inference
6. **General Architecture** - Modularity and extensibility patterns

**Priority Recommendations Summary:**

| Priority | Area | Improvement | Impact |
|----------|------|-------------|--------|
| P0 | Parser | Add Packrat/memoization | 2-5x parse time reduction |
| P0 | Compiler | Compilation result caching | 50-200ms per request |
| P1 | LSP | Incremental document analysis | Real-time responsiveness |
| P1 | Runtime | Work-stealing scheduler | Better parallelism |
| P2 | Compiler | IR optimization passes | 10-30% runtime improvement |
| P2 | Type System | Bidirectional type inference | Better ergonomics |

---

## 1. Parser Architecture Review

### Current Implementation

**Technology:** cats-parse 1.0.0 (pure functional parser combinators)

**Structure:**
- Single monolithic `ConstellationParser` object (~520 lines)
- 11+ precedence levels for expressions
- Extensive use of `.backtrack` at choice points
- `P.defer()` + `lazy val` for recursive grammar
- `Located[A]` wrapping for span tracking

**Strengths:**
- Clean, readable combinator-based grammar
- Full span tracking for error reporting
- Good test coverage (~2000 test cases)

**Weaknesses Identified:**

#### 1.1 No Memoization (Critical)

The parser relies entirely on backtracking without memoization. For ambiguous inputs like:

```constellation
result = someFunction(a, b, c)
```

The parser attempts:
1. `lambdaExpr` → fails after `(` check → backtrack
2. `exprCoalesce` → eventually succeeds

**Impact:** Worst-case O(n²) behavior on deeply nested expressions or repeated backtracking.

**Recommendation:** Implement Packrat parsing or selective memoization.

```scala
// Current (no memoization)
lazy val expression: P[Expression] = P.defer(lambdaExpr.backtrack | exprCoalesce)

// Recommended: Memoized parser wrapper
class MemoizedParser[A](underlying: P[A]) {
  private val cache = collection.mutable.Map[Int, Either[Parser.Error, (A, Int)]]()

  def parse(input: String, offset: Int): Either[Parser.Error, (A, Int)] = {
    cache.getOrElseUpdate(offset, underlying.parse(input.substring(offset)))
  }
}
```

Alternatively, consider using a PEG-based parser generator that provides built-in memoization.

#### 1.2 No Error Recovery

The parser fails on the first error with minimal context:

```scala
def parse(source: String): Either[CompileError.ParseError, Program] =
  program.parseAll(source).left.map { err =>
    CompileError.ParseError(
      s"Parse error: ${err.expected.toList.map(_.toString).mkString(", ")}",
      Some(Span.point(err.failedAtOffset))
    )
  }
```

**Impact:** Users see only one error at a time, requiring multiple compilation cycles.

**Recommendation:** Implement panic-mode error recovery:

```scala
// Error recovery strategy
sealed trait RecoveryStrategy
case object SyncToNewline extends RecoveryStrategy      // Skip to next line
case object SyncToDeclaration extends RecoveryStrategy  // Skip to next in/out/type
case object InsertMissing extends RecoveryStrategy      // Insert expected token

def recoveryParser[A](main: P[A], recovery: RecoveryStrategy): P[Either[RecoveryError, A]] = {
  main.map(Right(_)).backtrack | recoverWith(recovery).map(Left(_))
}
```

This allows collecting multiple errors in a single parse pass.

#### 1.3 String Interpolation Complexity

String interpolation re-enters the full expression parser for each `${...}`:

```scala
private lazy val interpolation: P[Located[Expression]] =
  P.string("${") *> withSpan(P.defer(expression)) <* P.char('}')
```

**Impact:** Deep nesting in interpolations can cause performance issues.

**Recommendation:** Limit nesting depth or use a specialized expression subset:

```scala
// Simplified expression set for interpolation
private lazy val interpolationExpr: P[Expression] =
  varRef | fieldAccess | functionCallSimple  // No nested lambdas/branches
```

#### 1.4 Operator Precedence via Recursion

Current approach uses chained parsers for precedence:

```scala
exprCoalesce → exprGuard → exprOr → exprAnd → exprNot → exprCompare → exprAddSub → ...
```

**Impact:** Each precedence level adds function call overhead.

**Recommendation:** Consider Pratt parsing for expression handling:

```scala
// Pratt parser pseudo-code
def parseExpression(minPrecedence: Int): P[Expression] = {
  for {
    left <- parsePrimary
    result <- parseInfix(left, minPrecedence)
  } yield result
}

def parseInfix(left: Expression, minPrecedence: Int): P[Expression] = {
  operator.filter(_.precedence >= minPrecedence).flatMap { op =>
    for {
      right <- parseExpression(op.rightPrecedence)
      newLeft = BinaryOp(left, op, right)
      result <- parseInfix(newLeft, minPrecedence)
    } yield result
  } | P.pure(left)
}
```

This reduces the call stack depth and simplifies precedence management.

---

## 2. Compiler Architecture Review

### Current Implementation

**Pipeline:**
```
Parse → Type Check → IR Generate → DAG Compile
```

**Phases:**
1. **TypeChecker:** Uses `ValidatedNel` for accumulating errors
2. **IRGenerator:** Produces UUID-identified nodes with explicit dependencies
3. **DagCompiler:** Converts IR to executable `DagSpec` with inline transforms

**Strengths:**
- Clean separation of phases
- Error accumulation (all errors reported, not just first)
- Inline transforms reduce DAG complexity

**Weaknesses Identified:**

#### 2.1 No Optimization Passes

The IR is directly compiled to DAG without optimization:

```scala
// IRGenerator.scala
def generate(typedProgram: TypedProgram): IRProgram = {
  // Direct translation, no optimization
  declarations.foldLeft(GenContext.empty) { (ctx, decl) =>
    generateDeclaration(decl, ctx)
  }
}
```

**Missing Optimizations:**

| Optimization | Description | Impact |
|--------------|-------------|--------|
| Dead Code Elimination | Remove unused assignments | Reduce DAG size |
| Common Subexpression Elimination | Deduplicate identical computations | Avoid redundant work |
| Constant Folding | Evaluate constant expressions at compile time | Faster execution |
| Inline Expansion | Inline simple module calls | Reduce call overhead |
| Strength Reduction | Replace expensive ops with cheaper equivalents | Faster execution |

**Recommendation:** Add an optimization pass between IR generation and DAG compilation:

```scala
// New file: IROptimizer.scala
object IROptimizer {
  def optimize(ir: IRProgram): IRProgram = {
    val passes = List(
      DeadCodeElimination,
      ConstantFolding,
      CommonSubexpressionElimination,
      // Add more passes as needed
    )
    passes.foldLeft(ir)((program, pass) => pass.run(program))
  }
}

trait OptimizationPass {
  def run(ir: IRProgram): IRProgram
}

object DeadCodeElimination extends OptimizationPass {
  def run(ir: IRProgram): IRProgram = {
    // 1. Build dependency graph from outputs
    // 2. Mark reachable nodes via DFS from outputs
    // 3. Remove unreachable nodes
    val reachable = computeReachable(ir.outputs, ir.nodes)
    ir.copy(nodes = ir.nodes.filter(n => reachable.contains(n.id)))
  }
}
```

#### 2.2 No Incremental Compilation

Each compilation starts from scratch:

```scala
def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] = {
  for {
    program <- ConstellationParser.parse(source)  // Full re-parse
    typedProgram <- TypeChecker.check(program, registry)  // Full re-check
    // ...
  } yield result
}
```

**Impact:** Redundant work when only small portions of the program change.

**Recommendation:** Implement fine-grained caching:

```scala
case class CompilationCache(
  sourceHash: Int,
  parseResult: Option[Program],
  typeCheckResult: Option[TypedProgram],
  irResult: Option[IRProgram],
  dagResult: Option[DagSpec]
)

class IncrementalCompiler(cache: Ref[IO, Map[String, CompilationCache]]) {
  def compile(source: String, dagName: String): IO[Either[List[CompileError], CompileResult]] = {
    cache.get.flatMap { cached =>
      val hash = source.hashCode
      cached.get(dagName) match {
        case Some(c) if c.sourceHash == hash =>
          // Return cached result
          IO.pure(c.dagResult.toRight(List.empty))
        case _ =>
          // Full compilation, then cache
          fullCompile(source, dagName).flatTap(r => updateCache(dagName, hash, r))
      }
    }
  }
}
```

For more sophisticated incremental compilation, consider:
- AST diffing to identify changed declarations
- Dependency-aware re-checking (only re-check declarations affected by changes)
- Salsa-style demand-driven compilation

#### 2.3 Lambda Compilation Limitations

Lambda bodies have restricted node support:

```scala
// DagCompiler.scala
private def createLambdaEvaluator(lambda: TypedLambda): Either[..., Any => Any] = {
  // Supported: Input, Literal, FieldAccess, And, Or, Not, ModuleCall (limited), Conditional
  // Unsupported: Nested HOF, StringInterpolation, complex modules
}
```

**Impact:** Users cannot write complex lambda expressions.

**Recommendation:** Compile lambdas to full IR subgraphs that can be executed inline:

```scala
// Instead of function closures, compile to mini-DAGs
case class CompiledLambda(
  paramNodes: Map[String, UUID],  // Parameter bindings
  bodyDag: DagSpec,               // Executable subgraph
  outputNode: UUID                // Result node
)

// Execution: set param values, execute bodyDag, extract output
def executeLambda(lambda: CompiledLambda, args: Map[String, CValue]): IO[CValue] = {
  Runtime.run(lambda.bodyDag, args, modules).map(_.values.head)
}
```

This unifies lambda execution with DAG execution, removing the need for special cases.

#### 2.4 Type Checking Desugaring Coupling

Arithmetic and comparison operators are desugared during type checking:

```scala
// TypeChecker.scala
left + right  →  add(left, right)
left == right →  eq-int(left, right) or eq-string(left, right)
```

**Impact:** Type checker has too many responsibilities; desugaring logic is spread across phases.

**Recommendation:** Separate desugaring into its own pass:

```scala
// New phase order:
// Parse → Desugar → Type Check → IR Generate → Optimize → DAG Compile

object Desugarer {
  def desugar(program: Program): DesugaredProgram = {
    // Convert operators to function calls
    // Expand syntactic sugar
    // Normalize AST structure
  }
}
```

This improves phase separation and makes the type checker simpler.

---

## 3. Runtime Architecture Review

### Current Implementation

**Execution Model:**
- Cats Effect `Deferred` for synchronization
- `parTraverse` for parallel module execution
- Inline transforms executed as separate fibers
- `Eval.later` for lazy status/value computation

**Strengths:**
- Clean functional design with Cats Effect
- Good parallelism via `parTraverse`
- Memory optimization via `RawValue`
- Object pooling for high-throughput scenarios

**Weaknesses Identified:**

#### 3.1 No Work-Stealing Scheduler

Current approach uses flat `parTraverse`:

```scala
// Runtime.scala
moduleFibers <- modules.parTraverse { module =>
  module.run(runtime).start
}
```

**Impact:** All modules launched simultaneously regardless of dependencies; no prioritization.

**Recommendation:** Implement a work-stealing scheduler:

```scala
trait Scheduler {
  def submit(task: Task): IO[Unit]
  def steal(): IO[Option[Task]]
}

class WorkStealingScheduler(workers: Int) extends Scheduler {
  private val queues: Array[Deque[Task]] = Array.fill(workers)(new ConcurrentLinkedDeque())

  def submit(task: Task): IO[Unit] = {
    // Submit to least-loaded queue
    val minQueue = queues.minBy(_.size)
    IO(minQueue.addLast(task))
  }

  def steal(): IO[Option[Task]] = {
    // Try local queue first, then steal from busiest
    // ...
  }
}
```

This improves load balancing when modules have varying execution times.

#### 3.2 No Priority-Based Scheduling

All modules have equal priority:

**Impact:** Critical path modules aren't prioritized over non-critical ones.

**Recommendation:** Implement critical path analysis:

```scala
// Compute critical path during DAG compilation
case class SchedulingMetadata(
  criticalPath: List[UUID],      // Modules on longest path
  estimatedTime: Map[UUID, Duration],  // From profiling
  priority: Map[UUID, Int]       // Higher = more important
)

// Use priority in scheduler
def submitWithPriority(module: Module, priority: Int): IO[Unit] = {
  priorityQueue.insert(module, priority)
}
```

#### 3.3 Deferred Allocation per Execution

Each execution allocates new `Deferred` instances:

```scala
// Module initialization
for {
  inputDeferreds <- inputs.traverse(_ => Deferred[IO, Any])
  outputDeferreds <- outputs.traverse(_ => Deferred[IO, Any])
} yield ...
```

**Impact:** GC pressure from short-lived objects.

**Current Mitigation:** `DeferredPool` (already implemented)

**Additional Recommendation:** Pre-allocate per-DAG deferred templates:

```scala
// During DAG compilation, create deferred template
case class DagExecutionTemplate(
  inputSlots: Int,
  moduleSlots: Map[UUID, Int],  // Input count per module
  outputSlots: Int
)

// During execution, allocate from template
class TemplatedExecution(template: DagExecutionTemplate) {
  private val deferreds = Array.fill(template.totalSlots)(Deferred.unsafe[IO, Any])

  def getDeferred(slot: Int): Deferred[IO, Any] = deferreds(slot)
}
```

#### 3.4 No Speculative Execution

Execution waits for all inputs:

```scala
// Module.Runnable
consumes <- awaitOnInputs[I](consumesNamespace, runtime)
// Only then execute
```

**Recommendation:** For idempotent modules, consider speculative execution:

```scala
// Start execution with partial inputs, re-execute if needed
def speculativeExecute(module: Module, partialInputs: Map[String, CValue]): IO[Unit] = {
  if (module.isIdempotent && partialInputs.size >= requiredInputs * 0.8) {
    module.run(partialInputs).start.void  // Speculative
  } else {
    IO.unit
  }
}
```

This is useful for modules with deterministic behavior where partial inputs are often sufficient.

---

## 4. LSP Architecture Review

### Current Implementation

**Architecture:**
- WebSocket-based protocol over http4s
- Full re-parse/compile on every document change
- Stepped execution for debugging
- Custom protocol extensions for pipeline execution

**Strengths:**
- Good feature coverage (completion, hover, diagnostics)
- Stepped execution is unique and valuable
- Structured error reporting with code context

**Weaknesses Identified:**

#### 4.1 No Incremental Document Analysis

Every keystroke triggers full compilation:

```scala
// ConstellationLanguageServer.scala line 1149
compiler.compile(document.text, "validation")
```

**Impact:** Latency on large files; CPU waste.

**Recommendation:** Implement incremental analysis:

```scala
class IncrementalAnalyzer {
  private var lastAst: Option[Program] = None
  private var lastTypes: Option[TypedProgram] = None

  def analyze(change: TextDocumentChange): IO[List[Diagnostic]] = {
    // 1. Parse only changed region
    val changedDecls = identifyChangedDeclarations(change)

    // 2. Incrementally update AST
    val newAst = updateAst(lastAst, change)

    // 3. Re-type-check only affected declarations
    val affectedDecls = computeAffected(changedDecls, dependencyGraph)
    val newTypes = incrementalTypeCheck(lastTypes, affectedDecls)

    // 4. Cache results
    lastAst = Some(newAst)
    lastTypes = Some(newTypes)

    // 5. Return diagnostics
    extractDiagnostics(newTypes)
  }
}
```

For a simpler approach, implement debouncing:

```scala
// Debounce analysis by 100-300ms
def debouncedAnalysis(document: DocumentState): IO[List[Diagnostic]] = {
  debouncer.debounce(document.uri, 200.millis) {
    fullAnalysis(document)
  }
}
```

#### 4.2 No AST Caching Between Features

Completion, hover, and diagnostics all re-parse:

**Recommendation:** Cache parsed AST per document version:

```scala
case class DocumentCache(
  version: Int,
  ast: Option[Program],
  typedAst: Option[TypedProgram],
  diagnostics: List[Diagnostic]
)

class CachingLSP {
  private val cache = Ref.unsafe[IO, Map[String, DocumentCache]](Map.empty)

  def getAst(uri: String, version: Int): IO[Option[Program]] = {
    cache.get.map(_.get(uri).filter(_.version == version).flatMap(_.ast))
  }
}
```

#### 4.3 Completion Filtering is Linear

All modules are iterated for each completion request:

```scala
// Line 937-969
modules.filter(_.name.toLowerCase.startsWith(prefix.toLowerCase))
```

**Impact:** Slow for large module registries.

**Recommendation:** Use prefix tree (trie) for completion:

```scala
class CompletionTrie {
  private val root = new TrieNode()

  def insert(item: CompletionItem): Unit = {
    // Insert into trie by label
  }

  def findByPrefix(prefix: String): List[CompletionItem] = {
    // O(prefix.length + results) instead of O(all items)
  }
}
```

#### 4.4 No Semantic Tokens

LSP supports semantic highlighting, but it's not implemented:

**Recommendation:** Add semantic token provider:

```scala
// Semantic token types
enum SemanticTokenType:
  case Keyword, Function, Variable, Type, Parameter, Operator, String, Number, Comment

def computeSemanticTokens(document: DocumentState): List[SemanticToken] = {
  val ast = parse(document.text)
  ast.declarations.flatMap {
    case TypeDef(name, _) => List(SemanticToken(name.span, SemanticTokenType.Type))
    case InputDecl(name, typeExpr, _) =>
      List(
        SemanticToken(name.span, SemanticTokenType.Variable),
        computeTypeTokens(typeExpr)
      )
    // ... etc
  }
}
```

This enables richer syntax highlighting based on semantic analysis.

---

## 5. Type System Enhancements

### Current Implementation

**Compile-Time Types:** `SemanticType` (SString, SInt, SFloat, SBoolean, SRecord, SList, SOptional, SFunction, SUnion)

**Runtime Types:** `CType` (mirrors SemanticType for runtime representation)

**Features:**
- Structural typing for records
- Union types (`A | B`)
- Type merge algebra (`A + B`)
- Optional wrapping via guard expressions

**Weaknesses Identified:**

#### 5.1 No Subtyping

Types are compared for exact equality:

```scala
// TypeChecker.scala
if (actualType != expectedType) {
  TypeMismatch(expected, actual, span)
}
```

**Impact:** `SNothing` can't be used as a general bottom type; no variance.

**Recommendation:** Implement subtyping lattice:

```scala
object Subtyping {
  def isSubtype(sub: SemanticType, sup: SemanticType): Boolean = (sub, sup) match {
    case (_, _) if sub == sup => true
    case (SNothing, _) => true  // Nothing is subtype of everything
    case (_, SAny) => true      // Everything is subtype of Any
    case (SList(subElem), SList(supElem)) => isSubtype(subElem, supElem)  // Covariance
    case (SRecord(subFields), SRecord(supFields)) =>
      // Width subtyping: sub has all fields of sup
      supFields.forall { case (name, supType) =>
        subFields.get(name).exists(subType => isSubtype(subType, supType))
      }
    case (SUnion(subMembers), _) =>
      // All union members must be subtypes
      subMembers.forall(m => isSubtype(m, sup))
    case (_, SUnion(supMembers)) =>
      // Sub must be subtype of at least one member
      supMembers.exists(m => isSubtype(sub, m))
    case (SOptional(subInner), SOptional(supInner)) =>
      isSubtype(subInner, supInner)
    case _ => false
  }
}
```

#### 5.2 Limited Type Inference

Type inference requires explicit type annotations in many cases:

```scala
// Lambda parameter types must be inferred from context
items.filter((x) => x > 5)  // Works only if items type is known
```

**Recommendation:** Implement bidirectional type inference:

```scala
// Bidirectional type checking
def check(expr: Expression, expected: SemanticType): TypeResult[TypedExpression]
def infer(expr: Expression): TypeResult[(TypedExpression, SemanticType)]

// Example: lambda checking
def checkLambda(lambda: Lambda, expected: SFunction): TypeResult[TypedLambda] = {
  // Use expected.paramTypes to bind parameter types
  val paramBindings = lambda.params.zip(expected.paramTypes).map {
    case (param, expectedType) => param.name -> expectedType
  }
  // Check body against expected.returnType
  check(lambda.body, expected.returnType).map { typedBody =>
    TypedLambda(paramBindings, typedBody, expected)
  }
}
```

#### 5.3 No Effect Tracking

All modules can have side effects, but this isn't tracked:

**Recommendation:** Add effect annotations:

```scala
enum Effect:
  case Pure       // No side effects
  case IO         // File/network I/O
  case State      // Mutable state
  case Exception  // May throw

case class EffectfulType(
  returnType: SemanticType,
  effects: Set[Effect]
)

// ModuleBuilder with effect annotation
.implementationPure[I, O] { ... }  // Effect = Pure
.implementation[I, O] { ... }      // Effect = IO
```

This enables:
- Compile-time purity checking
- Optimization of pure expressions (memoization, reordering)
- Better error messages for effect mismatches

#### 5.4 No Row Polymorphism

Records require exact field matching:

```scala
// This doesn't work:
def processUser(user: { name: String }) = ...
processUser({ name: "Alice", age: 30 })  // Error: extra field 'age'
```

**Recommendation:** Implement row polymorphism:

```scala
// Row variable represents "rest of the fields"
case class SRecordOpen(
  knownFields: Map[String, SemanticType],
  rowVar: Option[RowVariable]  // None = closed, Some = open
)

// Type inference unifies row variables
def unifyRecords(r1: SRecordOpen, r2: SRecordOpen): Option[Substitution] = {
  // Unify known fields, propagate row variables
}
```

---

## 6. General Architecture Recommendations

### 6.1 Plugin System for Extensions

Currently, adding new features requires modifying core modules.

**Recommendation:** Implement plugin architecture:

```scala
trait CompilerPlugin {
  def transformAst(ast: Program): Program = ast
  def transformIR(ir: IRProgram): IRProgram = ir
  def transformDag(dag: DagSpec): DagSpec = dag
}

trait RuntimePlugin {
  def beforeExecution(dag: DagSpec, inputs: Map[String, CValue]): IO[Unit]
  def afterExecution(dag: DagSpec, outputs: Map[String, CValue]): IO[Unit]
}

// Plugin registration
val compiler = LangCompiler.builder()
  .withPlugin(LoggingPlugin)
  .withPlugin(ProfilingPlugin)
  .withPlugin(CachingPlugin)
  .build()
```

### 6.2 Observability Infrastructure

Limited visibility into execution internals.

**Recommendation:** Add structured telemetry:

```scala
trait Telemetry {
  def recordCompilation(dagName: String, duration: Duration, success: Boolean): IO[Unit]
  def recordModuleExecution(moduleId: UUID, duration: Duration, status: Status): IO[Unit]
  def recordDagExecution(dagName: String, duration: Duration, outputs: Int): IO[Unit]
}

// OpenTelemetry integration
class OTelTelemetry(tracer: Tracer) extends Telemetry {
  def recordModuleExecution(...) = {
    tracer.spanBuilder("module.execute")
      .setAttribute("module.id", moduleId.toString)
      .setAttribute("module.duration_ms", duration.toMillis)
      .startSpan()
      // ...
  }
}
```

### 6.3 Version Compatibility

Module signatures can change, breaking pipelines.

**Recommendation:** Add version constraints:

```scala
// In constellation-lang
use stdlib.math@^1.0  // Compatible with 1.x
use mylib.transform@=2.3.1  // Exact version

// Version checking during compilation
def checkVersionConstraint(
  required: VersionConstraint,
  available: Version
): Either[VersionError, Unit]
```

### 6.4 Better Error Messages

Current errors are functional but could be more helpful.

**Recommendation:** Implement error explanation system:

```scala
trait ErrorExplanation {
  def code: String        // E001, E002, etc.
  def title: String       // Short title
  def explanation: String // Detailed explanation
  def suggestions: List[String]  // How to fix
  def relatedDocs: List[String]  // Links to documentation
}

// Example
object UndefinedVariableError extends ErrorExplanation {
  def code = "E001"
  def title = "Undefined variable"
  def explanation = """
    The variable '${name}' is used but was never declared.
    Variables must be declared before use, either as:
    - An input: `in ${name}: Type`
    - An assignment: `${name} = SomeModule(...)`
  """
  def suggestions = List(
    s"Did you mean '${similarNames.head}'?",
    s"Add an input declaration: in ${name}: String"
  )
}
```

### 6.5 Documentation Generation

No automated documentation from module definitions.

**Recommendation:** Generate docs from signatures:

```scala
object DocGenerator {
  def generateMarkdown(modules: List[Module.Uninitialized]): String = {
    modules.map { module =>
      s"""
      ## ${module.spec.name}

      ${module.spec.description}

      **Version:** ${module.spec.version}

      **Parameters:**
      ${module.spec.consumes.map { case (name, ctype) =>
        s"- `$name`: ${formatType(ctype)}"
      }.mkString("\n")}

      **Returns:**
      ${module.spec.produces.map { case (name, ctype) =>
        s"- `$name`: ${formatType(ctype)}"
      }.mkString("\n")}
      """
    }.mkString("\n---\n")
  }
}
```

---

## 7. Implementation Roadmap

### Phase 1: Quick Wins (1-2 weeks)

| Task | Impact | Effort |
|------|--------|--------|
| Add compilation caching | High | Low |
| Implement debounced LSP analysis | Medium | Low |
| Add completion trie | Low | Low |
| Improve error messages | Medium | Medium |

### Phase 2: Core Improvements (2-4 weeks)

| Task | Impact | Effort |
|------|--------|--------|
| Add IR optimization passes | High | Medium |
| Implement parser memoization | High | Medium |
| Add semantic tokens to LSP | Medium | Medium |
| Implement subtyping | Medium | Medium |

### Phase 3: Advanced Features (4-8 weeks)

| Task | Impact | Effort |
|------|--------|--------|
| Incremental compilation | High | High |
| Work-stealing scheduler | Medium | High |
| Plugin architecture | Medium | High |
| Bidirectional type inference | Medium | High |

### Phase 4: Long-term Vision (8+ weeks)

| Task | Impact | Effort |
|------|--------|--------|
| Row polymorphism | Medium | Very High |
| Effect system | Medium | Very High |
| Full incremental LSP | High | Very High |
| GraalVM native image | High | High |

---

## 8. Conclusion

Constellation Engine has a solid architectural foundation with clean separation between parsing, compilation, and runtime phases. The use of functional programming patterns (Cats Effect, ValidatedNel, parser combinators) provides good composability and error handling.

**Key Strengths:**
- Well-structured multi-module build
- Clean type system with good runtime representation
- Effective inline transform optimization
- Good test coverage

**Priority Improvements:**
1. **Compilation caching** - Immediate high-impact improvement
2. **Parser memoization** - Prevents worst-case performance
3. **IR optimization passes** - Unlocks runtime improvements
4. **Incremental LSP analysis** - Better developer experience
5. **Subtyping** - More flexible type system

The existing optimization documentation (`docs/dev/optimizations/`) provides excellent guidance for runtime improvements. This review focuses on compiler and language-level improvements that complement those efforts.

---

## Appendix A: Benchmarking Recommendations

To validate improvements, establish baselines for:

```scala
// Parsing benchmarks
val parseTime = benchmark(ConstellationParser.parse(largeProgram))

// Compilation benchmarks
val compileTime = benchmark(compiler.compile(source, "test"))

// Runtime benchmarks
val executeTime = benchmark(Runtime.run(dag, inputs, modules))

// LSP benchmarks
val completionTime = benchmark(lsp.handleCompletion(position))
```

Consider using JMH (Java Microbenchmark Harness) for accurate measurements.

## Appendix B: References

- [Packrat Parsing: Simple, Powerful, Lazy, Linear Time](https://pdos.csail.mit.edu/~baford/packrat/icfp02/)
- [Incremental Type-Checking for Type-Directed Editing](https://arxiv.org/abs/2001.05134)
- [Salsa: A Generic Framework for On-Demand, Incrementalized Computation](https://github.com/salsa-rs/salsa)
- [Work-Stealing Made Simple](https://dl.acm.org/doi/10.1145/1988915.1988933)
- [Bidirectional Typing](https://arxiv.org/abs/1908.05839)
