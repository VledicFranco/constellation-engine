package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DebugModeTest extends AnyFlatSpec with Matchers {

  // Note: These tests verify the behavior with debug mode disabled (default).
  // To test with debug mode enabled, run with CONSTELLATION_DEBUG=true

  "DebugMode.isEnabled" should "be a boolean value" in {
    // This test verifies the field exists and is the correct type
    DebugMode.isEnabled shouldBe a[Boolean]
  }

  "DebugMode.safeCast" should "cast valid types successfully" in {
    val stringValue: Any = "hello"
    val result = DebugMode.safeCast[String](stringValue, "test context")
    result shouldBe "hello"
  }

  it should "cast integers successfully" in {
    val intValue: Any = 42
    val result = DebugMode.safeCast[Int](intValue, "test context")
    result shouldBe 42
  }

  it should "cast boxed primitives successfully" in {
    val longValue: Any = 100L
    val result = DebugMode.safeCast[Long](longValue, "test context")
    result shouldBe 100L
  }

  it should "cast collection types successfully" in {
    val listValue: Any = List(1, 2, 3)
    val result = DebugMode.safeCast[List[Int]](listValue, "test context")
    result shouldBe List(1, 2, 3)
  }

  it should "handle null values" in {
    val nullValue: Any = null
    val result = DebugMode.safeCast[String](nullValue, "test context")
    result shouldBe null
  }

  it should "cast custom types successfully" in {
    case class Person(name: String)
    val person: Any = Person("Alice")
    val result = DebugMode.safeCast[Person](person, "test context")
    result shouldBe Person("Alice")
  }

  "DebugMode.safeCastNamed" should "cast valid types successfully" in {
    val value: Any = Map("key" -> "value")
    val result = DebugMode.safeCastNamed[Map[String, String]](
      value,
      "Map[String, String]",
      "test context"
    )
    result shouldBe Map("key" -> "value")
  }

  "DebugMode.debugAssert" should "not throw when debug mode is disabled" in {
    // With debug mode disabled, this should be a no-op even with false condition
    noException should be thrownBy {
      DebugMode.debugAssert(false, "This should not throw", "test")
    }
  }

  "DebugMode.debugLog" should "not throw when called" in {
    // This verifies the method exists and doesn't crash
    noException should be thrownBy {
      DebugMode.debugLog("Test message")
    }
  }

  // Tests that verify behavior with debug mode enabled
  // These would only fail/behave differently if CONSTELLATION_DEBUG=true

  "DebugMode with invalid cast" should "behave differently based on debug mode" in {
    // When debug mode is disabled, safeCast just uses asInstanceOf
    // which defers errors until the value is actually used as the wrong type.
    // When debug mode is enabled, it throws TypeMismatchError immediately.

    // Test with reference types that don't throw immediately on asInstanceOf
    val stringValue: Any = "hello"

    if (!DebugMode.isEnabled) {
      // In non-debug mode, casting to a compatible supertype works
      noException should be thrownBy {
        val _ = DebugMode.safeCast[AnyRef](stringValue, "test")
      }
    }
  }

  "DebugMode.safeCast" should "throw TypeMismatchError when debug mode is enabled and cast is invalid" in {
    if (DebugMode.isEnabled) {
      val stringValue: Any = "hello"
      val error = intercept[TypeMismatchError] {
        DebugMode.safeCast[java.lang.Long](stringValue, "test context")
      }
      error.expected should include("Long")
      error.actual should include("String")
      error.context("location") shouldBe "test context"
    }
  }

  "DebugMode.debugAssert" should "throw when debug mode is enabled and condition is false" in {
    if (DebugMode.isEnabled) {
      val error = intercept[RuntimeException] {
        DebugMode.debugAssert(false, "Test failure", "test context")
      }
      error.getMessage should include("Debug assertion failed")
      error.getMessage should include("test context")
      error.getMessage should include("Test failure")
    }
  }
}
