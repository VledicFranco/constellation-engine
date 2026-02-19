package io.constellation.stream.connector

import scala.concurrent.duration.FiniteDuration

/** Raw connector configuration properties before validation.
  *
  * Use `validate(schema)` to check all properties against a ConnectorSchema and produce a
  * ValidatedConnectorConfig or accumulate all errors.
  */
final case class ConnectorConfig(properties: Map[String, String]) {

  /** Validate this config against a schema, accumulating all errors.
    *
    * @return
    *   Right(ValidatedConnectorConfig) if all validations pass, Left(errors) with all accumulated
    *   errors otherwise
    */
  def validate(schema: ConnectorSchema): Either[List[ConnectorConfigError], ValidatedConnectorConfig] = {
    val requiredErrors = checkRequired(schema.required)
    val requiredValid  = validateProperties(schema.required, isRequired = true)
    val optionalValid  = validateProperties(schema.optional, isRequired = false)

    val allErrors = requiredErrors ++ requiredValid ++ optionalValid

    if (allErrors.nonEmpty) Left(allErrors)
    else {
      val resolved = resolveDefaults(schema)
      Right(new ValidatedConnectorConfig(resolved))
    }
  }

  private def checkRequired(required: Map[String, PropertyType]): List[ConnectorConfigError] =
    required.keys.flatMap { key =>
      if (properties.contains(key)) None
      else Some(ConnectorConfigError.MissingRequired(key))
    }.toList

  private def validateProperties(
      props: Map[String, PropertyType],
      isRequired: Boolean
  ): List[ConnectorConfigError] =
    props.flatMap { case (key, propType) =>
      properties.get(key) match {
        case None    => Nil // Missing required already caught; missing optional is fine
        case Some(v) => validateValue(key, v, propType)
      }
    }.toList

  private def validateValue(
      key: String,
      value: String,
      propType: PropertyType
  ): List[ConnectorConfigError] =
    propType match {
      case _: PropertyType.StringProp => Nil // Any string is valid

      case PropertyType.IntProp(_, min, max) =>
        value.toIntOption match {
          case None => List(ConnectorConfigError.InvalidType(key, "Int", value))
          case Some(n) if n < min || n > max =>
            List(ConnectorConfigError.OutOfRange(key, n, min, max))
          case _ => Nil
        }

      case _: PropertyType.DurationProp =>
        ValidatedConnectorConfig.parseDuration(value) match {
          case None => List(ConnectorConfigError.InvalidType(key, "Duration", value))
          case _    => Nil
        }

      case PropertyType.EnumProp(allowed, _) =>
        if (allowed.contains(value)) Nil
        else List(ConnectorConfigError.InvalidEnum(key, value, allowed))
    }

  private def resolveDefaults(schema: ConnectorSchema): Map[String, String] = {
    val allProps = schema.required ++ schema.optional
    val defaults = allProps.flatMap { case (key, propType) =>
      if (properties.contains(key)) None
      else defaultFor(propType).map(key -> _)
    }
    properties ++ defaults
  }

  private def defaultFor(propType: PropertyType): Option[String] =
    propType match {
      case PropertyType.StringProp(default)       => default
      case PropertyType.IntProp(default, _, _)    => default.map(_.toString)
      case PropertyType.DurationProp(default)     => default.map(_.toString)
      case PropertyType.EnumProp(_, default)      => default
    }
}
