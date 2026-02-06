# Execution Storage

> **Path**: `docs/features/extensibility/execution-storage.md`
> **Parent**: [extensibility/](./README.md)

Persistence interfaces for compiled pipelines (`PipelineStore`) and suspended executions (`SuspensionStore`). These are separate from `CacheBackend` (which caches module results with TTL).

## Components Involved

| Component | Role | File Path |
|-----------|------|-----------|
| `PipelineStore` | SPI trait for compiled pipelines | `modules/runtime/src/main/scala/io/constellation/PipelineStore.scala` |
| `PipelineStoreImpl` | In-memory default implementation | `modules/runtime/src/main/scala/io/constellation/impl/PipelineStoreImpl.scala` |
| `SuspensionStore` | SPI trait for suspended executions | `modules/runtime/src/main/scala/io/constellation/SuspensionStore.scala` |
| `SuspensionHandle` | Opaque handle for stored suspensions | `modules/runtime/src/main/scala/io/constellation/SuspensionStore.scala` |
| `SuspensionFilter` | Query filter for listing suspensions | `modules/runtime/src/main/scala/io/constellation/SuspensionStore.scala` |
| `SuspensionSummary` | Summary of stored suspension | `modules/runtime/src/main/scala/io/constellation/SuspensionStore.scala` |
| `InMemorySuspensionStore` | In-memory default implementation | `modules/runtime/src/main/scala/io/constellation/impl/InMemorySuspensionStore.scala` |
| `ConstellationBackends` | Backend bundle configuration | `modules/runtime/src/main/scala/io/constellation/spi/ConstellationBackends.scala` |

## PipelineStore

Stores compiled pipeline images with content-addressable hashing and human-readable aliases.

### Trait API

```scala
package io.constellation

import cats.effect.IO

trait PipelineStore {
  /** Store a pipeline image. Returns the structural hash as key. */
  def store(image: PipelineImage): IO[String]

  /** Create or update a human-readable alias for a structural hash. */
  def alias(name: String, structuralHash: String): IO[Unit]

  /** Resolve an alias to a structural hash. */
  def resolve(name: String): IO[Option[String]]

  /** List all known aliases. */
  def listAliases: IO[Map[String, String]]

  /** Retrieve a pipeline image by structural hash. */
  def get(structuralHash: String): IO[Option[PipelineImage]]

  /** Retrieve a pipeline image by alias name. */
  def getByName(name: String): IO[Option[PipelineImage]]

  /** Index syntactic hash to structural hash for cache lookups. */
  def indexSyntactic(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit]

  /** Look up structural hash by syntactic hash and registry hash. */
  def lookupSyntactic(syntacticHash: String, registryHash: String): IO[Option[String]]

  /** List all stored pipeline images. */
  def listImages: IO[List[PipelineImage]]

  /** Remove a pipeline image by structural hash. Returns true if found. */
  def remove(structuralHash: String): IO[Boolean]
}
```

### Method Reference

| Method | Description |
|--------|-------------|
| `store(image)` | Save image, return structural hash |
| `alias(name, hash)` | Map human-readable name to hash |
| `resolve(name)` | Get hash for alias |
| `listAliases` | All alias mappings |
| `get(hash)` | Retrieve by structural hash |
| `getByName(name)` | Retrieve by alias |
| `indexSyntactic(...)` | Cache source-to-structural mapping |
| `lookupSyntactic(...)` | Find structural hash from source hash |
| `listImages` | All stored images |
| `remove(hash)` | Delete by structural hash |

### Hashing Strategy

PipelineStore uses two types of hashes:

| Hash Type | Input | Purpose |
|-----------|-------|---------|
| **Syntactic hash** | Source code text | Fast cache lookup for unchanged sources |
| **Structural hash** | Compiled DAG structure | Content-addressable storage, deduplication |
| **Registry hash** | Function signatures | Invalidate cache when modules change |

The `indexSyntactic` method maps `(syntacticHash, registryHash) -> structuralHash` for cache-hit detection when source code hasn't changed but modules may have been updated.

### Built-in Implementations

| Class | Location | Description |
|-------|----------|-------------|
| `PipelineStoreImpl` | `runtime` | In-memory, lost on restart |
| `FileSystemPipelineStore` | `http-api` | Filesystem-backed with memory cache |

## SuspensionStore

Stores suspended execution snapshots for resumption.

### Trait API

```scala
package io.constellation

import cats.effect.IO
import java.time.Instant

trait SuspensionStore {
  /** Save a suspended execution and return a handle. */
  def save(suspended: SuspendedExecution): IO[SuspensionHandle]

  /** Load a suspended execution by handle. */
  def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]]

  /** Delete a stored suspension. Returns true if found. */
  def delete(handle: SuspensionHandle): IO[Boolean]

  /** List stored suspensions matching filter criteria. */
  def list(filter: SuspensionFilter = SuspensionFilter.All): IO[List[SuspensionSummary]]
}
```

### Supporting Types

```scala
/** Opaque handle to a stored suspended execution. */
final case class SuspensionHandle(id: String)

/** Filter criteria for listing stored suspensions. */
final case class SuspensionFilter(
  structuralHash: Option[String] = None,
  executionId: Option[java.util.UUID] = None,
  minResumptionCount: Option[Int] = None,
  maxResumptionCount: Option[Int] = None
)

object SuspensionFilter {
  val All: SuspensionFilter = SuspensionFilter()
}

/** Summary of a stored suspension (without full snapshot). */
final case class SuspensionSummary(
  handle: SuspensionHandle,
  executionId: java.util.UUID,
  structuralHash: String,
  resumptionCount: Int,
  missingInputs: Map[String, CType],
  createdAt: Instant,
  lastResumedAt: Option[Instant]
)
```

### Method Reference

| Method | Description |
|--------|-------------|
| `save(suspended)` | Store snapshot, return handle |
| `load(handle)` | Retrieve by handle |
| `delete(handle)` | Remove, returns true if existed |
| `list(filter)` | Query with optional filters |

### Built-in Implementation

`InMemorySuspensionStore` ships with `runtime`:

```scala
import io.constellation.impl.InMemorySuspensionStore

// Basic in-memory store (entries live forever)
val store: IO[SuspensionStore] = InMemorySuspensionStore.init

// With TTL for automatic expiry
val store: IO[SuspensionStore] = InMemorySuspensionStore.initWithTTL(24.hours)

// With codec validation (catches serialization bugs)
val store: IO[SuspensionStore] = InMemorySuspensionStore.initWithCodecValidation(codec)
```

## Example: PostgreSQL PipelineStore

```scala
import io.constellation.{PipelineImage, PipelineStore}
import doobie._
import doobie.implicits._
import cats.effect.IO
import io.circe.syntax._
import io.circe.parser

class PostgresPipelineStore(xa: Transactor[IO]) extends PipelineStore {

  def store(image: PipelineImage): IO[String] = {
    val hash = image.structuralHash
    val json = image.asJson.noSpaces
    sql"""INSERT INTO pipelines (structural_hash, image_json)
          VALUES ($hash, $json)
          ON CONFLICT (structural_hash) DO NOTHING"""
      .update.run.transact(xa).as(hash)
  }

  def get(structuralHash: String): IO[Option[PipelineImage]] =
    sql"SELECT image_json FROM pipelines WHERE structural_hash = $structuralHash"
      .query[String]
      .option
      .transact(xa)
      .map(_.flatMap(j => parser.decode[PipelineImage](j).toOption))

  def alias(name: String, structuralHash: String): IO[Unit] =
    sql"""INSERT INTO pipeline_aliases (name, structural_hash)
          VALUES ($name, $structuralHash)
          ON CONFLICT (name) DO UPDATE SET structural_hash = $structuralHash"""
      .update.run.transact(xa).void

  def resolve(name: String): IO[Option[String]] =
    sql"SELECT structural_hash FROM pipeline_aliases WHERE name = $name"
      .query[String].option.transact(xa)

  def listAliases: IO[Map[String, String]] =
    sql"SELECT name, structural_hash FROM pipeline_aliases"
      .query[(String, String)].to[List].transact(xa).map(_.toMap)

  def getByName(name: String): IO[Option[PipelineImage]] =
    resolve(name).flatMap {
      case Some(hash) => get(hash)
      case None => IO.pure(None)
    }

  def indexSyntactic(syntacticHash: String, registryHash: String, structuralHash: String): IO[Unit] =
    sql"""INSERT INTO syntactic_index (syntactic_hash, registry_hash, structural_hash)
          VALUES ($syntacticHash, $registryHash, $structuralHash)
          ON CONFLICT DO NOTHING"""
      .update.run.transact(xa).void

  def lookupSyntactic(syntacticHash: String, registryHash: String): IO[Option[String]] =
    sql"""SELECT structural_hash FROM syntactic_index
          WHERE syntactic_hash = $syntacticHash AND registry_hash = $registryHash"""
      .query[String].option.transact(xa)

  def listImages: IO[List[PipelineImage]] =
    sql"SELECT image_json FROM pipelines"
      .query[String].to[List].transact(xa)
      .map(_.flatMap(j => parser.decode[PipelineImage](j).toOption))

  def remove(structuralHash: String): IO[Boolean] =
    sql"DELETE FROM pipelines WHERE structural_hash = $structuralHash"
      .update.run.transact(xa).map(_ > 0)
}
```

### PostgreSQL Schema

```sql
CREATE TABLE pipelines (
  structural_hash VARCHAR(64) PRIMARY KEY,
  image_json JSONB NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pipeline_aliases (
  name VARCHAR(255) PRIMARY KEY,
  structural_hash VARCHAR(64) NOT NULL REFERENCES pipelines(structural_hash),
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE syntactic_index (
  syntactic_hash VARCHAR(64) NOT NULL,
  registry_hash VARCHAR(64) NOT NULL,
  structural_hash VARCHAR(64) NOT NULL REFERENCES pipelines(structural_hash),
  PRIMARY KEY (syntactic_hash, registry_hash)
);
```

## Example: Redis SuspensionStore

```scala
import io.constellation._
import io.lettuce.core.api.async.RedisAsyncCommands
import cats.effect.IO
import io.circe.syntax._
import io.circe.parser
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

class RedisSuspensionStore(
  commands: RedisAsyncCommands[String, String],
  keyPrefix: String = "suspension:",
  ttl: FiniteDuration = 24.hours
)(implicit codec: io.circe.Codec[SuspendedExecution]) extends SuspensionStore {

  def save(suspended: SuspendedExecution): IO[SuspensionHandle] = {
    val handleId = UUID.randomUUID().toString
    val key = s"$keyPrefix$handleId"
    val json = suspended.asJson.noSpaces

    IO.async_ { cb =>
      val future = commands.setex(key, ttl.toSeconds, json)
      future.whenComplete { (_, error) =>
        if (error != null) cb(Left(error))
        else cb(Right(SuspensionHandle(handleId)))
      }
    }
  }

  def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]] = {
    val key = s"$keyPrefix${handle.id}"

    IO.async_ { cb =>
      val future = commands.get(key)
      future.whenComplete { (value, error) =>
        if (error != null) cb(Left(error))
        else cb(Right(Option(value).flatMap { json =>
          parser.decode[SuspendedExecution](json).toOption
        }))
      }
    }
  }

  def delete(handle: SuspensionHandle): IO[Boolean] = {
    val key = s"$keyPrefix${handle.id}"

    IO.async_ { cb =>
      val future = commands.del(key)
      future.whenComplete { (count, error) =>
        if (error != null) cb(Left(error))
        else cb(Right(count > 0))
      }
    }
  }

  def list(filter: SuspensionFilter): IO[List[SuspensionSummary]] = {
    // Redis SCAN for keys matching prefix, then load each
    // For production, consider using Redis Search or a dedicated index
    IO.async_ { cb =>
      val future = commands.keys(s"$keyPrefix*")
      future.whenComplete { (keys, error) =>
        if (error != null) cb(Left(error))
        else {
          // Load each and filter - this is O(n), consider indexing for large datasets
          // Simplified implementation - production would use pipelining
          cb(Right(List.empty)) // TODO: Implement proper iteration
        }
      }
    }
  }
}
```

## Wiring

```scala
import io.constellation.ConstellationImpl

val pipelineStore = new PostgresPipelineStore(transactor)
val suspensionStore = InMemorySuspensionStore.initWithTTL(24.hours).unsafeRunSync()

val constellation = ConstellationImpl.builder()
  .withPipelineStore(pipelineStore)
  .withSuspensionStore(suspensionStore)
  .build()
```

## Use Cases

### Pipeline Versioning

```scala
// Compile and store a pipeline
val image = compiler.compile(source).unsafeRunSync()
val hash = pipelineStore.store(image).unsafeRunSync()

// Create version alias
pipelineStore.alias("my-pipeline:v1.0", hash).unsafeRunSync()
pipelineStore.alias("my-pipeline:latest", hash).unsafeRunSync()

// Load by alias
val loaded = pipelineStore.getByName("my-pipeline:latest").unsafeRunSync()
```

### Execution Suspension/Resume

```scala
// Execute with missing inputs
val result = constellation.execute(image, partialInputs).unsafeRunSync()

result match {
  case SuspendedResult(suspended) =>
    // Store for later
    val handle = suspensionStore.save(suspended).unsafeRunSync()
    println(s"Execution suspended. Handle: ${handle.id}")
    println(s"Missing inputs: ${suspended.missingInputs.keys.mkString(", ")}")

  case CompletedResult(outputs) =>
    println(s"Execution completed: $outputs")
}

// Later, resume with remaining inputs
val handle = SuspensionHandle("previously-stored-handle-id")
val suspended = suspensionStore.load(handle).unsafeRunSync().get
val resumed = constellation.resume(suspended, remainingInputs).unsafeRunSync()
```

### Listing Suspended Executions

```scala
// Find all suspensions for a specific pipeline
val filter = SuspensionFilter(structuralHash = Some("abc123..."))
val suspensions = suspensionStore.list(filter).unsafeRunSync()

suspensions.foreach { summary =>
  println(s"Handle: ${summary.handle.id}")
  println(s"Missing: ${summary.missingInputs.keys.mkString(", ")}")
  println(s"Resumed ${summary.resumptionCount} times")
}
```

## Gotchas

| Issue | Mitigation |
|-------|------------|
| **Content addressing** | `PipelineStore` uses structural hashes (content-addressable). Same pipeline source produces same hash. |
| **Aliases vs hashes** | Aliases are mutable pointers. Structural hashes are immutable. Use aliases for "latest" semantics. |
| **Syntactic index** | Maps (sourceHash, registryHash) to structuralHash. Invalidates cache when module signatures change. |
| **Suspension TTL** | Use `initWithTTL` in production to prevent unbounded growth. Orphaned suspensions consume memory. |
| **Serialization** | Suspended executions contain CValues. Ensure your codec handles all CType variants. |
| **Resume idempotency** | Resuming the same suspension multiple times increments `resumptionCount`. Track this for debugging. |

## Comparison: CacheBackend vs Storage

| Aspect | CacheBackend | PipelineStore / SuspensionStore |
|--------|--------------|----------------------------------|
| Purpose | Module result caching | Pipeline persistence |
| TTL | Required (entries expire) | Optional (entries may live forever) |
| Miss handling | Recompute on miss | Error on miss (data must exist) |
| Key type | Opaque string | Content-addressed hash or handle |
| Eviction | LRU or TTL-based | Manual or TTL-based |
| Use case | Performance optimization | State management |

## See Also

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why SPI over inheritance
- [ETHOS.md](./ETHOS.md) - Constraints for modifying SPIs
- [cache-backend.md](./cache-backend.md) - TTL-based result caching (different purpose)
- [execution-listener.md](./execution-listener.md) - Track execution lifecycle events
