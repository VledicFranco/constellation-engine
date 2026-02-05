# MetricsProvider

> **Path**: `docs/extensibility/metrics-provider.md`
> **Parent**: [extensibility/](./README.md)

Interface for emitting runtime metrics to observability systems (Prometheus, Datadog, CloudWatch, OpenTelemetry).

## Trait API

```scala
package io.constellation.spi

import cats.effect.IO

trait MetricsProvider {
  /** Increment a counter by 1. */
  def counter(name: String, tags: Map[String, String] = Map.empty): IO[Unit]

  /** Record a value in a histogram (distribution). */
  def histogram(name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit]

  /** Set a gauge to a specific value. */
  def gauge(name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit]
}

object MetricsProvider {
  /** No-op implementation (default). Zero overhead. */
  val noop: MetricsProvider
}
```

## Method Reference

| Method | Type | Description |
|--------|------|-------------|
| `counter(name, tags)` | Counter | Increment by 1 (monotonic) |
| `histogram(name, value, tags)` | Distribution | Record latency, size, etc. |
| `gauge(name, value, tags)` | Gauge | Set current value (non-monotonic) |

## Built-in Metrics

When a `MetricsProvider` is configured, these are emitted automatically:

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `execution.started` | counter | `dag_name` | DAG execution started |
| `execution.completed` | counter | `dag_name`, `success` | DAG execution completed |
| `execution.duration_ms` | histogram | `dag_name` | End-to-end execution time |
| `module.started` | counter | `module_name` | Module execution started |
| `module.completed` | counter | `module_name` | Module execution completed |
| `module.duration_ms` | histogram | `module_name` | Per-module execution time |
| `module.failed` | counter | `module_name` | Module execution failures |
| `scheduler.active` | gauge | - | Currently running tasks |
| `scheduler.queued` | gauge | - | Tasks waiting in queue |

## Example: Prometheus via Micrometer

```scala
import io.constellation.spi.MetricsProvider
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import cats.effect.IO

class PrometheusMetricsProvider(registry: PrometheusMeterRegistry) extends MetricsProvider {

  def counter(name: String, tags: Map[String, String]): IO[Unit] = IO {
    val tagArray = tags.flatMap { case (k, v) => Seq(k, v) }.toArray
    registry.counter(name, tagArray: _*).increment()
  }

  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO {
    val tagArray = tags.flatMap { case (k, v) => Seq(k, v) }.toArray
    registry.summary(name, tagArray: _*).record(value)
  }

  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO {
    val tagArray = tags.flatMap { case (k, v) => Seq(k, v) }.toArray
    registry.gauge(name, java.util.Collections.emptyList(), value)
  }
}

object PrometheusMetricsProvider {
  def create: (PrometheusMetricsProvider, PrometheusMeterRegistry) = {
    val registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    (new PrometheusMetricsProvider(registry), registry)
  }
}
```

## Example: Datadog StatsD

```scala
import io.constellation.spi.MetricsProvider
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import cats.effect.IO

class DatadogMetricsProvider(host: String, port: Int, prefix: String) extends MetricsProvider {

  private val client = new NonBlockingStatsDClientBuilder()
    .hostname(host)
    .port(port)
    .prefix(prefix)
    .build()

  def counter(name: String, tags: Map[String, String]): IO[Unit] = IO {
    client.incrementCounter(name, formatTags(tags): _*)
  }

  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO {
    client.recordDistributionValue(name, value, formatTags(tags): _*)
  }

  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO {
    client.recordGaugeValue(name, value, formatTags(tags): _*)
  }

  private def formatTags(tags: Map[String, String]): Array[String] =
    tags.map { case (k, v) => s"$k:$v" }.toArray
}
```

## Wiring

```scala
val (metrics, prometheusRegistry) = PrometheusMetricsProvider.create

ConstellationImpl.builder()
  .withMetrics(metrics)
  .build()

// Expose /metrics endpoint for Prometheus scraping
// prometheusRegistry.scrape() returns the metrics in Prometheus format
```

## Gotchas

- **Thread safety**: Methods may be called concurrently. Micrometer and StatsD clients are thread-safe.
- **Fire-and-forget**: Exceptions are swallowed. Log errors internally if needed.
- **Performance**: Metrics calls are on the hot path. Use non-blocking implementations.
- **Tag cardinality**: Avoid high-cardinality tags (execution IDs) that create unbounded series. Stick to `dag_name`, `module_name`, `success`.

## See Also

- [execution-listener.md](./execution-listener.md) - Structured event streaming
- [cache-backend.md](./cache-backend.md) - Cache statistics via `CacheStats`
