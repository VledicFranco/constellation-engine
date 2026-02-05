package io.constellation.execution

import scala.concurrent.duration.*

import cats.effect.{Deferred, IO, Ref}

/** A lazy, memoized value.
  *
  * The computation is deferred until `force` is called. Once computed, the result is cached and
  * subsequent `force` calls return immediately.
  *
  * ==Thread Safety==
  *
  * This implementation is thread-safe. If multiple fibers call `force` concurrently, only one
  * computation runs and others wait for the result.
  *
  * ==Usage==
  *
  * {{{
  * val lazyVal = LazyValue(IO {
  *   println("Computing...")
  *   expensiveComputation()
  * }).unsafeRunSync()
  *
  * // Nothing printed yet
  *
  * val result1 = lazyVal.force.unsafeRunSync()  // "Computing..." printed
  * val result2 = lazyVal.force.unsafeRunSync()  // Returns cached result, no print
  * }}}
  *
  * ==Memory==
  *
  * The cached result is held in memory until the LazyValue is garbage collected. For large results
  * that should be recomputed, consider using a cache with TTL.
  */
class LazyValue[A] private (
    compute: IO[A],
    stateRef: Ref[IO, LazyValue.State[A]]
) {

  /** Force evaluation of the lazy value.
    *
    *   - If not yet computed, starts computation
    *   - If computing, waits for result
    *   - If computed, returns cached value immediately
    *
    * @return
    *   The computed value
    */
  def force: IO[A] =
    stateRef.get.flatMap {
      case LazyValue.Computed(value) =>
        IO.pure(value)

      case LazyValue.Computing(deferred) =>
        deferred.get

      case LazyValue.Pending =>
        // Try to transition to Computing
        Deferred[IO, A].flatMap { deferred =>
          stateRef.modify {
            case LazyValue.Pending =>
              // We won the race, start computing
              val newState = LazyValue.Computing(deferred)
              val computation = compute
                .flatTap { result =>
                  stateRef.set(LazyValue.Computed(result)) >>
                    deferred.complete(result).void
                }
                .handleErrorWith { error =>
                  // On error, reset to Pending so next force can retry
                  stateRef.set(LazyValue.Pending) >>
                    deferred.complete(throw error).attempt.void >>
                    IO.raiseError(error)
                }
              (newState, computation)

            case computing @ LazyValue.Computing(existingDeferred) =>
              // Someone else is computing, wait for them
              (computing, existingDeferred.get)

            case computed @ LazyValue.Computed(value) =>
              // Already computed while we were setting up
              (computed, IO.pure(value))
          }.flatten
        }
    }

  /** Check if the value has been computed.
    *
    * Note: This is a snapshot in time. The value may be computed immediately after this returns
    * false.
    */
  def isComputed: IO[Boolean] =
    stateRef.get.map {
      case LazyValue.Computed(_) => true
      case _                     => false
    }

  /** Check if computation is in progress. */
  def isComputing: IO[Boolean] =
    stateRef.get.map {
      case LazyValue.Computing(_) => true
      case _                      => false
    }

  /** Get the cached value if computed, None otherwise.
    *
    * Does not trigger computation.
    */
  def peek: IO[Option[A]] =
    stateRef.get.map {
      case LazyValue.Computed(value) => Some(value)
      case _                         => None
    }

  /** Reset to pending state, allowing recomputation.
    *
    * Use with caution - this discards any cached result. If computation is in progress, waits for
    * it to complete first.
    */
  def reset: IO[Unit] =
    stateRef.get.flatMap {
      case LazyValue.Computing(deferred) =>
        // Wait for current computation to finish, then reset
        deferred.get.attempt >> stateRef.set(LazyValue.Pending)
      case _ =>
        stateRef.set(LazyValue.Pending)
    }

  /** Map over the lazy value.
    *
    * Creates a new LazyValue that applies `f` to the result. The original computation is shared -
    * if this value is forced, the mapped value doesn't recompute.
    */
  def map[B](f: A => B): IO[LazyValue[B]] =
    LazyValue(force.map(f))

  /** FlatMap over the lazy value.
    *
    * Creates a new LazyValue that sequences computations.
    */
  def flatMap[B](f: A => IO[B]): IO[LazyValue[B]] =
    LazyValue(force.flatMap(f))
}

object LazyValue {

  /** Internal state of a lazy value. */
  private sealed trait State[+A]
  private case object Pending                                extends State[Nothing]
  private case class Computing[A](deferred: Deferred[IO, A]) extends State[A]
  private case class Computed[A](value: A)                   extends State[A]

  /** Create a new lazy value.
    *
    * @param compute
    *   The computation to defer
    * @return
    *   A lazy value wrapping the computation
    */
  def apply[A](compute: IO[A]): IO[LazyValue[A]] =
    Ref.of[IO, State[A]](Pending).map { stateRef =>
      new LazyValue(compute, stateRef)
    }

  /** Create a lazy value that is already computed.
    *
    * Useful for testing or when you have a value but need LazyValue interface.
    */
  def pure[A](value: A): IO[LazyValue[A]] =
    Ref.of[IO, State[A]](Computed(value)).map { stateRef =>
      new LazyValue(IO.pure(value), stateRef)
    }

  /** Create a lazy value that always fails.
    *
    * Useful for testing error handling.
    */
  def raiseError[A](error: Throwable): IO[LazyValue[A]] =
    apply(IO.raiseError(error))

  /** Sequence multiple lazy values.
    *
    * Forces all values and returns results as a list.
    */
  def sequence[A](values: List[LazyValue[A]]): IO[List[A]] = {
    import cats.implicits.*
    values.traverse(_.force)
  }

  /** Create lazy values from a list of computations.
    *
    * Does not force any of them.
    */
  def fromList[A](computations: List[IO[A]]): IO[List[LazyValue[A]]] = {
    import cats.implicits.*
    computations.traverse(LazyValue(_))
  }
}
