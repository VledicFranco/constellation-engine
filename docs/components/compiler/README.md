# Compiler Component

> **Path**: `docs/components/compiler/`
> **Parent**: [components/](../README.md)
> **Modules**: `modules/lang-parser/`, `modules/lang-compiler/`

The compiler pipeline that transforms constellation-lang source into executable DAGs.

## Key Files

### Parser (`lang-parser`)

| File | Purpose |
|------|---------|
| `ConstellationParser.scala` | cats-parse based parser for constellation-lang |
| `MemoizationSupport.scala` | Parser memoization for performance |

### Compiler (`lang-compiler`)

| File | Purpose |
|------|---------|
| `LangCompiler.scala` | Main compiler interface and builder |
| `CachingLangCompiler.scala` | Compilation caching wrapper |
| `CompilationCache.scala` | LRU cache for compiled pipelines |
| **Semantic Analysis** | |
| `semantic/TypeChecker.scala` | Entry point for type checking |
| `semantic/BidirectionalTypeChecker.scala` | Bidirectional type inference |
| `semantic/SemanticType.scala` | Semantic type representations |
| `semantic/Subtyping.scala` | Subtype relationships and LUB |
| `semantic/RowUnification.scala` | Record type unification |
| **IR Generation** | |
| `compiler/IRGenerator.scala` | Typed AST to IR conversion |
| `compiler/IR.scala` | Intermediate representation types |
| `compiler/DagCompiler.scala` | IR to DagSpec compilation |
| **Optimization** | |
| `optimizer/IROptimizer.scala` | Optimization pass orchestration |
| `optimizer/DeadCodeElimination.scala` | Remove unused nodes |
| `optimizer/ConstantFolding.scala` | Evaluate constant expressions |
| `optimizer/CommonSubexpressionElimination.scala` | Share duplicate computations |
| **Visualization** | |
| `viz/DagVizCompiler.scala` | IR to visualization IR |
| `viz/SugiyamaLayout.scala` | Graph layout algorithm |
| `viz/SVGRenderer.scala`, `MermaidRenderer.scala`, etc. | Output renderers |
| **Errors** | |
| `compiler/CompilerError.scala` | Compiler error types |
| `compiler/ErrorFormatter.scala` | Error message formatting |
| `compiler/Suggestions.scala` | Fix suggestions for errors |

## Role in the System

The compiler transforms source code through multiple phases:

```
                          ┌─────────────┐
                          │    core     │
                          └──────┬──────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
        ▼                        ▼                        │
   [runtime]               [lang-parser]                  │
        │                        │                        │
        │                        ▼                        │
        │                  [lang-compiler] ◄──────────────┘
        │                        │
        │      ┌─────────────────┼─────────────────┐
        │      │                 │                 │
        │      ▼                 ▼                 ▼
        │  [lang-stdlib]    [lang-lsp]       [http-api]
        │      │
        └──────┴─────────────────┐
                                 │
                                 ▼
                           [example-app]
```

## Compilation Pipeline

```
Source Code (.cst)
         │
         ▼
    ┌─────────────────────┐
    │   1. Parse          │  ConstellationParser
    │   String → AST      │  cats-parse combinators
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   2. Type Check     │  BidirectionalTypeChecker
    │   AST → TypedAST    │  Validates types, infers lambdas
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   3. Generate IR    │  IRGenerator
    │   TypedAST → IR     │  Creates IR nodes with UUIDs
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   4. Optimize       │  IROptimizer
    │   IR → IR           │  DCE, constant folding, CSE
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   5. Compile DAG    │  DagCompiler
    │   IR → DagSpec      │  Creates data/module nodes, edges
    └──────────┬──────────┘
               │
               ▼
    DagSpec + Synthetic Modules
```

## Parser

### Supported Syntax

| Construct | Parser Rule |
|-----------|-------------|
| `in name: Type` | `inputDecl` |
| `out expr` | `outputDecl` |
| `type Name = Type` | `typeDef` |
| `name = expr` | `assignment` |
| `use namespace` | `useDecl` |
| `Module(args)` | `functionCall` |
| `with option: value` | `withClause` |
| `if (c) t else e` | `conditional` |
| `branch { ... }` | `branchExpr` |
| `expr when cond` | `exprGuard` |
| `opt ?? default` | `exprCoalesce` |
| `(x) => body` | `lambdaExpr` |

### Module Call Options

The parser recognizes these options in `with` clauses:

```constellation
result = Fetch(url) with retry: 3, timeout: 5s, cache: 10min
```

| Option | Syntax | Type |
|--------|--------|------|
| `retry` | `retry: N` | Integer |
| `timeout` | `timeout: Ns` | Duration |
| `delay` | `delay: Ns` | Duration |
| `backoff` | `backoff: strategy` | exponential/linear/fixed |
| `fallback` | `fallback: expr` | Expression |
| `cache` | `cache: Ns` | Duration |
| `throttle` | `throttle: N/Ns` | Rate |
| `concurrency` | `concurrency: N` | Integer |
| `on_error` | `on_error: strategy` | propagate/skip/log/wrap |
| `lazy` | `lazy` or `lazy: bool` | Boolean |
| `priority` | `priority: level` | critical/high/normal/low/background or Integer |

## Type System

### Semantic Types

Internal type representation during type checking:

```scala
SemanticType (sealed trait)
├── SString, SInt, SFloat, SBoolean  // Primitives
├── SList(element)                    // Homogeneous list
├── SMap(key, value)                  // Key-value map
├── SRecord(fields: Map[String, T])   // Named fields
├── SUnion(members: Set[T])           // Union type
├── SOptional(inner)                  // Optional value
├── SFunction(params, return)         // Function type
├── SNothing                          // Bottom type
└── SAny                              // Top type
```

### Bidirectional Type Checking

The type checker uses bidirectional inference:

1. **Synthesis mode** - Infer type from expression structure
2. **Checking mode** - Check expression against expected type

This enables:
- Lambda parameter type inference from context
- Empty list typing from expected type
- Better error messages with contextual information

### Type Algebra

| Operation | Rule | Example |
|-----------|------|---------|
| Merge (`+`) | Right wins on conflict | `{a: Int} + {b: String} = {a: Int, b: String}` |
| Union (`\|`) | Set union of types | `Int \| String` |
| Projection (`[f]`) | Select fields | `{a, b, c}[a, b] = {a, b}` |
| Guard (`when`) | Wrap in Optional | `T when cond = Optional<T>` |
| Coalesce (`??`) | Unwrap Optional | `Optional<T> ?? T = T` |

## IR (Intermediate Representation)

### IR Node Types

```scala
IRNode (sealed trait)
├── Input(name, outputType)
├── LiteralNode(value, outputType)
├── ModuleCall(moduleName, inputs, outputType, options)
├── MergeNode(left, right, outputType)
├── ProjectNode(source, fields, outputType)
├── FieldAccessNode(source, field, outputType)
├── ConditionalNode(condition, thenBr, elseBr, outputType)
├── AndNode, OrNode, NotNode
├── GuardNode, CoalesceNode, BranchNode
├── StringInterpolationNode(parts, expressions)
├── HigherOrderNode(operation, source, lambda, outputType)
└── ListLiteralNode(elements, elementType)
```

### IR Pipeline

```scala
case class IRPipeline(
  nodes: Map[UUID, IRNode],
  topologicalOrder: List[UUID],
  variableBindings: Map[String, UUID],
  declaredOutputs: List[String]
)
```

## Optimization Passes

| Pass | Purpose | Example |
|------|---------|---------|
| Dead Code Elimination | Remove unused nodes | Unreferenced assignments |
| Constant Folding | Evaluate constant expressions | `1 + 2` -> `3` |
| Common Subexpression | Share duplicate computations | `f(x)` used twice |

Optimization is configured via `OptimizationConfig`:

```scala
val config = OptimizationConfig(
  enableDeadCodeElimination = true,
  enableConstantFolding = true,
  enableCSE = true
)
```

## Compilation Caching

The compiler supports LRU caching of compilation results:

```scala
LangCompiler.builder
  .withCaching(CompilationCache.Config(maxEntries = 1000, ttlSeconds = 3600))
  .build
```

Cache key is computed from:
- Source code hash (SHA-256)
- Registered function signatures

## Dependencies

- **Depends on:** `core` (CType, CValue, Spec types), `runtime` (Module types)
- **Depended on by:** `lang-stdlib`, `lang-lsp`, `http-api`

## Features Using This Component

| Feature | Compiler Role |
|---------|---------------|
| [Pipeline authoring](../../language/) | Parse and compile DSL |
| [Type safety](../../language/types/) | Type checking and inference |
| [Module options](../../language/options/) | Parse `with` clauses |
| [DAG visualization](../../features/visualization/) | DagVizCompiler |
| [LSP support](../lsp/) | Incremental compilation |
