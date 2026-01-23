# Constellation Engine

[![CI](https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/VledicFranco/constellation-engine/graph/badge.svg)](https://codecov.io/gh/VledicFranco/constellation-engine)

**Stop writing glue code. Start describing pipelines.**

Constellation Engine is a type-safe orchestration language that lets you define data transformation pipelines declaratively. Describe *what* you want to compute, not *how* to wire it together.

---

## Why Constellation?

### The Problem

Building ML and data pipelines today means drowning in boilerplate:

```python
# Traditional approach: 50 lines of glue code
def process_candidates(items, user_context, weights):
    enriched = []
    for item in items:
        merged = {**item, **user_context}
        score = (
            merged['relevance'] * weights['relevance_weight'] +
            merged['freshness'] * weights['freshness_weight']
        )
        enriched.append({
            'id': merged['id'],
            'title': merged['title'],
            'score': score,
            'userId': merged['userId']
        })
    return enriched
```

Type errors hide until runtime. Field mismatches cause silent bugs. Refactoring is terrifying.

### The Solution

```
# Constellation: 6 lines, fully type-checked
in items: Candidates<{ id: String, title: String, relevance: Int, freshness: Int }>
in context: { userId: Int }
in weights: { relevanceWeight: Int, freshnessWeight: Int }

enriched = items + context
output = enriched[id, title, relevance, freshness, userId]

out output
```

Every field access is validated at compile time. Type mismatches are caught before execution. Your IDE shows you exactly what's available.

---

## See It In Action

### Batch Enrichment (ML Ranking)

Add user context to every candidate in a ranking pipeline:

```
type Product = { id: String, title: String, score: Float }
type UserContext = { userId: Int, region: String, tier: String }

in products: Candidates<Product>
in context: UserContext

# Merge adds context to EACH product
enriched = products + context

# Project selects fields from EACH product
output = enriched[id, title, score, userId, region]

out output
```

**Input:**
```json
{
  "products": [
    {"id": "p1", "title": "Widget A", "score": 0.95},
    {"id": "p2", "title": "Widget B", "score": 0.87}
  ],
  "context": {"userId": 123, "region": "us-west", "tier": "premium"}
}
```

**Output:**
```json
{
  "output": [
    {"id": "p1", "title": "Widget A", "score": 0.95, "userId": 123, "region": "us-west"},
    {"id": "p2", "title": "Widget B", "score": 0.87, "userId": 123, "region": "us-west"}
  ]
}
```

### Multi-Factor Scoring

Build configurable scoring systems with full type safety:

```
type Candidate = { id: String, engagement: Int, recency: Int, quality: Int }
type Weights = { engagementW: Int, recencyW: Int, qualityW: Int, threshold: Int }

in candidate: Candidate
in weights: Weights

# Weighted scoring - types flow through every operation
engagementScore = multiply(candidate.engagement, weights.engagementW)
recencyScore = multiply(candidate.recency, weights.recencyW)
qualityScore = multiply(candidate.quality, weights.qualityW)

total = add(add(engagementScore, recencyScore), qualityScore)
qualified = gte(total, weights.threshold)

out total
out qualified
```

### Higher-Order Transformations

Filter and transform with lambdas:

```
in users: List<{ name: String, age: Int, active: Boolean }>

# Filter to active users over 18
adults = Filter(users, u => and(u.active, gte(u.age, 18)))

# Extract just names
names = Map(adults, u => u.name)

# Count them
count = Length(names)

out names
out count
```

---

## Key Features

### Type Algebra

Merge records with `+` (right side wins on conflicts):

```
in a: { x: Int, y: Int }
in b: { y: String, z: String }

merged = a + b  # Type: { x: Int, y: String, z: String }
```

### Projections

Select exactly the fields you need:

```
in user: { id: Int, name: String, email: String, internal: String }

public = user[id, name, email]  # Type: { id: Int, name: String, email: String }
```

### Candidates (Batch Operations)

First-class support for ML batching - operations apply element-wise:

```
in items: Candidates<{ id: String, features: List<Float> }>
in context: { userId: Int }

# Every item gets userId added
enriched = items + context  # Candidates<{ id: String, features: List<Float>, userId: Int }>
```

### Full IDE Support

- Real-time type checking as you type
- Autocomplete for fields and functions
- Hover for type information
- Go-to-definition for custom types
- Inline error messages with suggestions

---

## Quick Start

### 1. Install

```bash
git clone https://github.com/VledicFranco/constellation-engine.git
cd constellation-engine
make compile
```

### 2. Start the Server

```bash
make server
```

### 3. Run a Pipeline

**Via HTTP:**
```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in x: Int\nresult = add(x, 10)\nout result",
    "inputs": {"x": 5}
  }'
```

**Via VSCode:**
1. Install the Constellation extension
2. Create a `.cst` file
3. Press `Ctrl+Shift+R` to run

---

## Integrate With Your Code

```scala
import io.constellation.lang.runtime.LangCompiler
import io.constellation.lang.semantic._

// Define your ML modules
val compiler = LangCompiler.builder
  .withFunction(FunctionSignature(
    name = "embed-model",
    params = List("input" -> SemanticType.SCandidates(inputType)),
    returns = embeddingsType,
    moduleName = "embed-model"
  ))
  .withModule(embedModule)  // Your actual implementation
  .build

// Compile and run
compiler.compile(source, "my-pipeline") match {
  case Right(result) =>
    val dagSpec = result.dagSpec
    // Execute with Runtime.run(dagSpec, inputs, modules)
  case Left(errors) =>
    errors.foreach(e => println(e.format))
}
```

---

## Architecture

```
Source Code (.cst)
       |
       v
  +---------+     +-------------+     +-------------+     +-------------+
  | Parser  | --> | TypeChecker | --> | IRGenerator | --> | DagCompiler |
  +---------+     +-------------+     +-------------+     +-------------+
       |               |                    |                    |
       v               v                    v                    v
      AST          TypedAST            IRProgram             DagSpec
                                                          + Synthetic
                                                            Modules
```

| Stage | What It Does |
|-------|--------------|
| **Parser** | Parses source into AST with position tracking |
| **TypeChecker** | Validates types, catches field/type mismatches |
| **IRGenerator** | Transforms to intermediate representation |
| **DagCompiler** | Generates executable DAG + synthetic modules |

---

## Project Structure

```
modules/
├── core/           # Type system (CType, CValue)
├── runtime/        # DAG execution, ModuleBuilder API
├── lang-ast/       # AST definitions with source positions
├── lang-parser/    # Parser (cats-parse)
├── lang-compiler/  # Type checker, IR, DAG compiler
├── lang-stdlib/    # Standard library (Map, Filter, etc.)
├── lang-lsp/       # Language Server Protocol
├── http-api/       # HTTP server + WebSocket LSP
└── example-app/    # Example application
```

---

## Development

```bash
# Compile everything
make compile

# Run all tests
make test

# Start dev environment (server + hot reload)
make dev

# Run specific test suites
make test-core
make test-compiler
make test-lsp
```

---

## Requirements

- **JDK 17+**
- **SBT 1.9+**
- **Node.js 18+** (for VSCode extension)

---

## Documentation

| Resource | Description |
|----------|-------------|
| [Getting Started](docs/getting-started.md) | Complete tutorial from zero to custom modules |
| [Language Reference](docs/constellation-lang/README.md) | Full syntax and semantics |
| [Standard Library](docs/stdlib.md) | Built-in functions reference |
| [Pipeline Examples](docs/examples/README.md) | Real-world examples with explanations |
| [Architecture Guide](docs/architecture.md) | Deep dive into internals |
| [API Guide](docs/api-guide.md) | Programmatic usage |
| [Contributing](CONTRIBUTING.md) | Development setup and workflow |

---

## Why "Constellation"?

Pipelines are graphs of connected nodes - like stars forming constellations. Each node (module) is a point of light; together they form something meaningful.

---

## License

[MIT](LICENSE)

---

**Ready to stop writing glue code?** [Get started in 10 minutes](docs/getting-started.md)
