# Optimization 09: Timeout Tuning

**Priority:** 9 (Lower Priority - Situational)
**Expected Gain:** Reduced resource waste, better failure detection
**Complexity:** Low
**Status:** Not Implemented

---

## Problem Statement

The runtime uses fixed default timeouts:

```scala
// Runtime.scala:247, 257

val moduleTimeout = 3.seconds   // How long a module can run
val inputsTimeout = 6.seconds   // How long to wait for inputs
```

These defaults are suboptimal:

| Scenario | Default | Optimal | Issue |
|----------|---------|---------|-------|
| Fast CPU module | 3s | 100ms | Wastes resources on hangs |
| ML inference | 3s | 30s | Times out valid requests |
| External API call | 3s | 10s | May need longer |
| Input from slow module | 6s | Depends | May be too short/long |

---

## Proposed Solution

Allow per-module and per-DAG timeout configuration based on profiling data.

### Implementation

#### Step 1: Module-Level Timeout Configuration

```scala
// ModuleBuilder.scala additions

case class TimeoutConfig(
  execution: FiniteDuration = 3.seconds,
  inputWait: FiniteDuration = 6.seconds
)

// In ModuleBuilder
def timeout(execution: FiniteDuration): ModuleBuilder[I, O] = {
  copy(timeoutConfig = timeoutConfig.copy(execution = execution))
}

def inputTimeout(wait: FiniteDuration): ModuleBuilder[I, O] = {
  copy(timeoutConfig = timeoutConfig.copy(inputWait = wait))
}

// Usage
val fastModule = ModuleBuilder
  .metadata("FastHash", "Quick hash computation", 1, 0)
  .timeout(100.millis)  // Should complete very fast
  .implementationPure[Input, Output](...)
  .build

val slowModule = ModuleBuilder
  .metadata("MLInference", "Run ML model", 1, 0)
  .timeout(30.seconds)  // ML inference can be slow
  .inputTimeout(10.seconds)  // But inputs should arrive quickly
  .implementationIO[Input, Output](...)
  .build
```

#### Step 2: DAG-Level Timeout Override

```scala
// DagSpec additions

case class DagSpec(
  // ... existing fields
  globalTimeout: Option[FiniteDuration] = None,
  moduleTimeouts: Map[String, TimeoutConfig] = Map.empty
)

// In LangCompiler or HTTP API
def compile(
  source: String,
  timeoutOverrides: Map[String, FiniteDuration] = Map.empty
): Either[CompileError, DagSpec] = {
  // Apply overrides to compiled DAG
  baseCompile(source).map { dag =>
    dag.copy(moduleTimeouts = timeoutOverrides.map { case (name, duration) =>
      name -> TimeoutConfig(execution = duration)
    })
  }
}
```

#### Step 3: Runtime Timeout Resolution

```scala
// Runtime.scala modifications

private def resolveTimeout(
  moduleSpec: ModuleNodeSpec,
  module: Module.Uninitialized,
  dagSpec: DagSpec
): TimeoutConfig = {

  // Priority: DAG override > Module config > Global default
  dagSpec.moduleTimeouts.get(moduleSpec.name)
    .orElse(Some(module.timeoutConfig))
    .getOrElse(TimeoutConfig())
}

private def executeModule(
  module: Module.Runnable,
  dagSpec: DagSpec
): IO[Module.Status] = {
  val timeout = resolveTimeout(module.spec, module.uninitialized, dagSpec)

  module.run
    .timeout(timeout.execution)
    .attempt
    .map {
      case Right(result) => Module.Status.Completed(result)
      case Left(_: TimeoutException) => Module.Status.TimedOut(timeout.execution)
      case Left(error) => Module.Status.Failed(error)
    }
}
```

---

## Adaptive Timeout Learning

Automatically adjust timeouts based on historical execution times:

```scala
// New: AdaptiveTimeoutManager.scala

class AdaptiveTimeoutManager(
  historySize: Int = 100,
  percentile: Double = 99.0,
  safetyMultiplier: Double = 2.0
) {

  // Track execution times per module
  private val history: mutable.Map[String, CircularBuffer[Long]] =
    mutable.Map.empty

  def recordExecution(moduleName: String, durationNanos: Long): Unit = {
    val buffer = history.getOrElseUpdate(moduleName, new CircularBuffer(historySize))
    buffer.add(durationNanos)
  }

  def suggestTimeout(moduleName: String): Option[FiniteDuration] = {
    history.get(moduleName).flatMap { buffer =>
      if (buffer.size < 10) {
        None  // Not enough data
      } else {
        val sorted = buffer.toArray.sorted
        val p99Index = (sorted.length * percentile / 100).toInt
        val p99Nanos = sorted(math.min(p99Index, sorted.length - 1))
        Some((p99Nanos * safetyMultiplier).nanos)
      }
    }
  }

  def getRecommendedTimeouts: Map[String, FiniteDuration] = {
    history.keys.flatMap { name =>
      suggestTimeout(name).map(name -> _)
    }.toMap
  }
}

// Usage in Runtime
def executeModule(...): IO[Module.Status] = {
  val baseTimeout = resolveTimeout(...)
  val adaptiveTimeout = timeoutManager.suggestTimeout(module.name)
    .getOrElse(baseTimeout.execution)

  module.run
    .timed
    .flatMap { case (duration, result) =>
      IO.delay(timeoutManager.recordExecution(module.name, duration.toNanos))
        .as(result)
    }
    .timeout(adaptiveTimeout)
}
```

---

## Timeout Categories

Define timeout presets for common module types:

```scala
object TimeoutPresets {

  // CPU-bound, fast operations
  val Fast = TimeoutConfig(
    execution = 100.millis,
    inputWait = 1.second
  )

  // Standard computation
  val Standard = TimeoutConfig(
    execution = 3.seconds,
    inputWait = 6.seconds
  )

  // I/O operations (database, file system)
  val IO = TimeoutConfig(
    execution = 10.seconds,
    inputWait = 10.seconds
  )

  // External API calls
  val External = TimeoutConfig(
    execution = 30.seconds,
    inputWait = 10.seconds
  )

  // ML inference (GPU operations)
  val MLInference = TimeoutConfig(
    execution = 60.seconds,
    inputWait = 30.seconds
  )

  // Batch processing
  val Batch = TimeoutConfig(
    execution = 5.minutes,
    inputWait = 1.minute
  )
}

// Usage
val module = ModuleBuilder
  .metadata("CallOpenAI", "Call OpenAI API", 1, 0)
  .timeoutPreset(TimeoutPresets.External)
  .implementationIO[Input, Output](...)
  .build
```

---

## HTTP API Configuration

Allow timeout specification in API requests:

```scala
// ApiModels.scala

case class ExecuteRequest(
  source: String,
  inputs: Map[String, Json],
  // Timeout configuration
  globalTimeout: Option[FiniteDuration] = None,
  moduleTimeouts: Map[String, FiniteDuration] = Map.empty
)

// Example request
{
  "source": "in x: Int\nout y: Int\ny = SlowModule(x)",
  "inputs": {"x": 42},
  "globalTimeout": "60s",
  "moduleTimeouts": {
    "SlowModule": "30s"
  }
}
```

---

## Monitoring and Alerts

Track timeout occurrences for operational visibility:

```scala
// TimeoutMetrics.scala

class TimeoutMetrics {
  private val timeouts = new ConcurrentHashMap[String, AtomicLong]()
  private val executions = new ConcurrentHashMap[String, AtomicLong]()

  def recordExecution(moduleName: String): Unit = {
    executions.computeIfAbsent(moduleName, _ => new AtomicLong(0)).incrementAndGet()
  }

  def recordTimeout(moduleName: String): Unit = {
    timeouts.computeIfAbsent(moduleName, _ => new AtomicLong(0)).incrementAndGet()
  }

  def timeoutRate(moduleName: String): Double = {
    val total = executions.getOrDefault(moduleName, new AtomicLong(0)).get()
    val timedOut = timeouts.getOrDefault(moduleName, new AtomicLong(0)).get()
    if (total == 0) 0.0 else timedOut.toDouble / total
  }

  // Alert if timeout rate exceeds threshold
  def modulesNeedingAttention(threshold: Double = 0.01): List[String] = {
    executions.keySet().asScala.filter(name => timeoutRate(name) > threshold).toList
  }
}
```

---

## Configuration File

```hocon
# application.conf

constellation.timeouts {
  # Global defaults
  default {
    execution = 3s
    input-wait = 6s
  }

  # Per-category defaults
  categories {
    fast {
      execution = 100ms
      input-wait = 1s
    }
    ml-inference {
      execution = 60s
      input-wait = 30s
    }
  }

  # Per-module overrides
  modules {
    "OpenAIEmbedding" {
      execution = 30s
    }
    "FastHash" {
      execution = 50ms
    }
  }

  # Adaptive learning
  adaptive {
    enabled = true
    history-size = 100
    percentile = 99
    safety-multiplier = 2.0
  }
}
```

---

## Implementation Checklist

- [ ] Add `TimeoutConfig` to `ModuleSpec`
- [ ] Update `ModuleBuilder` with timeout configuration methods
- [ ] Add timeout override support to `DagSpec`
- [ ] Update `Runtime` to use resolved timeouts
- [ ] Implement `AdaptiveTimeoutManager`
- [ ] Add timeout presets for common module types
- [ ] Update HTTP API to accept timeout configuration
- [ ] Add timeout metrics and monitoring
- [ ] Write configuration loader for timeout settings

---

## Files to Modify

| File | Changes |
|------|---------|
| `modules/runtime/.../ModuleBuilder.scala` | Timeout configuration |
| `modules/core/.../Spec.scala` | Add timeout fields |
| `modules/runtime/.../Runtime.scala` | Timeout resolution |
| New: `modules/runtime/.../AdaptiveTimeoutManager.scala` | Learning |
| `modules/http-api/.../ApiModels.scala` | Request timeout fields |
| New: `modules/runtime/.../TimeoutMetrics.scala` | Monitoring |

---

## Related Optimizations

- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Pool timeout managers
- [Quick Wins](./12-quick-wins.md) - Basic timeout profiling
