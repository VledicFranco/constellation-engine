package io.constellation

import cats.effect.IO
import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import java.io.{InputStream, FilterInputStream}

/** Configuration for streaming JSON parser limits.
  *
  * Prevents DoS attacks via:
  * - Payload size limits (memory exhaustion)
  * - Array element limits (memory exhaustion)
  * - Nesting depth limits (stack overflow)
  */
case class StreamingLimits(
    maxPayloadSize: Long = 100 * 1024 * 1024, // 100MB
    maxArrayElements: Int = 1_000_000,         // 1M elements
    maxNestingDepth: Int = 50                  // 50 levels deep
) {
  def validate: Either[String, StreamingLimits] = {
    if (maxPayloadSize <= 0) Left("maxPayloadSize must be positive")
    else if (maxArrayElements <= 0) Left("maxArrayElements must be positive")
    else if (maxNestingDepth <= 0) Left("maxNestingDepth must be positive")
    else Right(this)
  }
}

object StreamingLimits {
  val default: StreamingLimits = StreamingLimits()
}

/** InputStream wrapper that enforces a maximum read size.
  *
  * Throws IllegalStateException when limit is exceeded.
  */
private class BoundedInputStream(underlying: InputStream, maxBytes: Long) extends FilterInputStream(underlying) {
  private var bytesRead: Long = 0

  override def read(): Int = {
    checkLimit(1)
    val result = super.read()
    if (result != -1) bytesRead += 1
    result
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    checkLimit(len)
    val result = super.read(b, off, len)
    if (result > 0) bytesRead += result
    result
  }

  private def checkLimit(requestedBytes: Int): Unit = {
    if (bytesRead + requestedBytes > maxBytes) {
      throw new IllegalStateException(
        s"Payload size limit exceeded: ${bytesRead + requestedBytes} > $maxBytes bytes"
      )
    }
  }

  def getBytesRead: Long = bytesRead
}

/** Streaming JSON converter using Jackson for memory-efficient parsing of large payloads.
  * Parses JSON incrementally without loading entire structure into memory.
  *
  * ==Security Features==
  *
  * - **Payload size limit**: Default 100MB (prevents memory exhaustion)
  * - **Array element limit**: Default 1M elements (prevents memory exhaustion)
  * - **Nesting depth limit**: Default 50 levels (prevents stack overflow)
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
  * Custom limits:
  * {{{
  * val limits = StreamingLimits(maxPayloadSize = 10_000_000, maxArrayElements = 100_000)
  * val converter = StreamingJsonConverter(limits)
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
class StreamingJsonConverter(limits: StreamingLimits = StreamingLimits.default) {
  private val factory = new JsonFactory()

  // Validate limits on construction
  limits.validate match {
    case Left(error) => throw new IllegalArgumentException(s"Invalid StreamingLimits: $error")
    case Right(_) => // OK
  }

  /** Stream JSON from InputStream directly to CValue.
    * Memory-efficient for large payloads.
    *
    * @param input The InputStream to read from
    * @param expectedType The expected CType
    * @return IO containing Either error message or converted CValue
    */
  def streamFromInputStream(input: InputStream, expectedType: CType): IO[Either[String, CValue]] = IO.blocking {
    // Wrap input stream with size limit
    val boundedInput = new BoundedInputStream(input, limits.maxPayloadSize)

    try {
      val parser = factory.createParser(boundedInput)
      try {
        parser.nextToken() // Move to first token
        Right(parseValue(parser, expectedType, "", depth = 0))
      } finally {
        parser.close()
      }
    } catch {
      case e: IllegalStateException if e.getMessage.contains("Payload size limit exceeded") =>
        Left(s"Payload too large: ${e.getMessage}")
      case e: IllegalStateException if e.getMessage.contains("Nesting depth limit exceeded") =>
        Left(e.getMessage)
      case e: IllegalStateException if e.getMessage.contains("Array element limit exceeded") =>
        Left(e.getMessage)
      case e: Exception =>
        Left(s"Streaming parse error: ${e.getMessage}")
    }
  }

  /** Parse from byte array using streaming (still streaming internally, but from memory).
    *
    * @param bytes The JSON bytes to parse
    * @param expectedType The expected CType
    * @return Either error message or converted CValue
    */
  def streamToCValue(bytes: Array[Byte], expectedType: CType): Either[String, CValue] = {
    // Check byte array size before parsing
    if (bytes.length > limits.maxPayloadSize) {
      return Left(s"Payload too large: ${bytes.length} > ${limits.maxPayloadSize} bytes")
    }

    try {
      val parser = factory.createParser(bytes)
      try {
        parser.nextToken() // Move to first token
        Right(parseValue(parser, expectedType, "", depth = 0))
      } finally {
        parser.close()
      }
    } catch {
      case e: IllegalStateException if e.getMessage.contains("Nesting depth limit exceeded") =>
        Left(e.getMessage)
      case e: IllegalStateException if e.getMessage.contains("Array element limit exceeded") =>
        Left(e.getMessage)
      case e: Exception =>
        Left(s"Streaming parse error: ${e.getMessage}")
    }
  }

  /** Parse from string using streaming.
    *
    * @param jsonString The JSON string to parse
    * @param expectedType The expected CType
    * @return Either error message or converted CValue
    */
  def streamFromString(jsonString: String, expectedType: CType): Either[String, CValue] = {
    // Check string size before parsing (rough estimate: 2 bytes per char)
    val estimatedSize = jsonString.length * 2L
    if (estimatedSize > limits.maxPayloadSize) {
      return Left(s"Payload too large: estimated $estimatedSize > ${limits.maxPayloadSize} bytes")
    }

    try {
      val parser = factory.createParser(jsonString)
      try {
        parser.nextToken()
        Right(parseValue(parser, expectedType, "", depth = 0))
      } finally {
        parser.close()
      }
    } catch {
      case e: IllegalStateException if e.getMessage.contains("Nesting depth limit exceeded") =>
        Left(e.getMessage)
      case e: IllegalStateException if e.getMessage.contains("Array element limit exceeded") =>
        Left(e.getMessage)
      case e: Exception =>
        Left(s"Streaming parse error: ${e.getMessage}")
    }
  }

  private def parseValue(parser: JsonParser, expectedType: CType, path: String, depth: Int): CValue = {
    // Check nesting depth
    if (depth > limits.maxNestingDepth) {
      throw new IllegalStateException(
        s"Nesting depth limit exceeded: $depth > ${limits.maxNestingDepth} at path '$path'"
      )
    }

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
        parseArray(parser, elemType, path, depth)

      case (JsonToken.START_OBJECT, CType.CProduct(fields)) =>
        parseObject(parser, fields, path, depth)

      case (JsonToken.START_OBJECT, CType.CUnion(structure)) =>
        parseUnion(parser, structure, path, depth)

      case (_, CType.COptional(innerType)) if token != JsonToken.VALUE_NULL =>
        CValue.CSome(parseValue(parser, innerType, path, depth), innerType)

      case _ =>
        throw new IllegalArgumentException(
          s"Unexpected token $token for type $expectedType at path '$path'"
        )
    }
  }

  private def parseArray(parser: JsonParser, elemType: CType, path: String, depth: Int): CValue.CList = {
    val builder = Vector.newBuilder[CValue]
    var idx = 0

    while (parser.nextToken() != JsonToken.END_ARRAY) {
      // Check array element limit
      if (idx >= limits.maxArrayElements) {
        throw new IllegalStateException(
          s"Array element limit exceeded: $idx >= ${limits.maxArrayElements} at path '$path'"
        )
      }

      builder += parseValue(parser, elemType, s"$path[$idx]", depth + 1)
      idx += 1
    }

    CValue.CList(builder.result(), elemType)
  }

  private def parseObject(parser: JsonParser, fields: Map[String, CType], path: String, depth: Int): CValue.CProduct = {
    val values = scala.collection.mutable.Map[String, CValue]()

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val fieldName = parser.getCurrentName
      parser.nextToken() // Move to value

      fields.get(fieldName) match {
        case Some(fieldType) =>
          val fieldPath = if (path.isEmpty) fieldName else s"$path.$fieldName"
          values(fieldName) = parseValue(parser, fieldType, fieldPath, depth + 1)
        case None =>
          skipValue(parser, depth) // Skip unknown fields
      }
    }

    // Check for missing required fields
    val missing = fields.keySet -- values.keySet
    if (missing.nonEmpty) {
      throw new IllegalArgumentException(s"Missing required fields at '$path': ${missing.mkString(", ")}")
    }

    CValue.CProduct(values.toMap, fields)
  }

  private def parseUnion(parser: JsonParser, structure: Map[String, CType], path: String, depth: Int): CValue.CUnion = {
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
              value = Some(parseValue(parser, vt, s"$path.value", depth + 1))
            case _ =>
              // Tag not yet seen, skip for now - this is a limitation
              throw new IllegalArgumentException(
                s"Union 'value' field must come after 'tag' field at path '$path'"
              )
          }
        case _ =>
          skipValue(parser, depth)
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
  private def skipValue(parser: JsonParser, depth: Int): Unit = {
    // Check nesting depth when skipping
    if (depth > limits.maxNestingDepth) {
      throw new IllegalStateException(
        s"Nesting depth limit exceeded while skipping: $depth > ${limits.maxNestingDepth}"
      )
    }

    val token = parser.currentToken()
    token match {
      case JsonToken.START_ARRAY =>
        var elementCount = 0
        while (parser.nextToken() != JsonToken.END_ARRAY) {
          if (elementCount >= limits.maxArrayElements) {
            throw new IllegalStateException(
              s"Array element limit exceeded while skipping: $elementCount >= ${limits.maxArrayElements}"
            )
          }
          skipValue(parser, depth + 1)
          elementCount += 1
        }
      case JsonToken.START_OBJECT =>
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          parser.nextToken() // Move to value
          skipValue(parser, depth + 1)
        }
      case _ => // Scalar value, already consumed
    }
  }
}

object StreamingJsonConverter {
  private lazy val defaultInstance = new StreamingJsonConverter()

  def apply(): StreamingJsonConverter = defaultInstance

  def apply(limits: StreamingLimits): StreamingJsonConverter = new StreamingJsonConverter(limits)

  /** Convenience method for streaming from bytes */
  def streamToCValue(bytes: Array[Byte], expectedType: CType): Either[String, CValue] =
    defaultInstance.streamToCValue(bytes, expectedType)

  /** Convenience method for streaming from string */
  def streamFromString(jsonString: String, expectedType: CType): Either[String, CValue] =
    defaultInstance.streamFromString(jsonString, expectedType)
}
