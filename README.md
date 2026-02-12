<p align="center">
  <img src="brand/logo-icon.svg" alt="Constellation Engine" width="80" height="80">
</p>

<h1 align="center">Constellation Engine</h1>

<p align="center">
  <strong>Type-safe pipeline orchestration for Scala</strong>
</p>

<p align="center">
  <a href="https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml"><img src="https://github.com/VledicFranco/constellation-engine/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://codecov.io/gh/VledicFranco/constellation-engine"><img src="https://codecov.io/gh/VledicFranco/constellation-engine/graph/badge.svg" alt="codecov"></a>
</p>

## What is Constellation?

Constellation is a **type-safe pipeline orchestration engine** that separates *what* your data pipeline does from *how* it's implemented.

You define pipeline logic in a declarative DSL (`constellation-lang`), and implement the underlying functions in Scala. The compiler validates every field access and type at compile time, then the runtime executes your pipeline with automatic parallelization.

```
┌─────────────────────────────────────────────────────────────────────────┐
│  constellation-lang (.cst files)           Scala Modules               │
│  ─────────────────────────────────         ─────────────────           │
│  Declarative pipeline logic                Function implementations     │
│  Type-checked at compile time              Full language power          │
│  Hot-reloadable                            IO, HTTP calls, databases    │
│                                                                         │
│  in order: Order                           val fetchCustomer = Module   │
│  customer = FetchCustomer(order.id)          .implementation { id =>    │
│  enriched = order + customer                   http.get(s"/api/$id")    │
│  out enriched                                }                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Why?

Backend services that aggregate data from multiple sources accumulate bugs over time. Field name typos, type mismatches, and null pointer exceptions hide in code that compiles fine but fails at runtime.

Constellation catches these errors at compile time:

```
in data: { userId: Int, userName: String }

x = data.userID        # Compile error: field 'userID' not found. Did you mean 'userId'?
y = data.userId + "!"  # Compile error: cannot concatenate Int with String
z = data[email]        # Compile error: field 'email' not found in record
```

**When Constellation makes sense:**
- API composition layers (BFF, API gateways)
- Data enrichment pipelines that call multiple services
- Backends where field mapping bugs have caused production incidents
- Teams that value type safety and want faster iteration cycles

**When to use something else:**
- Simple CRUD applications (your ORM is fine)
- Real-time streaming (use Kafka Streams, Flink)
- Data warehouse ETL (use Spark, dbt)

---

## Quick Example

This pipeline fetches an order, enriches it with customer data, and returns a response:

**Pipeline definition** (`order-enrichment.cst`):
```
type Order = { id: String, customerId: String, items: List<Item>, total: Float }
type Customer = { name: String, tier: String }

in order: Order

customer = FetchCustomer(order.customerId)
shipping = EstimateShipping(order.id)

# Merge records - compiler validates all fields exist
enriched = order + customer + shipping

out enriched[id, name, tier, items, total]
```

**Execute via CLI**:
```bash
constellation run order-enrichment.cst --input order='{"id":"ORD-123","customerId":"C-456"}'
```

**Or via HTTP**:
```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "...",
    "inputs": { "order": { "id": "ORD-123", "customerId": "C-456", ... } }
  }'
```

---

## CLI

The Constellation CLI brings compile, run, and deploy operations to your terminal. Designed for scripting, CI/CD, and fast iteration.

### Install

```bash
# Via Coursier (recommended)
cs install io.constellation:constellation-cli_3:0.6.0

# Or download fat JAR from GitHub Releases
curl -sSL https://github.com/VledicFranco/constellation-engine/releases/download/v0.6.0/constellation-cli.jar -o constellation-cli.jar
```

### Usage

```bash
# Configure server connection (once)
constellation config set server.url http://localhost:8080

# Compile and type-check
constellation compile pipeline.cst

# Execute with inputs
constellation run pipeline.cst --input text="Hello, World!"

# Generate DAG visualization
constellation viz pipeline.cst | dot -Tpng > dag.png

# Server operations
constellation server health
constellation server metrics

# Deploy with canary releases
constellation deploy push pipeline.cst
constellation deploy canary pipeline.cst --percent 10
constellation deploy promote my-pipeline
```

### CI/CD Integration

```yaml
# GitHub Actions example
- name: Validate pipelines
  run: |
    cs install io.constellation:constellation-cli_3:0.6.0
    for f in pipelines/*.cst; do
      constellation compile "$f" --json || exit 1
    done
```

All commands support `--json` for machine-readable output and deterministic exit codes for automation.

---

## Defining Functions in Scala

Functions called from constellation-lang are implemented as **Modules** in Scala. This gives you full language power for the implementation while the DSL handles composition and type checking.

### Pure Functions

For simple transformations without side effects:

```scala
case class TextInput(text: String)
case class TextOutput(result: String)

val uppercase = ModuleBuilder
  .metadata("Uppercase", "Convert text to uppercase", 1, 0)
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build
```

### Functions with IO

For HTTP calls, database access, or any side effects, use `cats.effect.IO`:

```scala
case class CustomerInput(customerId: String)
case class CustomerOutput(name: String, tier: String)

val fetchCustomer = ModuleBuilder
  .metadata("FetchCustomer", "Fetch customer from API", 1, 0)
  .implementation[CustomerInput, CustomerOutput] { input =>
    IO {
      val response = httpClient.get(s"/customers/${input.customerId}")
      CustomerOutput(response.name, response.tier)
    }
  }
  .build
```

### Why Scala for Functions?

1. **Full ecosystem access**: Use any JVM library - HTTP clients, database drivers, caching
2. **Type derivation**: Case class fields automatically map to constellation-lang types
3. **IO control**: Explicit effect handling with Cats Effect
4. **Testability**: Unit test modules independently before composing them
5. **Performance**: JVM optimization, no interpreter overhead for function bodies

---

## Setting Up the Runtime

### 1. Define Your Modules

```scala
package myapp

import io.constellation._
import io.constellation.lang.semantic._
import cats.effect.IO

object MyModules {
  // Module implementation
  case class CustomerInput(customerId: String)
  case class CustomerOutput(name: String, tier: String)

  val fetchCustomer = ModuleBuilder
    .metadata("FetchCustomer", "Fetch customer data", 1, 0)
    .implementation[CustomerInput, CustomerOutput] { input =>
      IO { /* your HTTP call, DB query, etc */ }
    }
    .build

  // Type signature for the compiler
  val fetchCustomerSig = FunctionSignature(
    name = "FetchCustomer",
    params = List("customerId" -> SemanticType.SString),
    returns = SemanticType.SRecord(Map(
      "name" -> SemanticType.SString,
      "tier" -> SemanticType.SString
    )),
    moduleName = "FetchCustomer"
  )

  def allModules = Map("FetchCustomer" -> fetchCustomer)
  def allSignatures = List(fetchCustomerSig)
}
```

### 2. Start the HTTP Server

```scala
package myapp

import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompilerBuilder
import io.constellation.http.ConstellationServer
import io.constellation.stdlib.StdLib

object Server extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      // Initialize the runtime
      constellation <- ConstellationImpl.init

      // Register modules for execution
      _ <- MyModules.allModules.values.toList.traverse(constellation.setModule)

      // Build compiler with your functions + stdlib
      compiler = MyModules.allSignatures
        .foldLeft(StdLib.registerAll(LangCompilerBuilder()))((b, sig) => b.withFunction(sig))
        .withModules(StdLib.allModules ++ MyModules.allModules)
        .build

      // Start HTTP server
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withPort(8080)
        .run
    } yield ()
}
```

### 3. API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/modules` | GET | List registered modules |
| `/compile` | POST | Compile constellation-lang source |
| `/run` | POST | Compile and execute in one call |
| `/pipelines` | GET | List stored pipelines |
| `/pipelines/{ref}` | GET | Get pipeline metadata |
| `/execute` | POST | Execute a stored pipeline |
| `/lsp` | WebSocket | Language Server Protocol for IDE support |

---

## Type System

### Record Operations

```
# Merge records (right side wins on conflicts)
combined = order + customer + { timestamp: now() }

# Project specific fields
response = combined[id, name, total]

# Field access
customerId = order.customerId
```

### List Operations

Operations on `List<Record>` apply element-wise:

```
in orders: List<{ id: String, amount: Float }>
in taxRate: Float

# Add tax to EVERY order
withTax = orders + { tax: multiply(orders.amount, taxRate) }

# Extract field from EVERY order
ids = orders.id  # Type: List<String>

# Project from EVERY order
summaries = orders[id, amount]  # Type: List<{ id: String, amount: Float }>
```

### Higher-Order Functions

```
in items: List<{ price: Float, quantity: Int }>

expensive = filter(items, (item) => gt(item.price, 100))
totals = map(items, (item) => multiply(item.price, item.quantity))
allInStock = all(items, (item) => gt(item.quantity, 0))
```

### Optional and Conditional

```
# Guard expression - returns Optional<T>
discount = ApplyDiscount(order) when gt(order.total, 1000)

# Coalesce - provide fallback
finalDiscount = discount ?? 0

# Branching
tier = branch {
  gt(points, 1000) -> "gold",
  gt(points, 500) -> "silver",
  otherwise -> "bronze"
}
```

---

## IDE Support

The VSCode extension provides:
- **Autocomplete**: Type `order.` to see available fields
- **Inline errors**: Red squiggles on type mismatches
- **Hover types**: Mouse over any variable to see its type
- **DAG visualization**: See your pipeline as a graph

Install from `vscode-extension/` or build with `npm install && npm run compile`.

---

## Performance

Built on Scala 3 and Cats Effect with minimal orchestration overhead. The numbers below measure pure engine cost — scheduling, fiber management, data flow — using no-op modules (`toUpperCase` / `toLowerCase`) that complete in nanoseconds.

### Orchestration Overhead

| Pipeline Size | Avg Latency | Per-Node Overhead |
|---------------|-------------|-------------------|
| 1 module | 1.06 ms | 1.06 ms |
| 3 modules | 1.67 ms | 0.56 ms |
| 10 modules | 3.01 ms | 0.30 ms |
| 50 modules | 7.40 ms | 0.15 ms |
| 100 modules | 14.67 ms | 0.15 ms |

Per-node overhead converges to **~0.15 ms** at scale — your services are the bottleneck, not the engine.

### Sustained Load (10,000 executions)

| Metric | Value |
|--------|-------|
| p50 latency | 0.06 ms |
| p99 latency | 0.49 ms |
| Heap growth | None (stable at ~95 MB) |
| p99 drift over time | < 1x (no degradation) |

### Parallelism

| Topology | Modules | Latency |
|----------|---------|---------|
| 100-node chain (sequential) | 100 | < 5 s |
| 500-node chain (sequential) | 500 | < 30 s |
| 50 branches x 10 depth (parallel) | 500 | < 30 s |
| 1,000 concurrent submissions | 1,000 | Bounded by scheduler |

**Key properties:**
- **Automatic parallelization**: Independent branches run concurrently on Cats Effect fibers
- **Bounded memory**: Heap stays flat across 10K+ executions — no leaks
- **Stable tail latency**: p99 does not degrade over sustained load

> Methodology: All numbers from `sbt "runtime/testOnly *ExecutionBenchmark"` and
> `sbt "runtime/testOnly *SustainedLoadTest"`. See [Performance Benchmarks](docs/dev/performance-benchmarks.md) for full details.

---

## Installation

Constellation Engine is published to Maven Central. Add the modules you need to your `build.sbt`:

```scala
val constellationVersion = "0.6.0"

libraryDependencies ++= Seq(
  "io.github.vledicfranco" %% "constellation-core"          % constellationVersion,
  "io.github.vledicfranco" %% "constellation-runtime"       % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-compiler" % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-stdlib"   % constellationVersion
)
```

Add the HTTP server module if you want to expose pipelines via REST API:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-http-api" % constellationVersion
```

### Available Modules

| Module | artifactId | Description |
|--------|------------|-------------|
| Core | `constellation-core` | Type system, foundational types |
| Runtime | `constellation-runtime` | DAG execution engine |
| Lang AST | `constellation-lang-ast` | Syntax tree definitions |
| Lang Parser | `constellation-lang-parser` | Text to AST parser |
| Lang Compiler | `constellation-lang-compiler` | AST to executable DAG |
| Lang Stdlib | `constellation-lang-stdlib` | Built-in functions |
| Lang LSP | `constellation-lang-lsp` | Language Server Protocol |
| HTTP API | `constellation-http-api` | REST API + WebSocket LSP |
| CLI | `constellation-lang-cli` | Command-line interface |

### Quick Start (Example App)

To try the included example application:

```bash
git clone https://github.com/VledicFranco/constellation-engine.git
cd constellation-engine
make compile
make server  # Starts on http://localhost:8080
```

---

## Deployment

Constellation is an embeddable library — you integrate it into your own application. The artifacts below are **reference examples** using the included `ExampleServer`.

### Fat JAR

```bash
make assembly
java -jar modules/example-app/target/scala-3.3.4/constellation-*.jar
```

### Docker

```bash
# Build and run
make docker-build
make docker-run    # http://localhost:8080

# Or with docker compose
docker compose up
```

### Kubernetes

```bash
kubectl apply -f deploy/k8s/
```

Manifests include liveness/readiness probes, resource limits, and a ConfigMap for all `CONSTELLATION_*` environment variables. See `deploy/k8s/` for details.

---

## Documentation

📖 **Full documentation:** [vledicfranco.github.io/constellation-engine](https://vledicfranco.github.io/constellation-engine/)

| Resource | Description |
|----------|-------------|
| [Getting Started](https://vledicfranco.github.io/constellation-engine/docs/getting-started/introduction) | Quick start guide and tutorial |
| [Language Reference](https://vledicfranco.github.io/constellation-engine/docs/language/) | Full syntax and type system |
| [API Reference](https://vledicfranco.github.io/constellation-engine/docs/api-reference/) | HTTP API, programmatic API, LSP |
| [Cookbook](https://vledicfranco.github.io/constellation-engine/docs/cookbook/) | Runnable examples and recipes |

### 🤖 For LLMs/AI Agents

If you're an AI agent helping users with Constellation, use the **[LLM-specialized documentation](https://vledicfranco.github.io/constellation-engine/docs/llm/)** at `/docs/llm/`. It's optimized for AI consumption with:
- Task-oriented navigation (quick "I need to..." → file mappings)
- Complete working code examples
- Decision matrices and mental models
- Context-window efficient organization

---

## Requirements

- JDK 17+
- SBT 1.10+
- Node.js 18+ (for VSCode extension)

## License

[MIT](LICENSE)
