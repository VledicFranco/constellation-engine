# ExecutionListener

> **Path**: `organon/features/extensibility/execution-listener.md`
> **Parent**: [extensibility/](./README.md)

Interface for observing DAG execution lifecycle events. Use for audit logs, event streaming, dashboard updates, or triggering downstream workflows.

## Components Involved

| Component | Role | File Path |
|-----------|------|-----------|
| `ExecutionListener` | SPI trait definition | `modules/runtime/src/main/scala/io/constellation/spi/ExecutionListener.scala` |
| `ExecutionListener.noop` | Zero-overhead default | `modules/runtime/src/main/scala/io/constellation/spi/ExecutionListener.scala` |
| `ExecutionListener.composite` | Multi-listener combinator | `modules/runtime/src/main/scala/io/constellation/spi/ExecutionListener.scala` |
| `CompositeExecutionListener` | Internal composite implementation | `modules/runtime/src/main/scala/io/constellation/spi/ExecutionListener.scala` |
| `ConstellationBackends` | Backend bundle configuration | `modules/runtime/src/main/scala/io/constellation/spi/ConstellationBackends.scala` |
| `TracerProvider` | Related: distributed tracing | `modules/runtime/src/main/scala/io/constellation/spi/TracerProvider.scala` |

## Trait API

```scala
package io.constellation.spi

import cats.effect.IO
import java.util.UUID

trait ExecutionListener {
  /** Called when a DAG execution starts. */
  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit]

  /** Called when a module begins executing within a DAG. */
  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit]

  /** Called when a module completes successfully. */
  def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit]

  /** Called when a module fails with an error. */
  def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit]

  /** Called when a DAG execution completes (success or failure). */
  def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit]

  /** Called when a DAG execution is cancelled. Default: no-op. */
  def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] = IO.unit
}

object ExecutionListener {
  /** No-op implementation (default). */
  val noop: ExecutionListener

  /** Combine multiple listeners into one. */
  def composite(listeners: ExecutionListener*): ExecutionListener
}
```

## Method Reference

| Method | When Called | Parameters |
|--------|-------------|------------|
| `onExecutionStart` | DAG begins | executionId, dagName |
| `onModuleStart` | Module begins | executionId, moduleId, moduleName |
| `onModuleComplete` | Module succeeds | executionId, moduleId, moduleName, durationMs |
| `onModuleFailed` | Module throws | executionId, moduleId, moduleName, error |
| `onExecutionComplete` | DAG finishes | executionId, dagName, succeeded, durationMs |
| `onExecutionCancelled` | Cancelled | executionId, dagName |

## Event Timeline

```
onExecutionStart(execId, "my-pipeline")
  |-- onModuleStart(execId, mod1Id, "Trim")
  |   `-- onModuleComplete(execId, mod1Id, "Trim", 2)
  |-- onModuleStart(execId, mod2Id, "Uppercase")    <- parallel if independent
  |   `-- onModuleComplete(execId, mod2Id, "Uppercase", 5)
  `-- onModuleStart(execId, mod3Id, "WordCount")
      `-- onModuleFailed(execId, mod3Id, "WordCount", exception)
onExecutionComplete(execId, "my-pipeline", succeeded=false, 15)
```

### Event Ordering Guarantees

| Guarantee | Description |
|-----------|-------------|
| `onExecutionStart` | Always called before any `onModule*` events |
| `onModuleStart` | Always called before corresponding `onModuleComplete`/`onModuleFailed` |
| `onExecutionComplete` | Always called after all `onModule*` events |
| Parallel modules | `onModuleStart` events may interleave for independent modules |
| Cancellation | `onExecutionCancelled` called instead of `onExecutionComplete` when cancelled |

## Example: Kafka Event Publisher

Publish execution events to a Kafka topic for downstream consumers:

```scala
import io.constellation.spi.ExecutionListener
import fs2.kafka._
import cats.effect.IO
import io.circe.syntax._
import io.circe.generic.auto._
import java.util.UUID

case class ExecutionEvent(
  eventType: String,
  executionId: String,
  dagName: Option[String],
  moduleName: Option[String],
  moduleId: Option[String],
  durationMs: Option[Long],
  succeeded: Option[Boolean],
  error: Option[String],
  timestamp: Long
)

class KafkaExecutionListener(producer: KafkaProducer[IO, String, String], topic: String)
    extends ExecutionListener {

  private def publish(event: ExecutionEvent): IO[Unit] = {
    val record = ProducerRecord(topic, event.executionId, event.asJson.noSpaces)
    producer.produce(ProducerRecords.one(record)).flatten.void
  }

  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
    publish(ExecutionEvent(
      eventType = "execution.started",
      executionId = executionId.toString,
      dagName = Some(dagName),
      moduleName = None,
      moduleId = None,
      durationMs = None,
      succeeded = None,
      error = None,
      timestamp = System.currentTimeMillis()
    ))

  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
    publish(ExecutionEvent(
      eventType = "module.started",
      executionId = executionId.toString,
      dagName = None,
      moduleName = Some(moduleName),
      moduleId = Some(moduleId.toString),
      durationMs = None,
      succeeded = None,
      error = None,
      timestamp = System.currentTimeMillis()
    ))

  def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
    publish(ExecutionEvent(
      eventType = "module.completed",
      executionId = executionId.toString,
      dagName = None,
      moduleName = Some(moduleName),
      moduleId = Some(moduleId.toString),
      durationMs = Some(durationMs),
      succeeded = Some(true),
      error = None,
      timestamp = System.currentTimeMillis()
    ))

  def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
    publish(ExecutionEvent(
      eventType = "module.failed",
      executionId = executionId.toString,
      dagName = None,
      moduleName = Some(moduleName),
      moduleId = Some(moduleId.toString),
      durationMs = None,
      succeeded = Some(false),
      error = Some(error.getMessage),
      timestamp = System.currentTimeMillis()
    ))

  def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
    publish(ExecutionEvent(
      eventType = "execution.completed",
      executionId = executionId.toString,
      dagName = Some(dagName),
      moduleName = None,
      moduleId = None,
      durationMs = Some(durationMs),
      succeeded = Some(succeeded),
      error = None,
      timestamp = System.currentTimeMillis()
    ))

  override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] =
    publish(ExecutionEvent(
      eventType = "execution.cancelled",
      executionId = executionId.toString,
      dagName = Some(dagName),
      moduleName = None,
      moduleId = None,
      durationMs = None,
      succeeded = Some(false),
      error = Some("Execution cancelled"),
      timestamp = System.currentTimeMillis()
    ))
}
```

## Example: Audit Logger

Write execution events to a structured audit log:

```scala
import io.constellation.spi.ExecutionListener
import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.util.UUID

class AuditLogListener extends ExecutionListener {

  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
    logger.info(s"AUDIT: Execution started | executionId=$executionId dagName=$dagName")

  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
    logger.info(s"AUDIT: Module started | executionId=$executionId moduleId=$moduleId moduleName=$moduleName")

  def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
    logger.info(s"AUDIT: Module completed | executionId=$executionId moduleId=$moduleId moduleName=$moduleName durationMs=$durationMs")

  def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
    logger.warn(s"AUDIT: Module failed | executionId=$executionId moduleId=$moduleId moduleName=$moduleName error=${error.getMessage}")

  def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
    logger.info(s"AUDIT: Execution completed | executionId=$executionId dagName=$dagName succeeded=$succeeded durationMs=$durationMs")

  override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] =
    logger.warn(s"AUDIT: Execution cancelled | executionId=$executionId dagName=$dagName")
}
```

## Example: WebSocket Notifier

Push execution events to connected dashboards:

```scala
import io.constellation.spi.ExecutionListener
import cats.effect.{IO, Ref}
import fs2.concurrent.Topic
import io.circe.syntax._
import io.circe.generic.auto._
import java.util.UUID

case class DashboardEvent(eventType: String, payload: Map[String, String])

class WebSocketNotifier(topic: Topic[IO, DashboardEvent]) extends ExecutionListener {

  private def notify(eventType: String, payload: Map[String, String]): IO[Unit] =
    topic.publish1(DashboardEvent(eventType, payload)).void

  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
    notify("execution.started", Map(
      "executionId" -> executionId.toString,
      "dagName" -> dagName
    ))

  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
    notify("module.started", Map(
      "executionId" -> executionId.toString,
      "moduleId" -> moduleId.toString,
      "moduleName" -> moduleName
    ))

  def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
    notify("module.completed", Map(
      "executionId" -> executionId.toString,
      "moduleId" -> moduleId.toString,
      "moduleName" -> moduleName,
      "durationMs" -> durationMs.toString
    ))

  def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
    notify("module.failed", Map(
      "executionId" -> executionId.toString,
      "moduleId" -> moduleId.toString,
      "moduleName" -> moduleName,
      "error" -> error.getMessage
    ))

  def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
    notify("execution.completed", Map(
      "executionId" -> executionId.toString,
      "dagName" -> dagName,
      "succeeded" -> succeeded.toString,
      "durationMs" -> durationMs.toString
    ))
}
```

## Combining Listeners

Use `composite` to invoke multiple listeners for each event:

```scala
import io.constellation.spi.ExecutionListener

val kafkaListener = new KafkaExecutionListener(producer, "constellation-events")
val auditListener = new AuditLogListener()
val dashboardListener = new WebSocketNotifier(topic)

val listener = ExecutionListener.composite(
  kafkaListener,
  auditListener,
  dashboardListener
)

ConstellationImpl.builder()
  .withListener(listener)
  .build()
```

### Composite Behavior

- All listeners receive every event
- Events are dispatched sequentially to listeners
- If one listener throws, the error is swallowed and others still execute
- Order of listeners determines callback order

## Wiring

```scala
import io.constellation.ConstellationImpl
import io.constellation.spi.{ConstellationBackends, ExecutionListener}

val listener = new KafkaExecutionListener(producer, "events")

val constellation = ConstellationImpl.builder()
  .withListener(listener)
  .build()

// Or via ConstellationBackends
val backends = ConstellationBackends(
  listener = listener,
  metrics = myMetrics,
  cache = Some(myCache)
)

val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()
```

## Gotchas

| Issue | Mitigation |
|-------|------------|
| **Fire-and-forget** | Exceptions do not propagate. Implement retry logic or dead-letter queues in your listener if needed. |
| **Performance** | Callbacks are on the hot path. Use async producers, connection pools, and batching where possible. |
| **Concurrency** | Module callbacks may fire concurrently for independent modules. Ensure listeners are thread-safe. |
| **Duration accuracy** | `durationMs` is wall-clock time including scheduling overhead, not just execution time. |
| **Cancellation** | `onExecutionCancelled` only fires when explicitly cancelled, not on timeout (which is treated as failure). |
| **Memory** | Storing events in-memory (e.g., for WebSocket) can cause memory pressure. Use bounded buffers. |
| **Ordering** | Events for the same execution are ordered, but events across executions may interleave. |

## Common Patterns

### Circuit Breaker for External Services

```scala
class ResilientListener(underlying: ExecutionListener, maxFailures: Int) extends ExecutionListener {

  private val failures = new java.util.concurrent.atomic.AtomicInteger(0)
  private val circuitOpen = new java.util.concurrent.atomic.AtomicBoolean(false)

  private def withCircuitBreaker(action: IO[Unit]): IO[Unit] =
    if (circuitOpen.get()) IO.unit
    else action.handleErrorWith { error =>
      IO {
        if (failures.incrementAndGet() >= maxFailures) {
          circuitOpen.set(true)
        }
      }
    }

  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
    withCircuitBreaker(underlying.onExecutionStart(executionId, dagName))

  // ... other methods similarly wrapped
}
```

### Batching for High-Throughput

```scala
class BatchingListener(
  underlying: ExecutionListener,
  batchSize: Int,
  flushInterval: FiniteDuration
) extends ExecutionListener {

  // Use a queue to batch events and flush periodically
  // Implementation depends on your concurrency primitives

  // This is a conceptual example - real implementation would use
  // fs2.Stream, Ref, and a background fiber for flushing
}
```

## See Also

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why SPI over inheritance
- [ETHOS.md](./ETHOS.md) - Constraints for modifying SPIs
- [metrics-provider.md](./metrics-provider.md) - Numeric metrics for dashboards
- [execution-storage.md](./execution-storage.md) - Persist execution snapshots
