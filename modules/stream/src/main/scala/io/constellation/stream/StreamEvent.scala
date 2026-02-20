package io.constellation.stream

import java.time.Instant

/** Monitoring events emitted by the streaming runtime. */
sealed trait StreamEvent {
  def timestamp: Instant
}

object StreamEvent {

  /** A circuit breaker opened on a module node. */
  final case class CircuitOpen(
      moduleName: String,
      consecutiveErrors: Int,
      timestamp: Instant = Instant.now()
  ) extends StreamEvent

  /** A circuit breaker closed (reset) on a module node. */
  final case class CircuitClosed(
      moduleName: String,
      timestamp: Instant = Instant.now()
  ) extends StreamEvent

  /** A zip join exhausted one side before the other. */
  final case class ZipExhausted(
      leftNodeId: String,
      rightNodeId: String,
      exhaustedSide: String,
      timestamp: Instant = Instant.now()
  ) extends StreamEvent

  /** An element was routed to the dead-letter queue. */
  final case class ElementDlq(
      moduleName: String,
      error: Throwable,
      timestamp: Instant = Instant.now()
  ) extends StreamEvent
}
