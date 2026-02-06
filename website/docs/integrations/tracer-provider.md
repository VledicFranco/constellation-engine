---
title: "Tracer Provider"
sidebar_position: 2
description: "SPI guide for distributed tracing with OpenTelemetry, Jaeger, or Zipkin integration."
---

# TracerProvider Integration Guide

## Overview

`TracerProvider` is the SPI trait for distributed tracing in Constellation Engine. Implement this trait to wrap DAG and module executions with trace spans, enabling end-to-end visibility in systems like Jaeger, Zipkin, or any OpenTelemetry-compatible backend.

## Trait API

```scala
package io.constellation.spi

import cats.effect.IO

trait TracerProvider {
  /** Wrap an IO computation with a trace span. */
  def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A]
}

object TracerProvider {
  /** No-op implementation (default). Passes body through unchanged. */
  val noop: TracerProvider = new TracerProvider {
    def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A] = body
  }
}
```

The runtime wraps key operations with spans:

| Span Name | Attributes | Wraps |
|-----------|------------|-------|
| `execution.run` | `dag_name`, `execution_id` | Entire DAG execution |
| `module.execute` | `module_name`, `module_id` | Individual module execution |

## Example 1: OpenTelemetry via otel4s

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "org.typelevel" %% "otel4s-oteljava" % "0.4.0",
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.34.0",
  "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.34.0"
)
```

**Implementation:**

```scala
import io.constellation.spi.TracerProvider
import cats.effect.IO
import org.typelevel.otel4s.trace.Tracer

class OtelTracerProvider(tracer: Tracer[IO]) extends TracerProvider {

  def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A] = {
    val spanBuilder = tracer.spanBuilder(name)
    val withAttrs = attributes.foldLeft(spanBuilder) { case (b, (k, v)) =>
      b.addAttribute(org.typelevel.otel4s.Attribute(k, v))
    }
    withAttrs.build.use { span =>
      body.handleErrorWith { err =>
        span.recordException(err) *>
          span.setStatus(org.typelevel.otel4s.trace.StatusCode.Error) *>
          IO.raiseError(err)
      }
    }
  }
}
```

**Wiring:**

```scala
import org.typelevel.otel4s.oteljava.OtelJava

OtelJava.autoConfigured[IO]().use { otel =>
  otel.tracerProvider.get("constellation").flatMap { tracer =>
    val tracerProvider = new OtelTracerProvider(tracer)

    val constellation = ConstellationImpl.builder()
      .withTracer(tracerProvider)
      .build()

    // ... run application
  }
}
```

**Environment configuration for OTLP exporter:**

```bash
OTEL_SERVICE_NAME=constellation-engine
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

## Example 2: Jaeger (Direct)

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "io.opentelemetry" % "opentelemetry-sdk" % "1.34.0",
  "io.opentelemetry" % "opentelemetry-exporter-jaeger" % "1.34.0"
)
```

**Implementation:**

```scala
import io.constellation.spi.TracerProvider
import io.opentelemetry.api.trace.{Tracer => JTracer, Span, StatusCode}
import cats.effect.IO

class JaegerTracerProvider(tracer: JTracer) extends TracerProvider {

  def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A] = {
    IO.bracket(
      IO {
        val spanBuilder = tracer.spanBuilder(name)
        attributes.foreach { case (k, v) =>
          spanBuilder.setAttribute(k, v)
        }
        spanBuilder.startSpan()
      }
    )(span =>
      IO.defer {
        val scope = span.makeCurrent()
        body.attempt.flatMap {
          case Right(a) =>
            IO { scope.close(); a }
          case Left(err) =>
            IO {
              span.setStatus(StatusCode.ERROR, err.getMessage)
              span.recordException(err)
              scope.close()
            } *> IO.raiseError(err)
        }
      }
    )(span => IO(span.end()))
  }
}
```

**Wiring:**

```scala
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter

val exporter = JaegerGrpcSpanExporter.builder()
  .setEndpoint("http://localhost:14250")
  .build()

val sdkTracerProvider = SdkTracerProvider.builder()
  .addSpanProcessor(io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(exporter))
  .build()

val otelSdk = OpenTelemetrySdk.builder()
  .setTracerProvider(sdkTracerProvider)
  .build()

val tracer = otelSdk.getTracer("constellation-engine")
val tracerProvider = new JaegerTracerProvider(tracer)

val constellation = ConstellationImpl.builder()
  .withTracer(tracerProvider)
  .build()
```

## Gotchas

- **Context propagation:** The `span` method wraps an `IO[A]` computation. The span is active for the duration of `body`. Nested spans (e.g., module spans inside execution spans) inherit the parent context automatically if your tracer implementation supports it.
- **Error recording:** Record exceptions on the span before re-raising. The examples above demonstrate this pattern.
- **Performance:** Span creation and attribute setting should be fast. Avoid expensive serialization in attributes.
- **No-op default:** When `TracerProvider.noop` is used (the default), the `body` computation passes through with zero overhead — no span objects are created.
- **Concurrency:** Modules execute in parallel. Each module span is independent. The execution span is the parent of all module spans within that DAG run.
