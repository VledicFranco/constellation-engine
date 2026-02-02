package io.constellation.impl

import cats.effect.{IO, Ref}
import io.constellation.*

import java.time.Instant
import java.util.UUID

/** In-memory implementation of [[SuspensionStore]] backed by a concurrent `Ref`.
  *
  * Suitable for development and testing. Stored suspensions are lost on JVM restart.
  *
  * @param store
  *   Concurrent map of handle ID -> stored entry
  * @param codecOpt
  *   Optional codec for round-trip validation on save
  */
final class InMemorySuspensionStore private (
    store: Ref[IO, Map[String, InMemorySuspensionStore.StoredEntry]],
    codecOpt: Option[SuspensionCodec]
) extends SuspensionStore {

  import InMemorySuspensionStore.StoredEntry

  def save(suspended: SuspendedExecution): IO[SuspensionHandle] = {
    // Optional codec round-trip validation
    val validate: IO[Unit] = codecOpt match {
      case Some(codec) =>
        IO.fromEither(
          codec.encode(suspended).flatMap(bytes => codec.decode(bytes)).map(_ => ())
        )
      case None => IO.unit
    }

    validate *> {
      val handleId = UUID.randomUUID().toString
      val now      = Instant.now()
      val entry    = StoredEntry(suspended, createdAt = now, lastResumedAt = None)
      store.update(_ + (handleId -> entry)).as(SuspensionHandle(handleId))
    }
  }

  def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]] =
    store.get.map(_.get(handle.id).map(_.suspended))

  def delete(handle: SuspensionHandle): IO[Boolean] =
    store.modify { entries =>
      if entries.contains(handle.id) then (entries - handle.id, true)
      else (entries, false)
    }

  def list(filter: SuspensionFilter): IO[List[SuspensionSummary]] =
    store.get.map { entries =>
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
