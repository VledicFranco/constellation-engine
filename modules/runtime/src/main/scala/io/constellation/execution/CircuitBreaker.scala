package io.constellation.execution

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import cats.implicits.*

/** State of a circuit breaker. */
enum CircuitState:
  case Closed, Open, HalfOpen

/** Statistics for a circuit breaker instance. */
final case class CircuitStats(
    state: CircuitState,
    consecutiveFailures: Int,
    totalSuccesses: Long,
    totalFailures: Long,
    totalRejected: Long
)

/** Configuration for circuit breaker behavior.
  *
  * @param failureThreshold
  *   Number of consecutive failures before opening the circuit
  * @param resetDuration
  *   How long to wait in Open state before transitioning to HalfOpen
  * @param halfOpenMaxProbes
  *   Maximum concurrent probe attempts in HalfOpen state
  */
final case class CircuitBreakerConfig(
    failureThreshold: Int = 5,
    resetDuration: FiniteDuration = 30.seconds,
    halfOpenMaxProbes: Int = 1
)

/** Exception thrown when a circuit breaker is open and rejecting calls. */
class CircuitOpenException(val moduleName: String)
    extends RuntimeException(s"Circuit breaker open for module '$moduleName'")

/** Circuit breaker that protects module execution from cascading failures.
  *
  * State machine:
  *   - Closed: Normal operation. Tracks consecutive failures. Opens after threshold.
  *   - Open: Rejects immediately. After resetDuration, transitions to HalfOpen.
  *   - HalfOpen: Allows limited probes. Success → Closed. Failure → Open.
  */
trait CircuitBreaker {

  /** Wrap an operation with circuit breaker protection.
    *
    * @param operation
    *   The IO operation to protect
    * @return
    *   The operation result, or CircuitOpenException if circuit is open
    */
  def protect[A](operation: IO[A]): IO[A]

  /** Current circuit state. */
  def state: IO[CircuitState]

  /** Current statistics. */
  def stats: IO[CircuitStats]

  /** Manually reset the circuit to Closed state. */
  def reset: IO[Unit]
}

object CircuitBreaker {

  private case class InternalState(
      circuitState: CircuitState,
      consecutiveFailures: Int,
      totalSuccesses: Long,
      totalFailures: Long,
      totalRejected: Long,
      openedAt: Option[FiniteDuration],
      halfOpenProbes: Int
  ) {
    def toStats: CircuitStats = CircuitStats(
      state = circuitState,
      consecutiveFailures = consecutiveFailures,
      totalSuccesses = totalSuccesses,
      totalFailures = totalFailures,
      totalRejected = totalRejected
    )
  }

  private object InternalState {
    val initial: InternalState = InternalState(
      circuitState = CircuitState.Closed,
      consecutiveFailures = 0,
      totalSuccesses = 0,
      totalFailures = 0,
      totalRejected = 0,
      openedAt = None,
      halfOpenProbes = 0
    )
  }

  /** Create a new circuit breaker for the given module.
    *
    * @param moduleName
    *   The module name (for error messages)
    * @param config
    *   Circuit breaker configuration
    */
  def create(moduleName: String, config: CircuitBreakerConfig): IO[CircuitBreaker] =
    Ref.of[IO, InternalState](InternalState.initial).map { ref =>
      new CircuitBreakerImpl(moduleName, config, ref)
    }

  private class CircuitBreakerImpl(
      moduleName: String,
      config: CircuitBreakerConfig,
      stateRef: Ref[IO, InternalState]
  ) extends CircuitBreaker {

    def protect[A](operation: IO[A]): IO[A] =
      for {
        now        <- IO.monotonic
        canProceed <- checkAndTransition(now)
        result <-
          if canProceed then {
            operation.attempt.flatMap {
              case Right(value) =>
                onSuccess.as(value)
              case Left(error) =>
                onFailure(now) *> IO.raiseError(error)
            }
          } else {
            stateRef.update(s => s.copy(totalRejected = s.totalRejected + 1)) *>
              IO.raiseError(new CircuitOpenException(moduleName))
          }
      } yield result

    /** Check current state and decide whether the operation can proceed. Also handles Open →
      * HalfOpen transition based on elapsed time.
      */
    private def checkAndTransition(now: FiniteDuration): IO[Boolean] =
      stateRef.modify { s =>
        s.circuitState match {
          case CircuitState.Closed =>
            (s, true)

          case CircuitState.Open =>
            s.openedAt match {
              case Some(openedTime) if (now - openedTime) >= config.resetDuration =>
                // Transition to HalfOpen
                (s.copy(circuitState = CircuitState.HalfOpen, halfOpenProbes = 1), true)
              case _ =>
                (s, false)
            }

          case CircuitState.HalfOpen =>
            if s.halfOpenProbes < config.halfOpenMaxProbes then {
              (s.copy(halfOpenProbes = s.halfOpenProbes + 1), true)
            } else {
              (s, false)
            }
        }
      }

    private def onSuccess: IO[Unit] =
      stateRef.update { s =>
        s.copy(
          circuitState = CircuitState.Closed,
          consecutiveFailures = 0,
          totalSuccesses = s.totalSuccesses + 1,
          openedAt = None,
          halfOpenProbes = 0
        )
      }

    private def onFailure(now: FiniteDuration): IO[Unit] =
      stateRef.update { s =>
        val newFailures = s.consecutiveFailures + 1
        if s.circuitState == CircuitState.HalfOpen || newFailures >= config.failureThreshold then {
          s.copy(
            circuitState = CircuitState.Open,
            consecutiveFailures = newFailures,
            totalFailures = s.totalFailures + 1,
            openedAt = Some(now),
            halfOpenProbes = 0
          )
        } else {
          s.copy(
            consecutiveFailures = newFailures,
            totalFailures = s.totalFailures + 1
          )
        }
      }

    def state: IO[CircuitState] = stateRef.get.map(_.circuitState)

    def stats: IO[CircuitStats] = stateRef.get.map(_.toStats)

    def reset: IO[Unit] =
      stateRef.set(InternalState.initial)
  }
}
