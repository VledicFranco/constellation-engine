package io.constellation.lsp

import cats.effect.{IO, Ref, Fiber}
import cats.syntax.all._
import scala.concurrent.duration._

/**
 * A debouncer that delays action execution until a quiet period.
 * Multiple rapid calls with the same key will cancel previous pending
 * actions and restart the timer.
 *
 * Thread-safe implementation using Cats Effect Ref and Fiber.
 *
 * @tparam K The type of key used to identify debounce contexts
 */
class Debouncer[K] private (
    pending: Ref[IO, Map[K, Fiber[IO, Throwable, Unit]]],
    val delay: FiniteDuration
) {

  /**
   * Schedule an action to run after the delay, canceling any
   * previously scheduled action for the same key.
   *
   * @param key The key to debounce on (e.g., document URI)
   * @param action The action to execute after the delay
   */
  def debounce(key: K)(action: IO[Unit]): IO[Unit] =
    for {
      // Cancel existing pending action and get old fiber
      oldFiber <- pending.modify { map =>
        (map - key, map.get(key))
      }
      _ <- oldFiber.traverse_(_.cancel)

      // Start new delayed action with cleanup
      fiber <- (IO.sleep(delay) *> action *> cleanup(key)).start
      _ <- pending.update(_ + (key -> fiber))
    } yield ()

  /**
   * Execute an action immediately without debouncing, canceling
   * any pending debounced action for the same key.
   *
   * Use this for operations that should bypass debouncing (e.g., document save).
   *
   * @param key The key to run immediately
   * @param action The action to execute immediately
   */
  def immediate(key: K)(action: IO[Unit]): IO[Unit] =
    for {
      oldFiber <- pending.modify { map =>
        (map - key, map.get(key))
      }
      _ <- oldFiber.traverse_(_.cancel)
      _ <- action
    } yield ()

  /**
   * Cancel any pending action for the given key without executing it.
   *
   * Use this when the key is no longer relevant (e.g., document closed).
   *
   * @param key The key to cancel
   */
  def cancel(key: K): IO[Unit] =
    pending.modify { map =>
      (map - key, map.get(key))
    }.flatMap(_.traverse_(_.cancel))

  /**
   * Cancel all pending actions.
   *
   * Use this during shutdown.
   */
  def cancelAll: IO[Unit] =
    pending.modify(map => (Map.empty, map.values.toList))
      .flatMap(_.traverse_(_.cancel))

  /**
   * Get the number of pending actions.
   * Primarily useful for testing.
   */
  def pendingCount: IO[Int] =
    pending.get.map(_.size)

  /**
   * Check if a key has a pending action.
   * Primarily useful for testing.
   */
  def hasPending(key: K): IO[Boolean] =
    pending.get.map(_.contains(key))

  private def cleanup(key: K): IO[Unit] =
    pending.update(_ - key)
}

object Debouncer {
  /** Default debounce delay of 500ms - balances responsiveness with avoiding excessive compilation */
  val DefaultDelay: FiniteDuration = 500.millis

  /**
   * Create a new Debouncer with the specified delay.
   *
   * @param delay How long to wait after the last call before executing
   * @tparam K The type of key used for debouncing
   */
  def create[K](delay: FiniteDuration = DefaultDelay): IO[Debouncer[K]] =
    Ref.of[IO, Map[K, Fiber[IO, Throwable, Unit]]](Map.empty)
      .map(new Debouncer(_, delay))
}
