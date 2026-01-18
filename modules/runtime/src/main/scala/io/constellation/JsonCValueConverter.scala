package io.constellation

import io.circe.Json

/** Bidirectional converter between JSON and CValue types.
  *
  * JSON → CValue requires type information (CType) to guide the conversion.
  * CValue → JSON is straightforward and doesn't require type information.
  */
object JsonCValueConverter {

  /** Convert JSON to CValue using the expected type as a guide.
    *
    * @param json The JSON value to convert
    * @param expectedType The expected CType to guide conversion
    * @param path The current field path for error reporting (e.g., "field.subfield")
    * @return Either an error message with field path or the converted CValue
    */
  def jsonToCValue(json: Json, expectedType: CType, path: String = ""): Either[String, CValue] = {
    expectedType match {
      case CType.CString =>
        json.asString match {
          case Some(str) => Right(CValue.CString(str))
          case None => Left(fieldError(path, s"expected String, got ${jsonTypeName(json)}"))
        }

      case CType.CInt =>
        json.asNumber.flatMap(_.toLong) match {
          case Some(num) => Right(CValue.CInt(num))
          case None => Left(fieldError(path, s"expected Int, got ${jsonTypeName(json)}"))
        }

      case CType.CFloat =>
        json.asNumber.map(_.toDouble) match {
          case Some(num) => Right(CValue.CFloat(num))
          case None => Left(fieldError(path, s"expected Float, got ${jsonTypeName(json)}"))
        }

      case CType.CBoolean =>
        json.asBoolean match {
          case Some(bool) => Right(CValue.CBoolean(bool))
          case None => Left(fieldError(path, s"expected Boolean, got ${jsonTypeName(json)}"))
        }

      case CType.CList(valuesType) =>
        json.asArray match {
          case Some(arr) =>
            val converted = arr.zipWithIndex.map { case (elem, idx) =>
              jsonToCValue(elem, valuesType, fieldPath(path, s"[$idx]"))
            }
            val errors = converted.collect { case Left(err) => err }
            if (errors.nonEmpty) {
              Left(errors.mkString("; "))
            } else {
              val values = converted.collect { case Right(v) => v }.toVector
              Right(CValue.CList(values, valuesType))
            }
          case None => Left(fieldError(path, s"expected Array, got ${jsonTypeName(json)}"))
        }

      case CType.CMap(keysType, valuesType) =>
        // Support two formats:
        // 1. JSON object (only if keys are strings): {"key1": val1, "key2": val2}
        // 2. Array of pairs: [["key1", val1], ["key2", val2]]
        json.asObject match {
          case Some(obj) if keysType == CType.CString =>
            // Object format - keys must be strings
            val converted = obj.toList.map { case (key, value) =>
              jsonToCValue(value, valuesType, fieldPath(path, key)).map { v =>
                (CValue.CString(key), v)
              }
            }
            val errors = converted.collect { case Left(err) => err }
            if (errors.nonEmpty) {
              Left(errors.mkString("; "))
            } else {
              val pairs = converted.collect { case Right(p) => p }.toVector
              Right(CValue.CMap(pairs, keysType, valuesType))
            }
          case _ =>
            // Array of pairs format
            json.asArray match {
              case Some(arr) =>
                val converted = arr.zipWithIndex.map { case (elem, idx) =>
                  elem.asArray match {
                    case Some(pair) if pair.size == 2 =>
                      for {
                        key <- jsonToCValue(pair(0), keysType, fieldPath(path, s"[$idx][0]"))
                        value <- jsonToCValue(pair(1), valuesType, fieldPath(path, s"[$idx][1]"))
                      } yield (key, value)
                    case _ => Left(fieldError(path, s"[$idx]: expected [key, value] pair"))
                  }
                }
                val errors = converted.collect { case Left(err) => err }
                if (errors.nonEmpty) {
                  Left(errors.mkString("; "))
                } else {
                  val pairs = converted.collect { case Right(p) => p }.toVector
                  Right(CValue.CMap(pairs, keysType, valuesType))
                }
              case None => Left(fieldError(path, s"expected Array or Object, got ${jsonTypeName(json)}"))
            }
        }

      case CType.CProduct(structure) =>
        json.asObject match {
          case Some(obj) =>
            val converted = structure.map { case (fieldName, fieldType) =>
              obj(fieldName) match {
                case Some(fieldJson) =>
                  jsonToCValue(fieldJson, fieldType, fieldPath(path, fieldName)).map(fieldName -> _)
                case None =>
                  Left(fieldError(path, s"missing required field '$fieldName'"))
              }
            }
            val errors = converted.collect { case Left(err) => err }
            if (errors.nonEmpty) {
              Left(errors.mkString("; "))
            } else {
              val fields = converted.collect { case Right(f) => f }.toMap
              Right(CValue.CProduct(fields, structure))
            }
          case None => Left(fieldError(path, s"expected Object, got ${jsonTypeName(json)}"))
        }

      case CType.CUnion(structure) =>
        // Expect format: {"tag": "...", "value": ...}
        json.asObject match {
          case Some(obj) =>
            (obj("tag"), obj("value")) match {
              case (Some(tagJson), Some(valueJson)) =>
                tagJson.asString match {
                  case Some(tag) =>
                    structure.get(tag) match {
                      case Some(valueType) =>
                        jsonToCValue(valueJson, valueType, fieldPath(path, "value")).map { v =>
                          CValue.CUnion(v, structure, tag)
                        }
                      case None =>
                        Left(fieldError(path, s"invalid union tag '$tag', expected one of: ${structure.keys.mkString(", ")}"))
                    }
                  case None =>
                    Left(fieldError(path, "union tag must be a string"))
                }
              case _ =>
                Left(fieldError(path, "union must have 'tag' and 'value' fields"))
            }
          case None => Left(fieldError(path, s"expected Object with 'tag' and 'value', got ${jsonTypeName(json)}"))
        }

      case CType.COptional(innerType) =>
        // null or missing field is None, otherwise convert inner value
        if (json.isNull) {
          Right(CValue.CNone(innerType))
        } else {
          jsonToCValue(json, innerType, path).map(v => CValue.CSome(v, innerType))
        }
    }
  }

  /** Convert CValue to JSON.
    *
    * This is a straightforward conversion that doesn't require type information.
    *
    * @param cValue The CValue to convert
    * @return The converted JSON value
    */
  def cValueToJson(cValue: CValue): Json = cValue match {
    case CValue.CString(value) => Json.fromString(value)
    case CValue.CInt(value) => Json.fromLong(value)
    case CValue.CFloat(value) => Json.fromDouble(value).getOrElse(Json.fromString(value.toString))
    case CValue.CBoolean(value) => Json.fromBoolean(value)

    case CValue.CList(values, _) =>
      Json.fromValues(values.map(cValueToJson))

    case CValue.CMap(pairs, keysType, _) =>
      // If keys are strings, use object format, otherwise use array of pairs
      keysType match {
        case CType.CString =>
          val fields = pairs.map {
            case (CValue.CString(key), value) => key -> cValueToJson(value)
            case _ => throw new RuntimeException("CMap with CString key type must have CString keys")
          }
          Json.obj(fields: _*)
        case _ =>
          // Use array of pairs format
          Json.fromValues(pairs.map { case (k, v) =>
            Json.fromValues(Vector(cValueToJson(k), cValueToJson(v)))
          })
      }

    case CValue.CProduct(fields, _) =>
      Json.obj(fields.map { case (name, value) => name -> cValueToJson(value) }.toSeq: _*)

    case CValue.CUnion(value, _, tag) =>
      Json.obj(
        "tag" -> Json.fromString(tag),
        "value" -> cValueToJson(value)
      )

    case CValue.CSome(value, _) => cValueToJson(value)
    case CValue.CNone(_) => Json.Null
  }

  /** Build field path for error reporting */
  private def fieldPath(parent: String, field: String): String = {
    if (parent.isEmpty) field
    else if (field.startsWith("[")) s"$parent$field"
    else s"$parent.$field"
  }

  /** Create error message with field path */
  private def fieldError(path: String, message: String): String = {
    if (path.isEmpty) message
    else s"field '$path': $message"
  }

  /** Get human-readable JSON type name */
  private def jsonTypeName(json: Json): String = {
    if (json.isNull) "null"
    else if (json.isBoolean) "boolean"
    else if (json.isNumber) "number"
    else if (json.isString) "string"
    else if (json.isArray) "array"
    else if (json.isObject) "object"
    else "unknown"
  }
}
