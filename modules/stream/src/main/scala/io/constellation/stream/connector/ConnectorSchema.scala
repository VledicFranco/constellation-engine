package io.constellation.stream.connector

/** Schema for connector configuration validation.
  *
  * Defines required and optional properties with their types and constraints.
  *
  * @param required
  *   Properties that must be provided in ConnectorConfig
  * @param optional
  *   Properties that may be omitted (defaults applied if defined)
  * @param description
  *   Human-readable description of this connector's configuration
  */
final case class ConnectorSchema(
    required: Map[String, PropertyType] = Map.empty,
    optional: Map[String, PropertyType] = Map.empty,
    description: String = ""
)

object ConnectorSchema {

  /** An empty schema for connectors that require no configuration. */
  val empty: ConnectorSchema = ConnectorSchema()
}
