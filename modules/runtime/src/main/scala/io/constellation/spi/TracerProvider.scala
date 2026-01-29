package io.constellation.spi

import cats.effect.IO

/** Service Provider Interface for distributed tracing.
  *
  * Embedders implement this trait to route Constellation spans to their
  * tracing backend (e.g., Jaeger, Zipkin, OpenTelemetry).
  *
  * The `span` method wraps an IO computation, creating a trace span
  * that covers the entire duration of the body.
  */
trait TracerProvider {

  /** Create a trace span around an IO computation.
    *
    * @param name Span name (e.g., "execute(myDag)" or "module(Uppercase)")
    * @param attributes Key-value pairs for span attributes
    * @param body The IO computation to trace
    * @return The result of the body computation
    */
  def span[A](name: String, attributes: Map[String, String] = Map.empty)(body: IO[A]): IO[A]
}

object TracerProvider {

  /** No-op implementation that passes through the body unchanged. */
  val noop: TracerProvider = new TracerProvider {
    def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A] = body
  }
}
