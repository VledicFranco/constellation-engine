package io.constellation

import java.io.ByteArrayInputStream

import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for StreamingJsonConverter security limits and DoS protection. */
class StreamingJsonConverterTest extends AnyFlatSpec with Matchers {

  "StreamingLimits" should "validate positive values" in {
    val limits = StreamingLimits(
      maxPayloadSize = 1000,
      maxArrayElements = 100,
      maxNestingDepth = 10
    )
    limits.validate shouldBe Right(limits)
  }

  it should "reject zero or negative maxPayloadSize" in {
    StreamingLimits(maxPayloadSize = 0).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxPayloadSize = -1).validate shouldBe a[Left[_, _]]
  }

  it should "reject zero or negative maxArrayElements" in {
    StreamingLimits(maxArrayElements = 0).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxArrayElements = -1).validate shouldBe a[Left[_, _]]
  }

  it should "reject zero or negative maxNestingDepth" in {
    StreamingLimits(maxNestingDepth = 0).validate shouldBe a[Left[_, _]]
    StreamingLimits(maxNestingDepth = -1).validate shouldBe a[Left[_, _]]
  }

  "StreamingJsonConverter" should "parse valid small payloads" in {
    val json      = """[1, 2, 3, 4, 5]"""
    val converter = StreamingJsonConverter()
    val result    = converter.streamFromString(json, CType.CList(CType.CInt))

    result match {
      case Right(CValue.CList(values, _)) =>
        values should have length 5
        values(0) shouldBe CValue.CInt(1)
        values(4) shouldBe CValue.CInt(5)
      case other =>
        fail(s"Expected CList, got: $other")
    }
  }

  it should "reject payloads exceeding size limit (from bytes)" in {
    val limits    = StreamingLimits(maxPayloadSize = 100)
    val converter = StreamingJsonConverter(limits)

    // Create a payload larger than 100 bytes
    val largeJson = "[" + (1 to 50).map(_ => "1").mkString(",") + "]"
    val bytes     = largeJson.getBytes("UTF-8")

    converter.streamToCValue(bytes, CType.CList(CType.CInt)) match {
      case Left(error) =>
        error should include("Payload too large")
      case Right(_) =>
        fail("Should have rejected oversized payload")
    }
  }

  it should "reject payloads exceeding size limit (from string)" in {
    val limits    = StreamingLimits(maxPayloadSize = 100)
    val converter = StreamingJsonConverter(limits)

    // Create a large string
    val largeJson = "[" + (1 to 100).map(_ => "1").mkString(",") + "]"

    converter.streamFromString(largeJson, CType.CList(CType.CInt)) match {
      case Left(error) =>
        error should include("Payload too large")
      case Right(_) =>
        fail("Should have rejected oversized payload")
    }
  }

  it should "reject payloads exceeding size limit (from InputStream)" in {
    val limits    = StreamingLimits(maxPayloadSize = 100)
    val converter = StreamingJsonConverter(limits)

    // Create a large payload
    val largeJson   = "[" + (1 to 50).map(_ => "1").mkString(",") + "]"
    val inputStream = new ByteArrayInputStream(largeJson.getBytes("UTF-8"))

    converter.streamFromInputStream(inputStream, CType.CList(CType.CInt)).unsafeRunSync() match {
      case Left(error) =>
        error should include("Payload too large")
      case Right(_) =>
        fail("Should have rejected oversized payload")
    }
  }

  it should "reject arrays exceeding element limit" in {
    val limits = StreamingLimits(
      maxPayloadSize = 1_000_000, // Large enough
      maxArrayElements = 10       // Only 10 elements allowed
    )
    val converter = StreamingJsonConverter(limits)

    val largeArray = "[" + (1 to 20).map(_ => "1").mkString(",") + "]"

    converter.streamFromString(largeArray, CType.CList(CType.CInt)) match {
      case Left(error) =>
        error should include("Array element limit exceeded")
        error should include("10")
      case Right(_) =>
        fail("Should have rejected array with too many elements")
    }
  }

  it should "reject deeply nested structures exceeding depth limit" in {
    val limits = StreamingLimits(
      maxPayloadSize = 1_000_000,
      maxNestingDepth = 5 // Only 5 levels deep
    )
    val converter = StreamingJsonConverter(limits)

    // Create a 10-level deep nested array: [[[[[[[[[[1]]]]]]]]]]
    val deeplyNested = "[[[[[[[[[[1]]]]]]]]]]"

    converter.streamFromString(deeplyNested, nestedListType(10)) match {
      case Left(error) =>
        error should include("Nesting depth limit exceeded")
        error should include("5")
      case Right(_) =>
        fail("Should have rejected deeply nested structure")
    }
  }

  it should "accept structures at the depth limit" in {
    val limits    = StreamingLimits(maxNestingDepth = 5)
    val converter = StreamingJsonConverter(limits)

    // Create a 5-level deep nested array: [[[[[1]]]]]
    val nested = "[[[[[1]]]]]"

    converter.streamFromString(nested, nestedListType(5)) match {
      case Right(_) =>
      // Should succeed
      case Left(error) =>
        fail(s"Should have accepted structure at limit: $error")
    }
  }

  it should "reject deeply nested structures during skipValue" in {
    val limits    = StreamingLimits(maxNestingDepth = 5)
    val converter = StreamingJsonConverter(limits)

    // Create a product with an unknown field containing deeply nested data
    val deepObject = """{"known": 1, "unknown": [[[[[[1]]]]]]}"""

    val productType = CType.CProduct(Map("known" -> CType.CInt))

    converter.streamFromString(deepObject, productType) match {
      case Left(error) =>
        error should include("Nesting depth limit exceeded")
      case Right(_) =>
        fail("Should have rejected deeply nested unknown field")
    }
  }

  it should "reject large arrays during skipValue" in {
    val limits = StreamingLimits(
      maxPayloadSize = 1_000_000,
      maxArrayElements = 10
    )
    val converter = StreamingJsonConverter(limits)

    // Create a product with an unknown field containing a large array
    val largeArray = """{"known": 1, "unknown": [""" + (1 to 20).map(_ => "1").mkString(",") + "]}"

    val productType = CType.CProduct(Map("known" -> CType.CInt))

    converter.streamFromString(largeArray, productType) match {
      case Left(error) =>
        error should include("Array element limit exceeded")
      case Right(_) =>
        fail("Should have rejected large array in unknown field")
    }
  }

  it should "handle valid nested structures within limits" in {
    val limits = StreamingLimits(
      maxPayloadSize = 10_000,
      maxArrayElements = 100,
      maxNestingDepth = 10
    )
    val converter = StreamingJsonConverter(limits)

    // Nested array: [[1, 2], [3, 4], [5, 6]]
    val json     = "[[1, 2], [3, 4], [5, 6]]"
    val listType = CType.CList(CType.CList(CType.CInt))

    converter.streamFromString(json, listType) match {
      case Right(CValue.CList(outer, _)) =>
        outer should have length 3
      case other =>
        fail(s"Expected nested list, got: $other")
    }
  }

  it should "handle products with nested arrays within limits" in {
    val limits    = StreamingLimits(maxNestingDepth = 10, maxArrayElements = 100)
    val converter = StreamingJsonConverter(limits)

    val json = """{"values": [1, 2, 3], "nested": [[4, 5]]}"""
    val productType = CType.CProduct(
      Map(
        "values" -> CType.CList(CType.CInt),
        "nested" -> CType.CList(CType.CList(CType.CInt))
      )
    )

    converter.streamFromString(json, productType) match {
      case Right(_) =>
      // Success
      case Left(error) =>
        fail(s"Should have accepted valid nested product: $error")
    }
  }

  it should "properly clean up resources on error" in {
    val limits    = StreamingLimits(maxArrayElements = 5)
    val converter = StreamingJsonConverter(limits)

    // This should fail due to array limit
    val json = "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]"

    // Multiple attempts should not cause resource leaks
    (1 to 10).foreach { _ =>
      converter.streamFromString(json, CType.CList(CType.CInt)) match {
        case Left(_)  => // Expected
        case Right(_) => fail("Should have rejected")
      }
    }
  }

  it should "use default limits when not specified" in {
    val converter = StreamingJsonConverter()

    // Default limits should allow reasonable payloads
    val reasonableArray = "[" + (1 to 1000).map(_ => "1").mkString(",") + "]"

    converter.streamFromString(reasonableArray, CType.CList(CType.CInt)) match {
      case Right(_) =>
      // Success with defaults
      case Left(error) =>
        fail(s"Default limits should allow reasonable payloads: $error")
    }
  }

  // Helper to create nested list types
  // depth = number of CType.CList wrappers around CType.CInt
  private def nestedListType(depth: Int): CType =
    if depth == 0 then CType.CInt
    else CType.CList(nestedListType(depth - 1))
}
