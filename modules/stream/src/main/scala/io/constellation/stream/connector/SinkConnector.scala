package io.constellation.stream.connector

import cats.effect.IO

import fs2.Pipe

import io.constellation.CValue

/** A sink connector consumes a stream of CValues and writes them to an external system.
  *
  * Implementations wrap external data sinks (queues, files, databases, etc.) and expose them as
  * fs2 Pipes.
  */
trait SinkConnector {

  /** The name of this sink connector. */
  def name: String

  /** The pipe that consumes values and writes them to the sink. */
  def pipe: Pipe[IO, CValue, Unit]
}
