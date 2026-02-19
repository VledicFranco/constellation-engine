package io.constellation.stream.config

/** Binding of a DAG source node to a connector type with configuration properties. */
final case class SourceBinding(
    connectorType: String,
    properties: Map[String, String] = Map.empty
)

/** Binding of a DAG sink node to a connector type with configuration properties. */
final case class SinkBinding(
    connectorType: String,
    properties: Map[String, String] = Map.empty
)

/** Configuration for a streaming pipeline, binding DAG sources/sinks to connectors.
  *
  * @param sourceBindings
  *   Map of source node name to its connector binding
  * @param sinkBindings
  *   Map of sink node name to its connector binding
  * @param options
  *   Additional pipeline-level options
  * @param dlq
  *   Optional dead-letter queue sink binding for failed elements
  */
final case class StreamPipelineConfig(
    sourceBindings: Map[String, SourceBinding] = Map.empty,
    sinkBindings: Map[String, SinkBinding] = Map.empty,
    options: Map[String, String] = Map.empty,
    dlq: Option[SinkBinding] = None
)
