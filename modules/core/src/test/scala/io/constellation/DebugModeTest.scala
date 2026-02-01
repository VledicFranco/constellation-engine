package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.ByteArrayOutputStream
import java.io.PrintStream

class DebugModeTest extends AnyFlatSpec with Matchers {

  // Note: Default level is ErrorsOnly. To test other levels, set CONSTELLATION_DEBUG env var.

  "DebugLevel.fromString" should "parse 'off' variants" in {
    DebugLevel.fromString("off") shouldBe Some(DebugLevel.Off)
    DebugLevel.fromString("false") shouldBe Some(DebugLevel.Off)
    DebugLevel.fromString("0") shouldBe Some(DebugLevel.Off)
    DebugLevel.fromString("OFF") shouldBe Some(DebugLevel.Off)
  }

  it should "parse 'errors' variants" in {
    DebugLevel.fromString("errors") shouldBe Some(DebugLevel.ErrorsOnly)
    DebugLevel.fromString("errorsonly") shouldBe Some(DebugLevel.ErrorsOnly)
    DebugLevel.fromString("errors-only") shouldBe Some(DebugLevel.ErrorsOnly)
    DebugLevel.fromString("ERRORS") shouldBe Some(DebugLevel.ErrorsOnly)
  }

  it should "parse 'full' variants" in {
    DebugLevel.fromString("full") shouldBe Some(DebugLevel.Full)
    DebugLevel.fromString("true") shouldBe Some(DebugLevel.Full)
    DebugLevel.fromString("1") shouldBe Some(DebugLevel.Full)
    DebugLevel.fromString("FULL") shouldBe Some(DebugLevel.Full)
  }

  it should "return None for invalid values" in {
    DebugLevel.fromString("invalid") shouldBe None
    DebugLevel.fromString("") shouldBe None
    DebugLevel.fromString("yes") shouldBe None
  }

  "DebugLevel.isEnabled" should "return true for ErrorsOnly and Full" in {
    DebugLevel.ErrorsOnly.isEnabled shouldBe true
    DebugLevel.Full.isEnabled shouldBe true
  }

  it should "return false for Off" in {
    DebugLevel.Off.isEnabled shouldBe false
  }

  "DebugLevel.shouldThrow" should "return true only for Full" in {
    DebugLevel.Full.shouldThrow shouldBe true
    DebugLevel.ErrorsOnly.shouldThrow shouldBe false
    DebugLevel.Off.shouldThrow shouldBe false
  }

  "DebugMode.level" should "be one of the three debug levels" in {
    DebugMode.level should (
      equal(DebugLevel.Off) or
      equal(DebugLevel.ErrorsOnly) or
      equal(DebugLevel.Full)
    )
  }

  "DebugMode.isEnabled" should "match level.isEnabled" in {
    DebugMode.isEnabled shouldBe DebugMode.level.isEnabled
  }

  "DebugMode.safeCast" should "cast valid types successfully in all modes" in {
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

  "DebugMode.safeCast with invalid cast in Full mode" should "throw TypeMismatchError" in {
    if (DebugMode.level == DebugLevel.Full) {
      val stringValue: Any = "hello"
      val error = intercept[TypeMismatchError] {
        DebugMode.safeCast[java.util.List[?]](stringValue, "test context")
      }
      error.expected should include("List")
      error.actual should include("String")
      error.context("location") shouldBe "test context"
    }
  }

  // Note: ErrorsOnly mode logs warnings but proceeds with asInstanceOf, which may still throw.
  // It's designed for "safe by construction" code where violations indicate bugs but casts
  // are expected to succeed. Testing invalid casts in this mode is not meaningful.

  "DebugMode.safeCast in Off mode" should "not validate types" in {
    if (DebugMode.level == DebugLevel.Off) {
      // Should not validate, just cast
      noException should be thrownBy {
        val _ = DebugMode.safeCast[CharSequence]("hello", "test")
      }
    }
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

  it should "use custom type name in Full mode" in {
    if (DebugMode.level == DebugLevel.Full) {
      val stringValue: Any = "hello"
      val error = intercept[TypeMismatchError] {
        DebugMode.safeCastNamed[java.util.List[?]](stringValue, "CustomList", "test context")
      }
      error.expected shouldBe "CustomList"
      error.actual should include("String")
      error.context("location") shouldBe "test context"
    }
  }


  "DebugMode.debugAssert" should "behave according to debug level" in {
    DebugMode.level match {
      case DebugLevel.Off =>
        // Should be no-op
        noException should be thrownBy {
          DebugMode.debugAssert(false, "This should not throw", "test")
        }

      case DebugLevel.ErrorsOnly =>
        // Should log but not throw
        val stderr = captureStderr {
          DebugMode.debugAssert(false, "Test failure", "test context")
        }
        stderr should include("[WARN] Debug assertion failed")
        stderr should include("test context")
        stderr should include("Test failure")

      case DebugLevel.Full =>
        // Should throw
        val error = intercept[RuntimeException] {
          DebugMode.debugAssert(false, "Test failure", "test context")
        }
        error.getMessage should include("Debug assertion failed")
        error.getMessage should include("test context")
        error.getMessage should include("Test failure")
    }
  }

  it should "not throw when condition is true" in {
    noException should be thrownBy {
      DebugMode.debugAssert(true, "This should not fail", "test")
    }
  }

  "DebugMode.debugLog" should "not throw when called" in {
    noException should be thrownBy {
      DebugMode.debugLog("Test message")
    }
  }

  it should "log in enabled modes" in {
    if (DebugMode.isEnabled) {
      val stderr = captureStderr {
        DebugMode.debugLog("Test log message")
      }
      stderr should include("[CONSTELLATION_DEBUG] Test log message")
    }
  }

  // Helper to capture stderr output
  private def captureStderr(block: => Unit): String = {
    val baos = new ByteArrayOutputStream()
    val ps = new PrintStream(baos)
    val oldErr = System.err

    System.setErr(ps)
    try {
      block
      baos.toString("UTF-8")
    } finally {
      System.setErr(oldErr)
      ps.close()
    }
  }
}
