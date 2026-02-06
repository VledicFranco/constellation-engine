---
title: "Execution Listener"
sidebar_position: 3
description: "SPI guide for observing DAG execution events to send audit logs, metrics, or trigger workflows."
---

# ExecutionListener Integration Guide

## Overview

`ExecutionListener` is the SPI trait for observing DAG execution lifecycle events. Implement this trait to send events to message queues, write audit logs, update dashboards, or trigger downstream workflows.

All callbacks are fire-and-forget — exceptions are caught by the runtime and do not affect pipeline execution.

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
  val noop: ExecutionListener = new ExecutionListener {
    def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] = IO.unit
    def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] = IO.unit
    def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] = IO.unit
    def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] = IO.unit
    def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] = IO.unit
  }

  /** Combine multiple listeners into one. All are invoked for each event. */
  def composite(listeners: ExecutionListener*): ExecutionListener = ???
}
```

### Event Timeline

```
onExecutionStart(execId, "my-pipeline")
  ├─ onModuleStart(execId, mod1Id, "Trim")
  │  └─ onModuleComplete(execId, mod1Id, "Trim", 2)
  ├─ onModuleStart(execId, mod2Id, "Uppercase")    ← parallel with Trim if independent
  │  └─ onModuleComplete(execId, mod2Id, "Uppercase", 5)
  └─ onModuleStart(execId, mod3Id, "WordCount")
     └─ onModuleFailed(execId, mod3Id, "WordCount", exception)
onExecutionComplete(execId, "my-pipeline", succeeded=false, 15)
```

## Example 1: Kafka Event Publishing

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "com.github.fd4s" %% "fs2-kafka" % "3.2.0"
)
```

**Implementation:**

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

  override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] =
    publish(ExecutionEvent("execution.cancelled", executionId.toString,
      Some(dagName), None, None, None, None, System.currentTimeMillis()))
}
```

**Wiring:**

```scala
val producerSettings = ProducerSettings[IO, String, String]
  .withBootstrapServers("localhost:9092")

KafkaProducer.resource(producerSettings).use { producer =>
  val listener = new KafkaExecutionListener(producer, "constellation-events")

  val constellation = ConstellationImpl.builder()
    .withListener(listener)
    .build()

  // ... run application
}
```

## Example 2: Database Audit Log with Doobie

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"   % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC5",
  "org.postgresql" % "postgresql"    % "42.7.0"
)
```

**SQL schema:**

```sql
CREATE TABLE execution_audit (
  id            SERIAL PRIMARY KEY,
  event_type    VARCHAR(50) NOT NULL,
  execution_id  UUID NOT NULL,
  dag_name      VARCHAR(255),
  module_name   VARCHAR(255),
  duration_ms   BIGINT,
  succeeded     BOOLEAN,
  error_message TEXT,
  created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_execution_id ON execution_audit(execution_id);
CREATE INDEX idx_audit_dag_name ON execution_audit(dag_name);
```

**Implementation:**

```scala
import io.constellation.spi.ExecutionListener
import doobie._
import doobie.implicits._
import cats.effect.IO
import java.util.UUID

class DoobieAuditListener(xa: Transactor[IO]) extends ExecutionListener {

  private def insert(eventType: String, executionId: UUID, dagName: Option[String],
                     moduleName: Option[String], durationMs: Option[Long],
                     succeeded: Option[Boolean], error: Option[String]): IO[Unit] =
    sql"""INSERT INTO execution_audit (event_type, execution_id, dag_name, module_name, duration_ms, succeeded, error_message)
          VALUES ($eventType, $executionId, $dagName, $moduleName, $durationMs, $succeeded, $error)"""
      .update.run.transact(xa).void

  def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
    insert("execution.started", executionId, Some(dagName), None, None, None, None)

  def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
    insert("module.started", executionId, None, Some(moduleName), None, None, None)

  def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
    insert("module.completed", executionId, None, Some(moduleName), Some(durationMs), Some(true), None)

  def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
    insert("module.failed", executionId, None, Some(moduleName), None, Some(false), Some(error.getMessage))

  def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
    insert("execution.completed", executionId, Some(dagName), None, Some(durationMs), Some(succeeded), None)
}
```

**Wiring:**

```scala
import doobie.hikari.HikariTransactor
import cats.effect.Resource

val transactor: Resource[IO, HikariTransactor[IO]] = HikariTransactor.newHikariTransactor[IO](
  driverClassName = "org.postgresql.Driver",
  url = "jdbc:postgresql://localhost:5432/constellation",
  user = "constellation",
  pass = "password",
  connectEC = runtime.compute
)

transactor.use { xa =>
  val listener = new DoobieAuditListener(xa)

  val constellation = ConstellationImpl.builder()
    .withListener(listener)
    .build()

  // ... run application
}
```

## Combining Multiple Listeners

Use `ExecutionListener.composite` to invoke multiple listeners for each event:

```scala
val listener = ExecutionListener.composite(
  kafkaListener,
  auditListener,
  loggingListener
)

val constellation = ConstellationImpl.builder()
  .withListener(listener)
  .build()
```

All listeners receive every event. If one listener throws, the others still execute.

## Gotchas

- **Fire-and-forget:** Listener exceptions do not propagate to the caller. If you need guaranteed delivery, implement retry logic within your listener.
- **Performance:** Callbacks are on the execution hot path. Keep them fast. Use async producers (Kafka) or connection pools (Doobie/HikariCP) to avoid blocking.
- **Concurrency:** Module callbacks (`onModuleStart`, `onModuleComplete`) may fire concurrently for independent modules. Ensure your implementation is thread-safe.
- **Duration accuracy:** `durationMs` is wall-clock time measured by the runtime. It includes scheduling overhead but not queue wait time.
- **Cancellation:** `onExecutionCancelled` is only called when `CancellableExecution.cancel` is invoked. It defaults to no-op — override it if you need cancellation tracking.
