package io.constellation.stream.connector

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

  /** All registered source names. */
  def sourceNames: Set[String] = sources.keySet

  /** All registered sink names. */
  def sinkNames: Set[String] = sinks.keySet
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
