package io.constellation.stream.config

import io.constellation.stream.connector.ConnectorConfigError

/** Errors produced during pipeline configuration validation. */
sealed trait ConfigValidationError {
  def message: String
}

object ConfigValidationError {

  /** A DAG source node has no connector binding in the config. */
  case class UnboundSource(sourceName: String) extends ConfigValidationError {
    def message: String = s"Source '$sourceName' has no connector binding"
  }

  /** A DAG sink node has no connector binding in the config. */
  case class UnboundSink(sinkName: String) extends ConfigValidationError {
    def message: String = s"Sink '$sinkName' has no connector binding"
  }

  /** The connector type specified in a binding is not registered. */
  case class UnknownConnectorType(bindingName: String, connectorType: String)
      extends ConfigValidationError {
    def message: String =
      s"Binding '$bindingName' references unknown connector type '$connectorType'"
  }

  /** The connector's configuration properties failed validation. */
  case class ConnectorConfigErrors(
      bindingName: String,
      errors: List[ConnectorConfigError]
  ) extends ConfigValidationError {
    def message: String =
      s"Binding '$bindingName' has config errors: ${errors.map(_.message).mkString("; ")}"
  }
}
