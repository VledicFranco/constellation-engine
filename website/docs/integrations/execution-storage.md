---
title: "Execution Storage"
sidebar_position: 5
description: "SPI guide for persisting execution history to a database for long-term retention and querying."
---

# ExecutionStorage Integration Guide

## Overview

`ExecutionStorage` stores execution history — inputs, outputs, per-node results, and DAG visualization data. The built-in implementation is in-memory with LRU eviction. Implement this trait to persist execution history to a database for long-term retention and querying.

## Trait API

```scala
package io.constellation.http

import cats.effect.IO

trait ExecutionStorage[F[_]] {
  /** Store an execution record. Returns the execution ID. */
  def store(execution: StoredExecution): F[String]

  /** Retrieve a single execution by ID. */
  def get(executionId: String): F[Option[StoredExecution]]

  /** List executions with pagination. */
  def list(limit: Int, offset: Int): F[List[ExecutionSummary]]

  /** List executions for a specific script. */
  def listByScript(scriptPath: String, limit: Int): F[List[ExecutionSummary]]

  /** Return storage statistics. */
  def stats: F[StorageStats]

  /** Update an execution record in place. */
  def update(executionId: String)(f: StoredExecution => StoredExecution): F[Option[StoredExecution]]

  /** Delete an execution record. */
  def delete(executionId: String): F[Boolean]

  /** Remove all records. */
  def clear: F[Unit]
}
```

### Supporting Types

```scala
case class StoredExecution(
  executionId: String,
  dagName: String,
  scriptPath: Option[String],
  startTime: Long,             // epoch millis
  endTime: Option[Long],       // epoch millis
  inputs: Map[String, Json],
  outputs: Option[Map[String, Json]],
  status: ExecutionStatus,     // Running, Completed, Failed
  nodeResults: Map[String, StoredNodeResult],
  dagVizIR: Option[DagVizIR],
  sampleRate: Double,
  source: ExecutionSource      // Dashboard, VSCodeExtension, API
)

case class ExecutionSummary(
  executionId: String,
  dagName: String,
  status: ExecutionStatus,
  startTime: Long,
  endTime: Option[Long],
  source: ExecutionSource
)

enum ExecutionStatus:
  case Running, Completed, Failed

enum ExecutionSource:
  case Dashboard, VSCodeExtension, API
```

### Built-in In-Memory Implementation

```scala
// Default: in-memory with LRU eviction
val storage: IO[ExecutionStorage[IO]] = ExecutionStorage.inMemory

// With custom configuration
val storage: IO[ExecutionStorage[IO]] = ExecutionStorage.inMemory(
  ExecutionStorage.Config(
    maxExecutions    = 1000,   // LRU eviction beyond this
    maxValueSizeBytes = 10240  // Truncate large values (10KB)
  )
)
```

## Example 1: PostgreSQL via Doobie

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"     % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-hikari"   % "1.0.0-RC5",
  "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC5",
  "org.postgresql" % "postgresql"      % "42.7.0"
)
```

**SQL schema:**

```sql
CREATE TABLE executions (
  execution_id   VARCHAR(255) PRIMARY KEY,
  dag_name       VARCHAR(255) NOT NULL,
  script_path    VARCHAR(1024),
  start_time     BIGINT NOT NULL,
  end_time       BIGINT,
  inputs         JSONB NOT NULL DEFAULT '{}',
  outputs        JSONB,
  status         VARCHAR(50) NOT NULL,
  node_results   JSONB NOT NULL DEFAULT '{}',
  dag_viz_ir     JSONB,
  sample_rate    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
  source         VARCHAR(50) NOT NULL
);

CREATE INDEX idx_executions_dag_name ON executions(dag_name);
CREATE INDEX idx_executions_script_path ON executions(script_path);
CREATE INDEX idx_executions_start_time ON executions(start_time DESC);
CREATE INDEX idx_executions_status ON executions(status);
```

**Implementation:**

```scala
import io.constellation.http.{ExecutionStorage, StoredExecution, ExecutionSummary, StorageStats}
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import cats.effect.IO
import io.circe.Json
import io.circe.syntax._

class PostgresExecutionStorage(xa: Transactor[IO]) extends ExecutionStorage[IO] {

  def store(execution: StoredExecution): IO[String] =
    sql"""INSERT INTO executions (execution_id, dag_name, script_path, start_time, end_time,
            inputs, outputs, status, node_results, dag_viz_ir, sample_rate, source)
          VALUES (${execution.executionId}, ${execution.dagName}, ${execution.scriptPath},
            ${execution.startTime}, ${execution.endTime},
            ${execution.inputs.asJson}::jsonb, ${execution.outputs.map(_.asJson)}::jsonb,
            ${execution.status.toString}, ${execution.nodeResults.asJson}::jsonb,
            ${execution.dagVizIR.map(_.asJson)}::jsonb,
            ${execution.sampleRate}, ${execution.source.toString})"""
      .update.run.transact(xa).as(execution.executionId)

  def get(executionId: String): IO[Option[StoredExecution]] =
    sql"SELECT * FROM executions WHERE execution_id = $executionId"
      .query[StoredExecution].option.transact(xa)

  def list(limit: Int, offset: Int): IO[List[ExecutionSummary]] =
    sql"""SELECT execution_id, dag_name, status, start_time, end_time, source
          FROM executions ORDER BY start_time DESC LIMIT $limit OFFSET $offset"""
      .query[ExecutionSummary].to[List].transact(xa)

  def listByScript(scriptPath: String, limit: Int): IO[List[ExecutionSummary]] =
    sql"""SELECT execution_id, dag_name, status, start_time, end_time, source
          FROM executions WHERE script_path = $scriptPath
          ORDER BY start_time DESC LIMIT $limit"""
      .query[ExecutionSummary].to[List].transact(xa)

  def stats: IO[StorageStats] =
    sql"SELECT COUNT(*) FROM executions"
      .query[Long].unique.transact(xa).map(count =>
        StorageStats(totalExecutions = count, /* ... */ ))

  def update(executionId: String)(f: StoredExecution => StoredExecution): IO[Option[StoredExecution]] =
    for {
      existing <- get(executionId)
      result <- existing.traverse { exec =>
        val updated = f(exec)
        sql"""UPDATE executions SET end_time = ${updated.endTime}, outputs = ${updated.outputs.map(_.asJson)}::jsonb,
              status = ${updated.status.toString}, node_results = ${updated.nodeResults.asJson}::jsonb
              WHERE execution_id = $executionId"""
          .update.run.transact(xa).as(updated)
      }
    } yield result

  def delete(executionId: String): IO[Boolean] =
    sql"DELETE FROM executions WHERE execution_id = $executionId"
      .update.run.transact(xa).map(_ > 0)

  def clear: IO[Unit] =
    sql"TRUNCATE TABLE executions".update.run.transact(xa).void
}
```

**Wiring:**

```scala
import doobie.hikari.HikariTransactor

HikariTransactor.newHikariTransactor[IO](
  driverClassName = "org.postgresql.Driver",
  url = "jdbc:postgresql://localhost:5432/constellation",
  user = "constellation",
  pass = "password",
  connectEC = runtime.compute
).use { xa =>
  val storage = new PostgresExecutionStorage(xa)
  // Pass storage to the HTTP server configuration
}
```

## Example 2: SQLite (Lightweight/Embedded)

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core" % "1.0.0-RC5",
  "org.xerial"    % "sqlite-jdbc"  % "3.44.0.0"
)
```

**Implementation:**

```scala
import doobie._
import doobie.implicits._
import cats.effect.IO

class SqliteExecutionStorage(xa: Transactor[IO]) extends ExecutionStorage[IO] {
  // Same implementation as PostgreSQL but with SQLite-compatible SQL
  // Replace JSONB with TEXT (store JSON as strings)
  // Replace TRUNCATE with DELETE FROM

  def store(execution: StoredExecution): IO[String] =
    sql"""INSERT INTO executions (execution_id, dag_name, script_path, start_time, end_time,
            inputs, outputs, status, node_results, sample_rate, source)
          VALUES (${execution.executionId}, ${execution.dagName}, ${execution.scriptPath},
            ${execution.startTime}, ${execution.endTime},
            ${execution.inputs.asJson.noSpaces}, ${execution.outputs.map(_.asJson.noSpaces)},
            ${execution.status.toString}, ${execution.nodeResults.asJson.noSpaces},
            ${execution.sampleRate}, ${execution.source.toString})"""
      .update.run.transact(xa).as(execution.executionId)

  // ... remaining methods follow the same pattern as PostgreSQL
  // with TEXT columns instead of JSONB
}

object SqliteExecutionStorage {
  def create(dbPath: String): IO[SqliteExecutionStorage] = {
    val xa = Transactor.fromDriverManager[IO](
      driver = "org.sqlite.JDBC",
      url = s"jdbc:sqlite:$dbPath"
    )
    // Create table if not exists
    sql"""CREATE TABLE IF NOT EXISTS executions (
      execution_id TEXT PRIMARY KEY,
      dag_name TEXT NOT NULL,
      script_path TEXT,
      start_time INTEGER NOT NULL,
      end_time INTEGER,
      inputs TEXT NOT NULL DEFAULT '{}',
      outputs TEXT,
      status TEXT NOT NULL,
      node_results TEXT NOT NULL DEFAULT '{}',
      sample_rate REAL NOT NULL DEFAULT 1.0,
      source TEXT NOT NULL
    )""".update.run.transact(xa).as(new SqliteExecutionStorage(xa))
  }
}
```

**Wiring:**

```scala
val storage = SqliteExecutionStorage.create("constellation-executions.db")
```

## Gotchas

- **JSON serialization:** `StoredExecution` contains `Map[String, Json]` fields. For PostgreSQL, use `JSONB` columns. For SQLite, store as `TEXT` with `Json.noSpaces`.
- **LRU vs persistent:** The in-memory implementation uses LRU eviction. Database implementations persist indefinitely — implement your own retention policy (e.g., delete executions older than 30 days).
- **Value truncation:** The in-memory implementation truncates large values (>10KB). Database implementations should handle large JSONB values appropriately (indexing, storage, query performance).
- **Concurrent writes:** Multiple executions may complete simultaneously. Use database-level concurrency control (transactions, optimistic locking) for the `update` method.
- **Connection pooling:** Always use connection pools (HikariCP via `doobie-hikari`) in production. A single connection will bottleneck under concurrent execution.
- **DagVizIR:** The `dagVizIR` field contains the DAG visualization intermediate representation. It can be large for complex pipelines. Consider storing it in a separate table or omitting it for long-term storage.
