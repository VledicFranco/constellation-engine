package io.constellation.spi

import io.constellation.cache.CacheBackend

/** Bundle of all pluggable backend services for Constellation.
  *
  * Provides a single configuration point for embedders to inject their
  * observability and storage implementations. All backends default to
  * no-op implementations that add zero overhead.
  *
  * @param metrics Provider for counters, histograms, and gauges
  * @param tracer Provider for distributed tracing spans
  * @param listener Callback listener for execution lifecycle events
  * @param cache Backend for compilation/result caching (optional)
  */
final case class ConstellationBackends(
    metrics: MetricsProvider = MetricsProvider.noop,
    tracer: TracerProvider = TracerProvider.noop,
    listener: ExecutionListener = ExecutionListener.noop,
    cache: Option[CacheBackend] = None
)

object ConstellationBackends {

  /** Default backends with all no-op implementations. */
  val defaults: ConstellationBackends = ConstellationBackends()
}
