---
title: "Pipeline Lifecycle: Parse → Execute"
sidebar_position: 2
description: "Deep dive into how Constellation transforms text into executable DAGs"
---

# Pipeline Lifecycle: Parse → Execute

**Goal:** Understand the complete journey from source code to results, including what can go wrong at each stage.

## Overview

Constellation Engine transforms `.cst` source files into executable DAGs through a multi-stage pipeline:

```
┌─────────┐      ┌──────────┐      ┌─────────────┐      ┌───────────┐
│ Source  │─────▶│  Parser  │─────▶│ Type Checker│─────▶│    IR     │
│  Text   │      │   (AST)  │      │  (Typed AST)│      │ Generator │
└─────────┘      └──────────┘      └─────────────┘      └───────────┘
                                                                │
                                                                ▼
┌─────────┐      ┌──────────┐      ┌─────────────┐      ┌───────────┐
│ Results │◀─────│ Runtime  │◀─────│     DAG     │◀─────│    DAG    │
│ (CValue)│      │Execution │      │  Compiler   │      │  (IRPipeline)
└─────────┘      └──────────┘      └─────────────┘      └───────────┘
```

**Key insight:** Each stage transforms the representation while preserving semantics and accumulating error information.

---

## Stage 1: Parsing (Text → AST)

**Purpose:** Convert raw source text into a structured Abstract Syntax Tree (AST).

**Location:** `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala`

### What Happens

The parser uses **cats-parse** combinators to:

1. **Tokenize** source into keywords, identifiers, operators, literals
2. **Apply grammar rules** to build hierarchical structure
3. **Track source locations** (spans) for error reporting
4. **Validate syntax** (but NOT semantics)

### Input Example

```constellation
in text: String
cleaned = Trim(text)
result = Uppercase(cleaned)
out result
```

### Output (Simplified AST)

```scala
Pipeline(
  declarations = List(
    InputDecl(
      name = "text",
      typeExpr = Primitive("String"),
      span = Span(0, 15)
    ),
    Assignment(
      name = "cleaned",
      expr = FunctionCall(
        name = QualifiedName("Trim"),
        args = List(VarRef("text")),
        span = Span(16, 37)
      )
    ),
    Assignment(
      name = "result",
      expr = FunctionCall(
        name = QualifiedName("Uppercase"),
        args = List(VarRef("cleaned")),
        span = Span(38, 65)
      )
    ),
    OutputDecl(name = "result", span = Span(66, 76))
  ),
  outputs = List("result")
)
```

### Key Components

**Declarations:**
- `InputDecl` - Input variable declarations (`in x: Type`)
- `Assignment` - Variable assignments (`x = Expr`)
- `OutputDecl` - Output declarations (`out x`)
- `TypeDef` - Type aliases (`type MyType = {a: Int}`)
- `UseDecl` - Namespace imports (`use stdlib.math`)

**Expressions:**
- `VarRef` - Variable references (`x`)
- `FunctionCall` - Module invocations (`Trim(text)`)
- `Conditional` - If-then-else (`if (cond) x else y`)
- `Branch` - Multi-way branching (`branch { ... }`)
- `Match` - Pattern matching (`match x { ... }`)
- `Literal` - Constant values (`42`, `"hello"`, `true`)
- `ListLit` - List literals (`[1, 2, 3]`)
- `RecordLit` - Record literals (`{name: "Alice", age: 30}`)
- `Projection` - Field selection (`x[field1, field2]`)
- `FieldAccess` - Single field access (`x.field`)
- `Merge` - Record merging (`a + b`)
- `BoolBinary` - Boolean operations (`a and b`, `a or b`)
- `Not` - Boolean negation (`not x`)
- `Guard` - Conditional wrapping (`x when condition`)
- `Coalesce` - Optional unwrapping (`optional ?? fallback`)
- `Lambda` - Anonymous functions (`(x) => x + 1`)
- `StringInterpolation` - String templates (`"Hello, ${name}!"`)

**Patterns (for match expressions):**
- `Record` - Match record structure (`{field1, field2}`)
- `TypeTest` - Match by type (`is String`)
- `Wildcard` - Match anything (`_`)

**Type Expressions:**
- `Primitive` - Built-in types (`String`, `Int`, `Float`, `Boolean`)
- `TypeRef` - User-defined types (`MyType`)
- `Record` - Record types (`{name: String, age: Int}`)
- `Parameterized` - Generic types (`List<Int>`, `Optional<String>`)
- `Union` - Union types (`Int | String`)
- `TypeMerge` - Type merging (`{a: Int} + {b: String}`)

### What Can Go Wrong

**Syntax Errors:**
```scala
CompileError.ParseError(
  message = "Parse error: expected identifier, found '='",
  span = Some(Span(10, 11))
)
```

**Common Parse Errors:**

1. **Missing keyword:**
   ```constellation
   text: String  // ERROR: Missing 'in'
   ```
   Error: `expected 'in', 'out', or identifier`

2. **Invalid identifier:**
   ```constellation
   in 123-var: String  // ERROR: Identifiers can't start with digits
   ```
   Error: `expected identifier`

3. **Unmatched parentheses:**
   ```constellation
   result = Trim(text  // ERROR: Missing closing paren
   ```
   Error: `expected ')'`

4. **Reserved word as identifier:**
   ```constellation
   in if: String  // ERROR: 'if' is a keyword
   ```
   Error: `expected identifier, found reserved word`

5. **Invalid type syntax:**
   ```constellation
   in data: List  // ERROR: List requires type parameter
   ```
   Error: `expected '<'`

**Key insight:** Parser errors are **syntactic only** - they don't understand types or module signatures.

### Performance Characteristics

**Typical performance:**
- Small files (<100 lines): **<5ms**
- Medium files (100-500 lines): **5-50ms**
- Large files (>500 lines): **50-200ms**

**Optimization:** The parser uses **memoization** to avoid re-parsing the same subexpressions:

```scala
object ConstellationParser extends MemoizationSupport {
  // Cache intermediate parse results
  private val memoCache = ...

  def parse(source: String): Either[CompileError.ParseError, Pipeline] = {
    clearMemoCache() // Fresh parse
    pipeline.parseAll(source).left.map { ... }
  }
}
```

---

## Stage 2: Type Checking (AST → Typed AST)

**Purpose:** Verify type correctness and resolve all names to concrete types.

**Location:** `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala`

### What Happens

The type checker performs **bidirectional type inference**:

1. **Resolve type expressions** to `SemanticType`
2. **Build type environment** with input types
3. **Check each expression** bottom-up
4. **Infer types** where not explicitly annotated
5. **Validate module calls** against registered signatures
6. **Check pattern exhaustiveness** in match expressions
7. **Apply subtyping rules** for union types and optionals

### Type System

**Semantic Types:**

```scala
sealed trait SemanticType

object SemanticType {
  // Primitives
  case object SString extends SemanticType
  case object SInt extends SemanticType
  case object SFloat extends SemanticType
  case object SBoolean extends SemanticType

  // Composite types
  case class SRecord(fields: Map[String, SemanticType]) extends SemanticType
  case class SList(elementType: SemanticType) extends SemanticType
  case class SMap(keyType: SemanticType, valueType: SemanticType) extends SemanticType
  case class SOptional(innerType: SemanticType) extends SemanticType
  case class SUnion(variants: Set[SemanticType]) extends SemanticType
  case class SFunction(paramTypes: List[SemanticType], returnType: SemanticType) extends SemanticType

  // Special types
  case object SNothing extends SemanticType // Bottom type (empty list element type)
}
```

### Type Environment

The type checker maintains an environment with:

```scala
case class TypeEnvironment(
  types: Map[String, SemanticType],         // User-defined type aliases
  variables: Map[String, SemanticType],     // Variable bindings
  functions: FunctionRegistry,              // Module signatures
  namespaceScope: NamespaceScope            // Import scope
)
```

### Bidirectional Type Checking

**Checking mode** (type flows down):
```constellation
in text: String      # Environment: {text: String}
result = Trim(text)  # Check: text has type String (matches Trim's input)
```

**Inference mode** (type flows up):
```constellation
x = 42              # Infer: x has type Int
y = [1, 2, 3]       # Infer: y has type List<Int>
z = []              # Infer: z has type List<SNothing> (compatible with any List<T>)
```

**Lambda inference:**
```scala
# Without context, requires type annotations
f = (x: Int) => x + 1

# With context from function signature, types can be inferred
numbers = Map([1, 2, 3], (x) => x * 2)  # x inferred as Int
```

### Input Example (AST)

```scala
Pipeline(
  declarations = List(
    InputDecl(name = "text", typeExpr = Primitive("String")),
    Assignment(name = "result", expr = FunctionCall("Uppercase", List(VarRef("text")))),
    OutputDecl(name = "result")
  )
)
```

### Output (Typed AST)

```scala
TypedPipeline(
  declarations = List(
    TypedDeclaration.InputDecl(
      name = "text",
      semanticType = SemanticType.SString,
      span = Span(0, 15)
    ),
    TypedDeclaration.Assignment(
      name = "result",
      value = TypedExpression.FunctionCall(
        name = "Uppercase",
        signature = FunctionSignature(
          qualifiedName = "Uppercase",
          moduleName = "Uppercase",
          params = List("text" -> SemanticType.SString),
          returns = SemanticType.SString
        ),
        args = List(
          TypedExpression.VarRef("text", SemanticType.SString, span)
        ),
        options = ModuleCallOptions.empty,
        typedFallback = None,
        span = Span(...)
      ),
      span = Span(...)
    ),
    TypedDeclaration.OutputDecl(
      name = "result",
      semanticType = SemanticType.SString,
      span = Span(...)
    )
  ),
  outputs = List(("result", SemanticType.SString, Span(...))),
  warnings = List()
)
```

### What Can Go Wrong

**Type Errors:**

1. **Type mismatch:**
   ```constellation
   in age: Int
   result = Uppercase(age)  # ERROR: Uppercase expects String, got Int
   ```
   ```scala
   CompileError.TypeMismatch(
     expected = "String",
     actual = "Int",
     span = Some(Span(...))
   )
   ```

2. **Undefined variable:**
   ```constellation
   result = Uppercase(unknown)  # ERROR: 'unknown' not defined
   ```
   ```scala
   CompileError.UndefinedVariable(
     name = "unknown",
     span = Some(Span(...))
   )
   ```

3. **Undefined module:**
   ```constellation
   result = MissingModule(text)  # ERROR: Module not registered
   ```
   ```scala
   CompileError.UndefinedFunction(
     name = "MissingModule",
     span = Some(Span(...))
   )
   ```

4. **Wrong arity:**
   ```constellation
   result = Uppercase(text, extra)  # ERROR: Uppercase expects 1 argument, got 2
   ```
   ```scala
   CompileError.TypeError(
     message = "Function Uppercase expects 1 arguments, got 2",
     span = Some(Span(...))
   )
   ```

5. **Invalid field access:**
   ```constellation
   in user: {name: String}
   age = user.age  # ERROR: Field 'age' doesn't exist
   ```
   ```scala
   CompileError.InvalidFieldAccess(
     field = "age",
     available = List("name"),
     span = Some(Span(...))
   )
   ```

6. **Invalid projection:**
   ```constellation
   in user: {name: String}
   subset = user[name, age]  # ERROR: Field 'age' doesn't exist
   ```
   ```scala
   CompileError.InvalidProjection(
     field = "age",
   available = List("name"),
     span = Some(Span(...))
   )
   ```

7. **Incompatible merge:**
   ```constellation
   a = 42
   b = "hello"
   c = a + b  # ERROR: Can't merge Int and String
   ```
   ```scala
   CompileError.IncompatibleMerge(
     left = "Int",
     right = "String",
     span = Some(Span(...))
   )
   ```

8. **Non-exhaustive match:**
   ```constellation
   type Result = {code: Int} | {message: String}
   in data: Result
   result = match data {
     {code} -> "got code"
     # ERROR: Missing case for {message}
   }
   ```
   ```scala
   CompileError.NonExhaustiveMatch(
     uncovered = List("{message: String}"),
     span = Some(Span(...))
   )
   ```

**Warnings:**

Type checking may also produce warnings (non-fatal):

```scala
CompileWarning.UnusedVariable(
  name = "temp",
  span = Span(...)
)
```

### Subtyping Rules

The type system supports **width subtyping** for records:

```constellation
type Person = {name: String, age: Int}
type Named = {name: String}

in person: Person
# Valid: Person is a subtype of Named (has all required fields)
name = extractName(person)  # where extractName expects Named
```

**Union types** use structural matching:

```constellation
type Result = {code: Int} | {message: String}

# Pattern matching discriminates by structure
result = match data {
  {code} -> "error code"
  {message} -> "error message"
}
```

**Optional types** have special coalescing behavior:

```constellation
in maybeValue: Optional<Int>

# Unwrap with default
value = maybeValue ?? 0  # Type: Int

# Chain optionals
backup = maybeValue ?? anotherOptional  # Type: Optional<Int>
```

---

## Stage 3: IR Generation (Typed AST → IR)

**Purpose:** Convert typed AST into an intermediate representation suitable for DAG compilation.

**Location:** `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala`

### What Happens

The IR generator performs a **tree walk** over the typed AST:

1. **Assign unique IDs** to each expression
2. **Flatten expressions** into IR nodes
3. **Track variable bindings** (name → node ID)
4. **Extract module call options** (retry, timeout, etc.)
5. **Convert lambda bodies** for higher-order functions
6. **Build dependency graph** implicitly via node references

### IR Node Types

```scala
sealed trait IRNode {
  def id: UUID
}

object IRNode {
  // Input nodes (top-level data)
  case class Input(
    id: UUID,
    name: String,
    outputType: SemanticType,
    debugSpan: Option[Span]
  ) extends IRNode

  // Module calls
  case class ModuleCall(
    id: UUID,
    moduleName: String,           // "Uppercase"
    languageName: String,          // "Uppercase" (what appears in .cst)
    inputs: Map[String, UUID],     // Parameter name -> input node ID
    outputType: SemanticType,
    options: IRModuleCallOptions,  // retry, timeout, cache, etc.
    debugSpan: Option[Span]
  ) extends IRNode

  // Data transformations (no module needed)
  case class MergeNode(id: UUID, left: UUID, right: UUID, outputType: SemanticType, ...) extends IRNode
  case class ProjectNode(id: UUID, source: UUID, fields: List[String], outputType: SemanticType, ...) extends IRNode
  case class FieldAccessNode(id: UUID, source: UUID, field: String, outputType: SemanticType, ...) extends IRNode

  // Control flow
  case class ConditionalNode(id: UUID, condition: UUID, thenBranch: UUID, elseBranch: UUID, outputType: SemanticType, ...) extends IRNode
  case class BranchNode(id: UUID, cases: List[(UUID, UUID)], otherwise: UUID, resultType: SemanticType, ...) extends IRNode
  case class MatchNode(id: UUID, scrutinee: UUID, cases: List[MatchCaseIR], resultType: SemanticType, ...) extends IRNode

  // Boolean operations
  case class AndNode(id: UUID, left: UUID, right: UUID, ...) extends IRNode
  case class OrNode(id: UUID, left: UUID, right: UUID, ...) extends IRNode
  case class NotNode(id: UUID, operand: UUID, ...) extends IRNode

  // Optional operations
  case class GuardNode(id: UUID, expr: UUID, condition: UUID, innerType: SemanticType, ...) extends IRNode
  case class CoalesceNode(id: UUID, left: UUID, right: UUID, resultType: SemanticType, ...) extends IRNode

  // Higher-order functions
  case class HigherOrderNode(
    id: UUID,
    operation: HigherOrderOp,      // Map, Filter, All, Any, SortBy
    source: UUID,                   // List to operate on
    lambda: TypedLambda,            // Function to apply
    outputType: SemanticType,
    debugSpan: Option[Span]
  ) extends IRNode

  // Literals
  case class LiteralNode(id: UUID, value: Any, outputType: SemanticType, ...) extends IRNode
  case class ListLiteralNode(id: UUID, elements: List[UUID], elementType: SemanticType, ...) extends IRNode
  case class RecordLitNode(id: UUID, fields: List[(String, UUID)], outputType: SemanticType, ...) extends IRNode
  case class StringInterpolationNode(id: UUID, parts: List[String], expressions: List[UUID], ...) extends IRNode
}
```

### IR Pipeline

```scala
case class IRPipeline(
  nodes: Map[UUID, IRNode],              // All IR nodes
  inputs: List[UUID],                     // IDs of Input nodes
  declaredOutputs: List[String],          // Output variable names
  variableBindings: Map[String, UUID],    // Variable name -> node ID
  topologicalOrder: List[UUID]            // Execution order (computed lazily)
)
```

### Module Call Options

```scala
case class IRModuleCallOptions(
  retry: Option[Int] = None,                           // Retry attempts
  timeout: Option[Duration] = None,                    // Per-module timeout
  delay: Option[Duration] = None,                      // Initial retry delay
  backoff: Option[BackoffStrategy] = None,             // exponential, linear, fixed
  fallback: Option[UUID] = None,                       // Fallback expression node ID
  cache: Option[Duration] = None,                      // Cache TTL
  cacheBackend: Option[String] = None,                 // "redis", "memory"
  throttle: Option[Rate] = None,                       // 100/1min
  concurrency: Option[Int] = None,                     // Max concurrent calls
  onError: Option[ErrorStrategy] = None,               // propagate, skip, log, wrap
  lazyEval: Option[Boolean] = None,                    // Defer execution
  priority: Option[Either[PriorityLevel, CustomPriority]] = None  // high, low, 75
)
```

### Input Example (Typed AST)

```scala
TypedDeclaration.Assignment(
  name = "result",
  value = TypedExpression.FunctionCall(
    name = "Uppercase",
    signature = ...,
    args = List(TypedExpression.VarRef("text", SString, ...)),
    options = ModuleCallOptions.empty,
    span = ...
  )
)
```

### Output (IR)

```scala
IRPipeline(
  nodes = Map(
    uuid1 -> IRNode.Input(
      id = uuid1,
      name = "text",
      outputType = SemanticType.SString,
      debugSpan = Some(Span(...))
    ),
    uuid2 -> IRNode.ModuleCall(
      id = uuid2,
      moduleName = "Uppercase",
      languageName = "Uppercase",
      inputs = Map("text" -> uuid1),
      outputType = SemanticType.SString,
      options = IRModuleCallOptions.empty,
      debugSpan = Some(Span(...))
    )
  ),
  inputs = List(uuid1),
  declaredOutputs = List("result"),
  variableBindings = Map("text" -> uuid1, "result" -> uuid2),
  topologicalOrder = List(uuid1, uuid2)
)
```

### What Can Go Wrong

**IR generation is typically safe** because it operates on type-checked AST. However, certain advanced features can fail:

1. **Unsupported higher-order operation:**
   ```scala
   CompilerError.UnsupportedOperation("SortBy")
   ```

2. **Malformed lambda body:**
   ```scala
   CompilerError.UnsupportedNodeType(
     nodeType = "ComplexExpression",
     context = "lambda body"
   )
   ```

**Key insight:** If type checking succeeds, IR generation almost always succeeds.

---

## Stage 4: DAG Compilation (IR → DAG Spec)

**Purpose:** Convert IR into an executable DAG with concrete module and data nodes.

**Location:** `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala`

### What Happens

The DAG compiler performs **topological processing**:

1. **Process IR nodes in dependency order**
2. **Create module node specs** for each `ModuleCall`
3. **Create data node specs** for inputs, outputs, intermediates
4. **Build edge sets** (in-edges, out-edges)
5. **Generate synthetic modules** for control flow (branch, match)
6. **Create inline transforms** for data operations (merge, projection)
7. **Track module options** for runtime execution

### DAG Structure

```scala
case class DagSpec(
  metadata: ComponentMetadata,
  modules: Map[UUID, ModuleNodeSpec],        // Module nodes
  data: Map[UUID, DataNodeSpec],             // Data nodes
  inEdges: Set[(UUID, UUID)],                // (data UUID, module UUID)
  outEdges: Set[(UUID, UUID)],               // (module UUID, data UUID)
  declaredOutputs: List[String],             // Output variable names
  outputBindings: Map[String, UUID]          // Output name -> data UUID
)
```

**Module Node:**
```scala
case class ModuleNodeSpec(
  metadata: ComponentMetadata,
  consumes: Map[String, CType],              // Input name -> type
  produces: Map[String, CType],              // Output name -> type
  config: ModuleConfig = ModuleConfig.default
)
```

**Data Node:**
```scala
case class DataNodeSpec(
  name: String,
  nicknames: Map[UUID, String],              // Module UUID -> parameter name
  cType: CType,
  inlineTransform: Option[InlineTransform] = None,
  transformInputs: Map[String, UUID] = Map.empty
)
```

### Inline Transforms

For operations that don't require separate modules, the compiler generates **inline transforms**:

```scala
sealed trait InlineTransform {
  def apply(inputs: Map[String, Any]): Any
}

object InlineTransform {
  // Data operations
  case class MergeTransform(leftType: CType, rightType: CType) extends InlineTransform
  case class ProjectTransform(fields: List[String], sourceType: CType) extends InlineTransform
  case class FieldAccessTransform(field: String, sourceType: CType) extends InlineTransform

  // Control flow
  case object ConditionalTransform extends InlineTransform  // if-then-else

  // Boolean operations
  case object AndTransform extends InlineTransform
  case object OrTransform extends InlineTransform
  case object NotTransform extends InlineTransform

  // Optional operations
  case object GuardTransform extends InlineTransform        // expr when cond
  case object CoalesceTransform extends InlineTransform     // left ?? right

  // Literals
  case class LiteralTransform(value: Any) extends InlineTransform
  case class ListLiteralTransform(size: Int) extends InlineTransform
  case class RecordBuildTransform(fieldNames: List[String]) extends InlineTransform
  case class StringInterpolationTransform(parts: List[String]) extends InlineTransform

  // Pattern matching
  case class MatchTransform(
    patternMatchers: List[Any => Boolean],   // Pattern test functions
    bodyEvaluators: List[Any => Any],        // Body evaluation functions
    scrutineeType: CType
  ) extends InlineTransform

  // Higher-order operations
  case class MapTransform(f: Any => Any) extends InlineTransform
  case class FilterTransform(predicate: Any => Boolean) extends InlineTransform
  case class AllTransform(predicate: Any => Boolean) extends InlineTransform
  case class AnyTransform(predicate: Any => Boolean) extends InlineTransform
}
```

### Type Conversion

The DAG compiler converts `SemanticType` to runtime `CType`:

```scala
object SemanticType {
  def toCType(semType: SemanticType): CType = semType match {
    case SString => CType.CString
    case SInt => CType.CInt
    case SFloat => CType.CFloat
    case SBoolean => CType.CBoolean
    case SList(elemType) => CType.CList(toCType(elemType))
    case SMap(keyType, valueType) => CType.CMap(toCType(keyType), toCType(valueType))
    case SRecord(fields) => CType.CProduct(fields.map { case (n, t) => n -> toCType(t) })
    case SOptional(innerType) => CType.COptional(toCType(innerType))
    case SUnion(variants) =>
      // Convert union to CUnion with variant tags
      val variantMap = variants.zipWithIndex.map { case (t, i) => s"variant$i" -> toCType(t) }.toMap
      CType.CUnion(variantMap)
    case SNothing => CType.CString // Placeholder (should not appear at runtime)
    case SFunction(_, _) => CType.CString // Functions not supported at runtime
  }
}
```

### Synthetic Modules

**Branch Module:**

Generated for multi-way conditionals:

```constellation
result = branch {
  age < 13 -> "child",
  age < 20 -> "teen",
  otherwise -> "adult"
}
```

Creates a synthetic module that:
1. Evaluates conditions in order
2. Returns first matching expression
3. Falls back to `otherwise` if none match

**Match Module (deprecated):**

Original approach for pattern matching (now uses inline transforms):

```constellation
type Result = {code: Int} | {message: String}
in data: Result
result = match data {
  {code} -> "error code",
  {message} -> "error message"
}
```

Creates a synthetic module that:
1. Tests each pattern against scrutinee
2. Extracts bindings from matched pattern
3. Evaluates corresponding body expression

### Input Example (IR)

```scala
IRPipeline(
  nodes = Map(
    uuid1 -> IRNode.Input("text", SString, ...),
    uuid2 -> IRNode.ModuleCall(
      moduleName = "Uppercase",
      inputs = Map("text" -> uuid1),
      outputType = SString,
      ...
    )
  ),
  variableBindings = Map("text" -> uuid1, "result" -> uuid2)
)
```

### Output (DAG Spec)

```scala
DagSpec(
  metadata = ComponentMetadata.empty("example"),
  modules = Map(
    moduleUuid -> ModuleNodeSpec(
      metadata = ComponentMetadata(name = "example.Uppercase", ...),
      consumes = Map("text" -> CType.CString),
      produces = Map("out" -> CType.CString),
      config = ModuleConfig.default
    )
  ),
  data = Map(
    dataUuid1 -> DataNodeSpec(
      name = "text",
      nicknames = Map(dataUuid1 -> "text", moduleUuid -> "text"),
      cType = CType.CString
    ),
    dataUuid2 -> DataNodeSpec(
      name = "Uppercase_output",
      nicknames = Map(moduleUuid -> "out"),
      cType = CType.CString
    )
  ),
  inEdges = Set((dataUuid1, moduleUuid)),
  outEdges = Set((moduleUuid, dataUuid2)),
  declaredOutputs = List("result"),
  outputBindings = Map("result" -> dataUuid2)
)
```

### What Can Go Wrong

**DAG compilation errors are rare** if earlier stages succeed:

1. **Node not found:**
   ```scala
   DagCompilerError.NodeNotFound(
     nodeId = uuid,
     context = "input to module Uppercase"
   )
   ```

2. **Unsupported operation:**
   ```scala
   DagCompilerError.UnsupportedOperation("SortBy")
   ```

3. **Unsupported node type in lambda:**
   ```scala
   DagCompilerError.UnsupportedNodeType(
     nodeType = "HigherOrderNode",
     context = "lambda body"
   )
   ```

4. **Unsupported function in lambda:**
   ```scala
   DagCompilerError.UnsupportedFunction(
     moduleName = "CustomModule",
     funcName = "CustomModule"
   )
   ```

**Key insight:** DAG compilation failures usually indicate internal bugs, not user errors.

---

## Stage 5: Runtime Execution (DAG → Results)

**Purpose:** Execute the DAG by running modules and propagating data.

**Location:** `modules/runtime/src/main/scala/io/constellation/Runtime.scala`

### What Happens

The runtime performs **asynchronous parallel execution**:

1. **Initialize modules** (create deferreds for data flow)
2. **Create runtime state** (track module status, data)
3. **Complete input data nodes** with user-provided values
4. **Start inline transform fibers** (compute derived values)
5. **Execute modules in parallel** (respecting dependencies via deferreds)
6. **Collect outputs** from final data nodes
7. **Return execution state** with results and metadata

### Execution Model

**Deferred-based data flow:**

Each data node has a `Deferred[IO, Any]` that:
- **Blocks readers** until value is available
- **Completes exactly once** with the data
- **Propagates automatically** to dependent modules

**Parallel execution:**

Modules run as **concurrent fibers**:
```scala
runnable.parTraverse { module =>
  scheduler.submit(priority, module.run(runtime))
}
```

Dependencies are enforced by **waiting on deferreds**:
```scala
for {
  inputValue <- runtime.getTableData(inputDataId)  // Waits for deferred
  result = processInput(inputValue)
  _ <- runtime.setTableData(outputDataId, result)  // Completes deferred
} yield ()
```

### Priority Scheduling

The runtime supports **priority-based scheduling**:

```scala
case class GlobalScheduler(
  enabled: Boolean,
  maxConcurrency: Int,
  starvationTimeout: Duration
)
```

**Module priorities:**
- **Critical:** 90-100 (health checks, user-facing)
- **High:** 70-89 (important background tasks)
- **Normal:** 40-69 (default)
- **Low:** 10-39 (analytics, logging)
- **Background:** 0-9 (cleanup, maintenance)

Modules can specify priority via options:
```constellation
result = SlowQuery(input) with priority: high
```

### Module Execution

**Module lifecycle:**

1. **Unfired:** Initial state
2. **Awaiting inputs:** Blocked on deferred data
3. **Running:** Executing user code
4. **Fired:** Completed successfully
5. **Failed:** Exception thrown
6. **Timed:** Timeout exceeded

**Module status:**
```scala
sealed trait Status
case object Unfired extends Status
case class Fired(latency: FiniteDuration, context: Option[Map[String, Json]]) extends Status
case class Timed(latency: FiniteDuration) extends Status
case class Failed(error: Throwable) extends Status
```

### Inline Transform Execution

Inline transforms run as **parallel fibers** alongside modules:

```scala
private def computeInlineTransform(
  dataId: UUID,
  spec: DataNodeSpec,
  runtime: Runtime
): IO[Unit] = spec.inlineTransform match {
  case Some(transform) =>
    for {
      // Wait for all input values (blocks until ready)
      inputValues <- spec.transformInputs.toList.traverse { case (name, inputDataId) =>
        runtime.getTableData(inputDataId).map(name -> _)
      }
      inputMap = inputValues.toMap

      // Apply the transform (pure function)
      result = transform.apply(inputMap)

      // Complete the output deferred
      _ <- runtime.setTableData(dataId, result)

      // Store in state for output retrieval
      cValue = anyToCValue(result, spec.cType)
      _ <- runtime.setStateData(dataId, cValue)
    } yield ()
}
```

### Input Example (DAG Spec + Data)

**DAG:**
```scala
DagSpec(
  modules = Map(moduleUuid -> ModuleNodeSpec(...)),
  data = Map(
    inputDataUuid -> DataNodeSpec("text", ...),
    outputDataUuid -> DataNodeSpec("Uppercase_output", ...)
  ),
  inEdges = Set((inputDataUuid, moduleUuid)),
  outEdges = Set((moduleUuid, outputDataUuid)),
  outputBindings = Map("result" -> outputDataUuid)
)
```

**Input data:**
```scala
Map("text" -> CValue.CString("hello world"))
```

### Output (Runtime State)

```scala
Runtime.State(
  processUuid = UUID(...),
  dag = dagSpec,
  moduleStatus = Map(
    moduleUuid -> Eval.later(Module.Status.Fired(
      latency = 5.milliseconds,
      context = None
    ))
  ),
  data = Map(
    inputDataUuid -> Eval.later(CValue.CString("hello world")),
    outputDataUuid -> Eval.later(CValue.CString("HELLO WORLD"))
  ),
  latency = Some(12.milliseconds)
)
```

### Extracting Results

After execution, results are extracted by output name:

```scala
val state: Runtime.State = ...

// Extract output by name
val resultCValue = state.outputBindings.get("result")
  .flatMap(dataId => state.data.get(dataId))
  .map(_.value)  // Force Eval to get CValue

// Convert to domain type
val result: String = resultCValue match {
  case CValue.CString(value) => value
  case _ => throw new RuntimeException("Unexpected type")
}
```

### What Can Go Wrong

**Runtime errors:**

1. **Module failure:**
   ```scala
   Module.Status.Failed(
     error = new RuntimeException("Database connection failed")
   )
   ```

2. **Timeout:**
   ```scala
   Module.Status.Timed(latency = 30.seconds)
   ```

3. **Missing input:**
   ```scala
   RuntimeException("Input 'text' was unexpected, input name might be misspelled.")
   ```

4. **Type mismatch:**
   ```scala
   RuntimeException("Input 'text' had different type, expected 'CString' but was 'CInt'.")
   ```

5. **Module not registered:**
   ```scala
   RuntimeException("Module 'Uppercase' not found in registry")
   ```

**Execution modes:**

The runtime provides several execution variants:

- **`run()`** - Basic execution with unbounded parallelism
- **`runWithScheduler()`** - Priority-based scheduling with bounded concurrency
- **`runWithBackends()`** - Adds metrics, tracing, circuit breakers
- **`runCancellable()`** - Returns handle for cancellation
- **`runWithTimeout()`** - Global timeout with automatic cancellation
- **`runPooled()`** - Object pooling for reduced GC pressure

---

## Complete Example: Step-by-Step

Let's trace a complete example through all stages.

### Source Code

```constellation
in firstName: String
in lastName: String

fullName = Concat(firstName, lastName)
normalized = Trim(fullName)
result = Uppercase(normalized)

out result
```

### Stage 1: Parse

```scala
Pipeline(
  declarations = List(
    InputDecl(Located("firstName", Span(3, 12)), Located(Primitive("String"), Span(14, 20))),
    InputDecl(Located("lastName", Span(24, 32)), Located(Primitive("String"), Span(34, 40))),
    Assignment(
      Located("fullName", Span(42, 50)),
      Located(
        FunctionCall(
          QualifiedName("Concat"),
          List(
            Located(VarRef("firstName"), Span(60, 69)),
            Located(VarRef("lastName"), Span(71, 79))
          ),
          ModuleCallOptions.empty
        ),
        Span(53, 80)
      )
    ),
    Assignment(
      Located("normalized", Span(81, 91)),
      Located(
        FunctionCall(
          QualifiedName("Trim"),
          List(Located(VarRef("fullName"), Span(99, 107))),
          ModuleCallOptions.empty
        ),
        Span(94, 108)
      )
    ),
    Assignment(
      Located("result", Span(109, 115)),
      Located(
        FunctionCall(
          QualifiedName("Uppercase"),
          List(Located(VarRef("normalized"), Span(128, 138))),
          ModuleCallOptions.empty
        ),
        Span(118, 139)
      )
    ),
    OutputDecl(Located("result", Span(145, 151)))
  ),
  outputs = List("result")
)
```

### Stage 2: Type Check

```scala
TypedPipeline(
  declarations = List(
    TypedDeclaration.InputDecl("firstName", SString, Span(3, 20)),
    TypedDeclaration.InputDecl("lastName", SString, Span(24, 40)),
    TypedDeclaration.Assignment(
      "fullName",
      TypedExpression.FunctionCall(
        "Concat",
        FunctionSignature("Concat", "Concat", List("a" -> SString, "b" -> SString), SString),
        List(
          TypedExpression.VarRef("firstName", SString, Span(60, 69)),
          TypedExpression.VarRef("lastName", SString, Span(71, 79))
        ),
        ModuleCallOptions.empty,
        None,
        Span(53, 80)
      ),
      Span(42, 80)
    ),
    TypedDeclaration.Assignment(
      "normalized",
      TypedExpression.FunctionCall(
        "Trim",
        FunctionSignature("Trim", "Trim", List("text" -> SString), SString),
        List(TypedExpression.VarRef("fullName", SString, Span(99, 107))),
        ModuleCallOptions.empty,
        None,
        Span(94, 108)
      ),
      Span(81, 108)
    ),
    TypedDeclaration.Assignment(
      "result",
      TypedExpression.FunctionCall(
        "Uppercase",
        FunctionSignature("Uppercase", "Uppercase", List("text" -> SString), SString),
        List(TypedExpression.VarRef("normalized", SString, Span(128, 138))),
        ModuleCallOptions.empty,
        None,
        Span(118, 139)
      ),
      Span(109, 139)
    ),
    TypedDeclaration.OutputDecl("result", SString, Span(145, 151))
  ),
  outputs = List(("result", SString, Span(145, 151))),
  warnings = List()
)
```

### Stage 3: IR Generation

```scala
IRPipeline(
  nodes = Map(
    id1 -> IRNode.Input(id1, "firstName", SString, Some(Span(3, 20))),
    id2 -> IRNode.Input(id2, "lastName", SString, Some(Span(24, 40))),
    id3 -> IRNode.ModuleCall(
      id3,
      "Concat",
      "Concat",
      Map("a" -> id1, "b" -> id2),
      SString,
      IRModuleCallOptions.empty,
      Some(Span(53, 80))
    ),
    id4 -> IRNode.ModuleCall(
      id4,
      "Trim",
      "Trim",
      Map("text" -> id3),
      SString,
      IRModuleCallOptions.empty,
      Some(Span(94, 108))
    ),
    id5 -> IRNode.ModuleCall(
      id5,
      "Uppercase",
      "Uppercase",
      Map("text" -> id4),
      SString,
      IRModuleCallOptions.empty,
      Some(Span(118, 139))
    )
  ),
  inputs = List(id1, id2),
  declaredOutputs = List("result"),
  variableBindings = Map(
    "firstName" -> id1,
    "lastName" -> id2,
    "fullName" -> id3,
    "normalized" -> id4,
    "result" -> id5
  ),
  topologicalOrder = List(id1, id2, id3, id4, id5)
)
```

### Stage 4: DAG Compilation

```scala
DagSpec(
  metadata = ComponentMetadata.empty("example"),
  modules = Map(
    m1 -> ModuleNodeSpec(
      metadata = ComponentMetadata(name = "example.Concat", version = "1.0"),
      consumes = Map("a" -> CString, "b" -> CString),
      produces = Map("out" -> CString)
    ),
    m2 -> ModuleNodeSpec(
      metadata = ComponentMetadata(name = "example.Trim", version = "1.0"),
      consumes = Map("text" -> CString),
      produces = Map("out" -> CString)
    ),
    m3 -> ModuleNodeSpec(
      metadata = ComponentMetadata(name = "example.Uppercase", version = "1.0"),
      consumes = Map("text" -> CString),
      produces = Map("out" -> CString)
    )
  ),
  data = Map(
    d1 -> DataNodeSpec("firstName", Map(d1 -> "firstName", m1 -> "a"), CString),
    d2 -> DataNodeSpec("lastName", Map(d2 -> "lastName", m1 -> "b"), CString),
    d3 -> DataNodeSpec("Concat_output", Map(m1 -> "out", m2 -> "text"), CString),
    d4 -> DataNodeSpec("Trim_output", Map(m2 -> "out", m3 -> "text"), CString),
    d5 -> DataNodeSpec("Uppercase_output", Map(m3 -> "out"), CString)
  ),
  inEdges = Set((d1, m1), (d2, m1), (d3, m2), (d4, m3)),
  outEdges = Set((m1, d3), (m2, d4), (m3, d5)),
  declaredOutputs = List("result"),
  outputBindings = Map("result" -> d5)
)
```

### Stage 5: Runtime Execution

**Input:**
```scala
Map(
  "firstName" -> CValue.CString("  john "),
  "lastName" -> CValue.CString(" doe  ")
)
```

**Execution trace:**

```
Time  Module      Input                          Output
----  ----------  -----------------------------  -------------------
0ms   -           firstName = "  john "          -
0ms   -           lastName = " doe  "            -
1ms   Concat      a = "  john ", b = " doe  "    "  john  doe  "
3ms   Trim        text = "  john  doe  "         "john  doe"
5ms   Uppercase   text = "john  doe"             "JOHN  DOE"
```

**Final state:**
```scala
Runtime.State(
  processUuid = UUID(...),
  dag = dagSpec,
  moduleStatus = Map(
    m1 -> Eval.later(Fired(1.ms, None)),
    m2 -> Eval.later(Fired(2.ms, None)),
    m3 -> Eval.later(Fired(2.ms, None))
  ),
  data = Map(
    d1 -> Eval.later(CString("  john ")),
    d2 -> Eval.later(CString(" doe  ")),
    d3 -> Eval.later(CString("  john  doe  ")),
    d4 -> Eval.later(CString("john  doe")),
    d5 -> Eval.later(CString("JOHN  DOE"))
  ),
  latency = Some(5.ms)
)
```

**Output:**
```scala
Map("result" -> CValue.CString("JOHN  DOE"))
```

---

## Error Detection Summary

| Stage | What It Checks | Example Errors |
|-------|----------------|----------------|
| **Parse** | Syntax validity | Missing keywords, unmatched parens, invalid tokens |
| **Type Check** | Type correctness, name resolution | Type mismatch, undefined variable, wrong arity |
| **IR Gen** | AST well-formedness | Unsupported lambda constructs |
| **DAG Compile** | IR node validity | Missing node references, unsupported operations |
| **Runtime** | Module availability, data validity | Module not found, timeout, runtime exception |

**Progressive refinement:** Each stage catches increasingly subtle errors:

1. **Parse:** "Does it look like valid syntax?"
2. **Type Check:** "Do the types line up?"
3. **IR Gen:** "Can we represent this as IR?"
4. **DAG Compile:** "Can we build an executable DAG?"
5. **Runtime:** "Does it actually run?"

---

## Performance Characteristics

### Compilation Performance

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Parse (small) | <5ms | <100 lines |
| Parse (medium) | <100ms | 100-500 lines |
| Type check | <50ms | Most programs |
| IR generation | <10ms | Most programs |
| DAG compilation | <20ms | Most programs |
| **Total compile** | **<200ms** | **Typical program** |

### Execution Performance

| Operation | Target | Notes |
|-----------|--------|-------|
| Runtime init | <10ms | Module setup |
| Data propagation | <1ms | Per deferred completion |
| Inline transform | <1ms | Merge, project, field access |
| Module overhead | <5ms | Framework overhead per module |

### Caching

The compiler supports **aggressive caching**:

```scala
// Parse cache (per-source-file)
private val parseCache: Map[String, Pipeline] = ...

// Type check cache (per-AST + function registry)
private val typeCheckCache: Map[(Pipeline, FunctionRegistry), TypedPipeline] = ...

// Compiled DAG cache (per-source + modules)
private val dagCache: Map[(String, Map[String, Module.Uninitialized]), DagSpec] = ...
```

**Cache effectiveness:**
- **Hit rate:** >80% for unchanged sources
- **Speedup:** >5x for cached compilation
- **Invalidation:** Automatic when source or modules change

---

## Visual Summary

```
┌──────────────────────────────────────────────────────────────────────┐
│ CONSTELLATION ENGINE PIPELINE LIFECYCLE                              │
└──────────────────────────────────────────────────────────────────────┘

SOURCE TEXT
  │
  ├─ in text: String
  ├─ result = Uppercase(text)
  └─ out result
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 1: PARSER (cats-parse combinators)                       │
├─────────────────────────────────────────────────────────────────┤
│ • Tokenize: keywords, identifiers, operators                    │
│ • Apply grammar rules                                           │
│ • Track source spans                                            │
│ • Validate syntax (NOT semantics)                               │
│                                                                  │
│ Errors: Missing keywords, unmatched parens, invalid identifiers │
└─────────────────────────────────────────────────────────────────┘
      │
      ▼
AST (Abstract Syntax Tree)
  │
  ├─ InputDecl("text", Primitive("String"))
  ├─ Assignment("result", FunctionCall("Uppercase", [VarRef("text")]))
  └─ OutputDecl("result")
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 2: TYPE CHECKER (bidirectional inference)                │
├─────────────────────────────────────────────────────────────────┤
│ • Resolve type expressions to SemanticType                      │
│ • Build type environment                                        │
│ • Check expressions bottom-up                                   │
│ • Validate module signatures                                    │
│ • Apply subtyping rules                                         │
│ • Check pattern exhaustiveness                                  │
│                                                                  │
│ Errors: Type mismatch, undefined variable, wrong arity          │
└─────────────────────────────────────────────────────────────────┘
      │
      ▼
TYPED AST
  │
  ├─ InputDecl("text", SString)
  ├─ Assignment("result",
  │    FunctionCall("Uppercase", signature=..., args=[VarRef("text", SString)]))
  └─ OutputDecl("result", SString)
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 3: IR GENERATOR (tree walk)                              │
├─────────────────────────────────────────────────────────────────┤
│ • Assign unique IDs to expressions                              │
│ • Flatten to IR nodes                                           │
│ • Track variable bindings                                       │
│ • Extract module options                                        │
│ • Convert lambda bodies                                         │
│                                                                  │
│ Errors: Unsupported lambda constructs                           │
└─────────────────────────────────────────────────────────────────┘
      │
      ▼
IR PIPELINE
  │
  ├─ Input(id1, "text", SString)
  ├─ ModuleCall(id2, "Uppercase", inputs={"text" -> id1}, ...)
  └─ variableBindings = {"text" -> id1, "result" -> id2}
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 4: DAG COMPILER (topological processing)                 │
├─────────────────────────────────────────────────────────────────┤
│ • Process IR nodes in dependency order                          │
│ • Create module node specs                                      │
│ • Create data node specs                                        │
│ • Build edge sets                                               │
│ • Generate synthetic modules (branch, match)                    │
│ • Create inline transforms (merge, project, field access)       │
│                                                                  │
│ Errors: Missing nodes, unsupported operations                   │
└─────────────────────────────────────────────────────────────────┘
      │
      ▼
DAG SPEC
  │
  ├─ modules: {m1 -> ModuleNodeSpec("Uppercase", ...)}
  ├─ data: {d1 -> DataNodeSpec("text", CString),
  │          d2 -> DataNodeSpec("Uppercase_output", CString)}
  ├─ inEdges: {(d1, m1)}
  ├─ outEdges: {(m1, d2)}
  └─ outputBindings: {"result" -> d2}
      │
      ▼
┌─────────────────────────────────────────────────────────────────┐
│ STAGE 5: RUNTIME (async parallel execution)                    │
├─────────────────────────────────────────────────────────────────┤
│ • Initialize modules (create deferreds)                         │
│ • Create runtime state                                          │
│ • Complete input data nodes                                     │
│ • Start inline transform fibers                                 │
│ • Execute modules in parallel (respect dependencies)            │
│ • Collect outputs                                               │
│                                                                  │
│ Errors: Module not found, timeout, runtime exception            │
└─────────────────────────────────────────────────────────────────┘
      │
      ▼
RESULTS (Runtime.State)
  │
  ├─ moduleStatus: {m1 -> Fired(5ms)}
  ├─ data: {d1 -> CString("hello"),
  │         d2 -> CString("HELLO")}
  └─ outputBindings: {"result" -> CString("HELLO")}
```

---

## Key Takeaways

1. **Five distinct stages:** Parse → Type Check → IR Gen → DAG Compile → Runtime
2. **Progressive refinement:** Each stage catches more subtle errors
3. **Type safety:** Strong static typing prevents runtime type errors
4. **Parallel execution:** Deferred-based data flow enables automatic parallelism
5. **Performance:** <200ms compile, <10ms runtime overhead
6. **Error detection:** Most errors caught before execution (parse + type check)
7. **Caching:** >5x speedup for unchanged sources
8. **Extensibility:** Inline transforms avoid synthetic modules for common operations

---

## Further Reading

- **Parser implementation:** `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala`
- **Type checker:** `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala`
- **IR generator:** `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala`
- **DAG compiler:** `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala`
- **Runtime:** `modules/runtime/src/main/scala/io/constellation/Runtime.scala`
- **Type system:** `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala`
- **AST definitions:** `modules/lang-ast/src/main/scala/io/constellation/lang/ast/AST.scala`

---

## Next Steps

Now that you understand the pipeline lifecycle:

1. **[Module Development](../patterns/module-development.md)** - Create reusable modules
2. **[Error Handling](../patterns/error-handling.md)** - Gracefully handle failures
3. **[Performance Tuning](../patterns/performance-tuning.md)** - Optimize execution
4. **[Testing Strategies](../patterns/testing-strategies.md)** - Ensure correctness
