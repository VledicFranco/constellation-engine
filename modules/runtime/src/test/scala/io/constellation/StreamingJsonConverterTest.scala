package io.constellation

import java.io.ByteArrayInputStream

import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Comprehensive tests for StreamingJsonConverter, StreamingLimits, and companion object. */
class StreamingJsonConverterTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // StreamingLimits
  // ---------------------------------------------------------------------------

  "StreamingLimits" should "have reasonable default values" in {
    val defaults = StreamingLimits.default
    defaults.maxPayloadSize shouldBe 100L * 1024 * 1024 // 100MB
    defaults.maxArrayElements shouldBe 1_000_000         // 1M elements
    defaults.maxNestingDepth shouldBe 50                 // 50 levels
  }

  it should "validate succeeds for valid limits" in {
    val limits = StreamingLimits(
      maxPayloadSize = 1000,
      maxArrayElements = 100,
      maxNestingDepth = 10
    )
    limits.validate shouldBe Right(limits)
  }

  it should "validate fails for non-positive maxPayloadSize" in {
    StreamingLimits(maxPayloadSize = 0).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxPayloadSize = -1).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxPayloadSize = -100).validate.left.getOrElse("") should include(
      "maxPayloadSize must be positive"
    )
  }

  it should "validate fails for non-positive maxArrayElements" in {
    StreamingLimits(maxArrayElements = 0).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxArrayElements = -1).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxArrayElements = -50).validate.left.getOrElse("") should include(
      "maxArrayElements must be positive"
    )
  }

  it should "validate fails for non-positive maxNestingDepth" in {
    StreamingLimits(maxNestingDepth = 0).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxNestingDepth = -1).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxNestingDepth = -10).validate.left.getOrElse("") should include(
      "maxNestingDepth must be positive"
    )
  }

  // ---------------------------------------------------------------------------
  // Basic types via streamToCValue (bytes)
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter.streamToCValue" should "parse string" in {
    val bytes  = "\"hello\"".getBytes("UTF-8")
    val result = StreamingJsonConverter().streamToCValue(bytes, CType.CString)
    result shouldBe Right(CValue.CString("hello"))
  }

  it should "parse int" in {
    val bytes  = "42".getBytes("UTF-8")
    val result = StreamingJsonConverter().streamToCValue(bytes, CType.CInt)
    result shouldBe Right(CValue.CInt(42L))
  }

  it should "parse float" in {
    val bytes  = "3.14".getBytes("UTF-8")
    val result = StreamingJsonConverter().streamToCValue(bytes, CType.CFloat)
    result shouldBe Right(CValue.CFloat(3.14))
  }

  it should "parse int as float" in {
    val bytes  = "42".getBytes("UTF-8")
    val result = StreamingJsonConverter().streamToCValue(bytes, CType.CFloat)
    result shouldBe Right(CValue.CFloat(42.0))
  }

  it should "parse boolean true" in {
    val bytes  = "true".getBytes("UTF-8")
    val result = StreamingJsonConverter().streamToCValue(bytes, CType.CBoolean)
    result shouldBe Right(CValue.CBoolean(true))
  }

  it should "parse boolean false" in {
    val bytes  = "false".getBytes("UTF-8")
    val result = StreamingJsonConverter().streamToCValue(bytes, CType.CBoolean)
    result shouldBe Right(CValue.CBoolean(false))
  }

  // ---------------------------------------------------------------------------
  // Arrays
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter arrays" should "parse int array" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("[1,2,3]", CType.CList(CType.CInt))

    result match {
      case Right(CValue.CList(values, subtype)) =>
        values should have length 3
        values(0) shouldBe CValue.CInt(1)
        values(1) shouldBe CValue.CInt(2)
        values(2) shouldBe CValue.CInt(3)
        subtype shouldBe CType.CInt
      case other =>
        fail(s"Expected CList, got: $other")
    }
  }

  it should "parse empty array" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("[]", CType.CList(CType.CString))

    result match {
      case Right(CValue.CList(values, subtype)) =>
        values shouldBe empty
        subtype shouldBe CType.CString
      case other =>
        fail(s"Expected empty CList, got: $other")
    }
  }

  it should "parse string array" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("""["a","b"]""", CType.CList(CType.CString))

    result match {
      case Right(CValue.CList(values, _)) =>
        values should have length 2
        values(0) shouldBe CValue.CString("a")
        values(1) shouldBe CValue.CString("b")
      case other =>
        fail(s"Expected CList of strings, got: $other")
    }
  }

  // ---------------------------------------------------------------------------
  // Objects (CProduct)
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter objects" should "parse object" in {
    val converter   = StreamingJsonConverter()
    val productType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    val result      = converter.streamFromString("""{"name":"Alice","age":30}""", productType)

    result match {
      case Right(CValue.CProduct(fields, structure)) =>
        fields("name") shouldBe CValue.CString("Alice")
        fields("age") shouldBe CValue.CInt(30)
        structure shouldBe productType.structure
      case other =>
        fail(s"Expected CProduct, got: $other")
    }
  }

  it should "parse object with extra fields (skip unknown fields)" in {
    val converter   = StreamingJsonConverter()
    val productType = CType.CProduct(Map("name" -> CType.CString))
    val result = converter.streamFromString(
      """{"name":"Alice","unknown_field":"ignored","age":30}""",
      productType
    )

    result match {
      case Right(CValue.CProduct(fields, _)) =>
        fields should have size 1
        fields("name") shouldBe CValue.CString("Alice")
      case other =>
        fail(s"Expected CProduct with only 'name', got: $other")
    }
  }

  // ---------------------------------------------------------------------------
  // Unions
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter unions" should "parse union" in {
    val converter = StreamingJsonConverter()
    val unionType = CType.CUnion(Map("Left" -> CType.CString, "Right" -> CType.CInt))
    val result =
      converter.streamFromString("""{"tag":"Left","value":"hello"}""", unionType)

    result match {
      case Right(CValue.CUnion(value, structure, tag)) =>
        tag shouldBe "Left"
        value shouldBe CValue.CString("hello")
        structure shouldBe unionType.structure
      case other =>
        fail(s"Expected CUnion, got: $other")
    }
  }

  it should "error when value comes before tag" in {
    val converter = StreamingJsonConverter()
    val unionType = CType.CUnion(Map("Left" -> CType.CString, "Right" -> CType.CInt))
    val result =
      converter.streamFromString("""{"value":"hello","tag":"Left"}""", unionType)

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("must come after")
  }

  it should "error when missing tag" in {
    val converter = StreamingJsonConverter()
    val unionType = CType.CUnion(Map("Left" -> CType.CString, "Right" -> CType.CInt))
    // Use only unknown fields so tag is never set - triggers "missing 'tag'" after loop
    val result = converter.streamFromString("""{"other":"hello"}""", unionType)

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("missing 'tag'")
  }

  it should "error when unknown tag" in {
    val converter = StreamingJsonConverter()
    val unionType = CType.CUnion(Map("Left" -> CType.CString, "Right" -> CType.CInt))
    // Tag only, no value - triggers "Unknown union tag" in final match
    val result = converter.streamFromString("""{"tag":"Unknown"}""", unionType)

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Unknown union tag")
  }

  it should "error when missing value" in {
    val converter = StreamingJsonConverter()
    val unionType = CType.CUnion(Map("Left" -> CType.CString, "Right" -> CType.CInt))
    val result    = converter.streamFromString("""{"tag":"Left"}""", unionType)

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("missing 'value'")
  }

  // ---------------------------------------------------------------------------
  // Optionals
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter optionals" should "parse null as optional (CNone)" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("null", CType.COptional(CType.CString))

    result shouldBe Right(CValue.CNone(CType.CString))
  }

  it should "parse non-null as optional (CSome)" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("\"hello\"", CType.COptional(CType.CString))

    result shouldBe Right(CValue.CSome(CValue.CString("hello"), CType.CString))
  }

  // ---------------------------------------------------------------------------
  // streamFromString
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter.streamFromString" should "parse basic string" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("\"world\"", CType.CString)
    result shouldBe Right(CValue.CString("world"))
  }

  it should "reject payload exceeding size limit" in {
    val limits    = StreamingLimits(maxPayloadSize = 10)
    val converter = StreamingJsonConverter(limits)

    // String length * 2 must exceed 10 bytes for the size check
    val largeJson = "\"" + "x" * 20 + "\""

    val result = converter.streamFromString(largeJson, CType.CString)
    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Payload too large")
  }

  // ---------------------------------------------------------------------------
  // streamFromInputStream
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter.streamFromInputStream" should "parse from input stream" in {
    val converter   = StreamingJsonConverter()
    val bytes       = "\"hello\"".getBytes("UTF-8")
    val inputStream = new ByteArrayInputStream(bytes)

    val result = converter.streamFromInputStream(inputStream, CType.CString).unsafeRunSync()
    result shouldBe Right(CValue.CString("hello"))
  }

  it should "reject payload exceeding size limit from stream" in {
    val limits    = StreamingLimits(maxPayloadSize = 10)
    val converter = StreamingJsonConverter(limits)

    // Create a payload bigger than 10 bytes
    val largeJson   = "[" + (1 to 20).map(_ => "1").mkString(",") + "]"
    val inputStream = new ByteArrayInputStream(largeJson.getBytes("UTF-8"))

    val result =
      converter.streamFromInputStream(inputStream, CType.CList(CType.CInt)).unsafeRunSync()
    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Payload too large")
  }

  // ---------------------------------------------------------------------------
  // Error cases
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter error cases" should "return Left for type mismatch (string token for CInt)" in {
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString("\"not_a_number\"", CType.CInt)

    result shouldBe a[Left[_, _]]
  }

  it should "return Left for missing required fields in object" in {
    val converter   = StreamingJsonConverter()
    val productType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    val result      = converter.streamFromString("""{"name":"Alice"}""", productType)

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Missing required fields")
  }

  it should "return Left with 'Payload too large' for oversized bytes" in {
    val limits    = StreamingLimits(maxPayloadSize = 5)
    val converter = StreamingJsonConverter(limits)

    val bytes  = "\"this is a long string\"".getBytes("UTF-8")
    val result = converter.streamToCValue(bytes, CType.CString)

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Payload too large")
  }

  // ---------------------------------------------------------------------------
  // Security limits
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter security limits" should "reject nesting depth exceeded" in {
    val limits    = StreamingLimits(maxNestingDepth = 3)
    val converter = StreamingJsonConverter(limits)

    // 6 levels deep: [[[[[[1]]]]]]
    val deeplyNested = "[[[[[[1]]]]]]"

    val result = converter.streamFromString(deeplyNested, nestedListType(6))
    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Nesting depth limit exceeded")
  }

  it should "reject array element limit exceeded" in {
    val limits    = StreamingLimits(maxPayloadSize = 1_000_000, maxArrayElements = 5)
    val converter = StreamingJsonConverter(limits)

    val json   = "[" + (1 to 10).map(_ => "1").mkString(",") + "]"
    val result = converter.streamFromString(json, CType.CList(CType.CInt))

    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Array element limit exceeded")
  }

  it should "throw IllegalArgumentException for invalid limits on construction" in {
    an[IllegalArgumentException] should be thrownBy {
      StreamingJsonConverter(StreamingLimits(maxPayloadSize = -1))
    }

    an[IllegalArgumentException] should be thrownBy {
      StreamingJsonConverter(StreamingLimits(maxArrayElements = 0))
    }

    an[IllegalArgumentException] should be thrownBy {
      StreamingJsonConverter(StreamingLimits(maxNestingDepth = -5))
    }
  }

  it should "accept structures exactly at the depth limit" in {
    val limits    = StreamingLimits(maxNestingDepth = 5)
    val converter = StreamingJsonConverter(limits)

    // 5-level deep nested array: [[[[[1]]]]]
    val nested = "[[[[[1]]]]]"
    val result = converter.streamFromString(nested, nestedListType(5))
    result shouldBe a[Right[_, _]]
  }

  it should "reject deeply nested structures during skipValue" in {
    val limits    = StreamingLimits(maxNestingDepth = 3)
    val converter = StreamingJsonConverter(limits)

    val deepObject  = """{"known": 1, "unknown": [[[[[[1]]]]]]}"""
    val productType = CType.CProduct(Map("known" -> CType.CInt))

    val result = converter.streamFromString(deepObject, productType)
    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Nesting depth limit exceeded")
  }

  it should "reject large arrays during skipValue" in {
    val limits    = StreamingLimits(maxPayloadSize = 1_000_000, maxArrayElements = 5)
    val converter = StreamingJsonConverter(limits)

    val largeArray  = """{"known": 1, "unknown": [""" + (1 to 10).map(_ => "1").mkString(",") + "]}"
    val productType = CType.CProduct(Map("known" -> CType.CInt))

    val result = converter.streamFromString(largeArray, productType)
    result shouldBe a[Left[_, _]]
    result.left.getOrElse("") should include("Array element limit exceeded")
  }

  it should "properly clean up resources on repeated errors" in {
    val limits    = StreamingLimits(maxArrayElements = 3)
    val converter = StreamingJsonConverter(limits)

    val json = "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]"

    (1 to 10).foreach { _ =>
      val result = converter.streamFromString(json, CType.CList(CType.CInt))
      result shouldBe a[Left[_, _]]
    }
  }

  // ---------------------------------------------------------------------------
  // Companion object
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter companion" should "return instance via apply()" in {
    val converter = StreamingJsonConverter()
    converter should not be null
  }

  it should "return instance with custom limits via apply(limits)" in {
    val limits    = StreamingLimits(maxPayloadSize = 500, maxArrayElements = 10, maxNestingDepth = 5)
    val converter = StreamingJsonConverter(limits)
    converter should not be null
  }

  it should "provide streamToCValue convenience method" in {
    val bytes  = "42".getBytes("UTF-8")
    val result = StreamingJsonConverter.streamToCValue(bytes, CType.CInt)
    result shouldBe Right(CValue.CInt(42L))
  }

  it should "provide streamFromString convenience method" in {
    val result = StreamingJsonConverter.streamFromString("\"test\"", CType.CString)
    result shouldBe Right(CValue.CString("test"))
  }

  // ---------------------------------------------------------------------------
  // Nested structures within limits
  // ---------------------------------------------------------------------------

  "StreamingJsonConverter nested structures" should "handle nested arrays within limits" in {
    val converter = StreamingJsonConverter()
    val json      = "[[1, 2], [3, 4], [5, 6]]"
    val listType  = CType.CList(CType.CList(CType.CInt))

    val result = converter.streamFromString(json, listType)

    result match {
      case Right(CValue.CList(outer, _)) =>
        outer should have length 3
        outer(0) match {
          case CValue.CList(inner, _) =>
            inner should have length 2
            inner(0) shouldBe CValue.CInt(1)
            inner(1) shouldBe CValue.CInt(2)
          case other => fail(s"Expected inner CList, got: $other")
        }
      case other =>
        fail(s"Expected nested list, got: $other")
    }
  }

  it should "handle products with nested arrays" in {
    val converter = StreamingJsonConverter()
    val json      = """{"values": [1, 2, 3], "label": "test"}"""
    val productType = CType.CProduct(
      Map(
        "values" -> CType.CList(CType.CInt),
        "label"  -> CType.CString
      )
    )

    val result = converter.streamFromString(json, productType)

    result match {
      case Right(CValue.CProduct(fields, _)) =>
        fields("label") shouldBe CValue.CString("test")
        fields("values") match {
          case CValue.CList(values, _) =>
            values should have length 3
          case other => fail(s"Expected CList, got: $other")
        }
      case other =>
        fail(s"Expected CProduct, got: $other")
    }
  }

  it should "use default limits allowing reasonable payloads" in {
    val converter      = StreamingJsonConverter()
    val reasonableJson = "[" + (1 to 1000).map(_ => "1").mkString(",") + "]"

    val result = converter.streamFromString(reasonableJson, CType.CList(CType.CInt))
    result shouldBe a[Right[_, _]]
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Create a nested CType.CList wrapping CType.CInt at the given depth.
    * depth=1 means CType.CList(CType.CInt), depth=2 means CType.CList(CType.CList(CType.CInt)), etc.
    */
  private def nestedListType(depth: Int): CType =
    if depth == 0 then CType.CInt
    else CType.CList(nestedListType(depth - 1))
}
