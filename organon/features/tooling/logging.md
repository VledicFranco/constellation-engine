# Logging Architecture & Implementation

## Overview

Constellation Engine implements targeted, conservative logging for debuggability without adding noise to library users or production deployments. Logging is **configured via environment variable** and uses **Logback/SLF4J** as the underlying framework.

---

## Core Pattern

### IO-Based Logging (async contexts)

For all IO-based classes:

```scala
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[MyClass])

// Usage in for comprehension
for {
  _ <- logger.info(s"Starting operation")
  result <- doSomething()
  _ <- logger.info(s"Completed: $result")
} yield result
```

**Why this pattern:**
- Preserves IO semantics (logging is an effect)
- Composable with other IO operations
- Thread-safe for concurrent execution

### Sync Logging (synchronous contexts)

For synchronous/pure classes:

```scala
import org.slf4j.{Logger => SLF4JLogger, LoggerFactory}

private val logger: SLF4JLogger = LoggerFactory.getLogger(getClass)

// Usage in imperative code
def compile(source: String): Either[Error, Result] = {
  val result = doCompile(source)
  result.left.foreach { error =>
    logger.error(s"Compilation failed: ${error.message}")
  }
  result
}
```

**Why this pattern:**
- No IO wrapper needed for pure code
- Simpler than wrapping in IO
- Raw SLF4J is lightweight

---

## Critical Execution Paths

### 1. Module Execution Failures

**Location:** `modules/runtime/src/main/scala/io/constellation/Runtime.scala`

**Module:** `object Module`

**Logger:**
```scala
private val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("io.constellation.Runtime")
```

**Log Points:**

| Condition | Level | Message Format | Why |
|-----------|-------|----------------|-----|
| Timeout after `inputsTimeout` | `WARN` | `Module '{name}' timed out after {duration}` | Module is blocking; needs intervention |
| Exception during execution | `ERROR` | `Module '{name}' failed: {exception.message}` | Full stacktrace available; root cause needed |

**Rationale:**
- WARN for timeout: actionable (module is hung, not crashing)
- ERROR for exception: stacktrace needed for debugging
- Module name included so operator knows *which* module failed
- Hash not included (module name is the identifier)

**Code context:**
```scala
.handleErrorWith {
  case _: TimeoutException =>
    logger.warn(s"Module '${partialSpec.metadata.name}' timed out after ${partialSpec.config.inputsTimeout}") *>
    runtime.setModuleStatus(moduleId, Module.Status.Timed(...))
  case e =>
    logger.error(e)(s"Module '${partialSpec.metadata.name}' failed: ${e.getMessage}") *>
    runtime.setModuleStatus(moduleId, Module.Status.Failed(e))
}
```

### 2. Pipeline Execution Lifecycle

**Location:** `modules/runtime/src/main/scala/io/constellation/impl/ConstellationImpl.scala`

**Class:** `ConstellationImpl`

**Logger:**
```scala
private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[ConstellationImpl])
```

**Log Points:**

| Event | Level | Message Format | Why |
|-------|-------|----------------|-----|
| Execution starts | `INFO` | `Executing pipeline '{name}' [{hash-prefix}]` | High-signal: shows what's running |
| Execution completes | `INFO` | `Pipeline '{name}' [{hash-prefix}] completed: {status} in {elapsed}ms` | Timing + status for observability |

**Rationale:**
- INFO level: low-frequency events, actionable for ops
- Pipeline name: human-readable identifier
- Hash prefix (first 12 chars): for correlating with stored pipelines
- Elapsed time: performance observability
- Status field: quick success/failure indicator

**Code context:**
```scala
val pipelineName = dagSpec.metadata.name
val hashPrefix = loaded.structuralHash.take(12)

for {
  _ <- logger.info(s"Executing pipeline '$pipelineName' [$hashPrefix]")
  // ... execution ...
  sig = buildDataSignature(...)
  elapsed = java.time.Duration.between(startedAt, Instant.now()).toMillis
  _ <- logger.info(s"Pipeline '$pipelineName' [$hashPrefix] completed: ${sig.status} in ${elapsed}ms")
} yield sig
```

### 3. Compilation Failures

**Location:** `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala`

**Class:** `LangCompilerImpl`

**Logger:**
```scala
private val logger: SLF4JLogger = LoggerFactory.getLogger(getClass)
```

**Log Points:**

| Event | Level | Message Format | Why |
|-------|-------|----------------|-----|
| Compilation fails | `ERROR` | `Compilation failed for '{name}': {count} error(s) — {details}` | Detailed diagnostics for fixing |

**Rationale:**
- ERROR level: compilation failure is always actionable
- Error count: quick summary of issue magnitude
- Error messages: included so log becomes searchable
- DAG name: identifies which pipeline failed
- Failures are logged, but success is not (too verbose in normal operation)

**Code context:**
```scala
def compile(source: String, dagName: String): Either[List[CompileError], CompilationOutput] = {
  val result = for {
    program <- ConstellationParser.parse(source).left.map(List(_))
    // ... phases ...
  } yield { /* success */ }

  result.left.foreach { errors =>
    logger.error(
      s"Compilation failed for '$dagName': ${errors.size} error(s) — ${errors.map(_.message).mkString("; ")}"
    )
  }
  result
}
```

### 4. Cache Observability

**Location:** `modules/lang-compiler/src/main/scala/io/constellation/lang/CachingLangCompiler.scala`

**Class:** `CachingLangCompiler`

**Logger:**
```scala
private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[CachingLangCompiler])
```

**Log Points:**

| Event | Level | Message Format | Why |
|-------|-------|----------------|-----|
| Cache hit | `DEBUG` | `Compilation cache hit for '{name}' [src:{hash-prefix}]` | Avoids recompilation; invisible in production |
| Cache miss | `DEBUG` | `Compilation cache miss for '{name}' — compiling` | Shows compilation is needed; hidden by default |

**Rationale:**
- DEBUG level: silent in production, visible only when explicitly enabled
- Hash prefix: identifies which source was cached
- Hit vs miss distinction: helps understand cache effectiveness
- DAG name: correlates with compilation logs above

**Code context:**
```scala
cache.get(dagName, sourceHash, registryHash).flatMap {
  case Some(cached) =>
    logger.debug(s"Compilation cache hit for '$dagName' [src:${sourceHash.take(8)}]") *>
    IO.pure(Right(cached))
  case None =>
    logger.debug(s"Compilation cache miss for '$dagName' — compiling") *>
    IO { underlying.compile(source, dagName) }.flatMap { result =>
      // ... cache and return ...
    }
}
```

---

## Configuration

### Environment Variable: CONSTELLATION_LOG_LEVEL

The logback.xml is already configured to respect this environment variable:

```xml
<logger name="io.constellation" level="${CONSTELLATION_LOG_LEVEL:-INFO}" />
```

**Valid values:**
- `DEBUG` — All debug messages visible (development)
- `INFO` — Normal operations (production default)
- `WARN` — Warnings and errors only
- `ERROR` — Errors only

**Behavior:**
- If not set, defaults to `INFO`
- Applies to all Constellation packages
- Root logger stays at WARN to suppress noise from dependencies

### Why Not Soft Log Levels?

We could use conditional logging like:

```scala
if (logger.isDebugEnabled) {
  logger.debug(s"expensive computation: ${computeDebugInfo()}")
}
```

We don't because:
1. **String interpolation is pre-computed** — `s"..."` always evaluates
2. **Negligible overhead** — We don't log expensive data, just names/hashes
3. **Cleaner code** — No wrapping needed

---

## What We Don't Log

### Not Logged: Success Paths

- ✗ "Compilation succeeded" — Too verbose; success is default
- ✗ Each parsing phase completion — Too granular
- ✗ Each IR generation step — Too verbose for normal operation

**Rationale:** High-frequency events would create log noise. Failures are actionable; successes are silent unless needed for timing analysis.

### Not Logged: Internal Transforms

- ✗ Per-module type checking — Too granular
- ✗ Per-data-node computation — Would create 1000s of log lines
- ✗ Cache eviction details — Implementation detail

**Rationale:** Debuggable with external tools (breakpoints, profilers); not needed in production logs.

### Not Logged: HTTP Middleware

- ✗ Per-request logging — Delegated to middleware if needed
- ✗ Route handler entry/exit — Would duplicate execution logs

**Rationale:** HTTP layer logs are optional; execution logs already provide request tracing via pipeline name + hash.

---

## Integration with Existing Logging

### Existing logback.xml Structure

```xml
<!-- Constellation packages - configurable -->
<logger name="io.constellation" level="${CONSTELLATION_LOG_LEVEL:-INFO}" />

<!-- HTTP server - fixed at INFO -->
<logger name="org.http4s" level="INFO" />

<!-- Suppress http4s internals -->
<logger name="org.http4s.ember" level="WARN" />

<!-- Root logger - fixed at WARN -->
<root level="WARN">
  <appender-ref ref="CONSOLE" />
</root>
```

**How it works:**
- All our logging goes through `io.constellation` hierarchy
- Gets the configured level from environment variable
- Child loggers inherit unless overridden
- Root stays WARN to suppress dependency noise

### Adding New Logging

When adding logging to a new class:

1. **Choose logger pattern:**
   - IO-based code → `Slf4jLogger.getLoggerFromClass[IO]`
   - Sync code → `LoggerFactory.getLogger(getClass)`

2. **Pick appropriate level:**
   - `WARN` — Unexpected but recoverable (timeouts, retries)
   - `ERROR` — Failures, exceptions with stacktraces
   - `INFO` — Major lifecycle events (start, completion)
   - `DEBUG` — Diagnostic details (cache hits, internal state)

3. **Include context:**
   - Entity name (module name, pipeline name, etc.)
   - Relevant IDs (hash prefix for correlation)
   - Don't include user data (input values)

4. **Keep it searchable:**
   - Message format should be parseable by grep/log aggregation
   - Include error counts, types in error messages
   - No random UUIDs without correlation key

---

## Testing Logging

### Unit Test Pattern

```scala
import org.typelevel.log4cats.testing.StructuredTestingLogger

"Module execution" should "log errors on failure" in {
  val logger = StructuredTestingLogger.impl[IO]()

  for {
    result <- myModuleLogic(logger)
  } yield {
    // Assert log entries
    val errorLogs = logger.getMessages(Level.Error)
    errorLogs should contain(
      "Module 'MyModule' failed"
    )
  }
}
```

### Integration Test Pattern

```scala
"Server execution" should "log pipeline start and completion" in {
  val output = runServerCapturingStderr {
    constellation.run(loaded, inputs, options)
  }

  output should contain("Executing pipeline 'MyPipeline'")
  output should contain("Pipeline 'MyPipeline' completed: success")
}
```

---

## Performance Notes

### Logging Overhead

| Level | Overhead | Notes |
|-------|----------|-------|
| `DEBUG` | ~5-10% | String interpolations evaluated, log lookups |
| `INFO` | ~1-2% | Most messages filtered, minimal overhead |
| `WARN` | <1% | Very few events |
| `ERROR` | Varies | Includes stacktrace capture (non-trivial for some failures) |

### Optimization: Use Lazy Messages

For expensive debug messages:

```scala
logger.debug(s"expensive computation: ${verySlowFunction()}")  // ✗ Always evaluates
logger.debug(s"result: ${result.summary}")                     // ✓ Cheap interpolation
```

We don't log expensive data, so all existing messages are fine.

---

## Coordination with Observability SPI

Constellation has an **SPI for observability** (metrics, tracing, listeners):

- **MetricsProvider** — Performance metrics (cache hits, request timing)
- **ExecutionListener** — Execution lifecycle events
- **TracerProvider** — Distributed tracing integration

Logging complements but doesn't replace these:

| Tool | Purpose | Use Case |
|------|---------|----------|
| Logs | Human-readable debugging | Error investigation, troubleshooting |
| Metrics | Quantitative performance | Dashboards, alerting, SLOs |
| Tracing | Request flow | Distributed systems debugging |
| Listeners | Internal events | Custom observability integration |

All four work together for complete observability.

