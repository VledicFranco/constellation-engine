package io.constellation.provider

import cats.effect.{IO, Ref}

/** A single executor endpoint in the pool. */
final case class ExecutorEndpoint(
    connectionId: String,
    executorUrl: String
)

/** Manages a pool of executor endpoints for a given namespace.
  *
  * For solo providers (no group_id), the pool contains a single endpoint.
  * For provider groups, it tracks all healthy group members and distributes
  * Execute calls across them via round-robin.
  */
trait ExecutorPool {

  /** Select the next healthy executor endpoint (round-robin). */
  def next: IO[ExecutorEndpoint]

  /** Add an executor endpoint to the pool. Idempotent — adding the same connectionId twice replaces the entry. */
  def add(endpoint: ExecutorEndpoint): IO[Unit]

  /** Remove an executor by connection ID. Returns true if the pool is now empty. */
  def remove(connectionId: String): IO[Boolean]

  /** Current number of endpoints in the pool. */
  def size: IO[Int]

  /** Get all current endpoints. */
  def endpoints: IO[Vector[ExecutorEndpoint]]
}

/** Round-robin executor pool implementation.
  *
  * Thread-safe via `Ref[IO, ...]`. The round-robin index advances on each `next` call
  * and wraps around the pool size.
  */
class RoundRobinExecutorPool private (
    pool: Ref[IO, Vector[ExecutorEndpoint]],
    nextIdx: Ref[IO, Int]
) extends ExecutorPool {

  def next: IO[ExecutorEndpoint] =
    pool.get.flatMap { eps =>
      if eps.isEmpty then
        IO.raiseError(new NoSuchElementException("No healthy executors in pool"))
      else
        nextIdx.modify { idx =>
          val selected = eps(idx % eps.size)
          ((idx + 1) % eps.size, selected)
        }
    }

  def add(endpoint: ExecutorEndpoint): IO[Unit] =
    pool.update { eps =>
      val filtered = eps.filterNot(_.connectionId == endpoint.connectionId)
      filtered :+ endpoint
    }

  def remove(connectionId: String): IO[Boolean] =
    pool.modify { eps =>
      val updated = eps.filterNot(_.connectionId == connectionId)
      (updated, updated.isEmpty)
    }

  def size: IO[Int] = pool.get.map(_.size)

  def endpoints: IO[Vector[ExecutorEndpoint]] = pool.get
}

object RoundRobinExecutorPool {

  /** Create a new empty round-robin executor pool. */
  def create: IO[RoundRobinExecutorPool] =
    for {
      pool    <- Ref.of[IO, Vector[ExecutorEndpoint]](Vector.empty)
      nextIdx <- Ref.of[IO, Int](0)
    } yield new RoundRobinExecutorPool(pool, nextIdx)

  /** Create a round-robin executor pool with an initial endpoint. */
  def withEndpoint(endpoint: ExecutorEndpoint): IO[RoundRobinExecutorPool] =
    for {
      pool    <- Ref.of[IO, Vector[ExecutorEndpoint]](Vector(endpoint))
      nextIdx <- Ref.of[IO, Int](0)
    } yield new RoundRobinExecutorPool(pool, nextIdx)
}
