package io.constellation.stream.error

/** Per-element error handling strategies for stream processing.
  *
  * These strategies determine what happens when a single element fails during stream processing,
  * without stopping the entire stream.
  */
sealed trait StreamErrorStrategy

object StreamErrorStrategy {

  /** Skip the failed element and continue processing. */
  case object Skip extends StreamErrorStrategy

  /** Log the error and continue with a placeholder value. */
  case object Log extends StreamErrorStrategy

  /** Propagate the error, terminating the stream. */
  case object Propagate extends StreamErrorStrategy

  /** Route the failed element to a dead-letter queue. */
  case object Dlq extends StreamErrorStrategy
}
