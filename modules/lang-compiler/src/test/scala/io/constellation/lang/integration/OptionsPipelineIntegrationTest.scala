package io.constellation.lang.integration

import io.constellation.ModuleCallOptions
import io.constellation.lang.*
import io.constellation.lang.compiler.CompilationOutput
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for verifying module call options flow through the full compilation pipeline
  * from source to CompilationOutput.
  */
class OptionsPipelineIntegrationTest extends AnyFlatSpec with Matchers {

  // Create a compiler with a test function registered
  private def compilerWithFunction(name: String): LangCompiler =
    LangCompiler.builder
      .withFunction(
        FunctionSignature(
          name = name,
          params = List("x" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = name
        )
      )
      .build

  private def compileWithModule(
      source: String,
      moduleName: String
  ): Either[Any, CompilationOutput] = {
    val compiler = compilerWithFunction(moduleName)
    compiler.compile(source, "test-dag")
  }

  private def getModuleOptions(result: CompilationOutput): Option[ModuleCallOptions] =
    result.pipeline.image.moduleOptions.values.headOption

  // ============================================================================
  // Basic Pipeline Tests
  // ============================================================================

  "Full pipeline" should "compile program with retry option" in {
    val source = """
      in x: Int
      result = TestModule(x) with retry: 3
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    val options  = getModuleOptions(compiled)
    options shouldBe defined
    options.get.retry shouldBe Some(3)
  }

  it should "compile program with timeout option" in {
    val source = """
      in x: Int
      result = TestModule(x) with timeout: 30s
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.timeoutMs shouldBe Some(30000L) // 30s = 30000ms
  }

  it should "compile program with delay option" in {
    val source = """
      in x: Int
      result = TestModule(x) with delay: 500ms
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.delayMs shouldBe Some(500L)
  }

  it should "compile program with backoff option" in {
    val source = """
      in x: Int
      result = TestModule(x) with backoff: exponential
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.backoff shouldBe Some("exponential")
  }

  it should "compile program with cache option" in {
    val source = """
      in x: Int
      result = TestModule(x) with cache: 5min
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.cacheMs shouldBe Some(300000L) // 5min = 300000ms
  }

  it should "compile program with cache_backend option" in {
    val source = """
      in x: Int
      result = TestModule(x) with cache: 5min, cache_backend: "redis"
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.cacheBackend shouldBe Some("redis")
  }

  it should "compile program with throttle option" in {
    val source = """
      in x: Int
      result = TestModule(x) with throttle: 100/1min
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.throttleCount shouldBe Some(100)
    options.get.throttlePerMs shouldBe Some(60000L) // 1min = 60000ms
  }

  it should "compile program with concurrency option" in {
    val source = """
      in x: Int
      result = TestModule(x) with concurrency: 5
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.concurrency shouldBe Some(5)
  }

  it should "compile program with on_error option" in {
    val source = """
      in x: Int
      result = TestModule(x) with on_error: skip
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.onError shouldBe Some("skip")
  }

  it should "compile program with lazy option" in {
    val source = """
      in x: Int
      result = TestModule(x) with lazy: true
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.lazyEval shouldBe Some(true)
  }

  it should "compile program with priority option (named level)" in {
    val source = """
      in x: Int
      result = TestModule(x) with priority: high
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    // high priority should be normalized to 75 (or similar)
    options.get.priority shouldBe defined
  }

  it should "compile program with priority option (numeric)" in {
    val source = """
      in x: Int
      result = TestModule(x) with priority: 90
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.priority shouldBe Some(90)
  }

  // ============================================================================
  // Combined Options Tests
  // ============================================================================

  it should "compile program with multiple Phase 1 options" in {
    val source = """
      in x: Int
      result = TestModule(x) with retry: 3, timeout: 30s, cache: 5min
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.retry shouldBe Some(3)
    options.get.timeoutMs shouldBe Some(30000L)
    options.get.cacheMs shouldBe Some(300000L)
  }

  it should "compile program with retry, delay, and backoff" in {
    val source = """
      in x: Int
      result = TestModule(x) with retry: 5, delay: 1s, backoff: exponential
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.retry shouldBe Some(5)
    options.get.delayMs shouldBe Some(1000L)
    options.get.backoff shouldBe Some("exponential")
  }

  it should "compile program with rate control options" in {
    val source = """
      in x: Int
      result = TestModule(x) with throttle: 50/1s, concurrency: 10
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.throttleCount shouldBe Some(50)
    options.get.throttlePerMs shouldBe Some(1000L)
    options.get.concurrency shouldBe Some(10)
  }

  it should "compile program with all options" in {
    val source = """
      in x: Int
      result = TestModule(x) with
          retry: 3,
          timeout: 30s,
          delay: 1s,
          backoff: exponential,
          cache: 5min,
          cache_backend: "memory",
          throttle: 100/1min,
          concurrency: 5,
          on_error: log,
          lazy: true,
          priority: high
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
    options.get.retry shouldBe Some(3)
    options.get.timeoutMs shouldBe Some(30000L)
    options.get.delayMs shouldBe Some(1000L)
    options.get.backoff shouldBe Some("exponential")
    options.get.cacheMs shouldBe Some(300000L)
    options.get.cacheBackend shouldBe Some("memory")
    options.get.throttleCount shouldBe Some(100)
    options.get.throttlePerMs shouldBe Some(60000L)
    options.get.concurrency shouldBe Some(5)
    options.get.onError shouldBe Some("log")
    options.get.lazyEval shouldBe Some(true)
    options.get.priority shouldBe defined
  }

  // ============================================================================
  // Fallback Tests
  // ============================================================================

  it should "compile program with fallback as variable reference" in {
    val source = """
      in x: Int
      in defaultValue: Int
      result = TestModule(x) with fallback: defaultValue
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    // Fallback is resolved at IR level (not in ModuleCallOptions)
    // Verify compilation succeeds and options are present
    val compiled = result.toOption.get
    val options  = getModuleOptions(compiled)
    options shouldBe defined
  }

  it should "compile program with fallback as literal" in {
    val source = """
      in x: Int
      result = TestModule(x) with fallback: 0
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    // Fallback is resolved at IR level (not in ModuleCallOptions)
    val options = getModuleOptions(result.toOption.get)
    options shouldBe defined
  }

  // ============================================================================
  // No Options Tests
  // ============================================================================

  it should "compile program without options (empty moduleOptions)" in {
    val source = """
      in x: Int
      result = TestModule(x)
      out result
    """
    val result = compileWithModule(source, "TestModule")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // No options should mean moduleOptions is empty
    compiled.pipeline.image.moduleOptions shouldBe empty
  }

  // ============================================================================
  // Duration Conversion Tests
  // ============================================================================

  it should "correctly convert all duration units to milliseconds" in {
    val testCases = List(
      ("timeout: 100ms", 100L),
      ("timeout: 5s", 5000L),
      ("timeout: 2min", 120000L),
      ("timeout: 1h", 3600000L),
      ("timeout: 1d", 86400000L)
    )

    for (optionStr, expectedMs) <- testCases do {
      val source = s"""
        in x: Int
        result = TestModule(x) with $optionStr
        out result
      """
      val result = compileWithModule(source, "TestModule")
      result.isRight shouldBe true

      val options = getModuleOptions(result.toOption.get)
      options.get.timeoutMs shouldBe Some(expectedMs)
    }
  }

  // ============================================================================
  // Error Strategy Conversion Tests
  // ============================================================================

  it should "correctly convert all error strategies" in {
    val testCases = List(
      ("on_error: propagate", "propagate"),
      ("on_error: skip", "skip"),
      ("on_error: log", "log"),
      ("on_error: wrap", "wrap")
    )

    for (optionStr, expectedStrategy) <- testCases do {
      val source = s"""
        in x: Int
        result = TestModule(x) with $optionStr
        out result
      """
      val result = compileWithModule(source, "TestModule")
      result.isRight shouldBe true

      val options = getModuleOptions(result.toOption.get)
      options.get.onError shouldBe Some(expectedStrategy)
    }
  }

  // ============================================================================
  // Backoff Strategy Conversion Tests
  // ============================================================================

  it should "correctly convert all backoff strategies" in {
    val testCases = List(
      ("backoff: fixed", "fixed"),
      ("backoff: linear", "linear"),
      ("backoff: exponential", "exponential")
    )

    for (optionStr, expectedStrategy) <- testCases do {
      val source = s"""
        in x: Int
        result = TestModule(x) with $optionStr
        out result
      """
      val result = compileWithModule(source, "TestModule")
      result.isRight shouldBe true

      val options = getModuleOptions(result.toOption.get)
      options.get.backoff shouldBe Some(expectedStrategy)
    }
  }

  // ============================================================================
  // Multi-Module Pipeline Tests
  // ============================================================================

  it should "compile pipeline with different options per module" in {
    val compiler = LangCompiler.builder
      .withFunction(
        FunctionSignature(
          name = "ModuleA",
          params = List("x" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = "ModuleA"
        )
      )
      .withFunction(
        FunctionSignature(
          name = "ModuleB",
          params = List("x" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = "ModuleB"
        )
      )
      .build

    val source = """
      in x: Int
      a = ModuleA(x) with retry: 3
      b = ModuleB(a) with cache: 5min
      out b
    """

    val result = compiler.compile(source, "test-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have options for both modules
    compiled.pipeline.image.moduleOptions.size shouldBe 2

    // Verify different options for each
    val allOptions    = compiled.pipeline.image.moduleOptions.values.toList
    val retriesOption = allOptions.find(_.retry.isDefined)
    val cacheOption   = allOptions.find(_.cacheMs.isDefined)

    retriesOption shouldBe defined
    retriesOption.get.retry shouldBe Some(3)

    cacheOption shouldBe defined
    cacheOption.get.cacheMs shouldBe Some(300000L)
  }
}
