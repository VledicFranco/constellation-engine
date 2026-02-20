package io.constellation.stream.connector

/** Errors produced during connector configuration validation. */
sealed trait ConnectorConfigError {
  def field: String
  def message: String
}

object ConnectorConfigError {

  case class MissingRequired(field: String) extends ConnectorConfigError {
    def message: String = s"Required field '$field' is missing"
  }

  case class InvalidType(field: String, expected: String, actual: String)
      extends ConnectorConfigError {
    def message: String = s"Field '$field': expected $expected but got '$actual'"
  }

  case class OutOfRange(field: String, value: Int, min: Int, max: Int)
      extends ConnectorConfigError {
    def message: String = s"Field '$field': value $value is out of range [$min, $max]"
  }

  case class InvalidEnum(field: String, value: String, allowed: Set[String])
      extends ConnectorConfigError {
    def message: String = s"Field '$field': '$value' is not one of ${allowed.mkString(", ")}"
  }
}
