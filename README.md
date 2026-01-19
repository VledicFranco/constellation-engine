# Constellation Engine

[![CI](https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/VledicFranco/constellation-engine/graph/badge.svg)](https://codecov.io/gh/VledicFranco/constellation-engine)

A Scala 3 framework for building type-safe ML pipeline DAGs with a declarative domain-specific language.

## What is Constellation Engine?

Constellation Engine lets you define machine learning pipelines as directed acyclic graphs (DAGs) with full compile-time type safety. It provides:

- **constellation-lang**: A clean DSL for declaring ML pipelines
- **Type algebra**: Merge and project record types with `+` and `[fields]`
- **Batched operations**: First-class `Candidates<T>` type for ML batching
- **Modular design**: Compose reusable modules into complex pipelines

## Quick Start

### Define a Pipeline

```
type Communication = {
  id: String,
  content: String,
  channel: String
}

in communications: Candidates<Communication>
in userId: Int

# Process through ML models
embeddings = embed-model(communications)
scores = ranking-model(embeddings + communications, userId)

# Select and merge fields
result = communications[id, channel] + scores[score]

out result
```

### Compile and Run

```scala
import io.constellation.lang.runtime.LangCompiler
import io.constellation.lang.semantic._

val compiler = LangCompiler.builder
  .withFunction(FunctionSignature(
    name = "embed-model",
    params = List("input" -> SemanticType.SCandidates(inputType)),
    returns = embeddingsType,
    moduleName = "embed-model"
  ))
  .withFunction(FunctionSignature(
    name = "ranking-model",
    params = List(
      "data" -> SemanticType.SCandidates(mergedType),
      "userId" -> SemanticType.SInt
    ),
    returns = scoresType,
    moduleName = "ranking-model"
  ))
  .build

compiler.compile(source, "my-pipeline") match {
  case Right(result) =>
    val dagSpec = result.dagSpec
    val modules = result.syntheticModules
    // Execute with Runtime.run(dagSpec, inputs, modules)
  case Left(errors) =>
    errors.foreach(e => println(e.format))
}
```

## Language Features

### Type System

```
# Primitives
in count: Int
in name: String
in score: Float
in active: Boolean

# Records
type User = { id: Int, name: String, email: String }

# Parameterized types
in items: List<String>
in lookup: Map<String, Int>
in candidates: Candidates<User>
```

### Type Algebra

Merge records with `+` (right side wins on conflicts):

```
in a: { x: Int, y: Int }
in b: { y: String, z: String }
merged = a + b  # { x: Int, y: String, z: String }
```

### Projections

Select fields from records:

```
in data: { id: Int, name: String, extra: String }
result = data[id, name]  # { id: Int, name: String }
```

### Candidates Operations

Operations on `Candidates<T>` apply element-wise:

```
in items: Candidates<{ id: String, features: List<Float> }>
in context: { userId: Int }

# Merge adds userId to each item
enriched = items + context  # Candidates<{ id: String, features: List<Float>, userId: Int }>

# Project selects fields from each item
selected = items[id]  # Candidates<{ id: String }>
```

## Architecture

```
Source Code
    |
    v
+--------+     +-------------+     +-------------+     +-----------+
| Parser | --> | TypeChecker | --> | IRGenerator | --> | DagCompiler |
+--------+     +-------------+     +-------------+     +-----------+
    |               |                    |                   |
    v               v                    v                   v
   AST          TypedAST            IRProgram            DagSpec
                                                      + Synthetic
                                                        Modules
```

| Component | Description |
|-----------|-------------|
| Parser | cats-parse based parser producing AST with position info |
| TypeChecker | Validates types, resolves references, produces typed AST |
| IRGenerator | Transforms typed AST to intermediate representation |
| DagCompiler | Generates DagSpec and synthetic modules for merge/project |

## Project Structure

```
modules/
├── core/                       # Core type system (CType, CValue)
├── runtime/                    # DAG execution engine, ModuleBuilder
├── lang-ast/                   # AST definitions with positions
├── lang-parser/                # cats-parse based parser
├── lang-compiler/              # Type checker, IR, DAG compiler
├── lang-stdlib/                # Standard library functions
├── lang-lsp/                   # Language Server Protocol
├── http-api/                   # HTTP server and WebSocket LSP
└── example-app/                # Example application
```

## Building

```bash
# Compile
sbt compile

# Run tests
sbt test

# Run specific test suite
sbt "testOnly io.constellation.lang.parser.ParserTest"
sbt "testOnly io.constellation.lang.semantic.TypeCheckerTest"
sbt "testOnly io.constellation.lang.runtime.LangCompilerTest"
```

## Requirements

- Scala 3.3.x
- SBT 1.9.x
- JDK 21+

## Dependencies

- [Cats](https://typelevel.org/cats/) - Functional programming
- [Cats Effect](https://typelevel.org/cats-effect/) - Effect system
- [cats-parse](https://github.com/typelevel/cats-parse) - Parser combinators
- [Circe](https://circe.github.io/circe/) - JSON handling

## Documentation

See the [docs](docs/) directory for detailed documentation:

- [Documentation Index](docs/README.md)
- [constellation-lang Reference](docs/constellation-lang/README.md)
- [Standard Library](docs/stdlib.md)
- [Architecture Guide](docs/architecture.md)
- [API Guide](docs/api-guide.md)

## License

MIT
