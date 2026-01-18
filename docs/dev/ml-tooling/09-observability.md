# Observability

**Priority:** 9 (Medium)
**Target Level:** Scala module (infrastructure integration)
**Status:** Not Implemented

---

## Overview

Observability is essential for production ML systems. Unlike traditional software, ML systems can fail silently—producing valid outputs that are wrong. Comprehensive metrics, logging, and tracing are required to detect and diagnose issues.

### The Three Pillars

| Pillar | Purpose | ML-Specific Concerns |
|--------|---------|---------------------|
| **Metrics** | Quantitative measurements | Prediction distributions, latency, feature stats |
| **Logging** | Event records | Input/output pairs, model versions, errors |
| **Tracing** | Request flow | DAG execution path, module timing |

---

## Metrics

### Key ML Metrics to Track

```scala
// Inference metrics
val predictionLatency = Histogram("constellation_prediction_latency_seconds")
val predictionCount = Counter("constellation_predictions_total")
val predictionValue = Histogram("constellation_prediction_value")

// Feature metrics
val featureValue = Histogram("constellation_feature_value", labels = Seq("feature_name"))
val featureMissing = Counter("constellation_feature_missing_total", labels = Seq("feature_name"))

// Model metrics
val modelCallCount = Counter("constellation_model_calls_total", labels = Seq("model_name", "version"))
val modelLatency = Histogram("constellation_model_latency_seconds", labels = Seq("model_name"))
val modelErrors = Counter("constellation_model_errors_total", labels = Seq("model_name", "error_type"))

// Cache metrics
val cacheHits = Counter("constellation_cache_hits_total", labels = Seq("cache_type"))
val cacheMisses = Counter("constellation_cache_misses_total", labels = Seq("cache_type"))

// DAG metrics
val dagExecutionTime = Histogram("constellation_dag_execution_seconds", labels = Seq("dag_name"))
val moduleExecutionTime = Histogram("constellation_module_execution_seconds", labels = Seq("module_name"))
```

### Prometheus Integration

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/observability/MetricsExporter.scala

package io.constellation.ml.observability

import io.prometheus.client._
import cats.effect.IO

class PrometheusMetricsExporter {

  // Histograms with meaningful buckets for ML latencies
  private val predictionLatency = Histogram.build()
    .name("constellation_prediction_latency_seconds")
    .help("Prediction latency in seconds")
    .buckets(0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0)
    .labelNames("model_name")
    .register()

  private val predictionValue = Histogram.build()
    .name("constellation_prediction_value")
    .help("Prediction output values")
    .buckets(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
    .labelNames("model_name")
    .register()

  private val featureValue = Histogram.build()
    .name("constellation_feature_value")
    .help("Feature values for drift monitoring")
    .labelNames("feature_name")
    .register()

  def recordPrediction(modelName: String, prediction: Double, latencySeconds: Double): IO[Unit] = IO {
    predictionLatency.labels(modelName).observe(latencySeconds)
    predictionValue.labels(modelName).observe(prediction)
  }

  def recordFeature(featureName: String, value: Double): IO[Unit] = IO {
    featureValue.labels(featureName).observe(value)
  }

  def recordCacheHit(cacheType: String): IO[Unit] = IO {
    cacheHits.labels(cacheType).inc()
  }

  def recordCacheMiss(cacheType: String): IO[Unit] = IO {
    cacheMisses.labels(cacheType).inc()
  }
}
```

---

## Logging

### Structured Logging

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/observability/PredictionLogger.scala

package io.constellation.ml.observability

import io.circe.syntax._
import io.circe.generic.auto._
import cats.effect.IO

case class PredictionLog(
  timestamp: Long,
  requestId: String,
  modelName: String,
  modelVersion: String,
  inputs: Map[String, Any],
  features: Map[String, Double],
  prediction: Double,
  latencyMs: Long,
  cached: Boolean,
  metadata: Map[String, String] = Map.empty
)

trait PredictionLogger {
  def log(entry: PredictionLog): IO[Unit]
  def logBatch(entries: List[PredictionLog]): IO[Unit]
}

class KafkaPredictionLogger(
  producer: KafkaProducer[String, String],
  topic: String
) extends PredictionLogger {

  override def log(entry: PredictionLog): IO[Unit] = IO {
    producer.send(new ProducerRecord(topic, entry.requestId, entry.asJson.noSpaces))
  }

  override def logBatch(entries: List[PredictionLog]): IO[Unit] = {
    entries.traverse(log).void
  }
}

class BigQueryPredictionLogger(
  client: BigQueryClient,
  dataset: String,
  table: String
) extends PredictionLogger {

  override def log(entry: PredictionLog): IO[Unit] = {
    logBatch(List(entry))
  }

  override def logBatch(entries: List[PredictionLog]): IO[Unit] = IO {
    val rows = entries.map(toTableRow)
    client.insertAll(dataset, table, rows)
  }

  private def toTableRow(entry: PredictionLog): Map[String, Any] = {
    Map(
      "timestamp" -> entry.timestamp,
      "request_id" -> entry.requestId,
      "model_name" -> entry.modelName,
      "prediction" -> entry.prediction,
      "latency_ms" -> entry.latencyMs
      // ... etc
    )
  }
}
```

---

## Tracing

### OpenTelemetry Integration

```scala
// modules/ml-integrations/src/main/scala/io/constellation/ml/observability/Tracing.scala

package io.constellation.ml.observability

import io.opentelemetry.api.trace.{Span, Tracer}
import io.opentelemetry.api.common.Attributes
import cats.effect.IO

class ConstellationTracer(tracer: Tracer) {

  def traceDAGExecution[A](dagName: String)(body: IO[A]): IO[A] = {
    IO.defer {
      val span = tracer.spanBuilder(s"dag:$dagName")
        .setAttribute("dag.name", dagName)
        .startSpan()

      body
        .flatTap(_ => IO(span.setStatus(io.opentelemetry.api.trace.StatusCode.OK)))
        .handleErrorWith { e =>
          IO(span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage)) *>
            IO.raiseError(e)
        }
        .guarantee(IO(span.end()))
    }
  }

  def traceModule[A](moduleName: String)(body: IO[A]): IO[A] = {
    IO.defer {
      val span = tracer.spanBuilder(s"module:$moduleName")
        .setAttribute("module.name", moduleName)
        .startSpan()

      body
        .flatTap(_ => IO(span.setStatus(io.opentelemetry.api.trace.StatusCode.OK)))
        .handleErrorWith { e =>
          IO(span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage)) *>
            IO.raiseError(e)
        }
        .guarantee(IO(span.end()))
    }
  }

  def traceModelCall[A](modelName: String, version: String)(body: IO[A]): IO[A] = {
    IO.defer {
      val span = tracer.spanBuilder(s"model:$modelName")
        .setAttribute("model.name", modelName)
        .setAttribute("model.version", version)
        .startSpan()

      val startTime = System.nanoTime()

      body
        .flatTap { _ =>
          IO {
            val latencyMs = (System.nanoTime() - startTime) / 1_000_000
            span.setAttribute("model.latency_ms", latencyMs)
            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK)
          }
        }
        .handleErrorWith { e =>
          IO(span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage)) *>
            IO.raiseError(e)
        }
        .guarantee(IO(span.end()))
    }
  }
}
```

---

## Dashboards

### Recommended Grafana Panels

```yaml
# grafana-dashboard.yaml

panels:
  - title: "Prediction Latency P99"
    type: graph
    query: |
      histogram_quantile(0.99,
        sum(rate(constellation_prediction_latency_seconds_bucket[5m])) by (le, model_name)
      )

  - title: "Prediction Distribution"
    type: heatmap
    query: |
      sum(increase(constellation_prediction_value_bucket[5m])) by (le, model_name)

  - title: "Cache Hit Rate"
    type: stat
    query: |
      sum(rate(constellation_cache_hits_total[5m])) /
      (sum(rate(constellation_cache_hits_total[5m])) + sum(rate(constellation_cache_misses_total[5m])))

  - title: "Feature Distribution Drift"
    type: timeseries
    query: |
      constellation_feature_drift_score{feature_name=~".*"}

  - title: "Model Error Rate"
    type: graph
    query: |
      sum(rate(constellation_model_errors_total[5m])) by (model_name, error_type) /
      sum(rate(constellation_model_calls_total[5m])) by (model_name)

  - title: "DAG Execution Time"
    type: graph
    query: |
      histogram_quantile(0.95,
        sum(rate(constellation_dag_execution_seconds_bucket[5m])) by (le, dag_name)
      )
```

---

## Alerting Rules

```yaml
# prometheus-alerts.yaml

groups:
  - name: constellation-ml
    rules:
      - alert: HighPredictionLatency
        expr: |
          histogram_quantile(0.99, sum(rate(constellation_prediction_latency_seconds_bucket[5m])) by (le, model_name)) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High prediction latency for {{ $labels.model_name }}"

      - alert: ModelErrorRate
        expr: |
          sum(rate(constellation_model_errors_total[5m])) by (model_name) /
          sum(rate(constellation_model_calls_total[5m])) by (model_name) > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate for model {{ $labels.model_name }}"

      - alert: FeatureDriftDetected
        expr: constellation_feature_drift_score > 0.2
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Feature drift detected for {{ $labels.feature_name }}"

      - alert: LowCacheHitRate
        expr: |
          sum(rate(constellation_cache_hits_total[5m])) /
          (sum(rate(constellation_cache_hits_total[5m])) + sum(rate(constellation_cache_misses_total[5m]))) < 0.5
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "Cache hit rate below 50%"
```

---

## Configuration

```hocon
constellation.observability {
  # Metrics
  metrics {
    enabled = true
    port = 9090
    path = "/metrics"

    # Custom buckets
    latency-buckets = [0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0]
  }

  # Logging
  logging {
    predictions {
      enabled = true
      destination = "kafka"
      topic = "prediction-logs"
      sample-rate = 1.0  # Log 100% of predictions
    }
  }

  # Tracing
  tracing {
    enabled = true
    exporter = "jaeger"
    sample-rate = 0.1  # Sample 10% of requests

    jaeger {
      endpoint = "http://jaeger:14268/api/traces"
    }
  }
}
```

---

## Implementation Checklist

- [ ] Implement `PrometheusMetricsExporter`
- [ ] Implement `PredictionLogger` with Kafka/BigQuery backends
- [ ] Implement `ConstellationTracer` with OpenTelemetry
- [ ] Add metrics endpoint to HTTP server
- [ ] Create Grafana dashboard template
- [ ] Create Prometheus alerting rules
- [ ] Write integration tests

---

## Files to Create

| File | Purpose |
|------|---------|
| `modules/ml-integrations/.../observability/MetricsExporter.scala` | Prometheus metrics |
| `modules/ml-integrations/.../observability/PredictionLogger.scala` | Structured logging |
| `modules/ml-integrations/.../observability/Tracing.scala` | OpenTelemetry tracing |
| `modules/http-api/.../MetricsEndpoint.scala` | Metrics HTTP endpoint |
| `deploy/grafana/dashboard.json` | Grafana dashboard |
| `deploy/prometheus/alerts.yaml` | Alert rules |

---

## Related Documents

- [Drift Detection](./08-drift-detection.md) - Export drift metrics
- [A/B Testing](./07-ab-testing.md) - Track experiment metrics
- [Model Inference](./02-model-inference.md) - Track inference latency
