package io.constellation

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AdaptiveJsonConverterTest extends AnyFlatSpec with Matchers {

  // ============= Strategy Selection =============

  "AdaptiveJsonConverter" should "use Eager strategy for small payloads" in {
    val converter = new AdaptiveJsonConverter()
    val json      = Json.fromInt(42)

    val result = converter.convert(json, CType.CInt)
    result shouldBe Right(CValue.CInt(42))
    converter.getLastStrategy shouldBe ConversionStrategy.Eager
  }

  it should "use Lazy strategy for medium payloads via sizeHint" in {
    val converter = new AdaptiveJsonConverter()
    val json      = Json.fromInt(42)

    converter.convert(json, CType.CInt, sizeHint = Some(50000))
    converter.getLastStrategy shouldBe ConversionStrategy.Lazy
  }

  it should "use Streaming strategy for large payloads via sizeHint" in {
    val converter = new AdaptiveJsonConverter()
    val json      = Json.fromInt(42)

    converter.convert(json, CType.CInt, sizeHint = Some(200000))
    converter.getLastStrategy shouldBe ConversionStrategy.Streaming
  }

  it should "use Eager for payloads at exactly the lazy threshold" in {
    val converter = new AdaptiveJsonConverter(lazyThreshold = 100, streamingThreshold = 1000)
    val json      = Json.fromString("x")

    converter.convert(json, CType.CString, sizeHint = Some(100))
    converter.getLastStrategy shouldBe ConversionStrategy.Eager
  }

  it should "use Lazy for payloads just above lazy threshold" in {
    val converter = new AdaptiveJsonConverter(lazyThreshold = 100, streamingThreshold = 1000)
    val json      = Json.fromString("x")

    converter.convert(json, CType.CString, sizeHint = Some(101))
    converter.getLastStrategy shouldBe ConversionStrategy.Lazy
  }

  it should "use Streaming for payloads just above streaming threshold" in {
    val converter = new AdaptiveJsonConverter(lazyThreshold = 100, streamingThreshold = 1000)
    val json      = Json.fromString("x")

    converter.convert(json, CType.CString, sizeHint = Some(1001))
    converter.getLastStrategy shouldBe ConversionStrategy.Streaming
  }

  // ============= Custom Thresholds =============

  "Custom thresholds" should "affect strategy selection" in {
    val converter = AdaptiveJsonConverter(lazyThreshold = 5, streamingThreshold = 20)
    val json      = Json.fromString("hello")

    // "hello" is about 7 bytes, above lazyThreshold=5
    converter.convert(json, CType.CString)
    converter.getLastStrategy shouldBe ConversionStrategy.Lazy
  }

  // ============= Conversion Results =============

  "convert" should "correctly convert Int JSON" in {
    val converter = new AdaptiveJsonConverter()
    converter.convert(Json.fromInt(42), CType.CInt) shouldBe Right(CValue.CInt(42))
  }

  it should "correctly convert String JSON" in {
    val converter = new AdaptiveJsonConverter()
    converter.convert(Json.fromString("hello"), CType.CString) shouldBe Right(
      CValue.CString("hello")
    )
  }

  it should "correctly convert Boolean JSON" in {
    val converter = new AdaptiveJsonConverter()
    converter.convert(Json.fromBoolean(true), CType.CBoolean) shouldBe Right(CValue.CBoolean(true))
  }

  it should "correctly convert Float JSON" in {
    val converter = new AdaptiveJsonConverter()
    val result    = converter.convert(Json.fromDoubleOrNull(3.14), CType.CFloat)
    result shouldBe Right(CValue.CFloat(3.14))
  }

  // ============= Companion Object =============

  "AdaptiveJsonConverter companion" should "expose default thresholds" in {
    AdaptiveJsonConverter.DefaultLazyThreshold shouldBe 10000
    AdaptiveJsonConverter.DefaultStreamingThreshold shouldBe 100000
  }

  it should "convert using default instance" in {
    val result = AdaptiveJsonConverter.convert(Json.fromInt(1), CType.CInt)
    result shouldBe Right(CValue.CInt(1))
  }

  it should "convert with explicit size hint" in {
    val result = AdaptiveJsonConverter.convert(Json.fromInt(1), CType.CInt, 100)
    result shouldBe Right(CValue.CInt(1))
  }

  // ============= Size Estimation =============

  "estimateSize" should "estimate small JSON as small" in {
    val converter = new AdaptiveJsonConverter()
    // Small JSON should use Eager strategy (below lazyThreshold=10000)
    converter.convert(Json.fromInt(1), CType.CInt)
    converter.getLastStrategy shouldBe ConversionStrategy.Eager
  }

  it should "use Eager for null JSON" in {
    val converter = new AdaptiveJsonConverter()
    // null JSON is tiny (~4 bytes)
    converter.convert(Json.Null, CType.COptional(CType.CInt))
    converter.getLastStrategy shouldBe ConversionStrategy.Eager
  }

  it should "use Eager for empty array" in {
    val converter = new AdaptiveJsonConverter()
    converter.convert(Json.arr(), CType.CList(CType.CInt))
    converter.getLastStrategy shouldBe ConversionStrategy.Eager
  }

  it should "use Eager for empty object" in {
    val converter = new AdaptiveJsonConverter()
    converter.convert(Json.obj(), CType.CProduct(Map.empty))
    converter.getLastStrategy shouldBe ConversionStrategy.Eager
  }
}
