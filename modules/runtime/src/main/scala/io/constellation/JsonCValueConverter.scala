package io.constellation

import io.circe.Json

/** Bidirectional converter between JSON and CValue/RawValue types.
  *
  * JSON → CValue/RawValue requires type information (CType) to guide the conversion. CValue → JSON
  * is straightforward and doesn't require type information.
  *
  * ==Conversion Strategies==
  *
  *   - **Eager** (`jsonToCValue`): Full recursive conversion. Best for small payloads (<10KB).
  *   - **Adaptive** (`convertAdaptive`): Automatically chooses strategy based on payload size. Uses
  *     streaming for large payloads, lazy for medium, eager for small.
  *   - **Streaming** (`StreamingJsonConverter`): Jackson-based streaming for very large payloads.
  *
  * ==Memory-Efficient Path==
  *
  * For large data (especially numeric arrays), use the RawValue methods:
  *   - `jsonToRawValue`: Direct JSON to RawValue (most efficient)
  *   - `rawValueToJson`: Direct RawValue to JSON using type info
  *
  * These avoid allocating intermediate CValue wrappers, providing ~6x memory reduction for large
  * numeric arrays.
  *
  * ==Performance Guidelines==
  *
  * | Payload Size   | Recommended Method                            |
  * |:---------------|:----------------------------------------------|
  * | < 10KB         | `jsonToCValue` (eager)                        |
  * | 10-100KB       | `convertAdaptive`                             |
  * | > 100KB        | `convertAdaptive` or `StreamingJsonConverter` |
  * | Numeric arrays | `jsonToRawValue`                              |
  */
object JsonCValueConverter {

  /** Adaptive conversion that automatically chooses the best strategy based on payload size.
    *
    * Uses:
    *   - Eager (recursive descent) for payloads < 10KB
    *   - Lazy conversion for payloads 10-100KB
    *   - Streaming (Jackson) for payloads > 100KB
    *
    * @param json
    *   The JSON to convert
    * @param expectedType
    *   The expected CType
    * @return
    *   Either error message or converted CValue
    */
  def convertAdaptive(json: Json, expectedType: CType): Either[String, CValue] =
    AdaptiveJsonConverter.convert(json, expectedType)

  /** Adaptive conversion with explicit size hint.
    *
    * @param json
    *   The JSON to convert
    * @param expectedType
    *   The expected CType
    * @param sizeHint
    *   Size of the JSON payload in bytes (avoids re-estimation)
    * @return
    *   Either error message or converted CValue
    */
  def convertAdaptive(json: Json, expectedType: CType, sizeHint: Int): Either[String, CValue] =
    AdaptiveJsonConverter.convert(json, expectedType, sizeHint)

  /** Convert JSON to CValue using the expected type as a guide.
    *
    * @param json
    *   The JSON value to convert
    * @param expectedType
    *   The expected CType to guide conversion
    * @param path
    *   The current field path for error reporting (e.g., "field.subfield")
    * @return
    *   Either an error message with field path or the converted CValue
    */
  def jsonToCValue(json: Json, expectedType: CType, path: String = ""): Either[String, CValue] = {
    expectedType match {
      case CType.CString =>
        json.asString match {
          case Some(str) => Right(CValue.CString(str))
          case None      => Left(fieldError(path, s"expected String, got ${jsonTypeName(json)}"))
        }

      case CType.CInt =>
        json.asNumber.flatMap(_.toLong) match {
          case Some(num) => Right(CValue.CInt(num))
          case None      => Left(fieldError(path, s"expected Int, got ${jsonTypeName(json)}"))
        }

      case CType.CFloat =>
        json.asNumber.map(_.toDouble) match {
          case Some(num) => Right(CValue.CFloat(num))
          case None      => Left(fieldError(path, s"expected Float, got ${jsonTypeName(json)}"))
        }

      case CType.CBoolean =>
        json.asBoolean match {
          case Some(bool) => Right(CValue.CBoolean(bool))
          case None       => Left(fieldError(path, s"expected Boolean, got ${jsonTypeName(json)}"))
        }

      case CType.CList(valuesType) =>
        json.asArray match {
          case Some(arr) =>
            val converted = arr.zipWithIndex.map { case (elem, idx) =>
              jsonToCValue(elem, valuesType, fieldPath(path, s"[$idx]"))
            }
            val errors = converted.collect { case Left(err) => err }
            if errors.nonEmpty then {
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
            if errors.nonEmpty then {
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
                        key   <- jsonToCValue(pair(0), keysType, fieldPath(path, s"[$idx][0]"))
                        value <- jsonToCValue(pair(1), valuesType, fieldPath(path, s"[$idx][1]"))
                      } yield (key, value)
                    case _ => Left(fieldError(path, s"[$idx]: expected [key, value] pair"))
                  }
                }
                val errors = converted.collect { case Left(err) => err }
                if errors.nonEmpty then {
                  Left(errors.mkString("; "))
                } else {
                  val pairs = converted.collect { case Right(p) => p }.toVector
                  Right(CValue.CMap(pairs, keysType, valuesType))
                }
              case None =>
                Left(fieldError(path, s"expected Array or Object, got ${jsonTypeName(json)}"))
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
            if errors.nonEmpty then {
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
                        Left(
                          fieldError(
                            path,
                            s"invalid union tag '$tag', expected one of: ${structure.keys.mkString(", ")}"
                          )
                        )
                    }
                  case None =>
                    Left(fieldError(path, "union tag must be a string"))
                }
              case _ =>
                Left(fieldError(path, "union must have 'tag' and 'value' fields"))
            }
          case None =>
            Left(
              fieldError(path, s"expected Object with 'tag' and 'value', got ${jsonTypeName(json)}")
            )
        }

      case CType.COptional(innerType) =>
        // null or missing field is None, otherwise convert inner value
        if json.isNull then {
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
    * @param cValue
    *   The CValue to convert
    * @return
    *   The converted JSON value
    */
  def cValueToJson(cValue: CValue): Json = cValue match {
    case CValue.CString(value)  => Json.fromString(value)
    case CValue.CInt(value)     => Json.fromLong(value)
    case CValue.CFloat(value)   => Json.fromDouble(value).getOrElse(Json.fromString(value.toString))
    case CValue.CBoolean(value) => Json.fromBoolean(value)

    case CValue.CList(values, _) =>
      Json.fromValues(values.map(cValueToJson))

    case CValue.CMap(pairs, keysType, _) =>
      // If keys are strings, use object format, otherwise use array of pairs
      keysType match {
        case CType.CString =>
          val fields = pairs.map {
            case (CValue.CString(key), value) => key -> cValueToJson(value)
            case _ =>
              throw new RuntimeException("CMap with CString key type must have CString keys")
          }
          Json.obj(fields*)
        case _ =>
          // Use array of pairs format
          Json.fromValues(pairs.map { case (k, v) =>
            Json.fromValues(Vector(cValueToJson(k), cValueToJson(v)))
          })
      }

    case CValue.CProduct(fields, _) =>
      Json.obj(fields.map { case (name, value) => name -> cValueToJson(value) }.toSeq*)

    case CValue.CUnion(value, _, tag) =>
      Json.obj(
        "tag"   -> Json.fromString(tag),
        "value" -> cValueToJson(value)
      )

    case CValue.CSome(value, _) => cValueToJson(value)
    case CValue.CNone(_)        => Json.Null
  }

  /** Build field path for error reporting */
  private def fieldPath(parent: String, field: String): String =
    if parent.isEmpty then field
    else if field.startsWith("[") then s"$parent$field"
    else s"$parent.$field"

  /** Create error message with field path */
  private def fieldError(path: String, message: String): String =
    if path.isEmpty then message
    else s"field '$path': $message"

  /** Get human-readable JSON type name */
  private def jsonTypeName(json: Json): String =
    if json.isNull then "null"
    else if json.isBoolean then "boolean"
    else if json.isNumber then "number"
    else if json.isString then "string"
    else if json.isArray then "array"
    else if json.isObject then "object"
    else "unknown"

  // ==========================================================================
  // RawValue Conversion Methods (Memory-Efficient Path)
  // ==========================================================================

  /** Convert JSON directly to RawValue using the expected type as a guide.
    *
    * This is more memory-efficient than JSON → CValue → RawValue for large data, as it avoids
    * allocating intermediate CValue wrappers.
    *
    * @param json
    *   The JSON value to convert
    * @param expectedType
    *   The expected CType to guide conversion
    * @param path
    *   The current field path for error reporting
    * @return
    *   Either an error message with field path or the converted RawValue
    */
  def jsonToRawValue(
      json: Json,
      expectedType: CType,
      path: String = ""
  ): Either[String, RawValue] = {
    expectedType match {
      case CType.CString =>
        json.asString match {
          case Some(str) => Right(RawValue.RString(str))
          case None      => Left(fieldError(path, s"expected String, got ${jsonTypeName(json)}"))
        }

      case CType.CInt =>
        json.asNumber.flatMap(_.toLong) match {
          case Some(num) => Right(RawValue.RInt(num))
          case None      => Left(fieldError(path, s"expected Int, got ${jsonTypeName(json)}"))
        }

      case CType.CFloat =>
        json.asNumber.map(_.toDouble) match {
          case Some(num) => Right(RawValue.RFloat(num))
          case None      => Left(fieldError(path, s"expected Float, got ${jsonTypeName(json)}"))
        }

      case CType.CBoolean =>
        json.asBoolean match {
          case Some(bool) => Right(RawValue.RBool(bool))
          case None       => Left(fieldError(path, s"expected Boolean, got ${jsonTypeName(json)}"))
        }

      case CType.CList(CType.CInt) =>
        // Specialized path for int lists - use unboxed array
        json.asArray match {
          case Some(arr) =>
            val result                = new Array[Long](arr.size)
            var i                     = 0
            var error: Option[String] = None
            while i < arr.size && error.isEmpty do
              arr(i).asNumber.flatMap(_.toLong) match {
                case Some(num) =>
                  result(i) = num
                  i += 1
                case None =>
                  error = Some(
                    fieldError(
                      fieldPath(path, s"[$i]"),
                      s"expected Int, got ${jsonTypeName(arr(i))}"
                    )
                  )
              }
            error match {
              case Some(err) => Left(err)
              case None      => Right(RawValue.RIntList(result))
            }
          case None => Left(fieldError(path, s"expected Array, got ${jsonTypeName(json)}"))
        }

      case CType.CList(CType.CFloat) =>
        // Specialized path for float lists - use unboxed array
        json.asArray match {
          case Some(arr) =>
            val result                = new Array[Double](arr.size)
            var i                     = 0
            var error: Option[String] = None
            while i < arr.size && error.isEmpty do
              arr(i).asNumber.map(_.toDouble) match {
                case Some(num) =>
                  result(i) = num
                  i += 1
                case None =>
                  error = Some(
                    fieldError(
                      fieldPath(path, s"[$i]"),
                      s"expected Float, got ${jsonTypeName(arr(i))}"
                    )
                  )
              }
            error match {
              case Some(err) => Left(err)
              case None      => Right(RawValue.RFloatList(result))
            }
          case None => Left(fieldError(path, s"expected Array, got ${jsonTypeName(json)}"))
        }

      case CType.CList(CType.CString) =>
        // Specialized path for string lists
        json.asArray match {
          case Some(arr) =>
            val result                = new Array[String](arr.size)
            var i                     = 0
            var error: Option[String] = None
            while i < arr.size && error.isEmpty do
              arr(i).asString match {
                case Some(str) =>
                  result(i) = str
                  i += 1
                case None =>
                  error = Some(
                    fieldError(
                      fieldPath(path, s"[$i]"),
                      s"expected String, got ${jsonTypeName(arr(i))}"
                    )
                  )
              }
            error match {
              case Some(err) => Left(err)
              case None      => Right(RawValue.RStringList(result))
            }
          case None => Left(fieldError(path, s"expected Array, got ${jsonTypeName(json)}"))
        }

      case CType.CList(CType.CBoolean) =>
        // Specialized path for bool lists
        json.asArray match {
          case Some(arr) =>
            val result                = new Array[Boolean](arr.size)
            var i                     = 0
            var error: Option[String] = None
            while i < arr.size && error.isEmpty do
              arr(i).asBoolean match {
                case Some(b) =>
                  result(i) = b
                  i += 1
                case None =>
                  error = Some(
                    fieldError(
                      fieldPath(path, s"[$i]"),
                      s"expected Boolean, got ${jsonTypeName(arr(i))}"
                    )
                  )
              }
            error match {
              case Some(err) => Left(err)
              case None      => Right(RawValue.RBoolList(result))
            }
          case None => Left(fieldError(path, s"expected Array, got ${jsonTypeName(json)}"))
        }

      case CType.CList(elemType) =>
        // Generic list for nested types
        json.asArray match {
          case Some(arr) =>
            val results = arr.zipWithIndex.map { case (elem, idx) =>
              jsonToRawValue(elem, elemType, fieldPath(path, s"[$idx]"))
            }
            val errors = results.collect { case Left(err) => err }
            if errors.nonEmpty then {
              Left(errors.mkString("; "))
            } else {
              Right(RawValue.RList(results.collect { case Right(v) => v }.toArray))
            }
          case None => Left(fieldError(path, s"expected Array, got ${jsonTypeName(json)}"))
        }

      case CType.CMap(keyType, valueType) =>
        json.asObject match {
          case Some(obj) if keyType == CType.CString =>
            val results = obj.toList.map { case (key, value) =>
              jsonToRawValue(value, valueType, fieldPath(path, key)).map { v =>
                (RawValue.RString(key): RawValue, v)
              }
            }
            val errors = results.collect { case Left(err) => err }
            if errors.nonEmpty then {
              Left(errors.mkString("; "))
            } else {
              Right(RawValue.RMap(results.collect { case Right(p) => p }.toArray))
            }
          case _ =>
            json.asArray match {
              case Some(arr) =>
                val results = arr.zipWithIndex.map { case (elem, idx) =>
                  elem.asArray match {
                    case Some(pair) if pair.size == 2 =>
                      for {
                        key   <- jsonToRawValue(pair(0), keyType, fieldPath(path, s"[$idx][0]"))
                        value <- jsonToRawValue(pair(1), valueType, fieldPath(path, s"[$idx][1]"))
                      } yield (key, value)
                    case _ => Left(fieldError(path, s"[$idx]: expected [key, value] pair"))
                  }
                }
                val errors = results.collect { case Left(err) => err }
                if errors.nonEmpty then {
                  Left(errors.mkString("; "))
                } else {
                  Right(RawValue.RMap(results.collect { case Right(p) => p }.toArray))
                }
              case None =>
                Left(fieldError(path, s"expected Array or Object, got ${jsonTypeName(json)}"))
            }
        }

      case CType.CProduct(structure) =>
        json.asObject match {
          case Some(obj) =>
            val sortedFields = structure.toList.sortBy(_._1)
            val results = sortedFields.map { case (fieldName, fieldType) =>
              obj(fieldName) match {
                case Some(fieldJson) =>
                  jsonToRawValue(fieldJson, fieldType, fieldPath(path, fieldName))
                case None =>
                  Left(fieldError(path, s"missing required field '$fieldName'"))
              }
            }
            val errors = results.collect { case Left(err) => err }
            if errors.nonEmpty then {
              Left(errors.mkString("; "))
            } else {
              Right(RawValue.RProduct(results.collect { case Right(v) => v }.toArray))
            }
          case None => Left(fieldError(path, s"expected Object, got ${jsonTypeName(json)}"))
        }

      case CType.CUnion(structure) =>
        json.asObject match {
          case Some(obj) =>
            (obj("tag"), obj("value")) match {
              case (Some(tagJson), Some(valueJson)) =>
                tagJson.asString match {
                  case Some(tag) =>
                    structure.get(tag) match {
                      case Some(valueType) =>
                        jsonToRawValue(valueJson, valueType, fieldPath(path, "value")).map { v =>
                          RawValue.RUnion(tag, v)
                        }
                      case None =>
                        Left(
                          fieldError(
                            path,
                            s"invalid union tag '$tag', expected one of: ${structure.keys.mkString(", ")}"
                          )
                        )
                    }
                  case None =>
                    Left(fieldError(path, "union tag must be a string"))
                }
              case _ =>
                Left(fieldError(path, "union must have 'tag' and 'value' fields"))
            }
          case None =>
            Left(
              fieldError(path, s"expected Object with 'tag' and 'value', got ${jsonTypeName(json)}")
            )
        }

      case CType.COptional(innerType) =>
        if json.isNull then {
          Right(RawValue.RNone)
        } else {
          jsonToRawValue(json, innerType, path).map(RawValue.RSome(_))
        }
    }
  }

  /** Convert RawValue to JSON using type information.
    *
    * @param raw
    *   The RawValue to convert
    * @param cType
    *   The CType describing the value's structure
    * @return
    *   The converted JSON value
    */
  def rawValueToJson(raw: RawValue, cType: CType): Json = (raw, cType) match {
    case (RawValue.RString(v), _) => Json.fromString(v)
    case (RawValue.RInt(v), _)    => Json.fromLong(v)
    case (RawValue.RFloat(v), _)  => Json.fromDouble(v).getOrElse(Json.fromString(v.toString))
    case (RawValue.RBool(v), _)   => Json.fromBoolean(v)

    case (RawValue.RSome(v), CType.COptional(innerType)) =>
      rawValueToJson(v, innerType)
    case (RawValue.RNone, _) => Json.Null

    case (RawValue.RIntList(values), _) =>
      Json.fromValues(values.map(Json.fromLong))

    case (RawValue.RFloatList(values), _) =>
      Json.fromValues(values.map(v => Json.fromDouble(v).getOrElse(Json.fromString(v.toString))))

    case (RawValue.RStringList(values), _) =>
      Json.fromValues(values.map(Json.fromString))

    case (RawValue.RBoolList(values), _) =>
      Json.fromValues(values.map(Json.fromBoolean))

    case (RawValue.RList(values), CType.CList(elemType)) =>
      Json.fromValues(values.map(v => rawValueToJson(v, elemType)))

    case (RawValue.RMap(entries), CType.CMap(keyType, valueType)) =>
      keyType match {
        case CType.CString =>
          val fields = entries.map {
            case (RawValue.RString(key), value) => key -> rawValueToJson(value, valueType)
            case (key, _) =>
              throw new RuntimeException(s"Expected RString key, got ${key.getClass.getSimpleName}")
          }
          Json.obj(fields.toSeq*)
        case _ =>
          Json.fromValues(entries.map { case (k, v) =>
            Json.fromValues(Vector(rawValueToJson(k, keyType), rawValueToJson(v, valueType)))
          })
      }

    case (RawValue.RProduct(values), CType.CProduct(structure)) =>
      val sortedFields = structure.toList.sortBy(_._1)
      val fields = sortedFields.zip(values).map { case ((name, fieldType), value) =>
        name -> rawValueToJson(value, fieldType)
      }
      Json.obj(fields*)

    case (RawValue.RUnion(tag, value), CType.CUnion(structure)) =>
      val valueType =
        structure.getOrElse(tag, throw new RuntimeException(s"Unknown union tag '$tag'"))
      Json.obj(
        "tag"   -> Json.fromString(tag),
        "value" -> rawValueToJson(value, valueType)
      )

    case (raw, cType) =>
      throw new RuntimeException(
        s"Cannot convert ${raw.getClass.getSimpleName} to JSON with type $cType"
      )
  }
}
