package io.constellation.spi

import cats.effect.IO

/** Service Provider Interface for metrics collection.
  *
  * Embedders implement this trait to route Constellation metrics to their
  * observability stack (e.g., Prometheus, Datadog, OpenTelemetry).
  *
  * All methods return `IO[Unit]` and are called fire-and-forget,
  * so implementations should not throw exceptions.
  */
trait MetricsProvider {

  /** Increment a counter metric.
    *
    * @param name Metric name (e.g., "constellation.execution.total")
    * @param tags Key-value pairs for metric dimensions
    */
  def counter(name: String, tags: Map[String, String] = Map.empty): IO[Unit]

  /** Record a histogram/distribution value.
    *
    * @param name Metric name (e.g., "constellation.module.duration_ms")
    * @param value The value to record
    * @param tags Key-value pairs for metric dimensions
    */
  def histogram(name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit]

  /** Set a gauge value.
    *
    * @param name Metric name (e.g., "constellation.execution.active")
    * @param value The current gauge value
    * @param tags Key-value pairs for metric dimensions
    */
  def gauge(name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit]
}

object MetricsProvider {

  /** No-op implementation that discards all metrics. */
  val noop: MetricsProvider = new MetricsProvider {
    def counter(name: String, tags: Map[String, String]): IO[Unit] = IO.unit
    def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
    def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
  }
}
