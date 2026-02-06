# Compiler Ethos

> Normative constraints for the compilation pipeline.

---

## Identity

- **IS:** A multi-phase compilation pipeline that transforms constellation-lang source into executable DAGs
- **IS NOT:** A runtime executor, HTTP server, module implementation, or type system definition

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ConstellationParser` | cats-parse based parser that transforms source text into AST |
| `Pipeline` | Parsed AST representing a complete constellation-lang program |
| `Declaration` | AST node: InputDecl, OutputDecl, Assignment, TypeDef, UseDecl |
| `Expression` | AST node representing values and computations |
| `TypeChecker` | Entry point for semantic analysis and type inference |
| `BidirectionalTypeChecker` | Bidirectional type inference with synthesis and checking modes |
| `SemanticType` | Internal type representation during type checking |
| `FunctionSignature` | Registered module signature with parameter and return types |
| `FunctionRegistry` | Registry of function signatures for type checking module calls |
| `TypedPipeline` | Type-annotated AST ready for IR generation |
| `IRGenerator` | Transforms TypedPipeline into IR with UUID-addressable nodes |
| `IRNode` | IR node types: Input, ModuleCall, MergeNode, ConditionalNode, etc. |
| `IRPipeline` | Complete IR with nodes, bindings, and topological order |
| `IROptimizer` | Orchestrates optimization passes on IR |
| `DagCompiler` | Compiles IR to DagSpec with data nodes, module nodes, and edges |
| `DagSpec` | Constellation execution specification with named outputs |
| `CompilationOutput` | Final output: LoadedPipeline + warnings |
| `CompileError` | Structured error with location, message, and category |
| `ErrorFormatter` | Formats errors with snippets, suggestions, and doc links |
| `Suggestions` | Generates "Did you mean?" suggestions using Levenshtein distance |
| `CompilationCache` | LRU cache for compiled pipelines keyed by source+registry hash |
| `CachingLangCompiler` | Compiler wrapper with transparent caching |

For complete type signatures, see:
- [io.constellation.lang](/docs/generated/io.constellation.lang.md)
- [io.constellation.lang.parser](/docs/generated/io.constellation.lang.parser.md)
- [io.constellation.lang.compiler](/docs/generated/io.constellation.lang.compiler.md)
- [io.constellation.lang.ast](/docs/generated/io.constellation.lang.ast.md)

---

## Compilation Pipeline

```
Source Code (.cst)
       |
       v
  +-----------------+
  | 1. Parse        |  ConstellationParser
  | String -> AST   |  cats-parse combinators
  +---------+-------+
            |
  +---------v-------+
  | 2. Type Check   |  BidirectionalTypeChecker
  | AST -> TypedAST |  Validates types, infers lambdas
  +---------+-------+
            |
  +---------v-------+
  | 3. Generate IR  |  IRGenerator
  | TypedAST -> IR  |  Creates IR nodes with UUIDs
  +---------+-------+
            |
  +---------v-------+
  | 4. Optimize     |  IROptimizer
  | IR -> IR        |  DCE, constant folding, CSE
  +---------+-------+
            |
  +---------v-------+
  | 5. Compile DAG  |  DagCompiler
  | IR -> DagSpec   |  Creates data/module nodes, edges
  +---------+-------+
            |
            v
  DagSpec + Synthetic Modules
```

---

## Invariants

### 1. Parsing is deterministic

The same source code always produces the same AST. Parser memoization is cleared before each parse to ensure fresh state.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala#parse` |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/property/CompilationPropertyTest.scala#Parser should be deterministic` |

### 2. Type checking is total

Every well-formed AST receives a type judgment (success with TypedPipeline) or a structured error (List[CompileError]). Type checking never throws exceptions for valid input.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala#check` |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/TypeCheckerTest.scala` |

### 3. Lambda types are inferred from context

Bidirectional type inference allows lambda parameter types to be inferred from the expected function type, enabling untyped lambda syntax like `(x) => x > 5`.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/BidirectionalTypeChecker.scala` |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/BidirectionalTypeCheckerSpec.scala` |

### 4. IR preserves type safety

All `asInstanceOf` casts in IR generation and DAG compilation are safe by construction because the type checker validates operand types during semantic analysis.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` (see type safety note) |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/integration/EndToEndPipelineTest.scala` |

### 5. Compilation is deterministic

The same source code with the same function registry always produces the same DagSpec structure (module counts, data counts, edge counts, output bindings).

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala#compile` |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/property/CompilationPropertyTest.scala#Compilation should be deterministic` |

### 6. Errors include actionable context

Compile errors include source location (line, column), code snippets with caret markers, categorized error codes, and "Did you mean?" suggestions.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/ErrorFormatter.scala` |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/compiler/ErrorFormatterTest.scala` |

### 7. Cache invalidation is hash-based

Compilation cache entries are keyed by source hash and registry hash. Changed source or different registered functions always trigger recompilation.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-compiler/src/main/scala/io/constellation/lang/CompilationCache.scala` |
| Test | `modules/lang-compiler/src/test/scala/io/constellation/lang/CompilationCacheTest.scala` |

---

## Principles (Prioritized)

1. **Early errors over late failures** - Catch type errors at compile time, not runtime
2. **Helpful errors over cryptic messages** - Include source context, suggestions, and documentation links
3. **Deterministic over stochastic** - Same input always produces same output
4. **Compositional over monolithic** - Each phase (parse, check, IR, optimize, compile) is independent

---

## Decision Heuristics

- When adding a new language construct, implement parser rule first, then type checking, then IR generation
- When extending the type system, add SemanticType variant, then subtyping rules, then CType conversion
- When uncertain about type inference, prefer bidirectional checking over unification
- When adding optimization passes, implement as stateless transformations on IRPipeline
- When handling errors, accumulate multiple errors rather than failing on first

---

## Out of Scope

- Runtime execution (see [runtime/](../runtime/))
- HTTP request handling (see [http-api/](../http-api/))
- Type system definitions (see [core/](../core/))
- Standard library functions (see [stdlib/](../stdlib/))
- LSP features (see [lsp/](../lsp/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Type Safety](../../features/type-safety/) | TypeChecker, BidirectionalTypeChecker, SemanticType, TypedPipeline |
| [Resilience](../../features/resilience/) | OptionValidator, OptionParser (compile-time validation of resilience options) |
