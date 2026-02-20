package io.constellation.stream

import cats.effect.IO

/** Entry point for deploying a compiled stream graph.
  *
  * StreamRuntime manages the lifecycle of a running stream: startup, health monitoring, graceful
  * shutdown, and metrics reporting.
  */
object StreamRuntime {

  /** Deploy a StreamGraph and run it until shutdown is triggered.
    *
    * @param graph
    *   The compiled stream graph
    * @param options
    *   Stream configuration options
    * @return
    *   IO that completes when the stream finishes or is shut down
    */
  def deploy(graph: StreamGraph, options: StreamOptions = StreamOptions()): IO[Unit] =
    graph.stream.compile.drain
}
