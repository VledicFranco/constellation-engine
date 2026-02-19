package io.constellation.stream.connector

import io.constellation.CType

/** Schema for connector configuration validation.
  *
  * @param name
  *   Connector type name (e.g., "memory", "kafka", "file")
  * @param configFields
  *   Expected configuration fields and their types
  */
final case class ConnectorSchema(
    name: String,
    configFields: Map[String, CType] = Map.empty
)
