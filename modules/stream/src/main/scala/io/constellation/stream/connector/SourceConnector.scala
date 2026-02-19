package io.constellation.stream.connector

import cats.effect.IO

import fs2.Stream

import io.constellation.CValue

/** A source connector provides a stream of CValues from an external system.
  *
  * Implementations wrap external data sources (queues, files, Kafka topics, etc.) and expose them
  * as fs2 Streams.
  */
trait SourceConnector {

  /** The name of this source connector. */
  def name: String

  /** The stream of values produced by this source. */
  def stream: Stream[IO, CValue]
}
