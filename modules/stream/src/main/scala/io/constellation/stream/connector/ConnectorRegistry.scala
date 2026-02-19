package io.constellation.stream.connector

import cats.effect.IO
import cats.implicits.*

/** Registry for source and sink connectors, used by StreamCompiler to bind DAG nodes to external
  * I/O.
  *
  * Built using a builder pattern for ergonomic construction.
  */
final class ConnectorRegistry private (
    private val sources: Map[String, SourceConnector],
    private val sinks: Map[String, SinkConnector]
) {

  /** Look up a source connector by name. */
  def getSource(name: String): Option[SourceConnector] = sources.get(name)

  /** Look up a sink connector by name. */
  def getSink(name: String): Option[SinkConnector] = sinks.get(name)

  /** Get the configuration schema for a named source connector. */
  def getSourceSchema(name: String): Option[ConnectorSchema] =
    sources.get(name).map(_.configSchema)

  /** Get the configuration schema for a named sink connector. */
  def getSinkSchema(name: String): Option[ConnectorSchema] =
    sinks.get(name).map(_.configSchema)

  /** All registered source names. */
  def sourceNames: Set[String] = sources.keySet

  /** All registered sink names. */
  def sinkNames: Set[String] = sinks.keySet

  /** All registered sources. */
  def allSources: Map[String, SourceConnector] = sources

  /** All registered sinks. */
  def allSinks: Map[String, SinkConnector] = sinks

  /** Run health checks on all registered connectors and return aggregated reports. */
  def healthCheck: IO[Map[String, ConnectorHealthReport]] =
    for {
      sourceReports <- sources.toList.traverse { case (name, src) =>
        src.healthReport.map(name -> _)
      }
      sinkReports <- sinks.toList.traverse { case (name, snk) =>
        snk.healthReport.map(name -> _)
      }
    } yield (sourceReports ++ sinkReports).toMap
}

object ConnectorRegistry {

  /** Create a new builder for constructing a ConnectorRegistry. */
  def builder: Builder = new Builder(Map.empty, Map.empty)

  /** An empty registry with no connectors. */
  val empty: ConnectorRegistry = new ConnectorRegistry(Map.empty, Map.empty)

  final class Builder private[ConnectorRegistry] (
      sources: Map[String, SourceConnector],
      sinks: Map[String, SinkConnector]
  ) {

    /** Register a source connector. */
    def source(name: String, connector: SourceConnector): Builder =
      new Builder(sources + (name -> connector), sinks)

    /** Register a sink connector. */
    def sink(name: String, connector: SinkConnector): Builder =
      new Builder(sources, sinks + (name -> connector))

    /** Build the immutable ConnectorRegistry. */
    def build: ConnectorRegistry = new ConnectorRegistry(sources, sinks)
  }
}
