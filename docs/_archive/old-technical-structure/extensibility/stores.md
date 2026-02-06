# PipelineStore and SuspensionStore

> **Path**: `docs/extensibility/stores.md`
> **Parent**: [extensibility/](./README.md)

Persistence interfaces for compiled pipelines and suspended executions. These are separate from `CacheBackend` (which caches module results with TTL).

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

final case class SuspensionHandle(id: String)

final case class SuspensionFilter(
  structuralHash: Option[String] = None,
  executionId: Option[java.util.UUID] = None,
  minResumptionCount: Option[Int] = None,
  maxResumptionCount: Option[Int] = None
)

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

## Wiring

```scala
val constellation = ConstellationImpl.builder()
  .withPipelineStore(myPostgresStore)
  .withSuspensionStore(mySuspensionStore)
  .build()
```

## Gotchas

- **Content addressing**: `PipelineStore` uses structural hashes (content-addressable). Same pipeline source produces same hash.
- **Aliases vs hashes**: Aliases are mutable pointers. Structural hashes are immutable.
- **Syntactic index**: Maps (sourceHash, registryHash) to structuralHash for compile cache hits when module registry changes.
- **Suspension TTL**: Use `initWithTTL` in production to prevent unbounded growth.

## See Also

- [cache-backend.md](./cache-backend.md) - TTL-based result caching (different purpose)
- [execution-listener.md](./execution-listener.md) - Track execution lifecycle events
