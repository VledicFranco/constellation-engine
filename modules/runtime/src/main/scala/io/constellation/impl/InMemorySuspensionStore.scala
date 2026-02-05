package io.constellation.impl

import java.time.{Duration, Instant}
import java.util.UUID

import scala.concurrent.duration.FiniteDuration

import cats.effect.{IO, Ref}

import io.constellation.*

/** In-memory implementation of [[SuspensionStore]] backed by a concurrent `Ref`.
  *
  * Suitable for development and testing. Stored suspensions are lost on JVM restart.
  *
  * @param store
  *   Concurrent map of handle ID -> stored entry
  * @param codecOpt
  *   Optional codec for round-trip validation on save
  * @param ttl
  *   Optional TTL for stored entries. Entries older than the TTL are lazily evicted on save/load
  *   operations. Default: None (entries live forever).
  */
final class InMemorySuspensionStore private (
    store: Ref[IO, Map[String, InMemorySuspensionStore.StoredEntry]],
    codecOpt: Option[SuspensionCodec],
    ttl: Option[Duration] = None
) extends SuspensionStore {

  import InMemorySuspensionStore.StoredEntry

  /** Evict entries older than the configured TTL (if set). Returns the number of evicted entries.
    */
  private def evictExpired(): IO[Int] = ttl match {
    case None => IO.pure(0)
    case Some(ttlDuration) =>
      IO.realTimeInstant.flatMap { now =>
        store.modify { entries =>
          val (live, expired) = entries.partition { case (_, entry) =>
            Duration.between(entry.createdAt, now).compareTo(ttlDuration) < 0
          }
          (live, expired.size)
        }
      }
  }

  def save(suspended: SuspendedExecution): IO[SuspensionHandle] = {
    // Optional codec round-trip validation
    val validate: IO[Unit] = codecOpt match {
      case Some(codec) =>
        IO.fromEither(
          codec.encode(suspended).flatMap(bytes => codec.decode(bytes)).map(_ => ())
        )
      case None => IO.unit
    }

    validate *> evictExpired() *> {
      val handleId = UUID.randomUUID().toString
      val now      = Instant.now()
      val entry    = StoredEntry(suspended, createdAt = now, lastResumedAt = None)
      store.update(_ + (handleId -> entry)).as(SuspensionHandle(handleId))
    }
  }

  def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]] =
    evictExpired() *> store.get.map(_.get(handle.id).map(_.suspended))

  def delete(handle: SuspensionHandle): IO[Boolean] =
    store.modify { entries =>
      if entries.contains(handle.id) then (entries - handle.id, true)
      else (entries, false)
    }

  def list(filter: SuspensionFilter): IO[List[SuspensionSummary]] =
    evictExpired() *> store.get.map { entries =>
      entries.toList.flatMap { case (handleId, entry) =>
        val suspended = entry.suspended
        val matches =
          filter.structuralHash.forall(_ == suspended.structuralHash) &&
            filter.executionId.forall(_ == suspended.executionId) &&
            filter.minResumptionCount.forall(suspended.resumptionCount >= _) &&
            filter.maxResumptionCount.forall(suspended.resumptionCount <= _)

        if matches then {
          val dagSpec = suspended.dagSpec
          val expectedInputNames =
            dagSpec.userInputDataNodes.values.flatMap(_.nicknames.values).toSet
          val providedInputNames = suspended.providedInputs.keySet
          val missingInputNames  = expectedInputNames -- providedInputNames

          // Build missing inputs map: name -> CType
          val inputNameToType: Map[String, CType] = dagSpec.userInputDataNodes.values.flatMap {
            spec =>
              spec.nicknames.values.map(name => name -> spec.cType)
          }.toMap

          val missingInputs = missingInputNames.flatMap { name =>
            inputNameToType.get(name).map(name -> _)
          }.toMap

          Some(
            SuspensionSummary(
              handle = SuspensionHandle(handleId),
              executionId = suspended.executionId,
              structuralHash = suspended.structuralHash,
              resumptionCount = suspended.resumptionCount,
              missingInputs = missingInputs,
              createdAt = entry.createdAt,
              lastResumedAt = entry.lastResumedAt
            )
          )
        } else None
      }
    }
}

object InMemorySuspensionStore {

  /** Internal storage entry. */
  private[impl] final case class StoredEntry(
      suspended: SuspendedExecution,
      createdAt: Instant,
      lastResumedAt: Option[Instant]
  )

  /** Create a new empty in-memory suspension store. */
  def init: IO[SuspensionStore] =
    Ref.of[IO, Map[String, StoredEntry]](Map.empty).map { store =>
      new InMemorySuspensionStore(store, codecOpt = None)
    }

  /** Create a new empty in-memory suspension store with a TTL.
    *
    * Entries older than the TTL are lazily evicted during save/load/list operations.
    *
    * @param ttl
    *   Maximum age of stored entries. Entries older than this are evicted.
    */
  def initWithTTL(ttl: FiniteDuration): IO[SuspensionStore] =
    Ref.of[IO, Map[String, StoredEntry]](Map.empty).map { store =>
      new InMemorySuspensionStore(
        store,
        codecOpt = None,
        ttl = Some(Duration.ofMillis(ttl.toMillis))
      )
    }

  /** Create a new empty in-memory suspension store with codec validation.
    *
    * When a codec is provided, every `save` will round-trip the suspension through encode/decode to
    * catch serialization bugs early.
    */
  def initWithCodecValidation(codec: SuspensionCodec): IO[SuspensionStore] =
    Ref.of[IO, Map[String, StoredEntry]](Map.empty).map { store =>
      new InMemorySuspensionStore(store, codecOpt = Some(codec))
    }
}
