package io.constellation

import cats.effect.IO
import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import java.io.InputStream

/** Streaming JSON converter using Jackson for memory-efficient parsing of large payloads.
  * Parses JSON incrementally without loading entire structure into memory.
  *
  * ==Usage==
  *
  * For byte arrays:
  * {{{
  * val converter = StreamingJsonConverter()
  * val result = converter.streamToCValue(bytes, CType.CList(CType.CFloat))
  * }}}
  *
  * For InputStreams (truly streaming):
  * {{{
  * val result = converter.streamFromInputStream(inputStream, expectedType).unsafeRunSync()
  * }}}
  *
  * ==Performance==
  *
  * The streaming converter is most beneficial for:
  * - Large payloads (>100KB)
  * - Numeric arrays (embeddings, feature vectors)
  * - When memory is constrained
  *
  * For small payloads, the overhead of Jackson parsing may exceed the benefits.
  */
class StreamingJsonConverter {
  private val factory = new JsonFactory()

  /** Stream JSON from InputStream directly to CValue.
    * Memory-efficient for large payloads.
    *
    * @param input The InputStream to read from
    * @param expectedType The expected CType
    * @return IO containing Either error message or converted CValue
    */
  def streamFromInputStream(input: InputStream, expectedType: CType): IO[Either[String, CValue]] = IO.blocking {
    try {
      val parser = factory.createParser(input)
      try {
        parser.nextToken() // Move to first token
        Right(parseValue(parser, expectedType, ""))
      } finally {
        parser.close()
      }
    } catch {
      case e: Exception => Left(s"Streaming parse error: ${e.getMessage}")
    }
  }

  /** Parse from byte array using streaming (still streaming internally, but from memory).
    *
    * @param bytes The JSON bytes to parse
    * @param expectedType The expected CType
    * @return Either error message or converted CValue
    */
  def streamToCValue(bytes: Array[Byte], expectedType: CType): Either[String, CValue] = {
    try {
      val parser = factory.createParser(bytes)
      try {
        parser.nextToken() // Move to first token
        Right(parseValue(parser, expectedType, ""))
      } finally {
        parser.close()
      }
    } catch {
      case e: Exception => Left(s"Streaming parse error: ${e.getMessage}")
    }
  }

  /** Parse from string using streaming.
    *
    * @param jsonString The JSON string to parse
    * @param expectedType The expected CType
    * @return Either error message or converted CValue
    */
  def streamFromString(jsonString: String, expectedType: CType): Either[String, CValue] = {
    try {
      val parser = factory.createParser(jsonString)
      try {
        parser.nextToken()
        Right(parseValue(parser, expectedType, ""))
      } finally {
        parser.close()
      }
    } catch {
      case e: Exception => Left(s"Streaming parse error: ${e.getMessage}")
    }
  }

  private def parseValue(parser: JsonParser, expectedType: CType, path: String): CValue = {
    val token = parser.currentToken()

    (token, expectedType) match {
      case (JsonToken.VALUE_STRING, CType.CString) =>
        CValue.CString(parser.getValueAsString)

      case (JsonToken.VALUE_NUMBER_INT, CType.CInt) =>
        CValue.CInt(parser.getValueAsLong)

      case (JsonToken.VALUE_NUMBER_FLOAT | JsonToken.VALUE_NUMBER_INT, CType.CFloat) =>
        CValue.CFloat(parser.getValueAsDouble)

      case (JsonToken.VALUE_TRUE | JsonToken.VALUE_FALSE, CType.CBoolean) =>
        CValue.CBoolean(parser.getValueAsBoolean)

      case (JsonToken.VALUE_NULL, CType.COptional(innerType)) =>
        CValue.CNone(innerType)

      case (JsonToken.START_ARRAY, CType.CList(elemType)) =>
        parseArray(parser, elemType, path)

      case (JsonToken.START_OBJECT, CType.CProduct(fields)) =>
        parseObject(parser, fields, path)

      case (JsonToken.START_OBJECT, CType.CUnion(structure)) =>
        parseUnion(parser, structure, path)

      case (_, CType.COptional(innerType)) if token != JsonToken.VALUE_NULL =>
        CValue.CSome(parseValue(parser, innerType, path), innerType)

      case _ =>
        throw new IllegalArgumentException(
          s"Unexpected token $token for type $expectedType at path '$path'"
        )
    }
  }

  private def parseArray(parser: JsonParser, elemType: CType, path: String): CValue.CList = {
    val builder = Vector.newBuilder[CValue]
    var idx = 0

    while (parser.nextToken() != JsonToken.END_ARRAY) {
      builder += parseValue(parser, elemType, s"$path[$idx]")
      idx += 1
    }

    CValue.CList(builder.result(), elemType)
  }

  private def parseObject(parser: JsonParser, fields: Map[String, CType], path: String): CValue.CProduct = {
    val values = scala.collection.mutable.Map[String, CValue]()

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.getCurrentName
      parser.nextToken() // Move to value

      fields.get(fieldName) match {
        case Some(fieldType) =>
          val fieldPath = if (path.isEmpty) fieldName else s"$path.$fieldName"
          values(fieldName) = parseValue(parser, fieldType, fieldPath)
        case None =>
          skipValue(parser) // Skip unknown fields
      }
    }

    // Check for missing required fields
    val missing = fields.keySet -- values.keySet
    if (missing.nonEmpty) {
      throw new IllegalArgumentException(s"Missing required fields at '$path': ${missing.mkString(", ")}")
    }

    CValue.CProduct(values.toMap, fields)
  }

  private def parseUnion(parser: JsonParser, structure: Map[String, CType], path: String): CValue.CUnion = {
    var tag: Option[String] = None
    var value: Option[CValue] = None
    var valueType: Option[CType] = None

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.getCurrentName
      parser.nextToken()

      fieldName match {
        case "tag" =>
          tag = Some(parser.getValueAsString)
          // If we already have valueType from structure lookup, we can now parse value if it's pending
          tag.foreach { t =>
            valueType = structure.get(t)
          }
        case "value" =>
          // Need to parse value, but we need the tag first
          // If tag is not yet available, we need to handle this case
          (tag, valueType) match {
            case (Some(_), Some(vt)) =>
              value = Some(parseValue(parser, vt, s"$path.value"))
            case _ =>
              // Tag not yet seen, skip for now - this is a limitation
              throw new IllegalArgumentException(
                s"Union 'value' field must come after 'tag' field at path '$path'"
              )
          }
        case _ =>
          skipValue(parser)
      }
    }

    (tag, value, valueType) match {
      case (Some(t), Some(v), Some(_)) =>
        CValue.CUnion(v, structure, t)
      case (None, _, _) =>
        throw new IllegalArgumentException(s"Union missing 'tag' field at path '$path'")
      case (Some(t), None, None) =>
        throw new IllegalArgumentException(s"Unknown union tag '$t' at path '$path'")
      case (Some(_), None, Some(_)) =>
        throw new IllegalArgumentException(s"Union missing 'value' field at path '$path'")
      case (Some(_), Some(_), None) =>
        // This shouldn't happen in valid code (value set but valueType not set)
        throw new IllegalArgumentException(s"Internal error: value parsed but type unknown at path '$path'")
    }
  }

  /** Skip a value and all its children */
  private def skipValue(parser: JsonParser): Unit = {
    val token = parser.currentToken()
    token match {
      case JsonToken.START_ARRAY =>
        while (parser.nextToken() != JsonToken.END_ARRAY) {
          skipValue(parser)
        }
      case JsonToken.START_OBJECT =>
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          parser.nextToken() // Move to value
          skipValue(parser)
        }
      case _ => // Scalar value, already consumed
    }
  }
}

object StreamingJsonConverter {
  private lazy val instance = new StreamingJsonConverter()

  def apply(): StreamingJsonConverter = instance

  /** Convenience method for streaming from bytes */
  def streamToCValue(bytes: Array[Byte], expectedType: CType): Either[String, CValue] =
    instance.streamToCValue(bytes, expectedType)

  /** Convenience method for streaming from string */
  def streamFromString(jsonString: String, expectedType: CType): Either[String, CValue] =
    instance.streamFromString(jsonString, expectedType)
}
