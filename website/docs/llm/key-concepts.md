---
title: "Key Concepts"
sidebar_position: 3
description: "Essential terminology and concepts for working with Constellation Engine"
---

# Key Concepts

**Goal:** Understand the 30 essential terms used throughout Constellation Engine.

## Core Architecture

### Constellation
The **main orchestration engine** that manages modules and execution.

**What it does:**
- Stores registered modules
- Provides module lookup
- Manages execution state

**Example:**
```scala
val constellation = Constellation.create[IO]
```

**See also:** [Embedded API](./integration/embedded-api.md)

---

### Module
A **reusable processing unit** with typed inputs and outputs.

**What it is:**
- Defined in Scala using `ModuleBuilder`
- Has a unique name (case-sensitive)
- Specifies input/output types
- Contains implementation logic

**Example:**
```scala
val uppercase = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build
```

**Key properties:**
- **Name:** "Uppercase" (must match usage in `.cst` files)
- **Version:** `1.0` (major.minor)
- **Type signature:** `TextInput => TextOutput`
- **Implementation:** Pure function or IO

**See also:** [Module Development](./patterns/module-development.md)

---

### Pipeline
A **DAG of module invocations** defined in constellation-lang (`.cst` files).

**What it contains:**
- Input declarations: `in x: String`
- Module calls: `result = Uppercase(x)`
- Output declarations: `out result`

**Example:**
```constellation
in text: String
trimmed = Trim(text)
result = Uppercase(trimmed)
out result
```

**Key properties:**
- Must be a valid DAG (no cycles)
- All variables must be defined before use
- All outputs must reference defined variables

**See also:** [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)

---

### DAG (Directed Acyclic Graph)
The **execution plan** compiled from a pipeline.

**What it represents:**
- Nodes = module invocations
- Edges = data dependencies
- Layers = parallel execution groups

**Example visualization:**
```
Layer 0: [Trim(text)]
         ↓
Layer 1: [Uppercase(trimmed)]
```

**Key properties:**
- No cycles allowed
- Topologically sorted into layers
- Nodes in same layer execute in parallel

**See also:** [DAG Execution](./foundations/dag-execution.md)

---

## Type System

### CType
**Type representation at compile time.**

**Hierarchy:**
```
CType
├─ CPrimitive
│  ├─ CString
│  ├─ CInt
│  ├─ CDouble
│  └─ CBoolean
├─ CRecord(Map[String, CType])
├─ CUnion(Set[CType])
├─ CList(CType)
└─ COptional(CType)
```

**Example:**
```scala
val stringType: CType = CString
val recordType: CType = CRecord(Map("name" -> CString, "age" -> CInt))
```

**See also:** [Type System](./foundations/type-system.md), [Type Syntax](./reference/type-syntax.md)

---

### CValue
**Value representation at runtime.**

**Hierarchy:**
```
CValue
├─ CPrimitive
│  ├─ CString("text")
│  ├─ CInt(42)
│  ├─ CDouble(3.14)
│  └─ CBoolean(true)
├─ CRecord(Map[String, CValue])
├─ CUnion(CValue, CType)
├─ CList(List[CValue])
├─ COptional(Option[CValue])
└─ CNone
```

**Example:**
```scala
val stringValue: CValue = CString("hello")
val recordValue: CValue = CRecord(Map("name" -> CString("Alice"), "age" -> CInt(30)))
```

**See also:** [Type System](./foundations/type-system.md)

---

### Type Compatibility
Rules for **when one type can be used where another is expected.**

**Key rules:**
1. **Exact match:** `CString` matches `CString`
2. **Subtyping:** `CNone` matches `COptional(T)` for any T
3. **Record subtyping:** `{a: String, b: Int}` matches `{a: String}` (extra fields OK)
4. **Union subtyping:** `String` matches `String | Int`

**Example:**
```constellation
in x: String | Int     # Union type
result = Process(x)     # OK if Process accepts String | Int
```

**See also:** [Type System](./foundations/type-system.md)

---

### Semantic Type
**Type information during compilation** (before final CType).

**What it tracks:**
- Type constraints from usage
- Type inference state
- Error locations

**Key semantic types:**
- `ConcreteType(CType)` - Fully resolved
- `UnionType(Set[SemanticType])` - Union of types
- `OptionalType(SemanticType)` - Optional wrapper

**See also:** [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)

---

## Compilation Stages

### Parser
**Converts `.cst` text to AST.**

**Input:** String source code
**Output:** AST (Abstract Syntax Tree)

**Example:**
```constellation
in x: Int
result = Double(x)
out result
```
↓
```scala
AST(
  inputs = Map("x" -> CInt),
  calls = List(Call("result", "Double", Map("x" -> Var("x")))),
  outputs = Set("result")
)
```

**See also:** [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)

---

### Type Checker
**Validates types and resolves inference.**

**Input:** AST
**Output:** Typed IR (Intermediate Representation)

**What it checks:**
- All variables are defined
- Types are compatible
- Module signatures match calls
- No type errors

**Example error:**
```
Error: Type mismatch at line 3
  Expected: CString
  Got: CInt
```

**See also:** [Error Handling](./patterns/error-handling.md)

---

### DAG Compiler
**Converts typed IR to executable DAG.**

**Input:** Typed IR
**Output:** Execution DAG

**What it does:**
- Builds dependency graph
- Detects cycles
- Sorts into execution layers
- Optimizes for parallelism

**See also:** [DAG Execution](./foundations/dag-execution.md)

---

### IR (Intermediate Representation)
**Typed AST before final compilation.**

**What it contains:**
- Type-checked expressions
- Resolved module references
- Variable bindings with types

**Example:**
```scala
IR(
  inputs = Map("x" -> CInt),
  bindings = Map("result" -> ModuleCall("Double", CInt -> CInt, Map("x" -> Var("x", CInt)))),
  outputs = Set("result")
)
```

**See also:** [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)

---

## Execution

### Hot Execution
**Precompiled DAG with fast execution.**

**Characteristics:**
- DAG compiled once
- Reused for multiple inputs
- Minimal startup latency
- Used by HTTP API

**Example:**
```scala
// Compile once
val dag = compiler.compile(source)

// Execute many times
val result1 = dag.execute(inputs1)
val result2 = dag.execute(inputs2)
```

**See also:** [Execution Modes](./foundations/execution-modes.md)

---

### Cold Execution
**Compile and execute on-demand.**

**Characteristics:**
- Compilation per execution
- More flexible (can change source)
- Higher latency
- Used in development/testing

**Example:**
```scala
// Compile and execute together
val result = compiler.compileAndExecute(source, inputs)
```

**See also:** [Execution Modes](./foundations/execution-modes.md)

---

### Layer
**Group of modules that can execute in parallel.**

**What it represents:**
- All nodes with same topological distance from inputs
- No dependencies between nodes in same layer
- Executes before next layer

**Example:**
```constellation
# Layer 0 (both parallel)
a = ProcessA(input)
b = ProcessB(input)

# Layer 1 (waits for layer 0)
result = Merge(a, b)
```

**See also:** [DAG Execution](./foundations/dag-execution.md)

---

### Execution Context
**Runtime state during pipeline execution.**

**What it contains:**
- Input values
- Intermediate results
- Module instances
- Error state

**See also:** [DAG Execution](./foundations/dag-execution.md)

---

## Resilience

### Retry
**Automatic retry on failure.**

**Syntax:**
```constellation
result = UnreliableAPI(input) with {
  retry: 3
}
```

**Behavior:**
- Retries up to N times
- Exponential backoff (configurable)
- Fails if all retries exhausted

**See also:** [Resilience Patterns](./patterns/resilience.md), [Module Options](./reference/module-options.md)

---

### Timeout
**Maximum execution time.**

**Syntax:**
```constellation
result = SlowAPI(input) with {
  timeout: 10s
}
```

**Behavior:**
- Cancels execution after timeout
- Raises timeout error
- Can combine with retry

**See also:** [Resilience Patterns](./patterns/resilience.md)

---

### Cache
**Store and reuse results.**

**Syntax:**
```constellation
result = ExpensiveComputation(input) with {
  cache: 1h
}
```

**Behavior:**
- Caches result by input hash
- Returns cached value if available
- Expires after TTL

**See also:** [Resilience Patterns](./patterns/resilience.md)

---

### Fallback
**Alternative value on failure.**

**Syntax:**
```constellation
result = UnreliableAPI(input) with {
  fallback: DefaultValue(input)
}
```

**Behavior:**
- Executes fallback on error
- Fallback must return compatible type
- Can combine with retry

**See also:** [Resilience Patterns](./patterns/resilience.md)

---

## API Concepts

### HTTP API
**REST interface for pipeline execution.**

**Key endpoints:**
- `POST /execute` - Execute pipeline
- `GET /health` - Health check
- `GET /modules` - List modules
- `POST /validate` - Validate pipeline

**Example:**
```bash
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"source": "...", "inputs": {...}}'
```

**See also:** [HTTP API Reference](./reference/http-api.md)

---

### Embedded API
**Programmatic usage in Scala applications.**

**Key components:**
- `Constellation[F]` - Module registry
- `DagCompiler[F]` - Compilation
- `ExecutionDag[F]` - Execution

**Example:**
```scala
for {
  constellation <- Constellation.create[IO]
  compiler      <- DagCompiler.create[IO](constellation)
  dag           <- compiler.compile(source)
  result        <- dag.execute(inputs)
} yield result
```

**See also:** [Embedded API](./integration/embedded-api.md)

---

### LSP (Language Server Protocol)
**Editor integration for `.cst` files.**

**Features:**
- Syntax highlighting
- Autocomplete
- Error diagnostics
- Hover documentation

**Used by:** VSCode extension

**See also:** [Project Structure](./project-structure.md)

---

## Module Builder

### ModuleBuilder
**DSL for defining modules.**

**Methods:**
- `.metadata(name, description, major, minor)` - Basic info
- `.implementationPure[I, O](f)` - Pure function
- `.implementation[I, O](f)` - IO function
- `.withRetry(config)` - Add retry
- `.withTimeout(duration)` - Add timeout
- `.withCache(ttl)` - Add caching
- `.build` - Create module

**Example:**
```scala
val module = ModuleBuilder
  .metadata("Process", "Processes data", 1, 0)
  .implementationPure[Input, Output] { input =>
    Output(process(input))
  }
  .withRetry(RetryConfig(maxAttempts = 3))
  .build
```

**See also:** [Module Development](./patterns/module-development.md), [Module Options](./reference/module-options.md)

---

### Implementation Types

#### Pure Implementation
**No side effects, deterministic.**

```scala
.implementationPure[Input, Output] { input =>
  Output(compute(input))
}
```

**Use when:**
- No IO needed
- Deterministic result
- No state changes

#### IO Implementation
**Side effects allowed.**

```scala
.implementation[Input, Output] { input =>
  IO {
    // Perform side effect
    Output(result)
  }
}
```

**Use when:**
- Need to call external API
- File I/O
- Database access
- Non-deterministic

**See also:** [Module Development](./patterns/module-development.md)

---

## constellation-lang Syntax

### Input Declaration
**Declare pipeline inputs.**

**Syntax:**
```constellation
in variableName: Type
```

**Example:**
```constellation
in text: String
in count: Int
in user: {name: String, age: Int}
```

**Rules:**
- Must appear before first module call
- Variable names must be unique
- Types must be valid CTypes

**See also:** [Type Syntax](./reference/type-syntax.md)

---

### Module Call
**Invoke a registered module.**

**Syntax:**
```constellation
variableName = ModuleName(arg1, arg2, ...) with { options }
```

**Example:**
```constellation
result = Uppercase(text)
cached = ExpensiveAPI(input) with { cache: 1h }
```

**Rules:**
- Module name must match registered module (case-sensitive)
- Arguments must match module input type
- Variable name must be unique

**See also:** [Module Options](./reference/module-options.md)

---

### Output Declaration
**Declare pipeline outputs.**

**Syntax:**
```constellation
out variableName
```

**Example:**
```constellation
out result
out processedData
```

**Rules:**
- Must reference a defined variable
- Can have multiple outputs
- Must appear after variable definition

---

### With Clause
**Attach resilience options to module call.**

**Syntax:**
```constellation
result = Module(input) with {
  retry: 3,
  timeout: 10s,
  cache: 1h,
  fallback: DefaultModule(input)
}
```

**Available options:**
- `retry: Int` - Max retry attempts
- `timeout: Duration` - Max execution time
- `cache: Duration` - Cache TTL
- `fallback: ModuleCall` - Fallback on error

**See also:** [Resilience Patterns](./patterns/resilience.md), [Module Options](./reference/module-options.md)

---

## Testing

### Test Fixtures
**Predefined test data for benchmarks and tests.**

**Available:**
- Small program (5 lines, 2 modules)
- Medium program (20 lines, 8 modules)
- Large program (50 lines, 20 modules)

**Location:** `modules/lang-compiler/src/test/scala/.../TestFixtures.scala`

**See also:** [Project Structure](./project-structure.md)

---

## Glossary Summary Table

| Term | Category | One-Line Definition |
|------|----------|---------------------|
| **Constellation** | Core | Module registry and orchestration engine |
| **Module** | Core | Reusable processing unit with typed I/O |
| **Pipeline** | Core | DAG of modules in `.cst` syntax |
| **DAG** | Core | Directed acyclic graph execution plan |
| **CType** | Type System | Type at compile time |
| **CValue** | Type System | Value at runtime |
| **Type Compatibility** | Type System | Rules for type matching |
| **Semantic Type** | Type System | Type during compilation |
| **Parser** | Compilation | Text → AST conversion |
| **Type Checker** | Compilation | AST → Typed IR validation |
| **DAG Compiler** | Compilation | Typed IR → Execution DAG |
| **IR** | Compilation | Intermediate representation |
| **Hot Execution** | Execution | Precompiled DAG, fast execution |
| **Cold Execution** | Execution | On-demand compilation |
| **Layer** | Execution | Parallel execution group |
| **Execution Context** | Execution | Runtime state during execution |
| **Retry** | Resilience | Automatic retry on failure |
| **Timeout** | Resilience | Maximum execution time |
| **Cache** | Resilience | Store and reuse results |
| **Fallback** | Resilience | Alternative on failure |
| **HTTP API** | API | REST interface for execution |
| **Embedded API** | API | Programmatic Scala usage |
| **LSP** | API | Language server for editors |
| **ModuleBuilder** | Module Builder | DSL for defining modules |
| **Pure Implementation** | Module Builder | No side effects |
| **IO Implementation** | Module Builder | Side effects allowed |
| **Input Declaration** | Syntax | `in x: Type` |
| **Module Call** | Syntax | `var = Module(args)` |
| **Output Declaration** | Syntax | `out var` |
| **With Clause** | Syntax | Module options |

## Next Steps

Now that you know the terminology:

1. **[Type System](./foundations/type-system.md)** - Deep dive into CType/CValue
2. **[Module Development](./patterns/module-development.md)** - Create your first module
3. **[Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)** - How compilation works

---

**Back to:** [Getting Started](./getting-started.md) | **Up to:** [LLM Guide Index](./index.md)
