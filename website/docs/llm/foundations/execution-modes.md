---
title: "Execution Modes"
sidebar_position: 4
description: "Hot vs cold execution, HTTP vs embedded API, and performance characteristics"
---

# Execution Modes

This guide explains the different ways to execute Constellation pipelines, comparing hot vs cold execution patterns and HTTP vs embedded API deployment models. Understanding these modes helps you make informed architectural decisions based on your performance, scalability, and deployment requirements.

## Quick Reference

| Mode | What Gets Reused | Typical Latency | Use Case |
|------|------------------|----------------|----------|
| **Cold Execution** | Nothing | Parse + Compile + Execute | One-off scripts, CI/CD pipelines |
| **Hot Execution (Compile-Once)** | Compiled pipeline | Execute only (~1-10ms overhead) | Production workloads, low-latency APIs |
| **Hot Execution (Cached)** | Compiled pipeline + cache hits | <5ms for unchanged source | Interactive tools, LSP, high-throughput services |

| Deployment | Architecture | Overhead | Use Case |
|-----------|--------------|----------|----------|
| **Embedded API** | In-process library calls | ~0.15ms/node | Batch jobs, embedded ML pipelines, tight control loops |
| **HTTP API** | Network + JSON serialization | +2-10ms per request | Multi-language clients, microservices, dashboard UI |

---

## Part 1: Hot vs Cold Execution

### Cold Execution

**Definition:** Every execution starts from source text. The pipeline is compiled from scratch each time.

**Flow:**
```
Source Text → Parse → TypeCheck → IR Generation → Optimize → DAG Compile → Execute → Results
    (5-50ms)     (5-50ms)    (5-20ms)      (5-20ms)    (10-50ms)    (1-10000ms)
```

**Example:**
```scala
import cats.effect.IO
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.lang.LangCompiler
import io.constellation.TypeSystem.CValue._

val source = """
  in text: String
  result = Uppercase(text)
  out result
"""

// Full cold execution on every call
def coldExecute(input: String): IO[String] =
  for {
    constellation <- ConstellationImpl.init
    _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
    compiler      = StdLib.compiler

    // 1. Compile from source (cold)
    compiled <- IO.fromEither(
      compiler.compile(source, "my-pipeline")
        .leftMap(errs => new RuntimeException(errs.map(_.message).mkString("\n")))
    )

    // 2. Execute
    sig <- constellation.run(compiled.pipeline, Map("text" -> VString(input)))

    // 3. Extract result
    result <- IO.fromOption(sig.outputs.get("result").collect { case VString(s) => s })(
      new NoSuchElementException("Missing output")
    )
  } yield result

// Every call pays full compilation cost
coldExecute("hello").unsafeRunSync()  // ~100ms first time
coldExecute("world").unsafeRunSync()  // ~100ms again (no reuse)
```

**When to Use Cold Execution:**
- **One-off scripts**: Shell scripts, CI/CD pipelines, cron jobs
- **Dynamic code generation**: When pipeline source changes every execution
- **Prototyping**: Fast iteration during development (though caching is faster)
- **Low execution frequency**: When compilation cost is amortized over long intervals

**Performance Characteristics:**

| Program Size | Parse | TypeCheck | Compile | Total Overhead |
|--------------|-------|-----------|---------|----------------|
| Small (10 nodes) | <5ms | <5ms | <10ms | ~20-30ms |
| Medium (50 nodes) | <50ms | <50ms | <50ms | ~100-150ms |
| Large (100 nodes) | <200ms | <200ms | <150ms | ~300-500ms |

For reference, these are target values from `dev/benchmarks/performance-benchmarks.md`.

**Limitations:**
- High latency unsuitable for request/response APIs
- Redundant work if source text doesn't change
- Cannot leverage compilation caching
- Module registry must be rebuilt each time

---

### Hot Execution (Compile-Once)

**Definition:** Compile once, execute many times. The compiled `LoadedPipeline` is stored and reused.

**Flow (First Execution):**
```
Source Text → Parse → TypeCheck → IR Gen → Optimize → DAG Compile → Store in PipelineStore
    (5-50ms)     (5-50ms)    (5-20ms)    (5-20ms)    (10-50ms)
                                                          ↓
                                                  (returns hash: abc123...)
```

**Flow (Subsequent Executions):**
```
Pipeline Reference (hash or alias) → PipelineStore Lookup → Execute → Results
                                         (<1ms)              (1-10000ms)
```

**Example:**
```scala
import cats.effect.IO
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.lang.LangCompiler
import io.constellation.TypeSystem.CValue._
import io.constellation.ExecutionOptions

val source = """
  in text: String
  result = Uppercase(text)
  out result
"""

// One-time setup (application startup)
val setup: IO[(io.constellation.Constellation, String)] =
  for {
    constellation <- ConstellationImpl.init
    _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
    compiler      = StdLib.compiler

    // Compile once
    compiled <- IO.fromEither(
      compiler.compile(source, "uppercase-pipeline")
        .leftMap(errs => new RuntimeException(errs.map(_.message).mkString("\n")))
    )

    // Store the pipeline image (content-addressed storage)
    hash <- constellation.PipelineStore.store(compiled.pipeline.image)

    // Create human-readable alias
    _ <- constellation.PipelineStore.alias("uppercase", hash)

  } yield (constellation, hash)

// Production usage (executes many times)
def hotExecute(
  constellation: io.constellation.Constellation,
  pipelineRef: String,
  input: String
): IO[String] =
  for {
    // Execute by reference (hash or alias) - no compilation
    sig <- constellation.run(
      pipelineRef,  // "uppercase" or hash
      Map("text" -> VString(input)),
      ExecutionOptions()
    )

    result <- IO.fromOption(sig.outputs.get("result").collect { case VString(s) => s })(
      new NoSuchElementException("Missing output")
    )
  } yield result

// Usage pattern
for {
  (constellation, hash) <- setup  // ~100ms (once at startup)

  // All executions reuse compiled pipeline
  r1 <- hotExecute(constellation, "uppercase", "hello")  // ~1-10ms overhead
  r2 <- hotExecute(constellation, "uppercase", "world")  // ~1-10ms overhead
  r3 <- hotExecute(constellation, hash, "test")          // ~1-10ms overhead (by hash)
} yield ()
```

**PipelineStore Operations:**

```scala
// Store pipeline by structural hash (content-addressed)
val hash: IO[String] = constellation.PipelineStore.store(pipelineImage)
// Returns: "a3f7c2e8b..." (SHA-256 of DAG structure)

// Create human-readable alias
constellation.PipelineStore.alias("my-pipeline", hash)

// Resolve alias to hash
val resolvedHash: IO[Option[String]] = constellation.PipelineStore.resolve("my-pipeline")

// Retrieve by hash
val byHash: IO[Option[PipelineImage]] = constellation.PipelineStore.get(hash)

// Retrieve by alias
val byName: IO[Option[PipelineImage]] = constellation.PipelineStore.getByName("my-pipeline")

// Execute by reference (alias or hash)
constellation.run("my-pipeline", inputs, ExecutionOptions())
constellation.run(s"sha256:$hash", inputs, ExecutionOptions())
```

**When to Use Hot Execution:**
- **Production APIs**: Request/response services where latency matters
- **High-throughput batch processing**: Millions of executions with same pipeline
- **Long-running services**: Web servers, microservices, daemon processes
- **Stable pipeline definitions**: When source text rarely changes

**Performance Characteristics:**

| Operation | Latency | Notes |
|-----------|---------|-------|
| PipelineStore lookup (in-memory) | <1ms | Hash-based retrieval |
| Pipeline rehydration | <1ms | Reconstruct LoadedPipeline from image |
| DAG execution overhead | ~0.15ms/node | Pure orchestration cost (no module time) |

For a 10-node pipeline where each module takes 5ms, total execution time:
- **Cold**: ~150ms (compile) + 50ms (execute) = **200ms**
- **Hot**: 50ms (execute only) = **50ms** — **4x faster**

**Key Benefits:**
- Eliminates redundant parsing, type-checking, and compilation
- Predictable latency (no compilation jitter)
- Enables content-addressed caching (structural hash deduplication)
- PipelineStore can be backed by persistent storage (survives restarts)

---

### Hot Execution with Caching

**Definition:** Combines compile-once with automatic cache-hit detection. If source text hasn't changed, the compiler returns a cached `LoadedPipeline` immediately.

**Flow (Cache Hit):**
```
Source Text → Syntactic Hash → PipelineStore Syntactic Index Lookup → Return Cached Pipeline
    (instant)      (<1ms)                  (<1ms)                          (total: <5ms)
```

**Flow (Cache Miss):**
```
Source Text → Parse → TypeCheck → IR Gen → Optimize → DAG Compile → Store & Index → Return Pipeline
    (5-50ms)     (5-50ms)    (5-20ms)    (5-20ms)    (10-50ms)       (<1ms)
```

**How It Works:**

The `PipelineStore` maintains a **syntactic index** that maps `(syntacticHash, registryHash) → structuralHash`:

1. **Syntactic Hash**: SHA-256 of the source text (unchanged source = same hash)
2. **Registry Hash**: Hash of the function registry (detects when available modules change)
3. **Structural Hash**: SHA-256 of the compiled DAG structure

When you compile source:
- Compiler computes syntactic hash immediately (no parsing required)
- Checks syntactic index for `(syntacticHash, registryHash)` pair
- If found: returns cached `LoadedPipeline` (cache hit)
- If not found: compiles, stores result, indexes `(syntactic, registry) → structural`

**Example:**
```scala
import io.constellation.lang.{CachingLangCompiler, LangCompiler}
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib

// Setup with caching compiler
val setup: IO[(io.constellation.Constellation, CachingLangCompiler)] =
  for {
    constellation <- ConstellationImpl.init
    _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
    baseCompiler  = StdLib.compiler

    // Wrap with caching layer
    cachingCompiler = CachingLangCompiler.withDefaults(baseCompiler)
  } yield (constellation, cachingCompiler)

val source = """
  in text: String
  result = Uppercase(text)
  out result
"""

// Usage pattern demonstrating cache hits
for {
  (constellation, compiler) <- setup

  // First compilation (cold - cache miss)
  compiled1 <- compiler.compileIO(source, "uppercase")
  // Took ~100ms (full compilation pipeline)

  // Store the compiled pipeline
  hash <- constellation.PipelineStore.store(compiled1.pipeline.image)

  // Second compilation of identical source (cache hit)
  compiled2 <- compiler.compileIO(source, "uppercase")
  // Took <5ms (cache hit - no parsing/typechecking/compilation)

  // Third compilation with modified source (cache miss)
  modifiedSource = """
    in text: String
    result = Lowercase(text)  # Changed: Uppercase → Lowercase
    out result
  """
  compiled3 <- compiler.compileIO(modifiedSource, "lowercase")
  // Took ~100ms (cache miss - different syntactic hash)

} yield ()
```

**Cache Performance:**

| Scenario | Latency | Speedup |
|----------|---------|---------|
| Cold compile (medium program) | ~100ms | Baseline |
| Warm cache hit | <5ms | **20x faster** |
| Cache hit after server restart (persistent store) | <5ms | **20x faster** |
| Source modified (cache miss) | ~100ms | No speedup (expected) |

**When to Use Cached Execution:**
- **LSP (Language Server Protocol)**: Every keystroke triggers recompilation
- **Interactive dashboards**: Users edit and re-run pipelines frequently
- **Development environments**: Rapid edit-test cycles
- **Multi-tenant platforms**: Many users running identical pipelines

**Example: LSP Autocomplete Performance**

```scala
// User types: "result = Upper|" (cursor at |)
// LSP needs to compile to provide autocomplete suggestions

// Without caching:
// Every keystroke = full recompilation = 100ms per keystroke = unusable

// With caching:
// Syntactic hash changes only when source changes
// Cache hit = <5ms = 50ms autocomplete response = smooth UX
```

**Cache Invalidation:**

The cache automatically invalidates when:
1. **Source text changes**: Different syntactic hash triggers cache miss
2. **Function registry changes**: Adding/removing modules changes registry hash
3. **Module signatures change**: Version bump in module metadata changes registry hash

No manual invalidation is required — cache correctness is guaranteed by the hash-based indexing.

**Monitoring Cache Effectiveness:**

```bash
# Check cache hit rate via HTTP metrics endpoint
curl http://localhost:8080/metrics | jq .cache

# Example output:
{
  "cache": {
    "hits": 847,
    "misses": 23,
    "hitRate": 0.973,
    "size": 45
  }
}
```

Target hit rate: **>80%** for production workloads with stable pipelines.

---

### Execution Mode Comparison Table

| Aspect | Cold Execution | Hot Execution (Compile-Once) | Hot Execution (Cached) |
|--------|----------------|------------------------------|------------------------|
| **Compilation** | Every execution | Once at startup | On cache miss only |
| **Typical Overhead** | 50-500ms | 1-10ms | <5ms (hit) / 50-500ms (miss) |
| **Memory Usage** | Low (no persistent state) | Medium (PipelineStore) | High (PipelineStore + cache) |
| **Startup Time** | Instant | Medium (pre-compile) | High (warm cache) |
| **Runtime Latency** | High (compile + execute) | Low (execute only) | Lowest (cache hit + execute) |
| **Source Changes** | Always fresh | Requires recompile | Auto-detects via hash |
| **Best For** | Scripts, CI/CD | Production APIs | LSP, dashboards, dev tools |

---

### Decision Matrix: Which Execution Mode?

**Choose Cold Execution if:**
- ✅ Execution frequency is low (minutes to hours between runs)
- ✅ Source text changes every execution (dynamic generation)
- ✅ Minimal memory footprint is required
- ✅ Startup time matters more than runtime latency
- ❌ You need sub-50ms response times
- ❌ You execute the same pipeline thousands of times

**Choose Hot Execution (Compile-Once) if:**
- ✅ Same pipeline executes many times (hundreds to millions)
- ✅ Pipeline definition is stable (source rarely changes)
- ✅ Latency matters (APIs, request/response services)
- ✅ You can afford startup cost (pre-compilation)
- ❌ Source text changes frequently (then use caching instead)
- ❌ You need to support interactive editing (then use caching)

**Choose Hot Execution (Cached) if:**
- ✅ Source text changes frequently but repeats (interactive editing)
- ✅ Multiple users may run identical pipelines (multi-tenant)
- ✅ You need to support LSP or live tooling
- ✅ Sub-5ms compilation latency is critical
- ✅ Memory for cache is available
- ❌ Every execution uses unique source text (no cache benefit)
- ❌ Minimal memory footprint is required

---

## Part 2: HTTP API vs Embedded API

### Embedded API (In-Process)

**Definition:** Constellation runs as a library within your application. All interactions are direct Scala method calls.

**Architecture:**
```
┌─────────────────────────────────────┐
│  Your Application (JVM Process)     │
│  ┌────────────────────────────────┐ │
│  │  Application Code              │ │
│  │    ↓                           │ │
│  │  constellation.run(...)        │ │  Direct method calls
│  │    ↓                           │ │  No network overhead
│  │  Constellation Engine          │ │  No serialization
│  │    ↓                           │ │
│  │  Module Execution              │ │
│  │    ↓                           │ │
│  │  Results (CValue)              │ │
│  └────────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Example:**
```scala
import cats.effect._
import cats.implicits._
import io.constellation._
import io.constellation.TypeSystem._
import io.constellation.TypeSystem.CValue._
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib

object EmbeddedExample extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // 1. Initialize Constellation instance (embedded in your process)
      constellation <- ConstellationImpl.init

      // 2. Register modules
      _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      // 3. Compile pipeline
      compiler = StdLib.compiler
      compiled <- IO.fromEither(
        compiler.compile("""
          in numbers: List[Int]
          sum = Sum(numbers)
          avg = Average(numbers)
          out sum
          out avg
        """, "stats-pipeline").leftMap(errs =>
          new RuntimeException(errs.map(_.message).mkString("\n"))
        )
      )

      // 4. Execute directly (no HTTP, no serialization)
      sig <- constellation.run(
        compiled.pipeline,
        Map("numbers" -> VList(List(VLong(10), VLong(20), VLong(30))))
      )

      // 5. Access results as typed Scala values
      sum <- IO.fromOption(sig.outputs.get("sum").collect { case VLong(n) => n })(
        new NoSuchElementException("Missing sum")
      )
      avg <- IO.fromOption(sig.outputs.get("avg").collect { case VFloat(d) => d })(
        new NoSuchElementException("Missing avg")
      )

      _ <- IO.println(s"Sum: $sum, Average: $avg")

    } yield ()
}
```

**Performance Characteristics:**

| Operation | Latency | Notes |
|-----------|---------|-------|
| Method call overhead | <0.01ms | Direct JVM method dispatch |
| Input conversion | <0.1ms | Scala values → CValue (zero-copy for most types) |
| DAG execution overhead | ~0.15ms/node | Pure orchestration (measured in benchmarks) |
| Output extraction | <0.1ms | CValue → Scala values |

For a 10-node pipeline where each module takes 5ms:
- **Execution**: 10 nodes × 5ms/node = 50ms (module logic)
- **Orchestration**: 10 nodes × 0.15ms/node = 1.5ms (DAG overhead)
- **Total**: **51.5ms** (pure engine cost)

**Advantages:**
- ✅ **Lowest latency**: No network round-trip, no JSON serialization
- ✅ **Type safety**: Compile-time guarantees for inputs/outputs
- ✅ **Direct control**: Fine-grained configuration (scheduler, backends, lifecycle)
- ✅ **Efficient resource usage**: Shared memory, no IPC overhead
- ✅ **Easier debugging**: Single process, stack traces work normally

**Trade-offs:**
- ❌ **JVM only**: Cannot call from Python, JavaScript, or other languages
- ❌ **Same process**: Crashes in modules crash your application
- ❌ **Deployment coupling**: Must redeploy application to update Constellation version
- ❌ **No cross-network execution**: Cannot distribute across machines

**When to Use Embedded API:**
- Batch processing jobs (ETL pipelines, data transformations)
- ML inference servers (where latency is critical)
- Embedded systems (IoT devices running JVM)
- Tight control loops (real-time systems, robotics)
- Single-tenant applications (desktop apps, CLI tools)

**Production Configuration:**

```scala
import io.constellation.impl.ConstellationImpl
import io.constellation.execution.{GlobalScheduler, ConstellationLifecycle}
import io.constellation.spi.ConstellationBackends
import scala.concurrent.duration._

// Full production setup
GlobalScheduler.bounded(
  maxConcurrency = 16,
  starvationTimeout = 30.seconds
).use { scheduler =>

  ConstellationLifecycle.create.flatMap { lifecycle =>

    val constellation = ConstellationImpl.builder()
      .withScheduler(scheduler)
      .withBackends(ConstellationBackends(
        metrics  = myPrometheusMetrics,
        tracer   = myOtelTracer,
        listener = myKafkaListener,
        cache    = Some(myRedisCache)
      ))
      .withDefaultTimeout(60.seconds)
      .withLifecycle(lifecycle)
      .build()

    // ... use constellation ...

    // Graceful shutdown
    lifecycle.shutdown(drainTimeout = 30.seconds)
  }
}
```

---

### HTTP API (Out-of-Process)

**Definition:** Constellation runs as a standalone HTTP server. Clients interact via REST API over the network.

**Architecture:**
```
┌──────────────────┐                    ┌──────────────────────────┐
│  Client App      │  HTTP POST         │  Constellation Server    │
│  (Any Language)  │  /compile          │  (JVM Process)           │
│  ┌─────────────┐ │ ──────────────────>│  ┌────────────────────┐  │
│  │ Python      │ │                    │  │ HTTP Routes        │  │
│  │ JavaScript  │ │  JSON Response     │  │   ↓                │  │
│  │ Go          │ │ <──────────────────│  │ Constellation API  │  │
│  │ Ruby        │ │                    │  │   ↓                │  │
│  └─────────────┘ │  HTTP POST         │  │ Module Execution   │  │
└──────────────────┘  /execute          │  │   ↓                │  │
                     ──────────────────>│  │ JSON Response      │  │
                                        │  └────────────────────┘  │
                                        └──────────────────────────┘
```

**Example (Server):**
```scala
import cats.effect._
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.http._

object HttpServerExample extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      constellation <- ConstellationImpl.init
      _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
      compiler      = StdLib.compiler

      // Start HTTP server
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .withPort(8080)
        .withDashboard  // Optional: web UI for interactive testing
        .run

    } yield ()
}
```

**Example (Client - curl):**
```bash
# Compile a pipeline
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "name": "stats-pipeline",
    "source": "in numbers: List[Int]\nsum = Sum(numbers)\nout sum"
  }'

# Response:
# {
#   "success": true,
#   "structuralHash": "a3f7c2e8b...",
#   "syntacticHash": "d9e1f4a2c...",
#   "name": "stats-pipeline"
# }

# Execute by name
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{
    "ref": "stats-pipeline",
    "inputs": {
      "numbers": [10, 20, 30]
    }
  }'

# Response:
# {
#   "success": true,
#   "outputs": {
#     "sum": 60
#   },
#   "status": "completed",
#   "executionId": "f8a2c3d4-..."
# }
```

**Example (Client - Python):**
```python
import requests
import json

# Compile pipeline
compile_response = requests.post('http://localhost:8080/compile', json={
    'name': 'stats-pipeline',
    'source': '''
        in numbers: List[Int]
        sum = Sum(numbers)
        avg = Average(numbers)
        out sum
        out avg
    '''
})

pipeline_hash = compile_response.json()['structuralHash']

# Execute pipeline
execute_response = requests.post('http://localhost:8080/execute', json={
    'ref': 'stats-pipeline',  # or use pipeline_hash
    'inputs': {
        'numbers': [10, 20, 30]
    }
})

results = execute_response.json()['outputs']
print(f"Sum: {results['sum']}, Average: {results['avg']}")
```

**Example (Client - JavaScript/TypeScript):**
```typescript
// Compile pipeline
const compileResponse = await fetch('http://localhost:8080/compile', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    name: 'stats-pipeline',
    source: `
      in numbers: List[Int]
      sum = Sum(numbers)
      out sum
    `
  })
});

const { structuralHash } = await compileResponse.json();

// Execute pipeline
const executeResponse = await fetch('http://localhost:8080/execute', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    ref: 'stats-pipeline',
    inputs: { numbers: [10, 20, 30] }
  })
});

const { outputs } = await executeResponse.json();
console.log(`Sum: ${outputs.sum}`);
```

**Performance Characteristics:**

| Operation | Latency | Notes |
|-----------|---------|-------|
| Network round-trip (same datacenter) | 0.5-2ms | Varies by network topology |
| Network round-trip (cross-region) | 10-50ms | Significant overhead |
| JSON serialization (request) | 0.1-1ms | Depends on input size |
| JSON deserialization (request) | 0.1-1ms | Circe parsing |
| DAG execution | 1ms + module time | Same as embedded |
| JSON serialization (response) | 0.1-1ms | Output encoding |
| JSON deserialization (response) | 0.1-1ms | Client parsing |

For a 10-node pipeline where each module takes 5ms:
- **Module execution**: 50ms (same as embedded)
- **Orchestration**: 1.5ms (same as embedded)
- **HTTP overhead**: 2-10ms (network + serialization)
- **Total**: **~53.5-61.5ms** (+ network latency)

**Typical overhead: +2-10ms per request** compared to embedded API.

**Advantages:**
- ✅ **Language-agnostic**: Call from Python, JavaScript, Go, etc.
- ✅ **Process isolation**: Module crashes don't affect client
- ✅ **Independent deployment**: Update Constellation without redeploying clients
- ✅ **Horizontal scaling**: Run multiple servers behind a load balancer
- ✅ **Built-in dashboard**: Web UI for testing and visualization

**Trade-offs:**
- ❌ **Higher latency**: Network + serialization overhead
- ❌ **JSON overhead**: Large inputs/outputs slow down significantly
- ❌ **No type safety**: Clients pass JSON (runtime errors possible)
- ❌ **More moving parts**: Network failures, load balancers, authentication

**When to Use HTTP API:**
- Multi-language environments (polyglot microservices)
- Multi-tenant platforms (SaaS applications)
- Web dashboards (browser-based clients)
- External integrations (third-party services)
- Distributed systems (cross-network execution)

**Production Configuration:**

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPort(8080)
  .withDashboard

  // Security hardening
  .withAuth(AuthConfig(apiKeys = Map(
    "admin-key" -> ApiRole.Admin,
    "app-key"   -> ApiRole.Execute
  )))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 200, burst = 40))

  // Health checks
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))

  // Pipeline management
  .withPipelineLoader(PipelineLoaderConfig(
    directory = java.nio.file.Paths.get(".constellation-pipelines"),
    filePattern = "*.cst"
  ))
  .withPersistentPipelineStore(java.nio.file.Paths.get(".constellation-store"))

  .run
```

**Available Endpoints:**

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/compile` | POST | Compile constellation-lang source to pipeline |
| `/execute` | POST | Execute pipeline by reference (hash or alias) |
| `/pipelines` | GET | List all stored pipelines |
| `/pipelines/:ref` | GET | Get pipeline details |
| `/pipelines/:ref` | DELETE | Delete a pipeline |
| `/modules` | GET | List available modules |
| `/health` | GET | Health check (uptime, status) |
| `/health/ready` | GET | Readiness probe (Kubernetes) |
| `/health/live` | GET | Liveness probe (Kubernetes) |
| `/metrics` | GET | Prometheus-style metrics |
| `/lsp` | WebSocket | Language Server Protocol (LSP) for editors |

**Authentication (opt-in):**

```bash
# Set API keys via environment variables
CONSTELLATION_API_KEYS="admin:Admin,app:Execute"

# Client must include X-API-Key header
curl -X POST http://localhost:8080/execute \
  -H "X-API-Key: admin" \
  -H "Content-Type: application/json" \
  -d '{ "ref": "my-pipeline", "inputs": {} }'
```

**Rate Limiting (opt-in):**

```bash
# Configure rate limits
CONSTELLATION_RATE_LIMIT_RPM=100  # 100 requests per minute per IP
CONSTELLATION_RATE_LIMIT_BURST=20  # Allow bursts of 20

# Exceeding limits returns HTTP 429 Too Many Requests
```

---

### Deployment Comparison Table

| Aspect | Embedded API | HTTP API |
|--------|--------------|----------|
| **Latency** | ~0.15ms/node overhead | +2-10ms per request |
| **Languages** | Scala/JVM only | Any (Python, JS, Go, Ruby, ...) |
| **Network** | No network (in-process) | HTTP/1.1 or HTTP/2 |
| **Serialization** | None (direct CValue) | JSON (Circe encoder/decoder) |
| **Type Safety** | Compile-time (Scala types) | Runtime (JSON validation) |
| **Isolation** | Same process (shared fate) | Separate process (fault isolation) |
| **Scalability** | Vertical (scale JVM) | Horizontal (multiple servers) |
| **Deployment** | Bundled with app | Independent service |
| **Debugging** | Easy (single process) | Harder (distributed tracing needed) |
| **Best For** | Latency-critical, JVM-only | Multi-language, distributed systems |

---

### Decision Matrix: HTTP vs Embedded?

**Choose Embedded API if:**
- ✅ Your application is written in Scala or another JVM language
- ✅ Sub-millisecond latency is critical (real-time systems)
- ✅ You process millions of executions per second (throughput-bound)
- ✅ Deployment coupling is acceptable (single binary)
- ✅ You need fine-grained control over scheduler/backends/lifecycle
- ❌ You need to call from non-JVM languages
- ❌ You want independent deployment of Constellation and clients

**Choose HTTP API if:**
- ✅ Clients use multiple languages (Python, JavaScript, Go, etc.)
- ✅ You need process isolation (module crashes shouldn't affect clients)
- ✅ Horizontal scaling is required (load balancer + multiple servers)
- ✅ You want a web dashboard for interactive testing
- ✅ You have a microservices architecture
- ❌ Network overhead is unacceptable (<1ms latency required)
- ❌ Large payloads make JSON serialization prohibitively slow

---

## Part 3: Combining Execution Modes

### Hot Execution + Embedded API (Best Performance)

**Use Case:** High-throughput batch processing, ML inference servers.

**Pattern:**
```scala
// Startup: compile once
for {
  constellation <- ConstellationImpl.init
  _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
  compiler      = StdLib.compiler

  compiled <- compiler.compileIO(source, "my-pipeline")
  hash     <- constellation.PipelineStore.store(compiled.pipeline.image)
  _        <- constellation.PipelineStore.alias("my-pipeline", hash)

  // Runtime: execute millions of times with zero compilation overhead
  _ <- Stream.iterate(0)(_ + 1)  // Infinite stream
    .evalMap { i =>
      constellation.run("my-pipeline", inputs(i), ExecutionOptions())
    }
    .compile
    .drain

} yield ()
```

**Performance:** ~0.15ms/node overhead + module execution time. No compilation, no network, no serialization.

---

### Hot Execution + HTTP API (Best Flexibility)

**Use Case:** Multi-tenant SaaS platforms, web applications.

**Pattern:**
```scala
// Server: pre-compile common pipelines at startup
for {
  constellation <- ConstellationImpl.init
  _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
  compiler      = StdLib.compiler

  // Pre-compile top 10 most-used pipelines
  _ <- commonPipelines.traverse { case (name, source) =>
    compiler.compileIO(source, name).flatMap { compiled =>
      constellation.PipelineStore.store(compiled.pipeline.image).flatMap { hash =>
        constellation.PipelineStore.alias(name, hash)
      }
    }
  }

  // Start HTTP server
  _ <- ConstellationServer.builder(constellation, compiler).run

} yield ()
```

**Clients:** Execute pre-compiled pipelines by name (no compilation overhead on each request).

**Performance:** ~2-10ms HTTP overhead + 0.15ms/node overhead + module execution time.

---

### Cached Execution + HTTP API (Best for Interactive Tools)

**Use Case:** LSP servers, web dashboards, live editors.

**Pattern:**
```scala
// Server with caching compiler
for {
  constellation <- ConstellationImpl.init
  _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)
  baseCompiler  = StdLib.compiler

  // Enable caching
  cachingCompiler = CachingLangCompiler.withDefaults(baseCompiler)

  // Start HTTP server with caching compiler
  _ <- ConstellationServer.builder(constellation, cachingCompiler).run

} yield ()
```

**Client workflow:**
1. User edits source in web UI
2. Client POSTs to `/compile` on every change
3. Server returns cached result (<5ms) if source unchanged
4. Client executes pipeline via `/execute`

**Performance:** <5ms compile (cache hit) + 2-10ms HTTP overhead + execution time.

---

## Part 4: Advanced Patterns

### Hybrid: Embedded Execution with HTTP Management API

**Use Case:** Local high-performance execution with remote pipeline management.

**Architecture:**
```
┌─────────────────────────────────────────┐
│  Application (JVM)                      │
│  ┌──────────────────────────────────┐   │
│  │  Embedded Constellation           │   │  Hot execution
│  │    ↓                              │   │  (in-process, fast)
│  │  constellation.run(...)           │   │
│  └──────────────────────────────────┘   │
│         ↑                                │
│         │ Sync pipelines                 │
└─────────┼──────────────────────────────┬─┘
          │                                │
          │ HTTP (periodic sync)           │
          │                                │
          ↓                                ↓
┌──────────────────────────────────────────┐
│  Central Constellation Server            │
│  (Pipeline registry + compilation)       │
└──────────────────────────────────────────┘
```

**Pattern:**
```scala
// Application: embedded execution + periodic sync from HTTP registry
for {
  localConstellation <- ConstellationImpl.init
  _                  <- StdLib.allModules.values.toList.traverse(localConstellation.setModule)

  // Background fiber: sync pipelines from central server every 60 seconds
  _ <- Stream.fixedRate[IO](60.seconds).evalMap { _ =>
    for {
      // Fetch latest pipelines from HTTP registry
      response <- client.get("http://registry:8080/pipelines")
      pipelines <- response.as[List[PipelineMetadata]]

      // Update local PipelineStore
      _ <- pipelines.traverse { pm =>
        client.get(s"http://registry:8080/pipelines/${pm.hash}").flatMap { resp =>
          resp.as[PipelineImage].flatMap { image =>
            localConstellation.PipelineStore.store(image) *>
            localConstellation.PipelineStore.alias(pm.name, pm.hash)
          }
        }
      }
    } yield ()
  }.compile.drain.start

  // Foreground: execute locally (hot, embedded)
  _ <- localConstellation.run("my-pipeline", inputs, ExecutionOptions())

} yield ()
```

**Benefits:**
- ✅ Centralized pipeline management (single source of truth)
- ✅ Local execution performance (no network on hot path)
- ✅ Cache-friendly (pipelines update infrequently)

---

### Persistent PipelineStore (Survive Restarts)

**Use Case:** Production servers where pipelines should survive process restarts.

**Pattern:**
```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPersistentPipelineStore(java.nio.file.Paths.get(".constellation-store"))
  .withPipelineLoader(PipelineLoaderConfig(
    directory = java.nio.file.Paths.get(".constellation-pipelines"),
    filePattern = "*.cst"
  ))
  .run
```

**How it works:**
1. **Startup**: Scans `.constellation-pipelines/` for `*.cst` files
2. **Compilation**: Compiles each file and stores in PipelineStore
3. **Persistence**: Writes `PipelineStore` to `.constellation-store/` (JSON files)
4. **Restart**: Loads pipelines from `.constellation-store/` (no re-compilation)

**Directory structure:**
```
.constellation-pipelines/
  text-processing.cst
  data-analysis.cst
  ml-inference.cst

.constellation-store/
  images/
    a3f7c2e8b....json  # PipelineImage (structural hash)
    d9e1f4a2c....json
  aliases.json         # { "text-processing": "a3f7c2e8b...", ... }
  syntactic_index.json # { ("syntax-hash", "registry-hash"): "structural-hash" }
```

**Performance:**
- **Cold start (first boot)**: Compiles all `.cst` files (~100ms each)
- **Warm start (restart)**: Loads from `.constellation-store/` (<1ms per pipeline)

---

## Part 5: Performance Optimization Strategies

### Strategy 1: Pre-Compilation at Build Time

**Problem:** First request after deployment is slow (cold start).

**Solution:** Compile pipelines during Docker build.

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jre

# Copy application
COPY target/assembly.jar /app/constellation.jar
COPY pipelines/ /app/pipelines/

# Pre-compile pipelines during image build
RUN java -cp /app/constellation.jar io.constellation.cli.Main compile \
  --input /app/pipelines/ \
  --output /app/.constellation-store/

# Runtime: server loads pre-compiled pipelines
CMD ["java", "-jar", "/app/constellation.jar", "server", \
     "--pipeline-store", "/app/.constellation-store"]
```

**Result:** Zero cold-start latency (all pipelines pre-compiled in image).

---

### Strategy 2: Pipeline Versioning and Canary Deployments

**Problem:** Updating a pipeline affects all in-flight executions.

**Solution:** Version pipelines and use canary routing.

**Pattern:**
```scala
// Deploy new version alongside old version
for {
  // Old version (v1)
  _ <- constellation.PipelineStore.alias("my-pipeline-v1", oldHash)

  // New version (v2)
  compiled <- compiler.compileIO(newSource, "my-pipeline")
  newHash  <- constellation.PipelineStore.store(compiled.pipeline.image)
  _        <- constellation.PipelineStore.alias("my-pipeline-v2", newHash)

  // Canary: 90% traffic to v1, 10% to v2
  _ <- canaryRouter.setWeights("my-pipeline", Map(
    "my-pipeline-v1" -> 0.9,
    "my-pipeline-v2" -> 0.1
  ))

  // Clients execute "my-pipeline" (router chooses version)
  _ <- constellation.run("my-pipeline", inputs, ExecutionOptions())

} yield ()
```

**Benefits:**
- ✅ Gradual rollout (reduce blast radius)
- ✅ A/B testing (compare performance/accuracy)
- ✅ Instant rollback (flip weights to 100% old version)

---

### Strategy 3: Module Result Caching

**Problem:** Expensive modules (ML inference, DB queries) recompute for identical inputs.

**Solution:** Implement `CacheBackend` to cache module results.

**Pattern:**
```scala
import io.constellation.cache.CacheBackend
import scala.concurrent.duration._

// Implement CacheBackend (example: Redis)
val redisCache: CacheBackend = new RedisCacheBackend(redisClient)

// Configure Constellation with cache
val constellation = ConstellationImpl.builder()
  .withBackends(ConstellationBackends(
    cache = Some(redisCache)
  ))
  .build()

// Modules automatically cache results by (moduleName, inputs) hash
// No code changes required in modules
```

**Performance:**
- **Cache miss**: Full module execution (e.g., 500ms for ML inference)
- **Cache hit**: <5ms (Redis lookup)
- **Speedup**: 100x for expensive cached operations

---

### Strategy 4: Bounded Scheduler for Multi-Tenant Load

**Problem:** One tenant's heavy load starves other tenants' requests.

**Solution:** Use bounded scheduler with priority-based execution.

**Pattern:**
```scala
GlobalScheduler.bounded(
  maxConcurrency = 16,
  starvationTimeout = 30.seconds
).use { scheduler =>

  val constellation = ConstellationImpl.builder()
    .withScheduler(scheduler)
    .build()

  // Tenant A: high-priority (paying customer)
  constellation.run(pipelineA, inputsA, ExecutionOptions(priority = 80))

  // Tenant B: normal priority (free tier)
  constellation.run(pipelineB, inputsB, ExecutionOptions(priority = 50))

  // Tenant C: background (analytics job)
  constellation.run(pipelineC, inputsC, ExecutionOptions(priority = 20))
}
```

**Result:** Tenant A's requests complete first, Tenant B's requests are delayed, Tenant C's requests wait (but eventually run due to starvation prevention).

---

## Part 6: Troubleshooting

### Problem: High Latency in HTTP API

**Symptoms:**
- Requests take >100ms even for small pipelines
- Network latency is acceptable (<5ms)

**Diagnosis:**
```bash
# Check metrics endpoint
curl http://localhost:8080/metrics | jq .

# Look for:
# - High compilation times (cache not working?)
# - High queue depth (scheduler overloaded?)
# - Low cache hit rate (<80% is bad)
```

**Solutions:**
1. **Enable caching**: Use `CachingLangCompiler`
2. **Pre-compile pipelines**: Use `PipelineLoader` at startup
3. **Increase scheduler concurrency**: `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=32`
4. **Check module performance**: Profile module execution times

---

### Problem: Cache Not Hitting

**Symptoms:**
- Identical source text recompiles every time
- Cache hit rate is 0% or very low

**Diagnosis:**
```scala
// Check cache stats
compiler.cacheStats.flatMap { stats =>
  IO.println(s"Hits: ${stats.hits}, Misses: ${stats.misses}, Hit Rate: ${stats.hitRate}")
}
```

**Solutions:**
1. **Function registry changing**: Module versions or signatures changing invalidates cache
2. **Source whitespace differences**: Even spaces/newlines change syntactic hash
3. **Cache eviction**: LRU cache too small, increase max entries
4. **Wrong compiler instance**: Using different compiler instance per request (create once at startup)

---

### Problem: Embedded API Memory Leak

**Symptoms:**
- Heap usage grows unbounded
- GC pressure increases over time

**Diagnosis:**
```bash
# Heap dump
jmap -dump:format=b,file=heap.bin <pid>

# Analyze with VisualVM or Eclipse MAT
```

**Common Causes:**
1. **PipelineStore unbounded growth**: No eviction policy, every compilation stores forever
2. **Module state leaks**: Modules holding onto resources (unclosed connections, file handles)
3. **Listener accumulation**: Adding execution listeners without removing them

**Solutions:**
1. **Implement PipelineStore eviction**: Remove unused pipelines periodically
2. **Use `Resource` for modules**: Ensure cleanup on shutdown
3. **Remove listeners**: Unregister listeners when no longer needed

---

## Summary Table: Execution Mode Decision Matrix

| Scenario | Recommended Mode | Key Reason |
|----------|------------------|------------|
| One-off scripts | Cold Execution | Simplest, no state management |
| Production APIs (JVM) | Hot Execution + Embedded API | Lowest latency, highest throughput |
| Production APIs (polyglot) | Hot Execution + HTTP API | Language-agnostic, scalable |
| LSP / Interactive tools | Cached Execution + HTTP API | Sub-5ms compilation, responsive UX |
| Batch processing (millions) | Hot Execution + Embedded API | Zero overhead, maximum throughput |
| Multi-tenant SaaS | Cached Execution + HTTP API | Per-tenant isolation, cache sharing |
| ML inference server | Hot Execution + Embedded API + Cache | Low latency, result caching |
| Microservices | Hot Execution + HTTP API | Process isolation, independent deployment |
| CI/CD pipelines | Cold Execution | Fresh compilation, no state persistence |
| Dashboard / Web UI | Cached Execution + HTTP API | Interactive, browser clients |

---

## Next Steps

- [Embedding Guide](../../getting-started/embedding-guide.md) — Complete embedded API reference
- [HTTP API Reference](../../api-reference/http-api-overview.md) — Full endpoint documentation
