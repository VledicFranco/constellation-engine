# MetricsProvider

> **Path**: `docs/features/extensibility/metrics-provider.md`
> **Parent**: [extensibility/](./README.md)

Interface for emitting runtime metrics to observability systems (Prometheus, Datadog, CloudWatch, OpenTelemetry).

## Components Involved

| Component | Role | File Path |
|-----------|------|-----------|
| `MetricsProvider` | SPI trait definition | `modules/runtime/src/main/scala/io/constellation/spi/MetricsProvider.scala` |
| `MetricsProvider.noop` | Zero-overhead default | `modules/runtime/src/main/scala/io/constellation/spi/MetricsProvider.scala` |
| `ConstellationBackends` | Backend bundle configuration | `modules/runtime/src/main/scala/io/constellation/spi/ConstellationBackends.scala` |
| `TracerProvider` | Related: distributed tracing | `modules/runtime/src/main/scala/io/constellation/spi/TracerProvider.scala` |

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
  val noop: MetricsProvider = new MetricsProvider {
    def counter(name: String, tags: Map[String, String]): IO[Unit] = IO.unit
    def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
    def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
  }
}
```

## Method Reference

| Method | Type | Description |
|--------|------|-------------|
| `counter(name, tags)` | Counter | Increment by 1 (monotonic, only increases) |
| `histogram(name, value, tags)` | Distribution | Record latency, size, or other distributions |
| `gauge(name, value, tags)` | Gauge | Set current value (can increase or decrease) |

## Built-in Metrics

When a `MetricsProvider` is configured, Constellation emits these metrics automatically:

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `constellation.execution.started` | counter | `dag_name` | DAG execution started |
| `constellation.execution.completed` | counter | `dag_name`, `success` | DAG execution completed |
| `constellation.execution.duration_ms` | histogram | `dag_name` | End-to-end execution time |
| `constellation.module.started` | counter | `module_name` | Module execution started |
| `constellation.module.completed` | counter | `module_name` | Module execution completed |
| `constellation.module.duration_ms` | histogram | `module_name` | Per-module execution time |
| `constellation.module.failed` | counter | `module_name` | Module execution failures |
| `constellation.scheduler.active` | gauge | - | Currently running tasks |
| `constellation.scheduler.queued` | gauge | - | Tasks waiting in queue |
| `constellation.cache.hits` | counter | - | Cache hits |
| `constellation.cache.misses` | counter | - | Cache misses |

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
    // Micrometer gauges require a number supplier; for simplicity, use a summary
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

### Exposing the /metrics Endpoint

```scala
import org.http4s._
import org.http4s.dsl.io._

val (metrics, prometheusRegistry) = PrometheusMetricsProvider.create

val metricsRoute = HttpRoutes.of[IO] {
  case GET -> Root / "metrics" =>
    Ok(prometheusRegistry.scrape())
}

// Wire into Constellation server
ConstellationServer.builder(constellation, compiler)
  .withMetrics(metrics)
  .withAdditionalRoutes(metricsRoute)
  .run
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

## Example: OpenTelemetry

```scala
import io.constellation.spi.MetricsProvider
import io.opentelemetry.api.metrics.{Meter, LongCounter, DoubleHistogram}
import io.opentelemetry.api.common.Attributes
import cats.effect.IO

class OpenTelemetryMetricsProvider(meter: Meter) extends MetricsProvider {

  private val counters = new java.util.concurrent.ConcurrentHashMap[String, LongCounter]()
  private val histograms = new java.util.concurrent.ConcurrentHashMap[String, DoubleHistogram]()

  def counter(name: String, tags: Map[String, String]): IO[Unit] = IO {
    val counter = counters.computeIfAbsent(name, _ =>
      meter.counterBuilder(name).build()
    )
    counter.add(1, toAttributes(tags))
  }

  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO {
    val histogram = histograms.computeIfAbsent(name, _ =>
      meter.histogramBuilder(name).build()
    )
    histogram.record(value, toAttributes(tags))
  }

  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO {
    // OpenTelemetry gauges are callback-based; for simplicity, record as histogram
    val histogram = histograms.computeIfAbsent(name, _ =>
      meter.histogramBuilder(name).build()
    )
    histogram.record(value, toAttributes(tags))
  }

  private def toAttributes(tags: Map[String, String]): Attributes = {
    val builder = Attributes.builder()
    tags.foreach { case (k, v) => builder.put(k, v) }
    builder.build()
  }
}
```

## Wiring

```scala
import io.constellation.ConstellationImpl
import io.constellation.spi.ConstellationBackends

val (metrics, prometheusRegistry) = PrometheusMetricsProvider.create

val constellation = ConstellationImpl.builder()
  .withMetrics(metrics)
  .build()

// Or via ConstellationBackends
val backends = ConstellationBackends(
  metrics = metrics,
  listener = myListener,
  cache = Some(myCache)
)

val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()
```

## Tag Naming Conventions

Use consistent, low-cardinality tags:

| Tag | Example Values | Cardinality |
|-----|----------------|-------------|
| `dag_name` | `"text-pipeline"`, `"data-transform"` | Low (10s) |
| `module_name` | `"Uppercase"`, `"WordCount"` | Low (100s) |
| `success` | `"true"`, `"false"` | Very low (2) |
| `error_type` | `"timeout"`, `"validation"` | Low (10s) |

**Avoid high-cardinality tags:**

| Bad Tag | Problem |
|---------|---------|
| `execution_id` | Unique per execution, unbounded series |
| `input_hash` | Unique per input, unbounded series |
| `timestamp` | Unique per second, unbounded series |
| `user_id` | Can be millions of users |

High-cardinality tags cause:
- Memory exhaustion in metrics backends
- Slow queries in dashboards
- Increased costs in SaaS monitoring

## Dashboard Examples

### Grafana Query (Prometheus)

```promql
# Execution throughput
rate(constellation_execution_completed_total[5m])

# P99 module latency
histogram_quantile(0.99, rate(constellation_module_duration_ms_bucket[5m]))

# Error rate by module
rate(constellation_module_failed_total[5m]) / rate(constellation_module_completed_total[5m])

# Cache hit ratio
rate(constellation_cache_hits_total[5m]) /
  (rate(constellation_cache_hits_total[5m]) + rate(constellation_cache_misses_total[5m]))
```

### Datadog Query

```
# Execution throughput
sum:constellation.execution.completed{*}.as_rate()

# P99 module latency
p99:constellation.module.duration_ms{*}

# Scheduler queue depth
avg:constellation.scheduler.queued{*}
```

## Gotchas

| Issue | Mitigation |
|-------|------------|
| **Thread safety** | Methods may be called concurrently. Micrometer and StatsD clients are thread-safe. |
| **Fire-and-forget** | Exceptions are swallowed. Log errors internally if needed for debugging. |
| **Performance** | Metrics calls are on the hot path. Use non-blocking implementations and connection pools. |
| **Tag cardinality** | Avoid high-cardinality tags that create unbounded series. Stick to `dag_name`, `module_name`, `success`. |
| **Metric naming** | Use consistent prefixes (`constellation.`) and follow the backend's naming conventions. |
| **Memory leaks** | Some metrics libraries accumulate metrics indefinitely. Configure retention or use TTL-based cleanup. |

## See Also

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why SPI over inheritance
- [ETHOS.md](./ETHOS.md) - Constraints for modifying SPIs
- [execution-listener.md](./execution-listener.md) - Structured event streaming
- [cache-backend.md](./cache-backend.md) - Cache statistics via `CacheStats`
