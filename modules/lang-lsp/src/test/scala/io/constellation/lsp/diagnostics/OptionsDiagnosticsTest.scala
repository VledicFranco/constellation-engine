package io.constellation.lsp.diagnostics

import io.constellation.lang.ast.{BackoffStrategy, ErrorStrategy, ModuleCallOptions, PriorityLevel}
import io.constellation.lsp.protocol.LspTypes.{DiagnosticSeverity, Position, Range}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OptionsDiagnosticsTest extends AnyFlatSpec with Matchers {

  val testRange: Range = Range(Position(0, 0), Position(0, 50))

  // ========== Diagnostic Tests ==========

  "OptionsDiagnostics.diagnose" should "return empty list for valid options" in {
    val options = ModuleCallOptions(
      retry = Some(3),
      delay =
        Some(io.constellation.lang.ast.Duration(1, io.constellation.lang.ast.DurationUnit.Seconds)),
      backoff = Some(BackoffStrategy.Exponential)
    )
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics shouldBe empty
  }

  it should "warn about delay without retry" in {
    val options = ModuleCallOptions(
      delay =
        Some(io.constellation.lang.ast.Duration(1, io.constellation.lang.ast.DurationUnit.Seconds))
    )
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics should have size 1
    diagnostics.head.severity shouldBe Some(DiagnosticSeverity.Warning)
    diagnostics.head.code shouldBe Some("OPTS001")
    diagnostics.head.message should include("delay")
    diagnostics.head.message should include("without")
    diagnostics.head.message should include("retry")
  }

  it should "warn about backoff without delay" in {
    val options = ModuleCallOptions(
      retry = Some(3),
      backoff = Some(BackoffStrategy.Exponential)
    )
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics should have size 1
    diagnostics.head.severity shouldBe Some(DiagnosticSeverity.Warning)
    diagnostics.head.code shouldBe Some("OPTS002")
    diagnostics.head.message should include("backoff")
    diagnostics.head.message should include("without")
    diagnostics.head.message should include("delay")
  }

  it should "warn about high retry count" in {
    val options     = ModuleCallOptions(retry = Some(15))
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics should have size 1
    diagnostics.head.severity shouldBe Some(DiagnosticSeverity.Warning)
    diagnostics.head.code shouldBe Some("OPTS003")
    diagnostics.head.message should include("15")
    diagnostics.head.message should include("exceeds")
  }

  it should "error on negative retry" in {
    val options     = ModuleCallOptions(retry = Some(-1))
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics should have size 1
    diagnostics.head.severity shouldBe Some(DiagnosticSeverity.Error)
    diagnostics.head.code shouldBe Some("OPTS004")
    diagnostics.head.message should include("negative")
  }

  it should "error on zero concurrency" in {
    val options     = ModuleCallOptions(concurrency = Some(0))
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics should have size 1
    diagnostics.head.severity shouldBe Some(DiagnosticSeverity.Error)
    diagnostics.head.code shouldBe Some("OPTS005")
    diagnostics.head.message should include("concurrency")
    diagnostics.head.message should include("positive")
  }

  it should "warn about backoff without retry" in {
    val options = ModuleCallOptions(
      delay =
        Some(io.constellation.lang.ast.Duration(1, io.constellation.lang.ast.DurationUnit.Seconds)),
      backoff = Some(BackoffStrategy.Exponential)
    )
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    // Should have 2 warnings: delay without retry, backoff without retry
    diagnostics.count(_.severity == Some(DiagnosticSeverity.Warning)) shouldBe 2
  }

  it should "warn about cache_backend without cache" in {
    val options     = ModuleCallOptions(cacheBackend = Some("redis"))
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    diagnostics should have size 1
    diagnostics.head.severity shouldBe Some(DiagnosticSeverity.Warning)
    diagnostics.head.code shouldBe Some("OPTS007")
    diagnostics.head.message should include("cache_backend")
    diagnostics.head.message should include("without")
    diagnostics.head.message should include("cache")
  }

  it should "detect multiple issues" in {
    val options = ModuleCallOptions(
      retry = Some(-1),
      delay =
        Some(io.constellation.lang.ast.Duration(1, io.constellation.lang.ast.DurationUnit.Seconds)),
      concurrency = Some(0)
    )
    val diagnostics = OptionsDiagnostics.diagnose(options, testRange)
    // Should have: error for negative retry, error for zero concurrency, warning for delay without (positive) retry
    diagnostics.count(_.severity == Some(DiagnosticSeverity.Error)) shouldBe 2
  }

  // ========== Hover Tests ==========

  "OptionsDiagnostics.getHover" should "return hover for option names in with clause context" in {
    val hover = OptionsDiagnostics.getHover("retry", "result = MyModule(input) with ")
    hover shouldBe defined
    hover.get.contents.value should include("retry")
    hover.get.contents.value should include("Int")
  }

  it should "return hover for timeout option" in {
    val hover = OptionsDiagnostics.getHover("timeout", "result = MyModule(input) with timeout: ")
    hover shouldBe defined
    hover.get.contents.value should include("timeout")
    hover.get.contents.value should include("Duration")
  }

  it should "return hover for backoff strategies" in {
    val hover =
      OptionsDiagnostics.getHover("exponential", "result = MyModule(input) with backoff: ")
    hover shouldBe defined
    hover.get.contents.value should include("exponential")
    hover.get.contents.value should include("Backoff Strategy")
  }

  it should "return hover for error strategies" in {
    val hover = OptionsDiagnostics.getHover("skip", "result = MyModule(input) with on_error: ")
    hover shouldBe defined
    hover.get.contents.value should include("skip")
    hover.get.contents.value should include("Error Strategy")
  }

  it should "return hover for priority levels" in {
    val hover = OptionsDiagnostics.getHover("critical", "result = MyModule(input) with priority: ")
    hover shouldBe defined
    hover.get.contents.value should include("critical")
    hover.get.contents.value should include("Priority Level")
  }

  it should "not return hover outside with clause context" in {
    val hover = OptionsDiagnostics.getHover("retry", "val retry = 3")
    hover shouldBe empty
  }

  it should "not return hover for unknown words" in {
    val hover = OptionsDiagnostics.getHover("unknown_option", "result = MyModule(input) with ")
    hover shouldBe empty
  }

  it should "include related options in hover" in {
    val hover = OptionsDiagnostics.getHover("delay", "result = MyModule(input) with ")
    hover shouldBe defined
    hover.get.contents.value should include("Related options")
    hover.get.contents.value should include("retry")
    hover.get.contents.value should include("backoff")
  }

  it should "include examples in hover" in {
    val hover = OptionsDiagnostics.getHover("cache", "result = MyModule(input) with ")
    hover shouldBe defined
    hover.get.contents.value should include("Example")
    hover.get.contents.value should include("cache:")
  }
}
