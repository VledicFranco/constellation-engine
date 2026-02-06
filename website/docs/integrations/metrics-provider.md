---
title: "Metrics Provider"
sidebar_position: 1
description: "SPI guide for emitting runtime metrics to Prometheus, Datadog, or other monitoring systems."
---

# MetricsProvider Integration Guide

## Overview

`MetricsProvider` is the SPI trait for emitting runtime metrics from Constellation Engine. Implement this trait to connect Constellation's instrumentation points to your metrics system (Prometheus, Datadog, CloudWatch, etc.).

All methods are fire-and-forget — exceptions are swallowed by the runtime to prevent metrics failures from affecting pipeline execution.

## Trait API

```scala
package io.constellation.spi

import cats.effect.IO

trait MetricsProvider {
  /** Increment a counter by 1. */
  def counter(name: String, tags: Map[String, String]): IO[Unit]

  /** Record a value in a histogram (distribution). */
  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit]

  /** Set a gauge to a specific value. */
  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit]
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

## Built-in Metric Names

:::note Metric Naming Convention
All built-in metrics use dot-separated names (e.g., `execution.started`, `module.duration_ms`). When implementing a custom provider, preserve these names or map them consistently to your system's naming convention.
:::

The runtime emits these metrics automatically when a `MetricsProvider` is configured:

| Name | Type | Tags | Description |
|------|------|------|-------------|
| `execution.started` | counter | `dag_name` | DAG execution started |
| `execution.completed` | counter | `dag_name`, `success` | DAG execution completed |
| `execution.duration_ms` | histogram | `dag_name` | End-to-end execution time |
| `module.started` | counter | `module_name` | Module execution started |
| `module.completed` | counter | `module_name` | Module execution completed |
| `module.duration_ms` | histogram | `module_name` | Per-module execution time |
| `module.failed` | counter | `module_name` | Module execution failures |
| `scheduler.active` | gauge | — | Currently running tasks |
| `scheduler.queued` | gauge | — | Tasks waiting in queue |

## Example 1: Prometheus via Micrometer

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "io.micrometer" % "micrometer-registry-prometheus" % "1.12.0"
)
```

**Implementation:**

```scala
import io.constellation.spi.MetricsProvider
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
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
    // Micrometer gauges track objects; for simplicity, use a counter-like approach
    // or maintain AtomicDouble references per gauge name+tags combination
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

**Wiring:**

```scala
val (metrics, prometheusRegistry) = PrometheusMetricsProvider.create

val backends = ConstellationBackends(metrics = metrics)
val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()

// Expose /metrics endpoint for Prometheus scraping
// The registry provides: prometheusRegistry.scrape()
```

## Example 2: Datadog StatsD

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "com.datadoghq" % "java-dogstatsd-client" % "4.2.0"
)
```

**Implementation:**

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

**Wiring:**

```scala
val metrics = new DatadogMetricsProvider("localhost", 8125, "constellation")

val constellation = ConstellationImpl.builder()
  .withMetrics(metrics)
  .build()
```

## Gotchas

:::tip Cardinality Considerations
Avoid high-cardinality tags like execution IDs, timestamps, or user IDs that create unbounded metric series. Stick to bounded tags like `dag_name`, `module_name`, and `success`. High cardinality can cause memory issues in your metrics backend and slow down queries.
:::

- **Thread safety:** The runtime may call metrics methods concurrently from multiple fibers. Ensure your implementation is thread-safe (Micrometer and StatsD clients are thread-safe by default).
- **Fire-and-forget:** Exceptions thrown from metrics methods are caught and discarded by the runtime. Log errors internally if you need visibility.
- **Performance:** Metrics calls are on the hot path. Keep implementations fast — avoid synchronous network calls. StatsD and Micrometer both use non-blocking approaches.
- **Tag cardinality:** Avoid high-cardinality tags (e.g., execution IDs) that create unbounded metric series. Stick to `dag_name`, `module_name`, and `success`.

## Related

- [Tracer Provider](./tracer-provider.md) — Distributed tracing for end-to-end visibility
- [Execution Listener](./execution-listener.md) — Event streaming for detailed audit logs
- [HTTP API Overview](../api-reference/http-api-overview.md) — The `/metrics` endpoint for scraping
- [Programmatic API](../api-reference/programmatic-api.md) — Wire metrics into your application
