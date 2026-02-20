package io.constellation.stream.connector

import scala.concurrent.duration.FiniteDuration

/** Property type definitions for connector configuration schema validation.
  *
  * Each variant describes the expected type and constraints for a configuration property.
  */
sealed trait PropertyType

object PropertyType {
  case class StringProp(default: Option[String] = None) extends PropertyType
  case class IntProp(default: Option[Int] = None, min: Int = Int.MinValue, max: Int = Int.MaxValue)
      extends PropertyType
  case class DurationProp(default: Option[FiniteDuration] = None)           extends PropertyType
  case class EnumProp(allowed: Set[String], default: Option[String] = None) extends PropertyType
}
