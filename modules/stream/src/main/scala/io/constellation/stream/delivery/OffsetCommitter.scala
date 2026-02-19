package io.constellation.stream.delivery

import cats.effect.{IO, Ref}

/** An opaque offset value for tracking stream consumption position. */
final case class Offset(value: String)

/** Interface for committing consumed offsets, enabling at-least-once delivery.
  *
  * Source connectors that support at-least-once delivery provide an OffsetCommitter to track
  * consumption progress. On restart, the source can resume from the last committed offset.
  */
trait OffsetCommitter {

  /** Commit the given offset, indicating all elements up to this offset have been processed. */
  def commit(offset: Offset): IO[Unit]

  /** Get the last committed offset, if any. */
  def currentOffset: IO[Option[Offset]]
}

object OffsetCommitter {

  /** A no-op committer that does nothing. Used by connectors without offset tracking. */
  val noop: OffsetCommitter = new OffsetCommitter {
    def commit(offset: Offset): IO[Unit]    = IO.unit
    def currentOffset: IO[Option[Offset]]   = IO.pure(None)
  }

  /** An in-memory committer backed by a Ref. Useful for testing. */
  def inMemory: IO[OffsetCommitter] =
    Ref.of[IO, Option[Offset]](None).map { ref =>
      new OffsetCommitter {
        def commit(offset: Offset): IO[Unit]  = ref.set(Some(offset))
        def currentOffset: IO[Option[Offset]] = ref.get
      }
    }
}
