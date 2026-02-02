package io.constellation.lang.integration

import io.constellation.lang.ast.*
import io.constellation.lang.parser.ConstellationParser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for parsing module call options (with clause). Tests all 12 options across 4
  * phases of the RFC implementation.
  */
class OptionsParserIntegrationTest extends AnyFlatSpec with Matchers {

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private def parseAndGetOptions(source: String): ModuleCallOptions = {
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations
      .collectFirst { case a: Declaration.Assignment => a }
      .getOrElse(fail("Expected an assignment declaration"))

    assignment.value.value match {
      case fc: Expression.FunctionCall => fc.options
      case _                           => fail("Expected a function call expression")
    }
  }

  // ============================================================================
  // Phase 1: Core Resilience Options (retry, timeout, fallback, cache)
  // ============================================================================

  "Parser" should "parse retry option with integer value" in {
    val source  = """
      in x: Int
      result = GetValue(x) with retry: 3
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(3)
  }

  it should "parse timeout option with various duration units" in {
    val testCases = List(
      ("timeout: 100ms", Duration(100, DurationUnit.Milliseconds)),
      ("timeout: 30s", Duration(30, DurationUnit.Seconds)),
      ("timeout: 5min", Duration(5, DurationUnit.Minutes)),
      ("timeout: 1h", Duration(1, DurationUnit.Hours)),
      ("timeout: 1d", Duration(1, DurationUnit.Days))
    )

    for (optionStr, expectedDuration) <- testCases do {
      val source = s"""
        in x: Int
        result = GetValue(x) with $optionStr
        out result
      """
      val options = parseAndGetOptions(source)
      options.timeout shouldBe Some(expectedDuration)
    }
  }

  it should "parse fallback option with variable reference" in {
    val source  = """
      in x: Int
      in defaultValue: Int
      result = GetValue(x) with fallback: defaultValue
      out result
    """
    val options = parseAndGetOptions(source)
    options.fallback shouldBe defined
    options.fallback.get.value shouldBe a[Expression.VarRef]
  }

  it should "parse fallback option with literal value" in {
    val source  = """
      in x: Int
      result = GetValue(x) with fallback: 0
      out result
    """
    val options = parseAndGetOptions(source)
    options.fallback shouldBe defined
    options.fallback.get.value shouldBe Expression.IntLit(0)
  }

  it should "parse cache option with duration" in {
    val source  = """
      in x: Int
      result = GetValue(x) with cache: 15min
      out result
    """
    val options = parseAndGetOptions(source)
    options.cache shouldBe Some(Duration(15, DurationUnit.Minutes))
  }

  it should "parse all Phase 1 options together" in {
    val source  = """
      in x: Int
      in defaultValue: Int
      result = GetValue(x) with retry: 3, timeout: 30s, fallback: defaultValue, cache: 5min
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(3)
    options.timeout shouldBe Some(Duration(30, DurationUnit.Seconds))
    options.fallback shouldBe defined
    options.cache shouldBe Some(Duration(5, DurationUnit.Minutes))
  }

  // ============================================================================
  // Phase 2: Delay and Backoff Options
  // ============================================================================

  it should "parse delay option with duration" in {
    val source  = """
      in x: Int
      result = GetValue(x) with delay: 1s
      out result
    """
    val options = parseAndGetOptions(source)
    options.delay shouldBe Some(Duration(1, DurationUnit.Seconds))
  }

  it should "parse backoff option with all strategies" in {
    val strategies = List(
      ("backoff: fixed", BackoffStrategy.Fixed),
      ("backoff: linear", BackoffStrategy.Linear),
      ("backoff: exponential", BackoffStrategy.Exponential)
    )

    for (optionStr, expectedStrategy) <- strategies do {
      val source = s"""
        in x: Int
        result = GetValue(x) with $optionStr
        out result
      """
      val options = parseAndGetOptions(source)
      options.backoff shouldBe Some(expectedStrategy)
    }
  }

  it should "parse all Phase 2 options together" in {
    val source  = """
      in x: Int
      result = GetValue(x) with retry: 3, delay: 1s, backoff: exponential
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(3)
    options.delay shouldBe Some(Duration(1, DurationUnit.Seconds))
    options.backoff shouldBe Some(BackoffStrategy.Exponential)
  }

  // ============================================================================
  // Phase 3: Rate Control Options (throttle, concurrency)
  // ============================================================================

  it should "parse throttle option with rate" in {
    val source  = """
      in x: Int
      result = GetValue(x) with throttle: 100/1min
      out result
    """
    val options = parseAndGetOptions(source)
    options.throttle shouldBe defined
    options.throttle.get.count shouldBe 100
    options.throttle.get.per shouldBe Duration(1, DurationUnit.Minutes)
  }

  it should "parse throttle option with various rate formats" in {
    val testCases = List(
      ("throttle: 10/1s", Rate(10, Duration(1, DurationUnit.Seconds))),
      ("throttle: 100/1min", Rate(100, Duration(1, DurationUnit.Minutes))),
      ("throttle: 1000/1h", Rate(1000, Duration(1, DurationUnit.Hours)))
    )

    for (optionStr, expectedRate) <- testCases do {
      val source = s"""
        in x: Int
        result = GetValue(x) with $optionStr
        out result
      """
      val options = parseAndGetOptions(source)
      options.throttle shouldBe Some(expectedRate)
    }
  }

  it should "parse concurrency option with integer value" in {
    val source  = """
      in x: Int
      result = GetValue(x) with concurrency: 5
      out result
    """
    val options = parseAndGetOptions(source)
    options.concurrency shouldBe Some(5)
  }

  it should "parse all Phase 3 options together" in {
    val source  = """
      in x: Int
      result = GetValue(x) with throttle: 100/1min, concurrency: 5
      out result
    """
    val options = parseAndGetOptions(source)
    options.throttle shouldBe Some(Rate(100, Duration(1, DurationUnit.Minutes)))
    options.concurrency shouldBe Some(5)
  }

  // ============================================================================
  // Phase 4: Advanced Options (on_error, lazy, priority)
  // ============================================================================

  it should "parse on_error option with all strategies" in {
    val strategies = List(
      ("on_error: propagate", ErrorStrategy.Propagate),
      ("on_error: skip", ErrorStrategy.Skip),
      ("on_error: log", ErrorStrategy.Log),
      ("on_error: wrap", ErrorStrategy.Wrap)
    )

    for (optionStr, expectedStrategy) <- strategies do {
      val source = s"""
        in x: Int
        result = GetValue(x) with $optionStr
        out result
      """
      val options = parseAndGetOptions(source)
      options.onError shouldBe Some(expectedStrategy)
    }
  }

  it should "parse lazy option as flag" in {
    val source  = """
      in x: Int
      result = GetValue(x) with lazy
      out result
    """
    val options = parseAndGetOptions(source)
    options.lazyEval shouldBe Some(true)
  }

  it should "parse lazy option with explicit boolean" in {
    val source  = """
      in x: Int
      result = GetValue(x) with lazy: true
      out result
    """
    val options = parseAndGetOptions(source)
    options.lazyEval shouldBe Some(true)

    val source2  = """
      in x: Int
      result = GetValue(x) with lazy: false
      out result
    """
    val options2 = parseAndGetOptions(source2)
    options2.lazyEval shouldBe Some(false)
  }

  it should "parse priority option with named levels" in {
    val levels = List(
      ("priority: critical", PriorityLevel.Critical),
      ("priority: high", PriorityLevel.High),
      ("priority: normal", PriorityLevel.Normal),
      ("priority: low", PriorityLevel.Low),
      ("priority: background", PriorityLevel.Background)
    )

    for (optionStr, expectedLevel) <- levels do {
      val source = s"""
        in x: Int
        result = GetValue(x) with $optionStr
        out result
      """
      val options = parseAndGetOptions(source)
      options.priority shouldBe Some(Left(expectedLevel))
    }
  }

  it should "parse priority option with numeric value" in {
    val source  = """
      in x: Int
      result = GetValue(x) with priority: 75
      out result
    """
    val options = parseAndGetOptions(source)
    options.priority shouldBe Some(Right(CustomPriority(75)))
  }

  it should "parse all Phase 4 options together" in {
    val source  = """
      in x: Int
      result = GetValue(x) with on_error: skip, lazy: true, priority: high
      out result
    """
    val options = parseAndGetOptions(source)
    options.onError shouldBe Some(ErrorStrategy.Skip)
    options.lazyEval shouldBe Some(true)
    options.priority shouldBe Some(Left(PriorityLevel.High))
  }

  // ============================================================================
  // Cache Backend Option
  // ============================================================================

  it should "parse cache_backend option with quoted string" in {
    val source  = """
      in x: Int
      result = GetValue(x) with cache: 5min, cache_backend: "redis"
      out result
    """
    val options = parseAndGetOptions(source)
    options.cache shouldBe Some(Duration(5, DurationUnit.Minutes))
    options.cacheBackend shouldBe Some("redis")
  }

  // ============================================================================
  // Comprehensive Tests
  // ============================================================================

  it should "parse all 12 options together" in {
    val source  = """
      in x: Int
      in defaultValue: Int
      result = GetValue(x) with
          retry: 3,
          timeout: 30s,
          delay: 1s,
          backoff: exponential,
          fallback: defaultValue,
          cache: 5min,
          cache_backend: "memory",
          throttle: 100/1min,
          concurrency: 5,
          on_error: log,
          lazy: true,
          priority: high
      out result
    """
    val options = parseAndGetOptions(source)

    // Verify all options are parsed
    options.retry shouldBe Some(3)
    options.timeout shouldBe Some(Duration(30, DurationUnit.Seconds))
    options.delay shouldBe Some(Duration(1, DurationUnit.Seconds))
    options.backoff shouldBe Some(BackoffStrategy.Exponential)
    options.fallback shouldBe defined
    options.cache shouldBe Some(Duration(5, DurationUnit.Minutes))
    options.cacheBackend shouldBe Some("memory")
    options.throttle shouldBe Some(Rate(100, Duration(1, DurationUnit.Minutes)))
    options.concurrency shouldBe Some(5)
    options.onError shouldBe Some(ErrorStrategy.Log)
    options.lazyEval shouldBe Some(true)
    options.priority shouldBe Some(Left(PriorityLevel.High))
  }

  it should "parse options on qualified function calls" in {
    val source  = """
      in x: Int
      result = stdlib.compute.process(x) with retry: 3, timeout: 10s
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(3)
    options.timeout shouldBe Some(Duration(10, DurationUnit.Seconds))
  }

  it should "parse options in multi-line format" in {
    val source  = """
      in x: Int
      result = GetValue(x) with
          retry: 5,
          delay: 500ms,
          backoff: linear
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(5)
    options.delay shouldBe Some(Duration(500, DurationUnit.Milliseconds))
    options.backoff shouldBe Some(BackoffStrategy.Linear)
  }

  it should "parse function call without options (empty options)" in {
    val source = """
      in x: Int
      result = GetValue(x)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations.collectFirst { case a: Declaration.Assignment => a }.get

    val fc = assignment.value.value.asInstanceOf[Expression.FunctionCall]
    fc.options.isEmpty shouldBe true
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  it should "handle large duration values" in {
    val source  = """
      in x: Int
      result = GetValue(x) with timeout: 3600s, cache: 1440min
      out result
    """
    val options = parseAndGetOptions(source)
    options.timeout shouldBe Some(Duration(3600, DurationUnit.Seconds))
    options.cache shouldBe Some(Duration(1440, DurationUnit.Minutes))
  }

  it should "handle zero retry value" in {
    val source  = """
      in x: Int
      result = GetValue(x) with retry: 0
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(0)
  }

  it should "parse fallback with complex expression" in {
    val source  = """
      in x: Int
      in fallbackRecord: { value: Int, message: String }
      result = GetValue(x) with fallback: fallbackRecord
      out result
    """
    val options = parseAndGetOptions(source)
    options.fallback shouldBe defined
  }

  it should "parse options after multi-argument function call" in {
    val source  = """
      in a: Int
      in b: Int
      in c: Int
      result = Compute(a, b, c) with retry: 2, timeout: 5s
      out result
    """
    val options = parseAndGetOptions(source)
    options.retry shouldBe Some(2)
    options.timeout shouldBe Some(Duration(5, DurationUnit.Seconds))
  }
}
