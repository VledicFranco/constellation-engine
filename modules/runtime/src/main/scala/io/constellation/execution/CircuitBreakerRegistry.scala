package io.constellation.execution

import cats.effect.{IO, Ref}
import cats.implicits._

/** Registry of circuit breakers keyed by module name.
  *
  * Provides get-or-create semantics so that the same circuit breaker instance
  * is shared across all DAG executions for a given module.
  */
trait CircuitBreakerRegistry {

  /** Get or create a circuit breaker for the given module name. */
  def getOrCreate(moduleName: String): IO[CircuitBreaker]

  /** Get an existing circuit breaker (if any) for the given module name. */
  def get(moduleName: String): IO[Option[CircuitBreaker]]

  /** Get statistics for all registered circuit breakers. */
  def allStats: IO[Map[String, CircuitStats]]
}

object CircuitBreakerRegistry {

  /** Create a new registry with the given default configuration. */
  def create(defaultConfig: CircuitBreakerConfig): IO[CircuitBreakerRegistry] =
    Ref.of[IO, Map[String, CircuitBreaker]](Map.empty).map { ref =>
      new CircuitBreakerRegistryImpl(ref, defaultConfig)
    }

  private class CircuitBreakerRegistryImpl(
      breakers: Ref[IO, Map[String, CircuitBreaker]],
      defaultConfig: CircuitBreakerConfig
  ) extends CircuitBreakerRegistry {

    def getOrCreate(moduleName: String): IO[CircuitBreaker] =
      breakers.get.flatMap { map =>
        map.get(moduleName) match {
          case Some(cb) => IO.pure(cb)
          case None =>
            // Race-safe: use modify to ensure only one breaker per name
            CircuitBreaker.create(moduleName, defaultConfig).flatMap { newCb =>
              breakers.modify { current =>
                current.get(moduleName) match {
                  case Some(existing) => (current, existing) // Another fiber won the race
                  case None           => (current + (moduleName -> newCb), newCb)
                }
              }
            }
        }
      }

    def get(moduleName: String): IO[Option[CircuitBreaker]] =
      breakers.get.map(_.get(moduleName))

    def allStats: IO[Map[String, CircuitStats]] =
      breakers.get.flatMap { map =>
        map.toList.traverse { case (name, cb) =>
          cb.stats.map(name -> _)
        }.map(_.toMap)
      }
  }
}
