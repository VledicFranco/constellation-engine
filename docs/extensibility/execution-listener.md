# ExecutionListener

> **Path**: `docs/extensibility/execution-listener.md`
> **Parent**: [extensibility/](./README.md)

Interface for observing DAG execution lifecycle events. Use for audit logs, event streaming, dashboard updates, or triggering downstream workflows.

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

## Example: Kafka Event Publisher

Publish execution events to a Kafka topic:

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
    publish(ExecutionEvent("execution.started", executionId.toString,
      Some(dagName), None, None, None, None, System.currentTimeMillis()))

  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
    publish(ExecutionEvent("module.started", executionId.toString,
      None, Some(moduleName), None, None, None, System.currentTimeMillis()))

  def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
    publish(ExecutionEvent("module.completed", executionId.toString,
      None, Some(moduleName), Some(durationMs), Some(true), None, System.currentTimeMillis()))

  def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
    publish(ExecutionEvent("module.failed", executionId.toString,
      None, Some(moduleName), None, Some(false), Some(error.getMessage), System.currentTimeMillis()))

  def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
    publish(ExecutionEvent("execution.completed", executionId.toString,
      Some(dagName), None, Some(durationMs), Some(succeeded), None, System.currentTimeMillis()))
}
```

## Wiring

```scala
val producerSettings = ProducerSettings[IO, String, String]
  .withBootstrapServers("localhost:9092")

KafkaProducer.resource(producerSettings).use { producer =>
  val listener = new KafkaExecutionListener(producer, "constellation-events")

  ConstellationImpl.builder()
    .withListener(listener)
    .build()
}
```

## Combining Listeners

Use `composite` to invoke multiple listeners:

```scala
val listener = ExecutionListener.composite(
  kafkaListener,
  auditListener,
  loggingListener
)

ConstellationImpl.builder()
  .withListener(listener)
  .build()
```

All listeners receive every event. If one throws, the others still execute.

## Gotchas

- **Fire-and-forget**: Exceptions do not propagate. Implement retry logic if needed.
- **Performance**: Callbacks are on the hot path. Use async producers and connection pools.
- **Concurrency**: Module callbacks may fire concurrently for independent modules.
- **Duration accuracy**: `durationMs` is wall-clock time including scheduling overhead.
- **Cancellation**: `onExecutionCancelled` only fires when explicitly cancelled.

## See Also

- [metrics-provider.md](./metrics-provider.md) - Numeric metrics for dashboards
- [stores.md](./stores.md) - Persist execution snapshots
