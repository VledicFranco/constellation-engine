package io.constellation.stream.connector

import scala.concurrent.duration.FiniteDuration

/** A validated connector configuration with typed accessors.
  *
  * Instances are only created by successful validation via `ConnectorConfig.validate`. All required
  * fields are guaranteed present and all values conform to their declared PropertyType.
  */
final class ValidatedConnectorConfig private[connector] (
    private val resolved: Map[String, String]
) {

  /** Get a string property value. */
  def getString(key: String): Option[String] = resolved.get(key)

  /** Get an integer property value. */
  def getInt(key: String): Option[Int] = resolved.get(key).flatMap(_.toIntOption)

  /** Get a duration property value (parsed from format like "1s", "500ms", "2m"). */
  def getDuration(key: String): Option[FiniteDuration] =
    resolved.get(key).flatMap(ValidatedConnectorConfig.parseDuration)

  /** Get a string property with a fallback default. */
  def getStringOrDefault(key: String, default: String): String =
    resolved.getOrElse(key, default)

  /** All resolved properties as a raw map. */
  def toMap: Map[String, String] = resolved
}

object ValidatedConnectorConfig {

  /** An empty validated config for connectors that require no configuration. */
  val empty: ValidatedConnectorConfig = new ValidatedConnectorConfig(Map.empty)

  /** Parse a duration string like "1s", "500ms", "2m", "1h" into FiniteDuration. */
  private[connector] def parseDuration(s: String): Option[FiniteDuration] = {
    import scala.concurrent.duration.*
    val trimmed = s.trim
    try
      Some(Duration(trimmed).asInstanceOf[FiniteDuration])
    catch {
      case _: Exception => None
    }
  }
}
