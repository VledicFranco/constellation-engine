package io.constellation.spi

import io.constellation.cache.CacheBackend
import io.constellation.execution.CircuitBreakerRegistry

/** Bundle of all pluggable backend services for Constellation.
  *
  * Provides a single configuration point for embedders to inject their observability and storage
  * implementations. All backends default to no-op implementations that add zero overhead.
  *
  * @param metrics
  *   Provider for counters, histograms, and gauges
  * @param tracer
  *   Provider for distributed tracing spans
  * @param listener
  *   Callback listener for execution lifecycle events
  * @param cache
  *   Backend for compilation/result caching (optional)
  * @param circuitBreakers
  *   Registry of per-module circuit breakers (optional)
  */
final case class ConstellationBackends(
    metrics: MetricsProvider = MetricsProvider.noop,
    tracer: TracerProvider = TracerProvider.noop,
    listener: ExecutionListener = ExecutionListener.noop,
    cache: Option[CacheBackend] = None,
    circuitBreakers: Option[CircuitBreakerRegistry] = None
)

object ConstellationBackends {

  /** Default backends with all no-op implementations. */
  val defaults: ConstellationBackends = ConstellationBackends()
}
